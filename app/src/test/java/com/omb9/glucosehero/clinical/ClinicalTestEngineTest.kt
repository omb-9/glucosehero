package com.omb9.glucosehero.clinical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClinicalTestEngineTest {

    @Test
    fun `food during basal window invalidates`() {
        val reason = ClinicalTestEngine.interferingReason(
            kind = ClinicalTestKind.OVERNIGHT_BASAL,
            startMillis = 1_000_000L,
            endMillis = 2_000_000L,
            events = listOf(
                InterferingEvent(timestampMillis = 1_200_000L, hasCarbs = true, hasBolus = false),
            ),
        )
        assertEquals("food", reason)
    }

    @Test
    fun `stable overnight glucose recommends no change`() {
        val start = 1_000_000L
        val observations = (0..8).map { i ->
            GlucoseObservation(start + i * 30 * 60_000L, 110.0 + i * 0.5)
        }
        val result = ClinicalTestEngine.analyzeBasal(
            observations,
            start,
            start + 4 * 3_600_000L,
        )
        assertNotNull(result)
        assertEquals(ClinicalCalibrationHint.STABLE_NO_CHANGE, result!!.hint)
    }

    @Test
    fun `rising overnight glucose flags basal maybe low`() {
        val start = 1_000_000L
        val observations = (0..8).map { i ->
            GlucoseObservation(start + i * 30 * 60_000L, 100.0 + i * 8.0)
        }
        val result = ClinicalTestEngine.analyzeBasal(
            observations,
            start,
            start + 4 * 3_600_000L,
        )
        assertEquals(ClinicalCalibrationHint.BASAL_MAY_BE_LOW, result!!.hint)
    }

    @Test
    fun `high post meal finish flags ICR maybe high`() {
        val start = 1_000_000L
        val observations = listOf(
            GlucoseObservation(start, 110.0),
            GlucoseObservation(start + 30 * 60_000L, 160.0),
            GlucoseObservation(start + 90 * 60_000L, 170.0),
            GlucoseObservation(start + 150 * 60_000L, 165.0),
        )
        val result = ClinicalTestEngine.analyzeMealRatio(
            observations = observations,
            startMillis = start,
            endMillis = start + 3 * 3_600_000L,
            preMealGlucoseMgdl = 110.0,
            mealBolusUnits = 4.0,
        )
        assertEquals(ClinicalCalibrationHint.ICR_MAY_BE_HIGH, result!!.hint)
    }

    @Test
    fun `too few readings returns null`() {
        assertNull(
            ClinicalTestEngine.analyzeBasal(
                listOf(GlucoseObservation(1L, 100.0)),
                0L,
                4 * 3_600_000L,
            ),
        )
    }
}
