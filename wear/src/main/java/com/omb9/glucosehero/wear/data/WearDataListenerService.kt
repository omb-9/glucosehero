package com.omb9.glucosehero.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol
import kotlinx.coroutines.runBlocking

/**
 * Receives phone → watch DataItems for `/glucosehero/glucose/latest` and
 * refreshes the local snapshot plus complication/tile surfaces.
 */
class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            for (event in buffer) {
                val path = event.dataItem.uri.path ?: continue
                if (path != WearSyncProtocol.PATH_GLUCOSE_LATEST) continue
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                runBlocking {
                    WearGlucoseStore.get(this@WearDataListenerService).saveFromDataMap(map)
                }
                WearPhoneMessenger.requestVisualUpdates(this)
            }
        }
    }
}
