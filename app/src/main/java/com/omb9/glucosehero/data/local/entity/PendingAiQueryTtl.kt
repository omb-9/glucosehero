package com.omb9.glucosehero.data.local.entity

/**
 * Validity window for queued Hero AI questions.
 *
 * Replaying an acute reading such as "sugar is 65" hours later is dangerous, so
 * queued prompts expire quickly. [DEFAULT_TTL_SECONDS] is 30 minutes and is also
 * the hard cap ([MAX_VALIDITY_SECONDS]). A stored [ttlSeconds] of 0 or less means
 * unknown age (migrated or restored rows) and is treated as already expired.
 *
 * FEATURE: pending-query-ttl
 */
object PendingAiQueryTtl {
    const val DEFAULT_TTL_SECONDS: Int = 30 * 60
    const val MAX_VALIDITY_SECONDS: Int = 30 * 60

    /**
     * Clamp a caller-supplied TTL. Non-positive values become the default so new
     * queue inserts are never written as unknown-age.
     */
    fun normalizeForInsert(ttlSeconds: Int): Int {
        val requested = if (ttlSeconds <= 0) DEFAULT_TTL_SECONDS else ttlSeconds
        return minOf(requested, MAX_VALIDITY_SECONDS)
    }

    /**
     * Effective TTL used at drain time. Unlike [normalizeForInsert], a stored
     * value of 0 stays 0 so migrated unknown-age rows expire immediately.
     */
    fun effectiveTtlSeconds(storedTtlSeconds: Int): Int {
        if (storedTtlSeconds <= 0) return 0
        return minOf(storedTtlSeconds, MAX_VALIDITY_SECONDS)
    }

    fun isExpired(
        createdAt: Long,
        ttlSeconds: Int,
        nowMillis: Long,
    ): Boolean {
        val ttl = effectiveTtlSeconds(ttlSeconds)
        if (ttl <= 0) return true
        if (createdAt <= 0L) return true
        if (createdAt > nowMillis) return true
        return nowMillis - createdAt > ttl * 1000L
    }
}
