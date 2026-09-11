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
import kotlinx.coroutines.runBlocking
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

    /**
     * Asks the system to redraw complication and tile only when the visible
     * signature changed. A wake that would reprint the same "112 · Just now"
     * is a watch battery regression.
     */
    fun requestVisualUpdates(context: Context) {
        val appContext = context.applicationContext
        val snapshot = runBlocking { WearGlucoseStore.get(appContext).latest() }
        val now = System.currentTimeMillis()
        WearFreshnessAlarmReceiver.scheduleCrossing(appContext, snapshot, now)
        val next = WearFreshnessPolicy.visualSignature(snapshot, now)
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_SIGNATURE, null)
        if (!WearFreshnessPolicy.shouldRequestUpdate(previous, next)) return
        prefs.edit().putString(KEY_SIGNATURE, next).apply()
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                appContext,
                ComponentName(appContext, GlucoseComplicationService::class.java),
            ).requestUpdateAll()
        }
        runCatching {
            TileService.getUpdater(appContext).requestUpdate(QuickEntryTileService::class.java)
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

    private const val PREFS = "wear_freshness_render"
    private const val KEY_SIGNATURE = "signature"
}
