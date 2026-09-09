package com.omb9.glucosehero.wear.data

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.omb9.glucosehero.wear.complication.GlucoseComplicationService
import com.omb9.glucosehero.wear.protocol.WearQuickEntryPayload
import com.omb9.glucosehero.wear.protocol.WearQuickEntryType
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol
import com.omb9.glucosehero.wear.tile.QuickEntryTileService
import kotlinx.coroutines.tasks.await

object WearPhoneMessenger {

    suspend fun requestGlucose(context: Context) {
        send(context, WearSyncProtocol.PATH_GLUCOSE_REQUEST, ByteArray(0))
    }

    suspend fun sendQuickEntry(
        context: Context,
        type: WearQuickEntryType,
        amount: Double,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val payload = WearQuickEntryPayload(
            type = type,
            amount = amount,
            timestampMillis = timestampMillis,
        )
        val bytes = WearSyncProtocol.json.encodeToString(
            WearQuickEntryPayload.serializer(),
            payload,
        ).toByteArray(Charsets.UTF_8)
        send(context, WearSyncProtocol.PATH_ENTRY_QUICK, bytes)
        WearGlucoseStore.get(context).setLastQuickEntry(feedbackLabel(type, amount))
        requestVisualUpdates(context)
    }

    fun requestVisualUpdates(context: Context) {
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, GlucoseComplicationService::class.java),
            ).requestUpdateAll()
        }
        runCatching {
            TileService.getUpdater(context).requestUpdate(QuickEntryTileService::class.java)
        }
    }

    private suspend fun send(context: Context, path: String, data: ByteArray) {
        val node = resolvePhoneNode(context) ?: return
        Wearable.getMessageClient(context).sendMessage(node.id, path, data).await()
    }

    private suspend fun resolvePhoneNode(context: Context): Node? {
        val capable = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WearSyncProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        capable.firstOrNull { it.isNearby }?.let { return it }
        capable.firstOrNull()?.let { return it }
        val connected = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
        }.getOrDefault(emptyList())
        return connected.firstOrNull { it.isNearby } ?: connected.firstOrNull()
    }

    private fun feedbackLabel(type: WearQuickEntryType, amount: Double): String = when (type) {
        WearQuickEntryType.WATER -> "Logged ${amount.toInt()} ml water"
        WearQuickEntryType.CARBS -> "Logged ${amount.toInt()} g carbs"
        WearQuickEntryType.INSULIN -> "Logged $amount U bolus"
    }
}
