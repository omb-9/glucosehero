package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.NightscoutCgmSource
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Nightscout REST poller.
 *
 * WorkManager's periodic floor is 15 minutes. Nightscout CGM readings arrive
 * about every 5 minutes, so this worker alone cannot keep the chart fresh.
 * Three tiers, only the first of which is this class:
 *
 * 1. **Background (this worker):** a 15-minute [PeriodicWorkRequest] that
 *    fetches since the Room cursor. Up to three 5-minute readings may be
 *    missed between runs; they are backfilled on the next success, so data
 *    is not lost even though freshness suffers.
 * 2. **Foreground:** while the app process is resumed, an in-process 5-minute
 *    loop in [com.omb9.glucosehero.data.cgm.nightscout.NightscoutPollCoordinator]
 *    calls [CgmIngestService.pull] and is cancelled on process stop.
 * 3. **Optional foreground service:** user-opt-in, **off by default**, for a
 *    true 5-minute cadence with the app in the background. That path shows a
 *    persistent notification and uses extra battery; see
 *    [com.omb9.glucosehero.data.cgm.nightscout.NightscoutForegroundService].
 *
 * Callers must invoke [CgmIngestService.pull] only; this worker re-checks
 * [SettingsDataStore.nightscoutEnabled] because the flag can flip after
 * enqueue. Transient network failures use [Result.retry] with exponential
 * backoff. Auth, cleartext, and acknowledgement failures are not retried.
 *
 * FEATURE: cgm-direct-ingest
 */
@HiltWorker
class NightscoutSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val ingestService: CgmIngestService,
    private val nightscoutSource: NightscoutCgmSource,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.nightscoutEnabledSnapshot()) {
            cancelPeriodic(applicationContext)
            return resultIfDisabled(enabled = false)!!
        }
        return try {
            val outcome = ingestService.pull(nightscoutSource)
            if (outcome.disabled) {
                cancelPeriodic(applicationContext)
                Result.success()
            } else {
                mapPullError(outcome.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mapPullError(NightscoutErrorCodes.NETWORK)
        }
    }

    companion object {
        const val UNIQUE_NAME = "nightscout_sync_periodic"

        /**
         * Returns [Result.success] when the source is off so WorkManager does
         * not retry a disabled poller. Null means the caller should proceed.
         */
        fun resultIfDisabled(enabled: Boolean): Result? =
            if (enabled) null else Result.success()

        fun mapPullError(errorCode: String?): Result {
            if (errorCode.isNullOrBlank()) return Result.success()
            return if (errorCode == NightscoutErrorCodes.NETWORK ||
                errorCode == NightscoutErrorCodes.HTTP
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        }

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<NightscoutSyncWorker>(
                NightscoutLimits.PERIODIC_WORK_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
