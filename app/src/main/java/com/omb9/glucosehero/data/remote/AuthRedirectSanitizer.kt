package com.omb9.glucosehero.data.remote

import com.omb9.glucosehero.domain.model.ProviderHttpException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Network interceptor that inspects every hop, including redirects.
 *
 * Untrusted destinations never keep [Authorization] or API token headers or
 * query parameters. The hop is then refused so a 302 cannot leak the prompt
 * body to an unknown host.
 */
class AuthRedirectSanitizer : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val acknowledged = request.tag(AcknowledgedHostsTag::class.java)?.hosts ?: emptySet()
        val status = AiEndpointGuard.evaluate(request.url, acknowledged)
        if (status is AiEndpointGuard.Status.Allowed) {
            return chain.proceed(request)
        }

        // Strip first so a logged request or accidental proceed cannot keep tokens.
        val stripped = stripSecrets(request)
        val host = stripped.url.host
        val message = when (status) {
            AiEndpointGuard.Status.HttpsRequired ->
                "Refusing non-HTTPS AI endpoint at $host."
            is AiEndpointGuard.Status.NeedsAcknowledgment ->
                "Refusing untrusted AI host $host."
            AiEndpointGuard.Status.InvalidUrl ->
                "Refusing invalid AI endpoint."
            AiEndpointGuard.Status.Allowed ->
                "Refusing untrusted AI host $host."
        }
        throw ProviderHttpException(message)
    }

    companion object {
        val SENSITIVE_HEADERS = listOf(
            "Authorization",
            "Proxy-Authorization",
            "X-Api-Key",
            "X-API-Key",
            "api-key",
            "x-goog-api-key",
            "OpenAI-Api-Key",
        )

        val SENSITIVE_QUERY_PARAMS = setOf(
            "key",
            "api_key",
            "apikey",
            "access_token",
            "token",
            "auth",
        )

        fun stripSecrets(request: Request): Request {
            val builder = request.newBuilder()
            for (header in SENSITIVE_HEADERS) {
                builder.removeHeader(header)
            }
            val strippedUrl = stripSensitiveQuery(request.url)
            builder.url(strippedUrl)
            return builder.build()
        }

        fun stripSensitiveQuery(url: HttpUrl): HttpUrl {
            val builder = url.newBuilder()
            val names = url.queryParameterNames
            if (names.none { SENSITIVE_QUERY_PARAMS.contains(it.lowercase()) }) {
                return url
            }
            builder.query(null)
            for (name in names) {
                if (SENSITIVE_QUERY_PARAMS.contains(name.lowercase())) continue
                for (value in url.queryParameterValues(name)) {
                    builder.addQueryParameter(name, value)
                }
            }
            return builder.build()
        }
    }
}
