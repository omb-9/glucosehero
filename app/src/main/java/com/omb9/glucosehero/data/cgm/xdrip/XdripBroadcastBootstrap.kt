package com.omb9.glucosehero.data.cgm.xdrip

import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.XdripBroadcastCgmSource
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * App-scoped observer that registers the xDrip push source when
 * [SettingsDataStore.xdripBroadcastEnabled] is on, including process start.
 *
 * Collects with [distinctUntilChanged] so a repeated identical flag does
 * not bind or unbind again. [start] is idempotent.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class XdripBroadcastBootstrap(
    private val enabled: Flow<Boolean>,
    private val onEnabled: () -> Unit,
    private val onDisabled: () -> Unit,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        settingsDataStore: SettingsDataStore,
        ingestService: CgmIngestService,
        source: XdripBroadcastCgmSource,
    ) : this(
        enabled = settingsDataStore.xdripBroadcastEnabled,
        onEnabled = { ingestService.bindPush(source) },
        onDisabled = { ingestService.unbindPush(source) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            enabled.distinctUntilChanged().collect { isEnabled ->
                if (isEnabled) {
                    onEnabled()
                } else {
                    onDisabled()
                }
            }
        }
    }

    internal fun hasStarted(): Boolean = started.get()
}
