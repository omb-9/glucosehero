package com.omb9.glucosehero.util

import java.time.Instant
import kotlin.math.min

/**
 * Pure Insulin-On-Board (IOB) decay math.
 *
 * Uses the Walsh / Scheiner rapid-acting IOB curve (the OpenAPS bilinear
 * activity triangle, integrated in closed form). Insulin activity is modeled
 * as a triangle of area 1 over `[0, DIA]`:
 *
 * - rises linearly from 0 at delivery to a peak at [ACTIVITY_PEAK_MINUTES]
 *   (clamped inside the DIA window, targeting 50-75 min)
 * - falls linearly from that peak to 0 at [diaHours] (typically a ~4 hour tail)
 *
 * Remaining fraction of a bolus at elapsed time `t` (milliseconds), with
 * `T` = DIA and `t_p` = peak, both in the same units:
 *
 * ```
 * remaining(t) = 1 - t² / (T · t_p)                         when 0 ≤ t ≤ t_p
 * remaining(t) = (T - t)² / (T · (T - t_p))                 when t_p < t < T
 * remaining(t) = 0                                          when t ≥ T
 * remaining(t) = 0                                          when t < 0 (future)
 * ```
 *
 * IOB is `dose_units * remaining(t)`, in insulin units. Compared with linear
 * DIA decay, remaining IOB stays higher around 60 min (little activity has
 * happened yet, the peak is still ahead or just arriving) and is lower at
 * 3–4 hours (the tail of the triangle is thinner than a straight line).
 *
 * This is an **approximate on-device model**, not a pharmacokinetic fit and
 * not a dosing recommendation. Individual absorption varies with site, dose,
 * and analog. Duration of insulin action remains configurable via [diaHours].
 *
 * Side-effect-free and timezone/locale-agnostic. Durations are exact epoch
 * millisecond differences, so the same inputs always produce the same output.
 */
object IobCalculator {

    const val MILLIS_PER_HOUR: Long = 3_600_000L

    /**
     * Target minutes from delivery to peak insulin *activity* (not peak IOB).
     * Walsh / OpenAPS rapid-acting default is 75 min, inside the 50-75 min
     * clinical window. Short DIA values clamp this so the peak stays before
     * the end of the action window.
     */
    const val ACTIVITY_PEAK_MINUTES: Double = 75.0

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
            acc + bolus.insulinUnits * remainingFraction(elapsedMillis, diaHours)
        }
    }

    /**
     * Insulin that will still be absorbed between [from] and [to] (inclusive of
     * the start, exclusive of a fully-elapsed DIA at [to]).
     *
     * Equals `IOB(from) - IOB(to)` under the same Walsh curve. Used by the
     * on-device glucose forecast so a 30-60 minute projection can convert
     * remaining insulin into an expected mg/dL drop via ISF. IOB(t) is
     * monotonically decreasing, so the result is never negative.
     *
     * @return units of insulin absorbed in the window; never negative.
     */
    fun insulinAbsorbedBetween(
        boluses: List<BolusEntry>,
        diaHours: Double,
        from: Instant,
        to: Instant,
    ): Double {
        if (!to.isAfter(from)) return 0.0
        val start = activeInsulinOnBoard(boluses, diaHours, from)
        val end = activeInsulinOnBoard(boluses, diaHours, to)
        return (start - end).coerceAtLeast(0.0)
    }

    /**
     * Minutes from delivery to peak activity for [diaHours], clamped so the
     * triangle stays well-formed (peak before half of DIA when DIA is short).
     */
    internal fun peakMillis(diaHours: Double): Double {
        val diaMillis = diaHours * MILLIS_PER_HOUR
        if (diaMillis <= 0.0) return 0.0
        val desired = ACTIVITY_PEAK_MINUTES * 60_000.0
        val maxPeak = diaMillis * 0.45
        val minPeak = min(50.0 * 60_000.0, maxPeak)
        return desired.coerceIn(minPeak, maxPeak)
    }

    /**
     * Fraction of a bolus still on board after [elapsedMillis]. 1 at delivery,
     * 0 at and after DIA. Approximate Walsh triangle, not a clinical dose.
     */
    internal fun remainingFraction(elapsedMillis: Double, diaHours: Double): Double {
        val diaMillis = diaHours * MILLIS_PER_HOUR
        if (diaMillis <= 0.0) return 0.0
        if (elapsedMillis < 0.0) return 0.0
        if (elapsedMillis >= diaMillis) return 0.0
        val peak = peakMillis(diaHours)
        if (peak <= 0.0 || peak >= diaMillis) {
            return (1.0 - elapsedMillis / diaMillis).coerceIn(0.0, 1.0)
        }
        return if (elapsedMillis <= peak) {
            (1.0 - (elapsedMillis * elapsedMillis) / (diaMillis * peak)).coerceIn(0.0, 1.0)
        } else {
            val tail = diaMillis - elapsedMillis
            val denom = diaMillis * (diaMillis - peak)
            (tail * tail / denom).coerceIn(0.0, 1.0)
        }
    }
}
