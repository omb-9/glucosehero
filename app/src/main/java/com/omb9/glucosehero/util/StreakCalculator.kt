package com.omb9.glucosehero.util

import java.time.LocalDate

/**
 * Pure streak arithmetic for the daily-logging streak. A "logged day" is a
 * local calendar day that has at least one streak-qualifying entry (glucose,
 * insulin, or meal); note-only and exercise-only entries do not count.
 *
 * Kept as a side-effect-free object so the same rules back both the reactive
 * Stats flow and the imperative before/after comparison in the save flow.
 */
object StreakCalculator {

    fun currentStreak(loggedDays: List<String>, today: LocalDate = LocalDate.now()): Int {
        val days = loggedDays.mapNotNullTo(mutableSetOf()) { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
        return currentStreak(days, today)
    }

    fun currentStreak(loggedDays: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        if (today in loggedDays) return countBack(today, loggedDays)

        // If today has not been logged yet, the current streak is still the
        // consecutive run ending yesterday — it is "alive" but not extended.
        val yesterday = today.minusDays(1)
        return if (yesterday in loggedDays) countBack(yesterday, loggedDays) else 0
    }

    private fun countBack(start: LocalDate, loggedDays: Set<LocalDate>): Int {
        var streak = 0
        var cursor = start
        while (cursor in loggedDays) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
