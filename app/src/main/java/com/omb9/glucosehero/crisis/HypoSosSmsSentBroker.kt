package com.omb9.glucosehero.crisis

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges [HypoSosSmsSentReceiver] result codes back to the coroutine that
 * called [android.telephony.SmsManager.sendTextMessage].
 */
object HypoSosSmsSentBroker {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Int>>()

    fun register(token: String): CompletableDeferred<Int> {
        val deferred = CompletableDeferred<Int>()
        pending[token] = deferred
        return deferred
    }

    fun complete(token: String, resultCode: Int) {
        pending.remove(token)?.complete(resultCode)
    }

    fun forget(token: String) {
        pending.remove(token)
    }
}
