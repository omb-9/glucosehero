package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.util.Formatters

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS

sealed class OccurredAtCaption {
    data object Now : OccurredAtCaption()
    data class MinutesAgo(val minutes: Int) : OccurredAtCaption()
    data class HoursAgo(val hours: Int) : OccurredAtCaption()
    data class Absolute(val millis: Long) : OccurredAtCaption()
}

/**
 * Caption for the add-entry occurred-at chip. Relative until 24 hours, then
 * an absolute date so a long-open sheet does not stay stuck on "N hours ago".
 */
fun occurredAtCaption(occurredAtMillis: Long?, nowMillis: Long): OccurredAtCaption {
    if (occurredAtMillis == null) return OccurredAtCaption.Now
    val elapsed = (nowMillis - occurredAtMillis).coerceAtLeast(0L)
    return when {
        elapsed < HOUR_MILLIS ->
            OccurredAtCaption.MinutesAgo((elapsed / MINUTE_MILLIS).toInt())
        elapsed < DAY_MILLIS ->
            OccurredAtCaption.HoursAgo((elapsed / HOUR_MILLIS).toInt())
        else -> OccurredAtCaption.Absolute(occurredAtMillis)
    }
}

fun formatAbsoluteOccurredAt(millis: Long, use24HourTime: Boolean): String =
    Formatters.dayHeader(Formatters.localDate(millis)) +
        " · " +
        Formatters.time(millis, use24HourTime)

fun clampOccurredAtMillis(millis: Long?, nowMillis: Long): Long? =
    millis?.coerceAtMost(nowMillis)
