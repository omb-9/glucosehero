package com.omb9.glucosehero.clinical

/**
 * Conservative overnight basal and single-meal ICR/ISF test heuristics.
 *
 * Recommendations are informational only. They never prescribe a new basal
 * rate, ICR, or ISF number. A test is invalidated if food or a correction
 * bolus appears in the watch window (and, for basal, in the 2 hours before start).
 *
 * Glucose units are canonical mg/dL.
 */
object ClinicalTestEngine {

    const val DEFAULT_BASAL_HOURS: Long = 4L
    const val DEFAULT_MEAL_HOURS: Long = 3L
    const val BASAL_PRE_WINDOW_MILLIS: Long = 2L * 60L * 60L * 1000L
    const val STABLE_RANGE_MGDL: Double = 30.0
    const val STABLE_DELTA_MGDL: Double = 25.0
    const val BASAL_SLOPE_MGDL_PER_HOUR: Double = 8.0
    const val MEAL_DELTA_MGDL: Double = 30.0
    const val MIN_READINGS: Int = 4

    fun interferingReason(
        kind: ClinicalTestKind,
        startMillis: Long,
        endMillis: Long,
        events: List<InterferingEvent>,
    ): String? {
        val watchStart = if (kind == ClinicalTestKind.OVERNIGHT_BASAL) {
            startMillis - BASAL_PRE_WINDOW_MILLIS
        } else {
            startMillis + 1L
        }
        val hit = events.firstOrNull { event ->
            event.timestampMillis in watchStart..endMillis && (event.hasCarbs || event.hasBolus)
        } ?: return null
        return when {
            hit.hasCarbs && hit.hasBolus -> "food_and_bolus"
            hit.hasCarbs -> "food"
            else -> "bolus"
        }
    }

    fun analyzeBasal(
        observations: List<GlucoseObservation>,
        startMillis: Long,
        endMillis: Long,
    ): ClinicalTestResult? {
        val window = observations
            .filter { it.timestampMillis in startMillis..endMillis && it.glucoseMgdl.isFinite() }
            .sortedBy { it.timestampMillis }
        if (window.size < MIN_READINGS) return null
        val startG = window.first().glucoseMgdl
        val endG = window.last().glucoseMgdl
        val minG = window.minOf { it.glucoseMgdl }
        val maxG = window.maxOf { it.glucoseMgdl }
        val range = maxG - minG
        val hours = ((endMillis - startMillis).coerceAtLeast(1L)) / 3_600_000.0
        val slope = (endG - startG) / hours
        val hint = when {
            range <= STABLE_RANGE_MGDL && kotlin.math.abs(endG - startG) <= STABLE_DELTA_MGDL ->
                ClinicalCalibrationHint.STABLE_NO_CHANGE
            endG - startG > STABLE_DELTA_MGDL || slope > BASAL_SLOPE_MGDL_PER_HOUR ->
                ClinicalCalibrationHint.BASAL_MAY_BE_LOW
            startG - endG > STABLE_DELTA_MGDL || slope < -BASAL_SLOPE_MGDL_PER_HOUR ->
                ClinicalCalibrationHint.BASAL_MAY_BE_HIGH
            else -> ClinicalCalibrationHint.INCONCLUSIVE
        }
        return ClinicalTestResult(
            kind = ClinicalTestKind.OVERNIGHT_BASAL,
            startGlucoseMgdl = startG,
            endGlucoseMgdl = endG,
            minMgdl = minG,
            maxMgdl = maxG,
            rangeMgdl = range,
            slopeMgdlPerHour = slope,
            readingCount = window.size,
            hint = hint,
            summary = basalSummary(hint, startG, endG, range, slope),
        )
    }

