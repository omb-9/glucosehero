package com.omb9.glucosehero.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Strict hostname allowlist for GlucoseHero network clients.
 *
 * Covers destinations the app actually uses: OpenAI-compatible presets, Open
 * Food Facts, Google Drive / Google APIs, and the public project site. Unknown
 * hosts are default-denied until the user explicitly acknowledges a custom
 * AI endpoint.
 */
object TrustedHosts {

    /**
     * Registrable domains. A hostname matches when it equals the suffix or is a
     * subdomain (`api.openai.com` matches `openai.com`).
     */
    val TRUSTED_SUFFIXES: Set<String> = setOf(
        "openai.com",
        "openrouter.ai",
        "googleapis.com",
        "openfoodfacts.org",
        "openfoodfacts.net",
        "glucosehero.app",
        "chromagrid.com",
    )

    fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()

    fun isTrustedBuiltIn(host: String): Boolean {
        val normalized = normalizeHost(host) ?: return false
        return TRUSTED_SUFFIXES.any { suffix ->
            normalized == suffix || normalized.endsWith(".$suffix")
        }
    }

    fun isAllowed(host: String, acknowledgedHosts: Set<String>): Boolean {
        val normalized = normalizeHost(host) ?: return false
        if (isTrustedBuiltIn(normalized)) return true
        return acknowledgedHosts.any { it.equals(normalized, ignoreCase = true) }
    }

    fun normalizeHost(host: String): String? {
        val normalized = host.trim()
            .lowercase()
            .removePrefix("[")
            .removeSuffix("]")
        return normalized.takeIf { it.isNotBlank() }
    }

    fun acknowledgedSetContains(host: String, acknowledgedHosts: Set<String>): Boolean {
        val normalized = normalizeHost(host) ?: return false
        return acknowledgedHosts.any { it.equals(normalized, ignoreCase = true) }
    }
}

/** Carried on OkHttp requests so redirect hops can reuse the same allowlist. */
class AcknowledgedHostsTag(val hosts: Set<String>)

fun HttpUrl.normalizedHost(): String = host.lowercase()
