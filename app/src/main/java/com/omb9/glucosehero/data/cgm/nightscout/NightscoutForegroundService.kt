package com.omb9.glucosehero.data.cgm.nightscout

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.NightscoutCgmSource
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional 5-minute Nightscout poll while the app is in the background.
 *
 * Why this exists: WorkManager will not run more often than 15 minutes.
 * This service is **off by default** because it needs a persistent
 * notification and extra battery. Pattern matches
 * [com.omb9.glucosehero.crisis.HypoSosForegroundService].
 *
 * FEATURE: cgm-direct-ingest
 */
@AndroidEntryPoint
class NightscoutForegroundService : Service() {

    @Inject lateinit var ingestService: CgmIngestService
    @Inject lateinit var nightscoutSource: NightscoutCgmSource
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var notifier: NightscoutNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifier.createChannel()
        val notification = notifier.ongoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NightscoutNotifier.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NightscoutNotifier.ONGOING_ID, notification)
        }
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val enabled = settingsDataStore.nightscoutEnabledSnapshot()
                val fgs = settingsDataStore.nightscoutForegroundServiceSnapshot()
                if (!enabled || !fgs) {
                    stopSelf()
                    return@launch
                }
                try {
                    ingestService.pull(nightscoutSource)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Last error is recorded by ingest. Keep the cadence.
                }
                delay(NightscoutLimits.FOREGROUND_POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
