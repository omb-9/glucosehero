package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthMode
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutCleartextException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutHostUnacknowledgedException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutV1UnsupportedException
import com.omb9.glucosehero.data.remote.CleartextGuardInterceptor
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NightscoutCgmSourceTest {

    @Test
    fun fetch_tokenQueryAndMapsEntries() = runBlocking {
        var recordedToken: String? = null
        var recordedSecret: String? = null
        var recordedAuth: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                recordedToken = chain.request().url.queryParameter("token")
                recordedSecret = chain.request().header("api-secret")
                recordedAuth = chain.request().header("Authorization")
                jsonResponse(
                    chain.request(),
                    """[{"_id":"n1","sgv":140,"date":1700000000000,"direction":"FortyFiveUp"}]""",
                )
            }
            .build()

        val source = source(
            client = client,
            snapshot = snapshot(authMode = NightscoutAuthMode.TOKEN, credential = "abc-token"),
        )
        val samples = source.fetch(1_700_000_000_000L - 1)
        assertEquals(1, samples.size)
        assertEquals("n1", samples.single().externalId)
        assertEquals(140.0, samples.single().glucoseMgdl, 0.0)
        assertEquals("FortyFiveUp", samples.single().trendArrow)
        assertEquals("abc-token", recordedToken)
        assertNull(recordedSecret)
        assertNull(recordedAuth)
    }

    @Test
    fun fetch_http404IsV1Unsupported() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("not found".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val source = source(client = client, snapshot = snapshot())
        assertThrows(NightscoutV1UnsupportedException::class.java) {
            runBlocking { source.fetch(0L) }
        }
    }

    @Test
    fun fetch_publicHttpThrowsCleartextBeforeNetwork() {
        val client = OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor())
            .addInterceptor { chain -> jsonResponse(chain.request(), "[]") }
            .build()
        val source = source(
            client = client,
            snapshot = snapshot(
                url = "http://mysite.example.com",
                acknowledged = setOf("mysite.example.com"),
            ),
        )
        assertThrows(NightscoutCleartextException::class.java) {
            runBlocking { source.fetch(0L) }
        }
    }

    @Test
    fun fetch_unacknowledgedHostDoesNotSend() {
        var sent = false
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                sent = true
                jsonResponse(chain.request(), "[]")
            }
            .build()
        val source = source(
            client = client,
            snapshot = snapshot(acknowledged = emptySet()),
        )
        assertThrows(NightscoutHostUnacknowledgedException::class.java) {
            runBlocking { source.fetch(0L) }
        }
        assertTrue(!sent)
    }

    @Test
    fun fetch_clientDoesNotCarryDynamicApiInterceptor() {
        val client = OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor())
            .build()
        assertTrue(client.interceptors.none { it is DynamicApiInterceptor })
    }

    private fun source(
        client: OkHttpClient,
        snapshot: NightscoutFetchSnapshot,
    ) = NightscoutCgmSource(
        client = client,
        loadSnapshot = { snapshot },
        decryptCredential = { it },
        nowMillis = { 1_700_086_400_000L },
    )

    private fun snapshot(
        url: String = "https://ns.example.com",
        authMode: NightscoutAuthMode = NightscoutAuthMode.TOKEN,
        credential: String = "secret",
        acknowledged: Set<String> = setOf("ns.example.com"),
        backfillHours: Int = NightscoutLimits.DEFAULT_BACKFILL_HOURS,
    ) = NightscoutFetchSnapshot(
        url = url,
        authMode = authMode,
        encryptedCredential = credential,
        acknowledgedHosts = acknowledged,
        backfillHours = backfillHours,
    )

    private fun jsonResponse(request: okhttp3.Request, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
