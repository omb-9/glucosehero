package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.util.AppJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OpenFoodFactsApiTest {

    private fun createApi(
        responseCode: Int = 200,
        responseJson: String = "{}",
        onRequestIntercepted: (Request) -> Unit = {},
    ): OpenFoodFactsApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                onRequestIntercepted(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message(if (responseCode == 200) "OK" else "Error")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(OpenFoodFactsApi::class.java)
    }

    @Test
    fun `product requests api-v2-product endpoint with barcode and default projected fields`() = runTest {
        var recordedRequest: Request? = null
        val api = createApi(
            responseJson = """{"status":1,"code":"3017620422003","product":{"product_name":"Nutella"}}"""
        ) { request ->
            recordedRequest = request
        }

        val response = api.product("3017620422003")

        assertNotNull(recordedRequest)
        assertEquals("/api/v2/product/3017620422003.json", recordedRequest!!.url.encodedPath)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals(
            "code,product_name,brands,quantity,serving_size,nutriments",
            recordedRequest!!.url.queryParameter("fields")
        )
        assertEquals(OpenFoodFactsApi.FIELDS, recordedRequest!!.url.queryParameter("fields"))
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `product allows custom fields projection query parameter`() = runTest {
        var recordedRequest: Request? = null
        val api = createApi(
            responseJson = """{"status":1,"code":"3017620422003","product":{"product_name":"Nutella"}}"""
        ) { request ->
            recordedRequest = request
        }

        api.product("3017620422003", "code,product_name")

        assertNotNull(recordedRequest)
        assertEquals("/api/v2/product/3017620422003.json", recordedRequest!!.url.encodedPath)
        assertEquals("code,product_name", recordedRequest!!.url.queryParameter("fields"))
    }

    @Test
    fun `product decodes successful status 1 response with projected fields`() = runTest {
        val payload = """
            {
              "status": 1,
              "status_verbose": "product found",
              "code": "3017620422003",
              "product": {
                "code": "3017620422003",
                "product_name": "Nutella",
                "brands": "Ferrero",
                "quantity": "400 g",
                "serving_size": "15 g",
                "nutriments": {
                  "carbohydrates_100g": 57.5,
                  "proteins_100g": 6.3,
                  "fat_100g": 30.9,
                  "energy-kcal_100g": 539,
                  "carbohydrates_serving": 8.6,
                  "proteins_serving": 0.9,
                  "fat_serving": 4.6,
                  "energy-kcal_serving": 81
                }
              }
            }
        """.trimIndent()

        val api = createApi(responseCode = 200, responseJson = payload)
        val response = api.product("3017620422003")

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull(body)
        assertEquals(1, body!!.status)
        assertEquals("product found", body.statusVerbose)
        assertTrue(body.isFound)
        assertFalse(body.isNotFound)

        val product = body.product
        assertNotNull(product)
        assertEquals("Nutella", product!!.productName)
        assertEquals("Ferrero", product.brands)
        assertEquals("400 g", product.quantity)
        assertEquals("15 g", product.servingSize)
        assertEquals(57.5, product.nutriments?.carbs100g!!, 1e-9)
        assertEquals(8.6, product.nutriments?.carbsServing!!, 1e-9)
    }

    @Test
    fun `product decodes status 0 not found response as successful HTTP 200 and not an HTTP error`() = runTest {
        val payload = """
            {
              "status": 0,
              "status_verbose": "product not found",
              "code": "0000000000000"
            }
        """.trimIndent()

        val api = createApi(responseCode = 200, responseJson = payload)
        val response = api.product("0000000000000")

        // Crucial requirement: status == 0 is an HTTP 200 payload, NOT an HTTP error.
        assertTrue("Response must be HTTP successful (200 OK)", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull(body)
        assertEquals(0, body!!.status)
        assertEquals("product not found", body.statusVerbose)
        assertTrue("isNotFound must be true for status 0", body.isNotFound)
        assertFalse("isFound must be false for status 0", body.isFound)
        assertNull("Product must be null when not found", body.product)
        assertEquals("0000000000000", body.code)
    }

    @Test
    fun `product handles HTTP 404 response gracefully`() = runTest {
        val api = createApi(responseCode = 404, responseJson = "Not Found")
        val response = api.product("0000000000000")

        assertFalse(response.isSuccessful)
        assertEquals(404, response.code())
        assertNull(response.body())
    }

    @Test
    fun `search requests expected URL path and default projected search fields`() = runTest {
        var recordedRequest: Request? = null
        val api = createApi(
            responseJson = """{"count":1,"products":[{"product_name":"Nutella"}]}"""
        ) { request ->
            recordedRequest = request
        }

        val response = api.search("nutella")

        assertNotNull(recordedRequest)
        assertEquals("/api/v2/search", recordedRequest!!.url.encodedPath)
        assertEquals("nutella", recordedRequest!!.url.queryParameter("search_terms"))
        assertEquals(OpenFoodFactsApi.SEARCH_FIELDS, recordedRequest!!.url.queryParameter("fields"))
        assertEquals("20", recordedRequest!!.url.queryParameter("page_size"))
        assertEquals("1", recordedRequest!!.url.queryParameter("json"))
        assertTrue(response.isSuccessful)
    }
}
