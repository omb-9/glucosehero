package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BolusDisplayBreakdownTest {

    private val six = LocalTime.of(6, 0)
    private val ten = LocalTime.of(10, 0)
    private val disclaimer = "GlucoseHero is a logging tool, not a medical device."

    private fun display(
        breakdown: BolusRecommendationBreakdown,
        unit: GlucoseUnit = GlucoseUnit.MGDL,
        start: LocalTime = six,
        end: LocalTime = ten,
        use24Hour: Boolean = true,
    ) = BolusDisplayFormatter.format(breakdown, unit, start, end, use24Hour)

    @Test
    fun `example meal plus correction minus iob reconciles in mgdl`() {
        val breakdown = BolusCalculator.recommend(
            currentGlucoseMgdl = 182.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 48.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 2.1,
        )
        val d = display(breakdown)
        assertEquals(4.8, breakdown.mealDose, 1e-9)
        assertEquals(1.64, breakdown.correctionDose, 1e-9)
        assertEquals(4.34, breakdown.units, 1e-9)
        assertTrue(d.showMealLine)
        assertFalse(d.correctionIsNegative)
        assertTrue(d.displayedTermsReconcileWithTotal())
        assertEquals("48", d.carbsGramsText)
        assertEquals("10", d.cirText)
        assertEquals("4.8", d.mealDoseText)
        assertEquals("182", d.glucoseText)
        assertEquals("50", d.isfText)
        assertEquals("50 mg/dL/U", d.isfWithUnit)
        val spoken = d.spokenSentenceForTest(disclaimer)
        assertTrue(spoken.contains("48 grams"))
        assertTrue(spoken.contains("4.8"))
        assertTrue(spoken.contains("50"))
        assertTrue(spoken.contains("2.1"))
        assertTrue(spoken.contains("4.3"))
        assertTrue(spoken.contains("06:00"))
        assertTrue(spoken.contains("10:00"))
        assertTrue(spoken.contains(disclaimer))
        assertFalse(spoken.contains("Kalman"))
    }

    @Test
    fun `mmol explanation converts glucose and ISF`() {
        val breakdown = BolusCalculator.recommend(
            currentGlucoseMgdl = 182.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 48.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 2.1,
        )
        val d = display(breakdown, GlucoseUnit.MMOL)
        assertTrue(d.displayedTermsReconcileWithTotal())
        assertEquals(Formatters.glucose(182.0, GlucoseUnit.MMOL), d.glucoseText)
        assertEquals(Formatters.glucose(100.0, GlucoseUnit.MMOL), d.targetText)
        assertEquals(Formatters.isf(50.0, GlucoseUnit.MMOL), d.isfText)
        assertEquals(Formatters.isfWithUnit(50.0, GlucoseUnit.MMOL), d.isfWithUnit)
        assertTrue(d.isfWithUnit.contains("mmol/L"))
        assertFalse(d.isfWithUnit.contains("mg/dL"))
        val spoken = d.spokenSentenceForTest(disclaimer)
        assertTrue(spoken.contains(d.isfText))
        assertTrue(spoken.contains(d.glucoseText))
        assertTrue(spoken.contains("units"))
    }

    @Test
    fun `zero carb omits meal line`() {
        val breakdown = BolusCalculator.recommend(
            currentGlucoseMgdl = 180.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 0.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 0.0,
        )
        val d = display(breakdown)
        assertFalse(d.showMealLine)
        assertTrue(d.showCorrectionLine)
        assertFalse(d.correctionIsNegative)
        assertTrue(d.displayedTermsReconcileWithTotal())
        val spoken = d.spokenSentenceForTest(disclaimer)
        assertFalse(spoken.contains("0 grams"))
        assertTrue(spoken.contains("1.6"))
    }

    @Test
    fun `negative correction is a subtraction of a positive amount`() {
        val breakdown = BolusCalculator.recommend(
            currentGlucoseMgdl = 90.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 48.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 0.0,
        )
        val d = display(breakdown)
        assertTrue(d.correctionIsNegative)
        assertEquals("0.2", d.correctionAbsText)
        assertFalse(d.correctionSignedText.startsWith("+"))
        assertTrue(d.displayedTermsReconcileWithTotal())
        val spoken = d.spokenSentenceForTest(disclaimer)
        assertTrue(spoken.startsWith("48 grams") || spoken.contains("48 grams"))
        assertTrue(spoken.contains("Minus correction"))
        assertFalse(spoken.contains("+ -"))
        assertTrue(spoken.contains(d.targetText))
        assertTrue(spoken.contains(d.glucoseText))
    }

    @Test
    fun `zero floor shows raw net then rounded up to zero`() {
        val breakdown = BolusCalculator.recommend(
            currentGlucoseMgdl = 90.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = 0.0,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 50.0,
            insulinOnBoard = 2.0,
        )
        val d = display(breakdown)
        assertTrue(d.zeroFloorApplied)
        assertEquals("0.0", d.totalText)
        assertTrue(d.displayedTermsReconcileWithTotal())
        val spoken = d.spokenSentenceForTest(disclaimer)
        assertTrue(spoken.contains("rounded up to 0"))
        assertTrue(spoken.contains(d.rawNetText))
    }

    @Test
    fun `x5 term rounding does not contradict the displayed total`() {
        val cases = listOf(
            BolusCalculator.recommend(112.5, 100.0, 2.5, 10.0, 50.0, 0.0),
            BolusCalculator.recommend(100.0, 100.0, 2.5, 10.0, 50.0, 0.0),
            BolusCalculator.recommend(112.5, 100.0, 2.5, 10.0, 50.0, 0.25),
            BolusCalculator.recommend(100.0, 100.0, 1.25, 10.0, 50.0, 0.0),
            BolusCalculator.recommend(87.5, 100.0, 5.0, 10.0, 50.0, 0.0),
        )
        for (unit in listOf(GlucoseUnit.MGDL, GlucoseUnit.MMOL)) {
            for (breakdown in cases) {
                val d = display(breakdown, unit)
                assertTrue(
                    "reconcile units=$unit meal=${breakdown.mealDose} corr=${breakdown.correctionDose} " +
                        "iob=${breakdown.insulinOnBoard} units=${breakdown.units} " +
                        "terms=${d.termsNetText} total=${d.totalText} floor=${d.zeroFloorApplied} " +
                        "round=${d.roundingStepShown}",
                    d.displayedTermsReconcileWithTotal(),
                )
                if (d.roundingStepShown) {
                    assertTrue(d.termsNetText != d.totalText)
                    assertEquals(BolusDisplayFormatter.oneDecimal(breakdown.units), d.totalText)
                }
            }
        }
    }

    @Test
    fun `spoken sentence is one string not fragments`() {
        val breakdown = BolusCalculator.recommend(182.0, 100.0, 48.0, 10.0, 50.0, 2.1)
        val spoken = display(breakdown).spokenSentenceForTest(disclaimer)
        assertTrue(spoken.contains(". "))
        assertFalse(spoken.contains(" 48 "))
        assertTrue(spoken.indexOf("grams") < spoken.indexOf("units"))
    }
}
