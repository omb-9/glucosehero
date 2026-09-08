package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.export.MarkdownExporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/**
 * Exports yesterday's log entries as a Markdown file into the app's local
 * document directory once every 24 hours. The worker is registered by
 * [com.omb9.glucosehero.GlucoseHeroApp] under a unique periodic name so the
 * cadence is preserved across app launches and process restarts.
 */
@HiltWorker
class DailyMarkdownWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val markdownExporter: MarkdownExporter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val yesterday = LocalDate.now().minusDays(1)
            markdownExporter.exportDayToLocalStorage(yesterday)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "daily_markdown_export_periodic"
    }
}
