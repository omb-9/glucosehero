package com.omb9.glucosehero.util

import java.time.LocalDate

/**
 * Client-side daily AI-call counting.
 *
 * This is a UX affordance, not a security boundary. App data can be cleared
 * (or the app reinstalled) and the counter reset locally; the real enforcement
 * belongs at the proxy alongside the managed API key. Keep the counter's shape
 * — tier plus used-today plus reset day — so it can be moved server-side
 * without a rewrite.
 */
enum class AiTier { FREE, PRO, BYOK }

object AiQuota {

    /** Daily allowance per tier, or null for BYOK (uncounted). */
    fun dailyLimit(tier: AiTier): Int? = when (tier) {
        AiTier.FREE -> 10
        AiTier.PRO -> 50
        AiTier.BYOK -> null
    }

    fun isExhausted(tier: AiTier, usedToday: Int): Boolean =
        dailyLimit(tier)?.let { usedToday >= it } ?: false

    fun remaining(tier: AiTier, usedToday: Int): Int? =
        dailyLimit(tier)?.let { (it - usedToday).coerceAtLeast(0) }

    /**
     * Day-boundary reset: true when the persisted day no longer matches the
     * supplied local day. Both are `yyyy-MM-dd` strings, matching EntryDao's
     * `strftime('%Y-%m-%d', ..., 'localtime')` convention.
     */
    fun shouldReset(storedDay: String?, todayDay: String): Boolean = storedDay != todayDay

    /** Today's LOCAL calendar day in the app-wide `yyyy-MM-dd` convention. */
    fun todayDay(): String = LocalDate.now().toString()
}
