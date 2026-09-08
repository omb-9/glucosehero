package com.omb9.glucosehero.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Refuses plain-HTTP requests to any host that is not on a local network.
 *
 * Android's network security config matches literal domains, not CIDR ranges,
 * so it cannot express "allow cleartext to any RFC1918 / loopback address".
 * This interceptor runs at the OkHttp layer where the host is known at request
 * time and enforces that rule instead.
 */
class CleartextGuardInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.isHttps && !isPrivateOrLoopback(url.host)) {
            throw IOException(
                "Refusing to send data over an unencrypted connection to ${url.host}. " +
                    "Plain HTTP is only permitted for local network endpoints."
            )
        }
        return chain.proceed(request)
    }
}

/**
 * True when [host] is loopback, an RFC1918 / link-local IPv4 literal, the
 * Android emulator host alias (10.0.2.2, already inside 10.0.0.0/8), or an
 * mDNS `.local` hostname. A bare hostname that is not an IP literal and not
 * `.local` is treated as public — it could resolve anywhere.
 */
internal fun isPrivateOrLoopback(host: String): Boolean {
    val normalized = host.trim()
        .lowercase()
        .removePrefix("[")
        .removeSuffix("]")
    if (normalized.isEmpty()) return false

    if (normalized == "localhost") return true
    if (normalized.endsWith(".local")) return true

    // IPv6 loopback (plus its fully-expanded form).
    if (normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") return true

    val octets = parseIpv4(normalized) ?: return false
    return isPrivateOrLoopbackIpv4(octets)
}

private fun parseIpv4(host: String): IntArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null

    val octets = IntArray(4)
    for (i in parts.indices) {
        val part = parts[i]
        if (part.isEmpty() || part.length > 3) return null
        val value = part.toIntOrNull() ?: return null
        if (value !in 0..255) return null
        octets[i] = value
    }
    return octets
}

private fun isPrivateOrLoopbackIpv4(octets: IntArray): Boolean {
    val first = octets[0]
    val second = octets[1]
    return when {
        first == 127 -> true                     // loopback 127.0.0.0/8
        first == 10 -> true                      // RFC1918 10.0.0.0/8 (incl. 10.0.2.2)
        first == 192 && second == 168 -> true    // RFC1918 192.168.0.0/16
        first == 172 && second in 16..31 -> true // RFC1918 172.16.0.0/12
        first == 169 && second == 254 -> true    // link-local 169.254.0.0/16
        else -> false
    }
}
