package com.omb9.glucosehero.wear

import android.content.Context
import androidx.room.InvalidationTracker
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts Wear sync when the process comes up: periodic WorkManager backup plus
 * a Room invalidation observer so new CGM samples and manual entries push to
 * the watch without touching existing save paths.
 */
@Singleton
class WearGlucosePushController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GlucoseHeroDatabase,
    private val settingsStore: WearSyncSettingsStore,
    private val dataLayerClient: WearDataLayerClient,
) {
    private var debounceJob: Job? = null
    private var observerRegistered = false

    fun start(scope: CoroutineScope) {
        registerInvalidationObserver(scope)
        scope.launch {
            settingsStore.syncEnabled.collectLatest { enabled ->
                if (enabled) {
                    WearSyncWorker.schedulePeriodic(context)
                    WearSyncWorker.enqueue(context)
                } else {
                    WearSyncWorker.cancelPeriodic(context)
                    runCatching { dataLayerClient.clearGlucose() }
                }
            }
        }
    }

    private fun registerInvalidationObserver(scope: CoroutineScope) {
        if (observerRegistered) return
        observerRegistered = true
        val observer = object : InvalidationTracker.Observer("entries", "glucose_samples") {
            override fun onInvalidated(tables: Set<String>) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    if (settingsStore.syncEnabled.first()) {
                        WearSyncWorker.enqueue(context)
                    }
                }
            }
        }
        database.invalidationTracker.addObserver(observer)
    }

    private companion object {
        const val DEBOUNCE_MS = 750L
    }
}
