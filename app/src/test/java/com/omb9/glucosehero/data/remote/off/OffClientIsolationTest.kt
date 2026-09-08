package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import com.omb9.glucosehero.di.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import javax.inject.Named

class OffClientIsolationTest {

    @Test
    fun `off client does not carry the dynamic api interceptor`() {
        val client = NetworkModule.provideOffOkHttpClient()

        assertTrue(client.interceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.networkInterceptors.none { it is DynamicApiInterceptor })
    }

    @Test
    fun `off client carries user agent interceptor`() {
        val client = NetworkModule.provideOffOkHttpClient()

        assertTrue(client.interceptors.any { it is OpenFoodFactsUserAgentInterceptor })
    }

    @Test
    fun `off retrofit has real open food facts base url`() {
        val client = NetworkModule.provideOffOkHttpClient()
        val retrofit = NetworkModule.provideOffRetrofit(client)

        assertEquals("https://world.openfoodfacts.org/", retrofit.baseUrl().toString())
    }

    @Test
    fun `off retrofit uses provided okhttp client`() {
        val client = NetworkModule.provideOffOkHttpClient()
        val retrofit = NetworkModule.provideOffRetrofit(client)

        assertEquals(client, retrofit.callFactory())
    }

    @Test
    fun `off client attaches required user agent header`() {
        var recordedUserAgent: String? = null
        val client = NetworkModule.provideOffOkHttpClient().newBuilder()
            .addInterceptor { chain ->
                recordedUserAgent = chain.request().header("User-Agent")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/123456.json")
            .build()
        val response = client.newCall(request).execute()
        response.close()

        assertEquals("GlucoseHero/${BuildConfig.VERSION_NAME} (outreach@chromagrid.com)", recordedUserAgent)
    }

    @Test
    fun `provideOffOkHttpClient has Named openfoodfacts qualifier`() {
        val method = NetworkModule::class.java.methods.first { it.name == "provideOffOkHttpClient" }
        val named = method.getAnnotation(Named::class.java)
        assertNotNull(named)
        assertEquals("openfoodfacts", named.value)
    }

    @Test
    fun `provideOffRetrofit has Named openfoodfacts qualifier and parameter`() {
        val method = NetworkModule::class.java.methods.first {
            it.name == "provideOffRetrofit" && it.parameterTypes.contains(OkHttpClient::class.java)
        }
        val named = method.getAnnotation(Named::class.java)
        assertNotNull(named)
        assertEquals("openfoodfacts", named.value)

        val paramAnnotation = method.parameterAnnotations[0].filterIsInstance<Named>().firstOrNull()
        assertNotNull(paramAnnotation)
        assertEquals("openfoodfacts", paramAnnotation?.value)
    }

    @Test
    fun `provideOpenFoodFactsApi parameter has Named openfoodfacts qualifier`() {
        val method = NetworkModule::class.java.methods.first {
            it.name == "provideOpenFoodFactsApi" && it.parameterTypes.contains(Retrofit::class.java)
        }
        val paramAnnotation = method.parameterAnnotations[0].filterIsInstance<Named>().firstOrNull()
        assertNotNull(paramAnnotation)
        assertEquals("openfoodfacts", paramAnnotation?.value)
    }

    @Test
    fun `provideOpenFoodFactsApi creates api instance`() {
        val client = NetworkModule.provideOffOkHttpClient()
        val retrofit = NetworkModule.provideOffRetrofit(client)
        val api = NetworkModule.provideOpenFoodFactsApi(retrofit)

        assertNotNull(api)
    }
}
