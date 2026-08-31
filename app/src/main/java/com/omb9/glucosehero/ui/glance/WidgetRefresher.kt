package com.omb9.glucosehero.ui.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decouples widget refreshes from ViewModels so screens never need to hold a
 * raw [Context]. All failures are swallowed here: a widget refresh must never
 * take down (or block) the save flow that triggered it.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refresh() {
        runCatching { GlucoseHeroGlanceWidget().updateAll(context) }
    }
}
