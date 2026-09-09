package com.omb9.glucosehero.exercise

/**
 * Live Health Connect snapshots used by the exercise-fueling worker.
 * Missing streams are empty; the evaluator treats them as "unknown," not zero intensity.
 */
data class LiveHeartRateSample(
    val timestampMillis: Long,
    val beatsPerMinute: Long,
)

data class LiveExerciseSession(
    val startMillis: Long,
    val endMillis: Long,
    val title: String?,
)

data class LiveActiveCalories(
    val startMillis: Long,
    val endMillis: Long,
    val kcal: Double,
)

data class ExerciseFuelingInput(
    val nowMillis: Long,
    val iobUnits: Double,
    val sessions: List<LiveExerciseSession>,
    val heartRate: List<LiveHeartRateSample>,
    val calories: List<LiveActiveCalories>,
    val userAgeYears: Int?,
    val elevatedIobUnits: Double = DEFAULT_ELEVATED_IOB_UNITS,
)

data class ExerciseFuelingDecision(
    val shouldAlert: Boolean,
    val reason: String,
    val meanHeartRateBpm: Double?,
    val recentKcal: Double,
    val activeSessionTitle: String?,
    val iobUnits: Double,
)

const val DEFAULT_ELEVATED_IOB_UNITS: Double = 1.0
const val FAST_ACTING_CARB_GRAMS: Int = 15
const val EXERCISE_LOOKBACK_MINUTES: Int = 20
const val HEART_RATE_INTENSITY_FRACTION: Double = 0.70
const val CALORIE_INTENSITY_KCAL: Double = 40.0
const val DEFAULT_MAX_HR_AGE: Int = 40
