package com.omb9.glucosehero.data.cgm.nightscout

import okhttp3.HttpUrl
import okhttp3.Request
import java.security.MessageDigest

/**
 * Applies Nightscout v1 credentials to an outbound request.
 *
 * Why this exists: token auth and legacy API-secret hashing must stay in one
 * place so neither path logs the secret, and so tests can prove the token is
 * a query parameter rather than an `Authorization` bearer (that header is
 * reserved for the isolated AI client).
 *
 * Assumptions:
 * - [credential] is already decrypted and lives only for this call.
 * - SHA-1 is Nightscout's wire format for `API_SECRET`, not a storage hash.
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutAuth {

    const val HEADER_API_SECRET = "api-secret"
    const val QUERY_TOKEN = "token"

    fun apply(
        url: HttpUrl.Builder,
        request: Request.Builder,
        mode: NightscoutAuthMode,
        credential: String,
    ) {
        val trimmed = credential.trim()
        when (mode) {
            NightscoutAuthMode.TOKEN -> url.setQueryParameter(QUERY_TOKEN, trimmed)
            NightscoutAuthMode.API_SECRET -> request.header(HEADER_API_SECRET, sha1Hex(trimmed))
        }
    }

    /**
     * Nightscout compares `api-secret` to SHA-1(API_SECRET) as lowercase hex.
     */
    fun sha1Hex(plain: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(plain.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val unsigned = byte.toInt() and 0xff
                append(HEX[unsigned shr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
