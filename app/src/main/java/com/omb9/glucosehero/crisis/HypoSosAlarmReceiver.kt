package com.omb9.glucosehero.crisis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Non-exported receiver for the app's own SOS timeout and dismiss
 * PendingIntents. External senders (notably arbitrary installed apps) must
 * not be able to cancel a live severe-hypo countdown, so this receiver has no
 * intent-filter and is not exported.
 */
@AndroidEntryPoint
class HypoSosAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: HypoSosManager

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    HypoSosManager.ACTION_TIMEOUT -> manager.onTimeout()
                    HypoSosManager.ACTION_DISMISS -> manager.dismissPrompt()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
