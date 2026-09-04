package com.omb9.glucosehero.util

import java.time.Instant

/**
 * Pure Insulin-On-Board (IOB) decay math.
 *
 * Applies the standard linear decay curve to recent rapid-acting boluses: a
 * bolus is assumed to be fully absorbed once one DIA (duration of insulin
 * action) has elapsed, with remaining activity shrinking linearly from 100%
 * at delivery down to 0% at the end of the window.
 *
 * Side-effect-free and timezone/locale-agnostic — durations are computed as
 * exact differences between epoch-millisecond timestamps, so the same inputs
 * always produce the same output on every device.
 */
object IobCalculator {

    const val MILLIS_PER_HOUR: Long = 3_600_000L

    /** A single rapid-acting bolus delivery, as consumed by IOB math. */
    data class BolusEntry(
        /** Delivery time, epoch milliseconds. */
        val timestampMillis: Long,
        /** Delivered amount, in insulin units. */
        val insulinUnits: Double,
    )

    /**
     * Sums the still-active insulin from [boluses] at [now].
     *
     * @param boluses recent bolus deliveries (timestamps + amounts).
     * @param diaHours duration of insulin action, in hours (must be > 0).
     * @param now the instant at which IOB is evaluated.
     * @return total active insulin, in units. Entries delivered before the DIA
     *   window (fully absorbed) and future-dated entries contribute nothing.
     */
    fun activeInsulinOnBoard(
        boluses: List<BolusEntry>,
        diaHours: Double,
        now: Instant = Instant.now(),
    ): Double {
        val diaMillis = diaHours * MILLIS_PER_HOUR
        if (diaMillis <= 0.0) return 0.0

        val nowMillis = now.toEpochMilli()
        return boluses.fold(0.0) { acc, bolus ->
            val elapsedMillis = (nowMillis - bolus.timestampMillis).toDouble()
            // Future-dated (clock skew) => not yet active.
            if (elapsedMillis < 0.0) return@fold acc
            // At or past the DIA boundary => fully absorbed.
            if (elapsedMillis >= diaMillis) return@fold acc

            acc + bolus.insulinUnits * (1.0 - elapsedMillis / diaMillis)
        }
    }
}