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
 * Exported solely so the system can deliver BOOT_COMPLETED. Re-evaluates the
 * latest glucose reading to re-arm a severe-hypo SOS that was in flight before
 * a reboot.
 */
@AndroidEntryPoint
class HypoSosBootReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: HypoSosManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                val override = testEvaluateLatest
                if (override != null) {
                    override()
                } else {
                    manager.evaluateLatest()
                }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Instrumentation-only override. Null in production. Lets tests
         * invoke the receiver without re-arming a live caregiver SOS.
         */
        @Volatile
        internal var testEvaluateLatest: (suspend () -> Unit)? = null
    }
}
