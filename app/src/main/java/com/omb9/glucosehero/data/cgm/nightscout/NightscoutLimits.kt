package com.omb9.glucosehero.data.cgm.nightscout

/**
 * Bounds for Nightscout pull windows and page size.
 *
 * Why this exists: an unbounded `find[date][$gt]=0` would download years of
 * SGV history on first enable. The user picks a backfill window; we still
 * hard-cap it.
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutLimits {
    const val DEFAULT_BACKFILL_HOURS = 24
    const val MAX_BACKFILL_HOURS = 72
    const val MIN_BACKFILL_HOURS = 6
    const val MAX_ENTRY_COUNT = 1000
    const val FOREGROUND_POLL_INTERVAL_MS = 5 * 60_000L
    const val PERIODIC_WORK_INTERVAL_MINUTES = 15L

    fun clampBackfillHours(hours: Int): Int =
        hours.coerceIn(MIN_BACKFILL_HOURS, MAX_BACKFILL_HOURS)

    fun backfillMillis(hours: Int): Long =
        clampBackfillHours(hours) * 60L * 60L * 1000L
}
