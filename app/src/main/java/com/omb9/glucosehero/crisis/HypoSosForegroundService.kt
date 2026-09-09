package com.omb9.glucosehero.crisis

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the 5-minute hypo SOS countdown alive with an ongoing notification.
 * Dismiss via the notification action or [HypoSosPromptActivity] cancels this
 * service and the timeout alarm.
 */
@AndroidEntryPoint
class HypoSosForegroundService : Service() {

    @Inject lateinit var manager: HypoSosManager
    @Inject lateinit var notifier: HypoSosNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            manager.hydrate()
            val pending = manager.pending.value ?: run {
                stopSelf()
                return@launch
            }
            val notification = notifier.promptNotification(pending)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    HypoSosNotifier.PROMPT_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(HypoSosNotifier.PROMPT_ID, notification)
            }
        }
        watchJob?.cancel()
        watchJob = scope.launch {
            while (true) {
                val pending = manager.pending.value ?: manager.let {
                    it.hydrate()
                    it.pending.value
                }
                if (pending == null) {
                    stopSelf()
                    return@launch
                }
                if (System.currentTimeMillis() >= pending.timeoutAtMillis) {
                    manager.onTimeout()
                    stopSelf()
                    return@launch
                }
                delay(1_000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
