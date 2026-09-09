package com.omb9.glucosehero.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class WearGlucosePayload(
    val reading: GlucosePointRow?,
    val trend: WearTrend,
    val settings: UserSettings,
)

@Singleton
class WearDataLayerClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun publishGlucose(payload: WearGlucosePayload) {
        if (!WearAvailability.playServicesAvailable(context)) return
        if (WearAvailability.status(context) != WearConnectionStatus.READY) return
        val request = PutDataMapRequest.create(WearSyncProtocol.PATH_GLUCOSE_LATEST).apply {
            val reading = payload.reading
            dataMap.putBoolean(WearSyncProtocol.KEY_HAS_READING, reading != null)
            dataMap.putFloat(
                WearSyncProtocol.KEY_GLUCOSE_MGDL,
                reading?.glucoseMgdl?.toFloat() ?: 0f,
            )
            dataMap.putLong(
                WearSyncProtocol.KEY_TIMESTAMP_MILLIS,
                reading?.timestamp ?: 0L,
            )
            dataMap.putString(WearSyncProtocol.KEY_TREND, payload.trend.wireName)
            dataMap.putString(
                WearSyncProtocol.KEY_UNIT,
                when (payload.settings.unit) {
                    GlucoseUnit.MMOL -> WearSyncProtocol.UNIT_MMOL
                    GlucoseUnit.MGDL -> WearSyncProtocol.UNIT_MGDL
                },
            )
            dataMap.putFloat(WearSyncProtocol.KEY_TARGET_LOW_MGDL, payload.settings.targetLowMgdl)
            dataMap.putFloat(WearSyncProtocol.KEY_TARGET_HIGH_MGDL, payload.settings.targetHighMgdl)
            setUrgent()
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
    }

    suspend fun clearGlucose() {
        if (!WearAvailability.playServicesAvailable(context)) return
        val uri = PutDataMapRequest.create(WearSyncProtocol.PATH_GLUCOSE_LATEST).uri
        runCatching {
            Wearable.getDataClient(context).deleteDataItems(uri).await()
        }
    }
}
