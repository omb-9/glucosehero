package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsUserAgentInterceptorTest {

    @Test
    fun `default user agent contains version name and contact email`() {
        val interceptor = OpenFoodFactsUserAgentInterceptor()
        val expected = "GlucoseHero/${BuildConfig.VERSION_NAME} (outreach@chromagrid.com)"

        assertEquals(expected, interceptor.userAgent)
        assertEquals(expected, OpenFoodFactsUserAgentInterceptor.DEFAULT_USER_AGENT)
    }

    @Test
    fun `interceptor sets User-Agent header on request`() {
        var recordedUserAgent: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(OpenFoodFactsUserAgentInterceptor())
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
        client.newCall(request).execute().close()

        val expected = "GlucoseHero/${BuildConfig.VERSION_NAME} (outreach@chromagrid.com)"
        assertEquals(expected, recordedUserAgent)
    }

    @Test
    fun `interceptor overwrites pre-existing User-Agent header`() {
        var recordedUserAgent: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(OpenFoodFactsUserAgentInterceptor())
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
            .header("User-Agent", "GenericBrowser/1.0")
            .build()
        client.newCall(request).execute().close()

        val expected = "GlucoseHero/${BuildConfig.VERSION_NAME} (outreach@chromagrid.com)"
        assertEquals(expected, recordedUserAgent)
    }

    @Test
    fun `custom contact email and version name can be configured`() {
        val interceptor = OpenFoodFactsUserAgentInterceptor(
            contactEmail = "dev@example.com",
            versionName = "2.5.0"
        )
        assertEquals("GlucoseHero/2.5.0 (dev@example.com)", interceptor.userAgent)

        var recordedUserAgent: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
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
        client.newCall(request).execute().close()

        assertEquals("GlucoseHero/2.5.0 (dev@example.com)", recordedUserAgent)
    }

    @Test
    fun `typealias OffUserAgentInterceptor is compatible`() {
        val interceptor: OffUserAgentInterceptor = OpenFoodFactsUserAgentInterceptor()
        assertTrue(interceptor is OpenFoodFactsUserAgentInterceptor)
    }
}
