package com.omb9.glucosehero.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validates the local automation webhook URL typed in Advanced settings.
 *
 * Empty is allowed and clears the destination. Parsed HTTP URLs must use
 * HTTPS unless the host is private or loopback, matching
 * [CleartextGuardInterceptor] so a Home Assistant box on the LAN still works.
 */
object WebhookUrlGuard {

    sealed interface Status {
        data object Empty : Status
        data object Allowed : Status
        data object InvalidUrl : Status
        data object HttpsRequired : Status
    }

    fun evaluate(url: String): Status {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return Status.Empty
        val parsed = trimmed.toHttpUrlOrNull() ?: return Status.InvalidUrl
        val host = parsed.normalizedHost()
        if (host.isBlank()) return Status.InvalidUrl
        if (!parsed.isHttps && !isPrivateOrLoopback(host)) return Status.HttpsRequired
        return Status.Allowed
    }

    fun isPersistable(url: String): Boolean {
        val status = evaluate(url)
        return status is Status.Empty || status is Status.Allowed
    }
}
