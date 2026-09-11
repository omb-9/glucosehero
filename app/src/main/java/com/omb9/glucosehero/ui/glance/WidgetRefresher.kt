package com.omb9.glucosehero.ui.glance

import android.content.Context
import android.os.TransactionTooLargeException
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.omb9.glucosehero.work.WidgetFreshnessRefreshWorker
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Decouples widget refreshes from ViewModels so screens never need to hold a
 * raw [Context]. All failures are swallowed here: a widget refresh must never
 * take down (or block) the save flow that triggered it.
 *
 * Glance compositions that include lists or large bitmaps can exceed Binder's
 * ~1 MB limit. [WidgetTrendBitmap] already caps the trend PNG; if an update
 * still throws [TransactionTooLargeException], this retries once with a
 * text-only widget (no trend bitmap).
 *
 * Freshness is classified at draw time, so this method must also run when
 * wall-clock time crosses Stale, not only when a new row lands. After a
 * successful (or skipped) draw it schedules [WidgetFreshnessRefreshWorker]
 * for that boundary. [updateAll] is skipped when [WidgetFreshnessPolicy.renderKey]
 * is unchanged so a 15-minute tick does not rewrite an identical caption.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refresh() {
        val appContext = context.applicationContext
        val nextKey = runCatching { currentRenderKey(appContext) }.getOrNull()
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousKey = prefs.getString(KEY_RENDER, null)
        if (nextKey != null && !WidgetFreshnessPolicy.shouldUpdate(previousKey, nextKey)) {
            runCatching { scheduleBoundary(appContext) }
            return
        }
        val first = runCatching { GlucoseHeroGlanceWidget().updateAll(appContext) }
        val error = first.exceptionOrNull()
        val succeeded = if (error == null) {
            true
        } else if (isTransactionTooLarge(error)) {
            runCatching { GlucoseHeroGlanceWidget(omitTrendBitmap = true).updateAll(appContext) }
                .isSuccess
        } else {
            false
        }
        if (succeeded && nextKey != null) {
            prefs.edit { putString(KEY_RENDER, nextKey) }
        }
        runCatching { scheduleBoundary(appContext) }
    }

    private suspend fun currentRenderKey(appContext: Context): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            GlucoseHeroWidgetEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository().settings.first()
        val reading = entryPoint.entryRepository().latestGlucoseReading()
        return WidgetFreshnessPolicy.renderKey(
            glucoseMgdl = reading?.glucoseMgdl,
            timestampMillis = reading?.timestamp,
            nowMillis = System.currentTimeMillis(),
            unitName = settings.unit.name,
        )
    }

    private suspend fun scheduleBoundary(appContext: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            GlucoseHeroWidgetEntryPoint::class.java,
        )
        val reading = entryPoint.entryRepository().latestGlucoseReading()
        val delay = WidgetFreshnessPolicy.millisUntilStaleCaption(
            timestampMillis = reading?.timestamp,
            nowMillis = System.currentTimeMillis(),
        )
        if (delay == null) {
            WidgetFreshnessRefreshWorker.cancel(appContext)
        } else {
            WidgetFreshnessRefreshWorker.schedule(appContext, delay)
        }
    }

    private companion object {
        const val PREFS = "glucosehero_widget_freshness"
        const val KEY_RENDER = "render_key"
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
