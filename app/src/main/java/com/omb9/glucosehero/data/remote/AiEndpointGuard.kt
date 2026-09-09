package com.omb9.glucosehero.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Decides whether an AI provider URL may receive the user's API key and prompt.
 *
 * Public destinations must be HTTPS. Built-in hosts (OpenAI, Gemini, OpenRouter,
 * and other app services) are allowed without extra confirmation. Any other host
 * is default-denied until the user acknowledges it. Acknowledged private or
 * loopback hosts may use HTTP so a local OpenAI-compatible box still works.
 */
object AiEndpointGuard {

    sealed interface Status {
        data object Allowed : Status
        data object InvalidUrl : Status
        data object HttpsRequired : Status
        data class NeedsAcknowledgment(val host: String) : Status
    }

    fun evaluate(url: String, acknowledgedHosts: Set<String>): Status {
        val parsed = url.toHttpUrlOrNull() ?: return Status.InvalidUrl
        return evaluate(parsed, acknowledgedHosts)
    }

    fun evaluate(url: HttpUrl, acknowledgedHosts: Set<String>): Status {
        val host = url.normalizedHost()
        if (host.isBlank()) return Status.InvalidUrl

        val trusted = TrustedHosts.isTrustedBuiltIn(host)
        val acknowledged = TrustedHosts.acknowledgedSetContains(host, acknowledgedHosts)
        val privateHost = isPrivateOrLoopback(host)

        if (!url.isHttps) {
            val localHttpAllowed = privateHost && acknowledged
            return if (localHttpAllowed) Status.Allowed else Status.HttpsRequired
        }

        if (trusted || acknowledged) return Status.Allowed
        return Status.NeedsAcknowledgment(host)
    }

    fun needsAcknowledgment(url: String, acknowledgedHosts: Set<String>): Boolean =
        evaluate(url, acknowledgedHosts) is Status.NeedsAcknowledgment

    fun isAllowed(url: HttpUrl, acknowledgedHosts: Set<String>): Boolean =
        evaluate(url, acknowledgedHosts) is Status.Allowed
}
