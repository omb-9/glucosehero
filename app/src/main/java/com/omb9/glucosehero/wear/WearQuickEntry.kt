package com.omb9.glucosehero.wear

import com.omb9.glucosehero.domain.model.LogEvent
import kotlin.math.roundToInt

fun WearQuickEntryPayload.toLogEvent(): LogEvent {
    val timestamp = timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    return when (type) {
        WearQuickEntryType.WATER -> LogEvent(
            timestamp = timestamp,
            note = "#water ${amount.roundToInt()} ml",
        )
        WearQuickEntryType.CARBS -> LogEvent(
            timestamp = timestamp,
            carbsGrams = amount.roundToInt(),
        )
        WearQuickEntryType.INSULIN -> LogEvent(
            timestamp = timestamp,
            insulinBolusUnits = amount,
        )
    }
}
