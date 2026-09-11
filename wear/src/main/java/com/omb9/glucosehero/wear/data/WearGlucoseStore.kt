package com.omb9.glucosehero.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataMap
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol
import com.omb9.glucosehero.wear.protocol.WearTrend
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wearGlucoseStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_glucose",
)

data class WearGlucoseSnapshot(
    val hasReading: Boolean = false,
    val glucoseMgdl: Float = 0f,
    val timestampMillis: Long = 0L,
    val trend: WearTrend = WearTrend.UNKNOWN,
    val unit: String = WearSyncProtocol.UNIT_MGDL,
    val targetLowMgdl: Float = 70f,
    val targetHighMgdl: Float = 180f,
    val lastQuickEntry: String = "",
) {
    val displayValue: String
        get() {
            if (!hasReading) return "--"
            return if (unit == WearSyncProtocol.UNIT_MMOL) {
                "%.1f".format(glucoseMgdl / WearSyncProtocol.MGDL_PER_MMOL.toFloat())
            } else {
                glucoseMgdl.roundToInt().toString()
            }
        }

    val unitLabel: String
        get() = if (unit == WearSyncProtocol.UNIT_MMOL) "mmol/L" else "mg/dL"
}

/**
 * On-watch cache of the phone's latest glucose DataItem so the activity,
 * complication, and tile can render without a live Data Layer round-trip.
 */
class WearGlucoseStore private constructor(private val context: Context) {

    private object Keys {
        val HAS_READING = booleanPreferencesKey(WearSyncProtocol.KEY_HAS_READING)
        val GLUCOSE_MGDL = floatPreferencesKey(WearSyncProtocol.KEY_GLUCOSE_MGDL)
        val TIMESTAMP = longPreferencesKey(WearSyncProtocol.KEY_TIMESTAMP_MILLIS)
        val TREND = stringPreferencesKey(WearSyncProtocol.KEY_TREND)
        val UNIT = stringPreferencesKey(WearSyncProtocol.KEY_UNIT)
        val TARGET_LOW = floatPreferencesKey(WearSyncProtocol.KEY_TARGET_LOW_MGDL)
        val TARGET_HIGH = floatPreferencesKey(WearSyncProtocol.KEY_TARGET_HIGH_MGDL)
        val LAST_QUICK_ENTRY = stringPreferencesKey("last_quick_entry")
    }

    val snapshot: Flow<WearGlucoseSnapshot> = context.wearGlucoseStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSnapshot() }

    suspend fun latest(): WearGlucoseSnapshot =
        context.wearGlucoseStore.data.catch { emit(emptyPreferences()) }.first().toSnapshot()

    suspend fun saveFromDataMap(map: DataMap) {
        context.wearGlucoseStore.edit { prefs ->
            prefs[Keys.HAS_READING] = map.getBoolean(WearSyncProtocol.KEY_HAS_READING, false)
            prefs[Keys.GLUCOSE_MGDL] = map.getFloat(WearSyncProtocol.KEY_GLUCOSE_MGDL, 0f)
            prefs[Keys.TIMESTAMP] = map.getLong(WearSyncProtocol.KEY_TIMESTAMP_MILLIS, 0L)
            prefs[Keys.TREND] = map.getString(WearSyncProtocol.KEY_TREND) ?: WearTrend.UNKNOWN.wireName
            prefs[Keys.UNIT] = map.getString(WearSyncProtocol.KEY_UNIT) ?: WearSyncProtocol.UNIT_MGDL
            prefs[Keys.TARGET_LOW] = map.getFloat(WearSyncProtocol.KEY_TARGET_LOW_MGDL, 70f)
            prefs[Keys.TARGET_HIGH] = map.getFloat(WearSyncProtocol.KEY_TARGET_HIGH_MGDL, 180f)
        }
    }

    suspend fun setLastQuickEntry(label: String) {
        context.wearGlucoseStore.edit { prefs ->
            prefs[Keys.LAST_QUICK_ENTRY] = label
        }
    }

    private fun Preferences.toSnapshot(): WearGlucoseSnapshot = WearGlucoseSnapshot(
        hasReading = this[Keys.HAS_READING] ?: false,
        glucoseMgdl = this[Keys.GLUCOSE_MGDL] ?: 0f,
        timestampMillis = this[Keys.TIMESTAMP] ?: 0L,
        trend = WearTrend.fromWire(this[Keys.TREND]),
        unit = this[Keys.UNIT] ?: WearSyncProtocol.UNIT_MGDL,
        targetLowMgdl = this[Keys.TARGET_LOW] ?: 70f,
        targetHighMgdl = this[Keys.TARGET_HIGH] ?: 180f,
        lastQuickEntry = this[Keys.LAST_QUICK_ENTRY].orEmpty(),
    )

    companion object {
        @Volatile
        private var instance: WearGlucoseStore? = null

        fun get(context: Context): WearGlucoseStore {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: WearGlucoseStore(app).also { instance = it }
            }
        }
    }
}
