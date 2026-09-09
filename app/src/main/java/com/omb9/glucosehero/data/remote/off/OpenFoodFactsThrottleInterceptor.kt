package com.omb9.glucosehero.data.remote.off

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Spaces outbound Open Food Facts calls so barcode hammering cannot 429 the API.
 *
 * Open Food Facts asks clients to stay around one request per second. Room is
 * the product cache (indefinite); this interceptor only gates cache misses that
 * already reached OkHttp.
 *
 * Contact used in the companion User-Agent: [OpenFoodFactsUserAgentInterceptor.CONTACT_EMAIL]
 * (project outreach mailbox; no dedicated OFF contact is published in-repo).
 */
class OpenFoodFactsThrottleInterceptor(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val nanoTime: () -> Long = { System.nanoTime() },
    private val park: (Long) -> Unit = { ms -> if (ms > 0L) Thread.sleep(ms) },
) : Interceptor {

    constructor() : this(
        minIntervalMs = DEFAULT_MIN_INTERVAL_MS,
        nanoTime = { System.nanoTime() },
        park = { ms -> if (ms > 0L) Thread.sleep(ms) },
    )

    private val lock = Any()
    private var lastRequestNanos: Long? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val waitMs: Long
        synchronized(lock) {
            val now = nanoTime()
            val previous = lastRequestNanos
            waitMs = if (previous == null) {
                0L
            } else {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(now - previous)
                (minIntervalMs - elapsedMs).coerceAtLeast(0L)
            }
            if (waitMs > 0L) {
                park(waitMs)
            }
            lastRequestNanos = nanoTime()
        }
        return chain.proceed(chain.request())
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS: Long = 1_000L
    }
}
