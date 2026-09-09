package com.omb9.glucosehero.wear

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Pushes the newest row from `glucose_readings` (CGM samples plus manual
 * entries) to the watch via DataClient. Missing Play services or an unpaired
 * watch is treated as success so WorkManager does not retry forever.
 */
@HiltWorker
class WearSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryDao: EntryDao,
    private val settingsRepository: SettingsRepository,
    private val settingsStore: WearSyncSettingsStore,
    private val dataLayerClient: WearDataLayerClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsStore.syncEnabled.first()) return Result.success()
        return try {
            val now = System.currentTimeMillis()
            val windowPoints = entryDao.glucoseReadingPointsSince(now - WearTrendCalculator.WINDOW_MS)
            val latest = windowPoints.lastOrNull() ?: entryDao.latestGlucoseReading()
            val trend = WearTrendCalculator.from(windowPoints, now)
            val settings = settingsRepository.settings.first()
            dataLayerClient.publishGlucose(
                WearGlucosePayload(
                    reading = latest,
                    trend = trend,
                    settings = settings,
                ),
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "wear_sync_periodic"
        const val ONE_SHOT_NAME = "wear_sync_oneshot"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WearSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WearSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_SHOT_NAME)
        }
    }
}
