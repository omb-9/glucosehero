package com.omb9.glucosehero.exercise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFuelingEvaluatorTest {

    @Test
    fun `elevated iob plus active session alerts`() {
        val now = 1_000_000L
        val decision = ExerciseFuelingEvaluator.evaluate(
            ExerciseFuelingInput(
                nowMillis = now,
                iobUnits = 2.0,
                sessions = listOf(
                    LiveExerciseSession(now - 5 * 60_000L, now + 10 * 60_000L, "Run"),
                ),
                heartRate = emptyList(),
                calories = emptyList(),
                userAgeYears = 30,
            ),
        )
        assertTrue(decision.shouldAlert)
    }

    @Test
    fun `high intensity without iob does not alert`() {
        val now = 1_000_000L
        val decision = ExerciseFuelingEvaluator.evaluate(
            ExerciseFuelingInput(
                nowMillis = now,
                iobUnits = 0.1,
                sessions = listOf(
                    LiveExerciseSession(now - 5 * 60_000L, now + 10 * 60_000L, "Run"),
                ),
                heartRate = emptyList(),
                calories = emptyList(),
                userAgeYears = 30,
            ),
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun `high heart rate and iob alerts even without a session`() {
        val now = 2_000_000L
        val samples = (0..5).map { i ->
            LiveHeartRateSample(now - i * 60_000L, 160)
        }
        val decision = ExerciseFuelingEvaluator.evaluate(
            ExerciseFuelingInput(
                nowMillis = now,
                iobUnits = 1.5,
                sessions = emptyList(),
                heartRate = samples,
                calories = emptyList(),
                userAgeYears = 30,
            ),
        )
        assertTrue(decision.shouldAlert)
    }

    @Test
    fun `missing health connect streams do not invent intensity`() {
        val decision = ExerciseFuelingEvaluator.evaluate(
            ExerciseFuelingInput(
                nowMillis = 1_000_000L,
                iobUnits = 3.0,
                sessions = emptyList(),
                heartRate = emptyList(),
                calories = emptyList(),
                userAgeYears = 40,
            ),
        )
        assertFalse(decision.shouldAlert)
    }
}
