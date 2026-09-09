package com.omb9.glucosehero.ui.glance

import android.content.Context
import android.os.TransactionTooLargeException
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decouples widget refreshes from ViewModels so screens never need to hold a
 * raw [Context]. All failures are swallowed here: a widget refresh must never
 * take down (or block) the save flow that triggered it.
 *
 * Glance compositions that include lists or large bitmaps can exceed Binder's
 * ~1 MB limit. [WidgetTrendBitmap] already caps the trend PNG; if an update
 * still throws [TransactionTooLargeException], this retries once with a
 * text-only widget (no trend bitmap).
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refresh() {
        val first = runCatching { GlucoseHeroGlanceWidget().updateAll(context) }
        val error = first.exceptionOrNull() ?: return
        if (!isTransactionTooLarge(error)) return
        runCatching { GlucoseHeroGlanceWidget(omitTrendBitmap = true).updateAll(context) }
    }
}

internal fun isTransactionTooLarge(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is TransactionTooLargeException) return true
        val message = current.message.orEmpty()
        if (message.contains("TransactionTooLargeException")) return true
        current = current.cause
    }
    return false
}
