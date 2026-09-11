package com.omb9.glucosehero.wear.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inexact RTC alarm at the Fresh-to-Stale boundary.
 *
 * Complication `UPDATE_PERIOD_SECONDS` is a hint the system may stretch to
 * ~30 minutes. This alarm (plus validTimeRange on the payload) is what
 * actually asks for a redraw when the stream goes silent. [setAndAllowWhileIdle]
 * does not need SCHEDULE_EXACT_ALARM; during Doze it may fire a few minutes
 * late, which is still inside the coarse "min ago" bucket.
 *
 * FEATURE: cgm-direct-ingest
 */
class WearFreshnessAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                WearPhoneMessenger.requestVisualUpdates(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.omb9.glucosehero.wear.ACTION_FRESHNESS_TICK"
        private const val REQUEST_CODE = 42
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun scheduleCrossing(context: Context, snapshot: WearGlucoseSnapshot, nowMillis: Long) {
            val triggerAt = if (snapshot.hasReading) {
                WearFreshnessPolicy.staleCrossingAtMillis(snapshot.timestampMillis, nowMillis)
            } else {
                null
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context)
            if (triggerAt == null) {
                am.cancel(pending)
                return
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WearFreshnessAlarmReceiver::class.java).setAction(ACTION)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
