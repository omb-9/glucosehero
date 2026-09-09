package com.omb9.glucosehero.util

import java.time.Instant

/**
 * Linear carbohydrate absorption ("carbs on board"). Independent of the
 * Walsh IOB curve in [IobCalculator]; meals still use a straight-line
 * action window.
 *
 * **Assumptions**
 * - Units: carbohydrate grams; the caller converts absorbed grams to mg/dL
 *   with `grams * (ISF / CIR)`. CIR is grams covered by 1 U; ISF is mg/dL
 *   dropped by 1 U, so 1 g raises glucose by ISF/CIR mg/dL.
 * - Curve: a meal is 0% absorbed at logging time and 100% absorbed after
 *   [DEFAULT_ACTION_HOURS] (3.0 h mixed-meal default). Remaining COB shrinks
 *   linearly. Fast-acting glucose (juice) can pass a shorter action time.
 * - Future-dated meals contribute nothing. Meals older than the action
 *   window contribute nothing.
 *
 * This is a simplified Walsh-style linear model, not a physiologic two-compartment
 * gut model. It is intentionally conservative and on-device.
 */
object CarbAbsorptionCalculator {

    const val DEFAULT_ACTION_HOURS: Double = 3.0
    const val FAST_ACTING_ACTION_HOURS: Double = 1.5

    /** A logged carbohydrate amount, as consumed by COB math. */
    data class CarbEntry(
        val timestampMillis: Long,
        val carbsGrams: Double,
    )

    /**
     * Sums still-unabsorbed carbohydrate at [now].
     *
     * @param actionHours absorption duration; must be > 0.
     */
    fun carbsOnBoard(
        meals: List<CarbEntry>,
        actionHours: Double = DEFAULT_ACTION_HOURS,
        now: Instant = Instant.now(),
    ): Double {
        val actionMillis = actionHours * IobCalculator.MILLIS_PER_HOUR
        if (actionMillis <= 0.0) return 0.0

        val nowMillis = now.toEpochMilli()
        return meals.fold(0.0) { acc, meal ->
            val elapsedMillis = (nowMillis - meal.timestampMillis).toDouble()
            if (elapsedMillis < 0.0) return@fold acc
            if (elapsedMillis >= actionMillis) return@fold acc
            acc + meal.carbsGrams * (1.0 - elapsedMillis / actionMillis)
        }
    }

    /**
     * Carbohydrate that will be absorbed between [from] and [to].
     * Equals `COB(from) - COB(to)` under the linear action curve.
     */
    fun carbsAbsorbedBetween(
        meals: List<CarbEntry>,
        actionHours: Double = DEFAULT_ACTION_HOURS,
        from: Instant,
        to: Instant,
    ): Double {
        if (!to.isAfter(from)) return 0.0
        val start = carbsOnBoard(meals, actionHours, from)
        val end = carbsOnBoard(meals, actionHours, to)
        return (start - end).coerceAtLeast(0.0)
    }
}
