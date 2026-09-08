package com.omb9.glucosehero.util

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pure Kotlin percentile helpers.
 *
 * SQLite has no median, and glucose response is right-skewed — one forgotten
 * bolus drags a mean badly — so the analytics engine deliberately summarises
 * deltas with median/quartiles instead of an arithmetic mean.
 */
object Percentiles {

    /**
     * Returns the [p]-th percentile of [sorted] (ascending order) using linear
     * interpolation between the two closest ranks.
     *
     * @throws IllegalArgumentException if [sorted] is empty or [p] is outside
     *   `[0.0, 1.0]`.
     */
    fun percentile(sorted: List<Double>, p: Double): Double {
        require(p in 0.0..1.0) { "Percentile must be in [0.0, 1.0], was $p" }
        require(sorted.isNotEmpty()) { "Cannot compute a percentile of an empty list" }

        if (sorted.size == 1) return sorted[0]

        val position = p * (sorted.size - 1)
        val lower = floor(position).toInt()
        val upper = ceil(position).toInt()

        if (lower == upper) return sorted[lower]

        val fraction = position - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    /** Median of [values]; the list is sorted internally. */
    fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot compute a median of an empty list" }
        return percentile(values.sorted(), 0.5)
    }

    /** Returns `(p25, median, p75)` for [values]; the list is sorted internally. */
    fun quartiles(values: List<Double>): Triple<Double, Double, Double> {
        val sorted = values.sorted()
        return Triple(
            percentile(sorted, 0.25),
            percentile(sorted, 0.5),
            percentile(sorted, 0.75),
        )
    }
}
