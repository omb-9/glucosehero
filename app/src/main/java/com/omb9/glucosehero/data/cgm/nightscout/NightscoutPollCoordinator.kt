package com.omb9.glucosehero.data.cgm.nightscout

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.NightscoutCgmSource
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.work.NightscoutSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns Nightscout scheduling: 15-minute WorkManager, 5-minute in-process
 * poll while resumed, and the optional foreground service.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class NightscoutPollCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val ingestService: CgmIngestService,
    private val nightscoutSource: NightscoutCgmSource,
    private val notifier: NightscoutNotifier,
) {

    private var inProcessJob: Job? = null
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        notifier.createChannel()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    startInProcessPoll(scope)
                    scope.launch { applyFlags() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    stopInProcessPoll()
                }
            },
        )
        scope.launch {
            combine(
                settingsDataStore.nightscoutEnabled,
                settingsDataStore.nightscoutForegroundService,
            ) { enabled, fgs -> enabled to fgs }
                .collect { applyFlags() }
        }
    }

    /**
     * Called from Settings when the user flips Nightscout or the FGS toggle
     * so WorkManager is cancelled immediately rather than waiting for the
     * next collect tick.
     */
    suspend fun applyFlags() {
        val enabled = settingsDataStore.nightscoutEnabledSnapshot()
        val fgs = settingsDataStore.nightscoutForegroundServiceSnapshot()
        if (enabled) {
            NightscoutSyncWorker.schedulePeriodic(context)
            if (fgs) startForegroundService() else stopForegroundService()
        } else {
            NightscoutSyncWorker.cancelPeriodic(context)
            stopForegroundService()
        }
    }

    private fun startInProcessPoll(scope: CoroutineScope) {
        inProcessJob?.cancel()
        inProcessJob = scope.launch {
            while (true) {
                val enabled = settingsDataStore.nightscoutEnabledSnapshot()
                val fgs = settingsDataStore.nightscoutForegroundServiceSnapshot()
                if (enabled && !fgs) {
                    try {
                        ingestService.pull(nightscoutSource)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                delay(NightscoutLimits.FOREGROUND_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopInProcessPoll() {
        inProcessJob?.cancel()
        inProcessJob = null
    }

    private fun startForegroundService() {
        val intent = Intent(context, NightscoutForegroundService::class.java)
        runCatching { context.startForegroundService(intent) }
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, NightscoutForegroundService::class.java))
    }
}
