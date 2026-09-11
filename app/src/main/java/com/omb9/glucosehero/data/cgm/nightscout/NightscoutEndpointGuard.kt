package com.omb9.glucosehero.data.cgm.nightscout

import com.omb9.glucosehero.data.remote.TrustedHosts
import com.omb9.glucosehero.data.remote.isPrivateOrLoopback
import com.omb9.glucosehero.data.remote.normalizedHost
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Decides whether a user-supplied Nightscout URL may receive an access token
 * or hashed API secret.
 *
 * Why this exists: Nightscout hosts are never built-in destinations.
 * [com.omb9.glucosehero.data.remote.TrustedHosts.TRUSTED_SUFFIXES] stays
 * default-deny for this path. Every host must be acknowledged explicitly,
 * then [TrustedHosts.isAllowed] is the same gate used by custom AI.
 *
 * Assumptions:
 * - Public destinations must be HTTPS. LAN / loopback / `.local` may use
 *   HTTP after acknowledgement (CleartextGuardInterceptor still enforces the
 *   same rule at the socket).
 * - A host on the AI allowlist is still not an implicit Nightscout target.
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutEndpointGuard {

    sealed interface Status {
        data object Allowed : Status
        data object InvalidUrl : Status
        data object HttpsRequired : Status
        data class NeedsAcknowledgment(val host: String) : Status
    }

    fun evaluate(url: String, acknowledgedHosts: Set<String>): Status {
        val parsed = url.trim().toHttpUrlOrNull() ?: return Status.InvalidUrl
        return evaluate(parsed, acknowledgedHosts)
    }

    fun evaluate(url: HttpUrl, acknowledgedHosts: Set<String>): Status {
        val host = url.normalizedHost()
        if (host.isBlank()) return Status.InvalidUrl

        if (!url.isHttps && !isPrivateOrLoopback(host)) {
            return Status.HttpsRequired
        }

        if (!TrustedHosts.acknowledgedSetContains(host, acknowledgedHosts)) {
            return Status.NeedsAcknowledgment(host)
        }

        // Same default-deny function as custom AI. After acknowledgement the
        // host is in [acknowledgedHosts], so this is Allowed.
        return if (TrustedHosts.isAllowed(host, acknowledgedHosts)) {
            Status.Allowed
        } else {
            Status.NeedsAcknowledgment(host)
        }
    }
}
