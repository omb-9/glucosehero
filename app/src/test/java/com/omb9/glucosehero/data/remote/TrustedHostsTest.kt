package com.omb9.glucosehero.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import okhttp3.OkHttpClient

class TrustedHostsTest {

    @Test
    fun builtInHostsMatchAppDestinations() {
        val hosts = listOf(
            "api.openai.com",
            "openrouter.ai",
            "generativelanguage.googleapis.com",
            "www.googleapis.com",
            "world.openfoodfacts.org",
            "static.openfoodfacts.org",
            "glucosehero.app",
            "www.glucosehero.app",
            "chromagrid.com",
        )
        for (host in hosts) {
            assertTrue(host, TrustedHosts.isTrustedBuiltIn(host))
        }
    }

    @Test
    fun unknownPublicHostIsDeniedUntilAcknowledged() {
        assertFalse(TrustedHosts.isAllowed("evil.example", emptySet()))
        assertTrue(TrustedHosts.isAllowed("evil.example", setOf("evil.example")))
    }

    @Test
    fun hostMatchingIsCaseInsensitiveAndIgnoresBrackets() {
        assertTrue(TrustedHosts.isTrustedBuiltIn("API.OpenAI.COM"))
        assertEquals("evil.example", TrustedHosts.normalizeHost("[Evil.Example]"))
    }
}

class AiEndpointGuardTest {

    @Test
    fun presetHttpsProvidersAreAllowedWithoutAck() {
        assertEquals(
            AiEndpointGuard.Status.Allowed,
            AiEndpointGuard.evaluate("https://api.openai.com/v1/", emptySet()),
        )
        assertEquals(
            AiEndpointGuard.Status.Allowed,
            AiEndpointGuard.evaluate(
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                emptySet(),
            ),
        )
        assertEquals(
            AiEndpointGuard.Status.Allowed,
            AiEndpointGuard.evaluate("https://openrouter.ai/api/v1/", emptySet()),
        )
    }

    @Test
    fun unknownHttpsHostNeedsAcknowledgment() {
        val status = AiEndpointGuard.evaluate("https://ollama.example/v1/", emptySet())
        assertTrue(status is AiEndpointGuard.Status.NeedsAcknowledgment)
        assertEquals("ollama.example", (status as AiEndpointGuard.Status.NeedsAcknowledgment).host)
        assertTrue(AiEndpointGuard.needsAcknowledgment("https://ollama.example/v1/", emptySet()))
    }

    @Test
    fun acknowledgedCustomHttpsHostIsAllowed() {
        assertEquals(
            AiEndpointGuard.Status.Allowed,
            AiEndpointGuard.evaluate("https://ollama.example/v1/", setOf("ollama.example")),
        )
    }

    @Test
    fun publicHttpIsRejectedEvenWhenAcknowledged() {
        assertEquals(
            AiEndpointGuard.Status.HttpsRequired,
            AiEndpointGuard.evaluate("http://api.openai.com/v1/", emptySet()),
        )
        assertEquals(
            AiEndpointGuard.Status.HttpsRequired,
            AiEndpointGuard.evaluate("http://evil.example/v1/", setOf("evil.example")),
        )
    }

    @Test
    fun privateHttpIsAllowedOnlyAfterAcknowledgment() {
        assertEquals(
            AiEndpointGuard.Status.HttpsRequired,
            AiEndpointGuard.evaluate("http://192.168.1.10:11434/v1/", emptySet()),
        )
        assertEquals(
            AiEndpointGuard.Status.Allowed,
            AiEndpointGuard.evaluate(
                "http://192.168.1.10:11434/v1/",
                setOf("192.168.1.10"),
            ),
        )
    }

    @Test
    fun invalidUrlIsRejected() {
        assertEquals(AiEndpointGuard.Status.InvalidUrl, AiEndpointGuard.evaluate("not a url", emptySet()))
    }
}

class AuthRedirectSanitizerTest {

    @Test
    fun stripSecretsRemovesBearerAndApiKeyQuery() {
        val request = Request.Builder()
            .url("https://evil.example/v1/chat?api_key=secret&model=x".toHttpUrl())
            .header("Authorization", "Bearer sk-test")
            .header("X-Api-Key", "secret")
            .header("x-goog-api-key", "secret")
            .build()

        val stripped = AuthRedirectSanitizer.stripSecrets(request)
        assertNull(stripped.header("Authorization"))
        assertNull(stripped.header("X-Api-Key"))
        assertNull(stripped.header("x-goog-api-key"))
        assertNull(stripped.url.queryParameter("api_key"))
        assertEquals("x", stripped.url.queryParameter("model"))
    }

    @Test
    fun interceptRefusesUntrustedHostAfterStripping() {
        val sanitizer = AuthRedirectSanitizer()
        var sawAuthOnProceed = false
        val client = OkHttpClient.Builder()
            .addInterceptor(sanitizer)
            .addInterceptor { chain ->
                sawAuthOnProceed = chain.request().header("Authorization") != null
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
            .url("https://evil.example/v1/chat")
            .header("Authorization", "Bearer sk-test")
            .build()

        val error = assertThrows(IOException::class.java) {
            client.newCall(request).execute()
        }
        assertTrue(error.message!!.contains("evil.example"))
        assertFalse(sawAuthOnProceed)
    }

    @Test
    fun interceptAllowsTrustedHostAndKeepsAuthorization() {
        var recordedAuth: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthRedirectSanitizer())
            .addInterceptor { chain ->
                recordedAuth = chain.request().header("Authorization")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer sk-test")
                .build(),
        ).execute().close()

        assertEquals("Bearer sk-test", recordedAuth)
    }
}
