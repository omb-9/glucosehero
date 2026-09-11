package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BolusCalculatorTest {

    @Test
    fun `sums meal and correction doses then subtracts iob`() {
        // 45g / 10 = 4.5; (180 - 100) / 50 = 1.6; 4.5 + 1.6 - 1.0 = 5.1
        val dose = BolusCalculator.recommend(
            currentGlucoseMgdl = 180.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 45.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 1.0,
        )
        assertEquals(5.1, dose.units, 1e-9)
        assertEquals(4.5, dose.mealDose, 1e-9)
        assertEquals(1.6, dose.correctionDose, 1e-9)
        assertEquals(1.0, dose.insulinOnBoard, 1e-9)
        assertEquals(5.1, dose.rawTotal, 1e-9)
        assertEquals(false, dose.zeroFloorApplied)
    }

    @Test
    fun `negative result is floored at zero`() {
        // (90 - 100) / 50 = -0.2; -0.2 - 2.0 = -2.2 -> 0.0
        val dose = BolusCalculator.recommend(
            currentGlucoseMgdl = 90.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 0.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 2.0,
        )
        assertEquals(0.0, dose.units, 1e-9)
        assertEquals(true, dose.zeroFloorApplied)
        assertEquals(-2.2, dose.rawTotal, 1e-9)
        assertEquals(-0.2, dose.correctionDose, 1e-9)
        assertEquals(0.0, dose.mealDose, 1e-9)
    }

    @Test
    fun `high iob cancels an otherwise positive dose`() {
        // 30 / 10 = 3.0; (120 - 100) / 50 = 0.4; 3.4 - 4.0 = -0.6 -> 0.0
        val dose = BolusCalculator.recommend(120.0, 100.0, 30.0, 10.0, 50.0, 4.0)
        assertEquals(0.0, dose.units, 1e-9)
        assertEquals(true, dose.zeroFloorApplied)
    }
}