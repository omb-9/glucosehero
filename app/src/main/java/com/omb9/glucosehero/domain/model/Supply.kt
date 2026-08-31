package com.omb9.glucosehero.domain.model

/** Hardware categories tracked by the Supply Tracker. */
enum class SupplyType(val label: String) {
    SENSOR("Sensor"),
    INSULIN_VIAL("Insulin Vial"),
    PUMP_SITE("Pump Site"),
}

/**
 * A single hardware item's lifecycle window. [expectedLifespanDays] is the
 * nominal wear time; the exact consumed/remaining time is derived by
 * [com.omb9.glucosehero.util.SupplyCalculator].
 */
data class Supply(
    val id: Long = 0L,
    val type: SupplyType,
    val startedAt: Long,
    val expectedLifespanDays: Int,
)
