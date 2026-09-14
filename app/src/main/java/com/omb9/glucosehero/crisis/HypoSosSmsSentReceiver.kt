package com.omb9.glucosehero.crisis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives SmsManager sentIntents. Not exported: only this app's
 * PendingIntents should complete a live SOS dispatch wait.
 */
class HypoSosSmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HypoSosSmsSender.ACTION_SMS_SENT) return
        val token = intent.getStringExtra(HypoSosSmsSender.EXTRA_TOKEN) ?: return
        HypoSosSmsSentBroker.complete(token, resultCode)
    }
}