    fun analyzeMealRatio(
        observations: List<GlucoseObservation>,
        startMillis: Long,
        endMillis: Long,
        preMealGlucoseMgdl: Double,
        mealBolusUnits: Double?,
    ): ClinicalTestResult? {
        val window = observations
            .filter { it.timestampMillis in startMillis..endMillis && it.glucoseMgdl.isFinite() }
            .sortedBy { it.timestampMillis }
        if (window.size < MIN_READINGS) return null
        val endG = window.last().glucoseMgdl
        val minG = window.minOf { it.glucoseMgdl }
        val maxG = window.maxOf { it.glucoseMgdl }
        val range = maxG - minG
        val hours = ((endMillis - startMillis).coerceAtLeast(1L)) / 3_600_000.0
        val slope = (endG - preMealGlucoseMgdl) / hours
        val delta = endG - preMealGlucoseMgdl
        val hint = when {
            minG < 54.0 -> ClinicalCalibrationHint.ICR_MAY_BE_LOW
            delta > MEAL_DELTA_MGDL -> ClinicalCalibrationHint.ICR_MAY_BE_HIGH
            delta < -MEAL_DELTA_MGDL -> ClinicalCalibrationHint.ICR_MAY_BE_LOW
            kotlin.math.abs(delta) <= MEAL_DELTA_MGDL -> {
                if (mealBolusUnits != null && mealBolusUnits > 0.0 && range > 80.0) {
                    if (maxG > preMealGlucoseMgdl + 80.0) ClinicalCalibrationHint.ISF_MAY_BE_LOW
                    else ClinicalCalibrationHint.STABLE_NO_CHANGE
                } else {
                    ClinicalCalibrationHint.STABLE_NO_CHANGE
                }
            }
            else -> ClinicalCalibrationHint.INCONCLUSIVE
        }
        return ClinicalTestResult(
            kind = ClinicalTestKind.MEAL_CARB_RATIO,
            startGlucoseMgdl = preMealGlucoseMgdl,
            endGlucoseMgdl = endG,
            minMgdl = minG,
            maxMgdl = maxG,
            rangeMgdl = range,
            slopeMgdlPerHour = slope,
            readingCount = window.size,
            hint = hint,
            summary = mealSummary(hint, preMealGlucoseMgdl, endG, delta),
        )
    }

    private fun basalSummary(
        hint: ClinicalCalibrationHint,
        startG: Double,
        endG: Double,
        range: Double,
        slope: Double,
    ): String = when (hint) {
        ClinicalCalibrationHint.STABLE_NO_CHANGE ->
            "Overnight glucose stayed relatively flat (${startG.toInt()} to ${endG.toInt()} mg/dL, range ${range.toInt()}). Basal may be about right for this window."
        ClinicalCalibrationHint.BASAL_MAY_BE_LOW ->
            "Glucose rose overnight (${startG.toInt()} to ${endG.toInt()} mg/dL, ${"%.1f".format(slope)} mg/dL/h). Basal may be too low. Discuss with your care team before changing anything."
        ClinicalCalibrationHint.BASAL_MAY_BE_HIGH ->
            "Glucose fell overnight (${startG.toInt()} to ${endG.toInt()} mg/dL, ${"%.1f".format(slope)} mg/dL/h). Basal may be too high. Discuss with your care team before changing anything."
        else ->
            "This overnight window was inconclusive. Try another night with no food or boluses."
    }

    private fun mealSummary(
        hint: ClinicalCalibrationHint,
        pre: Double,
        end: Double,
        delta: Double,
    ): String = when (hint) {
        ClinicalCalibrationHint.STABLE_NO_CHANGE ->
            "Post-meal glucose returned near the starting value (${pre.toInt()} to ${end.toInt()} mg/dL). Carb ratio may be about right for this meal."
        ClinicalCalibrationHint.ICR_MAY_BE_HIGH ->
            "Glucose was still ${delta.toInt()} mg/dL above the start at the end of the window. Insulin-to-carb ratio may be too high (not enough insulin per carb). This is not a prescription."
        ClinicalCalibrationHint.ICR_MAY_BE_LOW ->
            "Glucose ended ${kotlin.math.abs(delta).toInt()} mg/dL below the start, or dipped low. Insulin-to-carb ratio may be too low (too much insulin per carb). This is not a prescription."
        ClinicalCalibrationHint.ISF_MAY_BE_LOW ->
            "A wide post-meal swing with a modest finish can mean insulin sensitivity (ISF) is weaker than expected. Review ISF with your care team."
        ClinicalCalibrationHint.ISF_MAY_BE_HIGH ->
            "A larger-than-expected drop after insulin can mean ISF is stronger than expected. Review ISF with your care team."
        else ->
            "This meal test was inconclusive. Repeat with a known carb amount and no extra boluses."
    }
}
