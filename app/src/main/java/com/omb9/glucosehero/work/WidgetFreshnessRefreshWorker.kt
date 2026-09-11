package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * One-shot Glance redraw timed to the Fresh-to-Stale boundary.
 *
 * WorkManager's periodic floor is 15 minutes, which is later than the
 * 8-minute Fresh ceiling. This worker is scheduled from [WidgetRefresher]
 * with an initial delay of remaining Fresh time plus 1 ms so the widget
 * caption can flip without the user opening the app. Doze may still delay
 * it; [ForecastRefreshWorker] remains the 15-minute backstop.
 *
 * FEATURE: cgm-direct-ingest
 */
@HiltWorker
class WidgetFreshnessRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        widgetRefresher.refresh()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "widget_freshness_boundary"

        fun schedule(context: Context, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<WidgetFreshnessRefreshWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
