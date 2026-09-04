package com.omb9.glucosehero

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.omb9.glucosehero.work.InsightNotifier
import com.omb9.glucosehero.work.PatternRecognitionWorker
import com.omb9.glucosehero.work.PostMealReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GlucoseHeroApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var insightNotifier: InsightNotifier

    @Inject
    lateinit var postMealReminderNotifier: PostMealReminderNotifier

    override fun onCreate() {
        super.onCreate()
        insightNotifier.createChannel()
        postMealReminderNotifier.createChannel()
        schedulePatternRecognition()
    }

    /**
     * Schedules the nightly pattern detector to run once every 24 hours,
     * starting at the next 3 AM. KEEP preserves the original cadence across
     * app launches so a foreground launch never pushes the run later.
     */
    private fun schedulePatternRecognition() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PatternRecognitionWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayToNextNightlyRun(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PatternRecognitionWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun initialDelayToNextNightlyRun(): Long {
        val now = ZonedDateTime.now()
        val tonightAtThree = now.toLocalDate().atTime(LocalTime.of(3, 0)).atZone(now.zone)
        val nextRun = if (tonightAtThree.isAfter(now)) tonightAtThree else tonightAtThree.plusDays(1)
        return Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
    }

    /** WorkManager (manifest initializer removed) builds workers through Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
