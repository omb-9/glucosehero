package com.omb9.glucosehero.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared pull-to-refresh timing for Log and Stats: enqueue Health Connect
 * work when that source is on, wait for a terminal [WorkInfo] (or the
 * ceiling), and always hold the indicator for at least [MIN_MILLIS].
 */
internal object PullToRefreshSync {
    const val MIN_MILLIS = 600L
    const val MAX_MILLIS = 8000L

    data class Result(
        val enqueued: Boolean,
        val anySourceEnabled: Boolean,
    )

    suspend fun run(
        context: Context,
        settingsDataStore: SettingsDataStore,
    ): Result {
        val hcEnabled = settingsDataStore.healthConnectSyncEnabled.first()
        val ingest = settingsDataStore.cgmIngestSettingsSnapshot()
        val anySourceEnabled = hcEnabled ||
            ingest.nightscoutEnabled ||
            ingest.xdripBroadcastEnabled ||
            ingest.libreLinkUpEnabled

        coroutineScope {
            val floor = async { delay(MIN_MILLIS) }
            if (hcEnabled) {
                val workId = HealthConnectSyncWorker.enqueueExpedited(
                    context,
                    ExistingWorkPolicy.REPLACE,
                )
                awaitWorkTerminal(context, workId, MAX_MILLIS)
            }
            floor.await()
        }
        return Result(enqueued = hcEnabled, anySourceEnabled = anySourceEnabled)
    }
}

internal fun WorkInfo.State.isTerminal(): Boolean =
    this == WorkInfo.State.SUCCEEDED ||
        this == WorkInfo.State.FAILED ||
        this == WorkInfo.State.CANCELLED

internal suspend fun awaitWorkTerminal(
    context: Context,
    workId: UUID,
    timeoutMillis: Long,
) {
    withTimeoutOrNull(timeoutMillis) {
        WorkManager.getInstance(context)
            .getWorkInfoByIdFlow(workId)
            .filterNotNull()
            .first { it.state.isTerminal() }
    }
}
