package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.health.SyncResult
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Pulls Health Connect data into local storage: glucose samples into the
 * high-frequency sample table, nutrition, exercise, sleep, and cycle into the
 * shared log.
 *
 * Follows the [PendingQueryWorker] pattern with assisted injection and robust
 * exception handling.
 *
 * The first run (no stored changes token) performs an initial import over the
 * user's configured range for every enabled record type before minting a
 * token; every subsequent run syncs incrementally from that token.
 *
 * Incremental synchronization loops `getChanges` while `hasMore`, applies
 * `UpsertionChange` and `DeletionChange` (deletions propagate to both samples
 * and entries so a reading removed by the user in their CGM app does not skew eA1c),
 * and falls back to a full window re-import when `changesTokenExpired` is true.
 *
 * Scheduled periodically every three hours with no network constraint (Health
 * Connect is a local platform provider), plus a one-shot expedited run on app foreground.
 * If the user revokes Health Connect permission in system settings, a
 * [SecurityException] is raised on the next read — we cancel periodic work,
 * flip the settings flag off, and record revocation so Settings/UI can
 * surface a single-line banner instead of hammering a revoked permission.
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: HealthConnectRepository,
    private val settingsDataStore: SettingsDataStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Re-check the flag here even though scheduling already checked it: it
        // may have been disabled between scheduling and this run.
        if (!settingsDataStore.healthConnectSyncEnabled.first()) return Result.success()

        return try {
            if (settingsDataStore.healthConnectChangesToken.first() == null) {
                repository.importGlucose(repository.initialImportFilter())
                repository.importNutrition(repository.initialImportFilter())
                repository.importExercise(repository.initialImportFilter())
                repository.importSleep(repository.initialImportFilter())
                repository.importCycle(repository.initialImportFilter())
            }

            when (repository.syncChanges()) {
                SyncResult.Success -> {
                    repository.writeBack()
                    settingsDataStore.setHealthConnectLastSync(System.currentTimeMillis())
                    Result.success()
                }
                SyncResult.NeedsFullResync -> {
                    // Fall back to a full window re-import when changes token is expired
                    repository.importGlucose(repository.initialImportFilter())
                    repository.importNutrition(repository.initialImportFilter())
                    repository.importExercise(repository.initialImportFilter())
                    repository.importSleep(repository.initialImportFilter())
                    repository.importCycle(repository.initialImportFilter())
                    when (repository.syncChanges()) {
                        SyncResult.Success -> {
                            repository.writeBack()
                            settingsDataStore.setHealthConnectLastSync(System.currentTimeMillis())
                            Result.success()
                        }
                        else -> Result.retry()
                    }
                }
                SyncResult.Unavailable -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission was deliberately revoked in system Health Connect
            // settings. Cancel periodic work, flip the settings flag off, and
            // surface a single-line banner rather than retrying indefinitely
            // against permissions the user chose not to grant.
            cancelPeriodic(applicationContext)
            settingsDataStore.setHealthConnectSyncEnabled(false)
            settingsDataStore.setHealthConnectRevoked(true)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "health_connect_sync_periodic"
        const val EXPEDITED_UNIQUE_NAME = "health_connect_sync_expedited"

        /**
         * Schedules periodic Health Connect sync every three hours with no network constraint.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Enqueues a one-shot expedited Health Connect sync (e.g. on app foreground
         * or user-triggered refresh).
         */
        fun enqueueExpedited(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        ) {
            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                EXPEDITED_UNIQUE_NAME,
                policy,
                request,
            )
        }

        /**
         * Cancels periodic synchronization when Health Connect sync is toggled off.
         */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
