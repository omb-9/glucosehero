package com.omb9.glucosehero.util

/**
 * Pure, side-effect-free estimated-A1c math.
 *
 * When at least 70% of the rolling window's readings come from continuous
 * glucose samples, the clinically preferred estimate is the glucose management
 * indicator (GMI). Otherwise the card falls back to the classic ADAG formula.
 */
enum class Ea1cFormula(val label: String) {
    GMI("GMI"),
    ADAG("Estimated A1c (ADAG)"),
}

/**
 * True when CGM samples make up at least 70% of the window's readings.
 *
 * Returns false when there are no readings at all, so an empty window never
 * claims GMI and never divides by zero.
 */
fun shouldUseGmi(cgmReadingCount: Int, manualReadingCount: Int): Boolean {
    val total = cgmReadingCount + manualReadingCount
    return total > 0 && cgmReadingCount.toDouble() / total >= 0.70
}

/** GMI (%) from a mean glucose value in mg/dL. */
fun gmiPercentage(meanGlucoseMgdl: Double): Double =
    3.31 + 0.02392 * meanGlucoseMgdl

/** ADAG estimated A1c (%) from a mean glucose value in mg/dL. */
fun adagPercentage(meanGlucoseMgdl: Double): Double =
    (meanGlucoseMgdl + 46.7) / 28.7
