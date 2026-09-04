package com.omb9.glucosehero.util

/**
 * Pure, side-effect-free Smart Bolus recommendation math.
 *
 * Required Insulin = (Carbs / CIR) + ((Current Glucose - Target Glucose) / ISF) - IOB
 *
 * where CIR is the carb-to-insulin ratio (g per unit), ISF is the insulin
 * sensitivity factor (mg/dL per unit), and IOB is current insulin on board.
 * The result is floored at zero so the recommendation is never negative.
 */
object BolusCalculator {

    fun recommend(
        currentGlucoseMgdl: Double,
        targetGlucoseMgdl: Double,
        carbsGrams: Double,
        carbRatio: Double,
        insulinSensitivityMgdl: Double,
        insulinOnBoard: Double,
    ): Double {
        val mealDose = carbsGrams / carbRatio
        val correctionDose = (currentGlucoseMgdl - targetGlucoseMgdl) / insulinSensitivityMgdl
        return (mealDose + correctionDose - insulinOnBoard).coerceAtLeast(0.0)
    }
}