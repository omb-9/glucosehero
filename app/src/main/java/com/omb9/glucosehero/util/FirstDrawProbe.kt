package com.omb9.glucosehero.util

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One-shot cold-start timestamps for first *draw* vs composition.
 *
 * [logOnce] is safe from a draw pass. Do not use these lines as a loading
 * delay; splash keep-on-screen is the first-frame fix.
 */
object FirstDrawProbe {
    const val TAG = "GHFirstDraw"

    private val originElapsed = AtomicLong(0L)
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun markOrigin(reason: String) {
        originElapsed.compareAndSet(0L, SystemClock.elapsedRealtime())
        logOnce("origin", "ORIGIN $reason")
    }

    fun logOnce(key: String, message: String) {
        if (!seen.add(key)) return
        val now = SystemClock.elapsedRealtime()
        val origin = originElapsed.get().let { if (it == 0L) now else it }
        Log.i(TAG, "$message key=$key t_ms=${now - origin} elapsedRealtime=$now")
    }
}

/** Logs the first time this modifier actually participates in a draw pass. */
fun Modifier.probeFirstDraw(key: String, message: String): Modifier =
    drawWithContent {
        drawContent()
        FirstDrawProbe.logOnce(key, message)
    }
