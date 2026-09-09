package com.omb9.glucosehero.wear

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

enum class WearConnectionStatus {
    PLAY_SERVICES_MISSING,
    NO_WATCH,
    READY,
}

object WearAvailability {

    fun playServicesAvailable(context: Context): Boolean {
        val result = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    suspend fun status(context: Context): WearConnectionStatus {
        if (!playServicesAvailable(context)) return WearConnectionStatus.PLAY_SERVICES_MISSING
        val watchNodes = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WearSyncProtocol.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        if (watchNodes.isNotEmpty()) return WearConnectionStatus.READY
        val connected = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
        }.getOrDefault(emptyList())
        return if (connected.isEmpty()) WearConnectionStatus.NO_WATCH else WearConnectionStatus.READY
    }
}
