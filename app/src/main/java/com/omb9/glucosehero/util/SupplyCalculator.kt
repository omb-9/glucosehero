package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.Supply
import java.time.Instant

/**
 * Pure, side-effect-free supply lifecycle math. All calculations are exact
 * double-precision deltas between epoch-millisecond timestamps — there is no
 * locale or timezone involvement, so the same inputs produce the same results
 * on every device.
 */
object SupplyCalculator {

    const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    data class SupplyStatus(
        val daysRemaining: Double,
        val hoursRemaining: Double,
        /** 0.0f → brand new, 1.0f → fully consumed (or over-consumed). */
        val progressPercentage: Float,
    ) {
        val isExpired: Boolean get() = progressPercentage >= 1f
    }

    fun status(supply: Supply, now: Instant = Instant.now()): SupplyStatus {
        val lifespanMillis = supply.expectedLifespanDays.coerceAtLeast(0) * MILLIS_PER_DAY
        if (lifespanMillis <= 0L) {
            return SupplyStatus(
                daysRemaining = 0.0,
                hoursRemaining = 0.0,
                progressPercentage = 1f,
            )
        }

        val nowMillis = now.toEpochMilli()
        val elapsedMillis = (nowMillis - supply.startedAt)
            .coerceIn(0L, lifespanMillis)
        val remainingMillis = (supply.startedAt + lifespanMillis - nowMillis)
            .coerceIn(0L, lifespanMillis)

        val progress = (elapsedMillis.toDouble() / lifespanMillis.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
        val daysRemaining = remainingMillis.toDouble() / MILLIS_PER_DAY.toDouble()

        return SupplyStatus(
            daysRemaining = daysRemaining,
            hoursRemaining = daysRemaining * 24.0,
            progressPercentage = progress,
        )
    }
}
