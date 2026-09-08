package com.omb9.glucosehero

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.work.AutoBackupWorker
import com.omb9.glucosehero.work.DailyMarkdownWorker
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import com.omb9.glucosehero.work.InsightNotifier
import com.omb9.glucosehero.work.PatternRecognitionWorker
import com.omb9.glucosehero.work.PostMealReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class GlucoseHeroApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var insightNotifier: InsightNotifier

    @Inject
    lateinit var postMealReminderNotifier: PostMealReminderNotifier

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        insightNotifier.createChannel()
        postMealReminderNotifier.createChannel()
        schedulePatternRecognition()
        scheduleHealthConnectSync()
        observeForegroundHealthConnectSync()
        scheduleAutoBackup()
        scheduleDailyMarkdownExport()
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

    /**
     * Schedules a Health Connect sync every three hours. Health Connect is a
     * local platform component, so the request never requires a network. The
     * periodic work is only registered once sync has been enabled; the worker
     * re-checks the flag on every run in case it flips after scheduling.
     */
    private fun scheduleHealthConnectSync() {
        applicationScope.launch {
            if (!settingsDataStore.healthConnectSyncEnabled.first()) return@launch
            HealthConnectSyncWorker.schedulePeriodic(this@GlucoseHeroApp)
        }
    }

    /**
     * Registers an activity lifecycle observer to trigger a one-shot expedited Health Connect
     * sync whenever the app transitions from background to foreground.
     */
    private fun observeForegroundHealthConnectSync() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivityCount = 0
            private var isChangingConfig = false

            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0 && !isChangingConfig) {
                    applicationScope.launch {
                        if (settingsDataStore.healthConnectSyncEnabled.first()) {
                            HealthConnectSyncWorker.enqueueExpedited(activity, ExistingWorkPolicy.KEEP)
                        }
                    }
                }
                startedActivityCount++
                isChangingConfig = false
            }

            override fun onActivityStopped(activity: Activity) {
                isChangingConfig = activity.isChangingConfigurations
                startedActivityCount--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Schedules a daily backup into the user-nominated document tree. The
     * worker re-checks both the enabled flag and the folder URI on every run
     * so a folder revoked in system settings disables cleanly instead of
     * retrying forever.
     */
    private fun scheduleAutoBackup() {
        applicationScope.launch {
            if (!settingsDataStore.backupEnabled.first()) return@launch
            if (settingsDataStore.backupDirUri.first().isNullOrBlank()) return@launch

            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this@GlucoseHeroApp).enqueueUniquePeriodicWork(
                AutoBackupWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    /**
     * Schedules a daily Markdown export to run once every 24 hours, starting
     * at the next 3 AM so "yesterday" always refers to a fully elapsed local
     * day. KEEP preserves the cadence across app launches.
     */
    private fun scheduleDailyMarkdownExport() {
        val request = PeriodicWorkRequestBuilder<DailyMarkdownWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayToNextNightlyRun(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyMarkdownWorker.UNIQUE_NAME,
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
