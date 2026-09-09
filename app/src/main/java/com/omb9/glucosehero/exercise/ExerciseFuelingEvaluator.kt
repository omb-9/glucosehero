package com.omb9.glucosehero.exercise

/**
 * Detects high physical intensity overlapping elevated insulin-on-board.
 *
 * Intensity is high when any of:
 * - an exercise session overlaps the last [EXERCISE_LOOKBACK_MINUTES]
 * - mean heart rate over that window is >= 70% of estimated max (220 - age)
 * - active calories in the window exceed [CALORIE_INTENSITY_KCAL]
 *
 * IOB is elevated at or above [ExerciseFuelingInput.elevatedIobUnits] (default 1.0 U).
 * Missing Health Connect streams simply fail that clause; they never invent intensity.
 */
object ExerciseFuelingEvaluator {

    fun evaluate(input: ExerciseFuelingInput): ExerciseFuelingDecision {
        val windowStart = input.nowMillis - EXERCISE_LOOKBACK_MINUTES * 60_000L
        val activeSession = input.sessions.firstOrNull { session ->
            session.endMillis >= windowStart && session.startMillis <= input.nowMillis
        }
        val hrInWindow = input.heartRate.filter { it.timestampMillis >= windowStart }
        val meanHr = hrInWindow.takeIf { it.isNotEmpty() }?.map { it.beatsPerMinute.toDouble() }?.average()
        val recentKcal = input.calories
            .filter { it.endMillis >= windowStart && it.startMillis <= input.nowMillis }
            .sumOf { it.kcal }

        val age = input.userAgeYears?.takeIf { it in 5..90 } ?: DEFAULT_MAX_HR_AGE
        val hrThreshold = (220 - age) * HEART_RATE_INTENSITY_FRACTION
        val highHr = meanHr != null && meanHr >= hrThreshold
        val highCalories = recentKcal >= CALORIE_INTENSITY_KCAL
        val sessionActive = activeSession != null
        val highIntensity = sessionActive || highHr || highCalories
        val elevatedIob = input.iobUnits >= input.elevatedIobUnits

        val reason = buildString {
            if (!highIntensity) append("intensity_low")
            else {
                val parts = buildList {
                    if (sessionActive) add("session")
                    if (highHr) add("heart_rate")
                    if (highCalories) add("calories")
                }
                append(parts.joinToString("+"))
            }
            append("|iob=")
            append("%.2f".format(input.iobUnits))
        }

        return ExerciseFuelingDecision(
            shouldAlert = highIntensity && elevatedIob,
            reason = reason,
            meanHeartRateBpm = meanHr,
            recentKcal = recentKcal,
            activeSessionTitle = activeSession?.title,
            iobUnits = input.iobUnits,
        )
    }
}
