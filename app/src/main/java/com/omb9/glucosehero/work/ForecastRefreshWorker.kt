package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.forecast.GlucoseForecastRepository
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Refreshes the persisted 30-60 minute glucose forecast while the app is idle.
 *
 * Also redraws the Glance widget. WorkManager will not run this more often
 * than 15 minutes, so this job is the backstop for coarse freshness buckets
 * (hours, days), not the Fresh-to-Stale flip. That boundary is a one-shot
 * in [WidgetFreshnessRefreshWorker], scheduled from [WidgetRefresher].
 * Skipping an unchanged caption is [WidgetRefresher]'s job so this wake
 * does not rewrite RemoteViews every 15 minutes when the label is still
 * "2 h ago".
 *
 * FEATURE: cgm-direct-ingest
 */
@HiltWorker
class ForecastRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val forecastRepository: GlucoseForecastRepository,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val forecastResult = try {
            forecastRepository.refresh()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
        runCatching { widgetRefresher.refresh() }
        return forecastResult
    }

    companion object {
        const val UNIQUE_NAME = "glucose_forecast_refresh"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<ForecastRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
