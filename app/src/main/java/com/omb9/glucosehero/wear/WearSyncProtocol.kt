package com.omb9.glucosehero.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wearable Data Layer contract shared with `:wear`. Keep constants identical to
 * `wear/.../protocol/WearSyncProtocol.kt`. Glucose values are canonical mg/dL,
 * matching Room's `glucose_readings` view.
 */
object WearSyncProtocol {
    const val CAPABILITY_WATCH = "glucosehero_wear"
    const val CAPABILITY_PHONE = "glucosehero_phone"

    const val PATH_GLUCOSE_LATEST = "/glucosehero/glucose/latest"
    const val PATH_GLUCOSE_REQUEST = "/glucosehero/glucose/request"
    const val PATH_ENTRY_QUICK = "/glucosehero/entry/quick"

    const val KEY_HAS_READING = "has_reading"
    const val KEY_GLUCOSE_MGDL = "glucose_mgdl"
    const val KEY_TIMESTAMP_MILLIS = "timestamp_millis"
    const val KEY_TREND = "trend"
    const val KEY_UNIT = "unit"
    const val KEY_TARGET_LOW_MGDL = "target_low_mgdl"
    const val KEY_TARGET_HIGH_MGDL = "target_high_mgdl"

    const val UNIT_MGDL = "MGDL"
    const val UNIT_MMOL = "MMOL"

    const val DEFAULT_WATER_ML = 250.0
    const val DEFAULT_CARBS_G = 15.0
    const val DEFAULT_INSULIN_U = 1.0

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

enum class WearTrend(val wireName: String, val arrow: String) {
    DOUBLE_UP("DOUBLE_UP", "↑↑"),
    SINGLE_UP("SINGLE_UP", "↑"),
    FORTY_FIVE_UP("FORTY_FIVE_UP", "↗"),
    FLAT("FLAT", "→"),
    FORTY_FIVE_DOWN("FORTY_FIVE_DOWN", "↘"),
    SINGLE_DOWN("SINGLE_DOWN", "↓"),
    DOUBLE_DOWN("DOUBLE_DOWN", "↓↓"),
    UNKNOWN("UNKNOWN", ""),
    ;

    companion object {
        fun fromWire(value: String?): WearTrend =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
enum class WearQuickEntryType { WATER, CARBS, INSULIN }

@Serializable
data class WearQuickEntryPayload(
    val type: WearQuickEntryType,
    val amount: Double,
    val timestampMillis: Long,
)
