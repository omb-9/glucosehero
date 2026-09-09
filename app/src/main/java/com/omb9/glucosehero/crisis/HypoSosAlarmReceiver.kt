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
                    Intent.ACTION_BOOT_COMPLETED -> manager.evaluateLatest()
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
