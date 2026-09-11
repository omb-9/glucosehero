package com.omb9.glucosehero.data.cgm.xdrip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.CgmPushSink
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Shared onReceive path for the manifest receiver and the runtime receiver.
 *
 * Gates on [SettingsDataStore.xdripBroadcastEnabledSnapshot], validates the
 * sending package, maps extras, then delivers through the [CgmPushSink]
 * installed by [com.omb9.glucosehero.data.cgm.CgmIngestService.bindPush].
 * If the process was just started by a stopped-package explicit broadcast,
 * bindPush may not have run yet; ingest is then called directly so the first
 * reading is not dropped.
 *
 * Double delivery: when the toggle is on, both the manifest component and the
 * runtime receiver are registered for the same actions. An explicit
 * `setPackage` send (xDrip+ Identify receiver filled, or AAPS
 * `queryBroadcastReceivers`) is delivered to both. [CgmIngestService.ingest]
 * already skips a second insert via `(source, external_id)`, so forecast /
 * hypo / widget / Wear do not run twice. The remaining cost is a redundant
 * `samplesBetween()` plus `goAsync()`. This handler de-dups on
 * `(action, timestampMillis)` for one CGM interval so the second path
 * returns before ingest. Both receivers stay registered: the manifest
 * component is the only path that can wake a stopped process, and the
 * runtime receiver is the only path for implicit sends (Identify receiver
 * left blank). Filtering implicit vs explicit on the receiving side is
 * less reliable than this TTL.
 *
 * [goAsync] is finished in a `finally` guarded by [AtomicBoolean] so a
 * timeout, an ingest failure, or a second finish attempt cannot leak the
 * pending result or call [BroadcastReceiver.PendingResult.finish] twice.
 * [handle] is bounded by [RECEIVE_TIMEOUT_MS], well under the ~10 s
 * foreground BroadcastReceiver window. Fan-out (widget / Wear / forecast /
 * hypo) is scheduled by ingest with `awaitFanOut = false` so it is not on
 * this budget. Wear still observes Room invalidation, and the explicit
 * Wear notify runs on that scheduled fan-out immediately after the insert.
 *
 * Never logs glucose values, reading timestamps, or sender-identifying data.
 * Does not assume the sender is trusted.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class XdripBroadcastHandler(
    private val isEnabled: suspend () -> Boolean,
    private val ingest: suspend (List<GlucoseSampleEntity>) -> Unit,
    private val resolveSender: (BroadcastReceiver, Context) -> XdripSenderIdentity,
    private val allowlistedInstalled: (Context) -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val receiveTimeoutMs: Long = RECEIVE_TIMEOUT_MS,
    private val logWarning: (String) -> Unit = { message -> Log.w(TAG, message) },
) {
    @Inject
    constructor(
        settingsDataStore: SettingsDataStore,
        ingestService: CgmIngestService,
        installedAppsCache: XdripInstalledAppsCache,
    ) : this(
        isEnabled = { settingsDataStore.xdripBroadcastEnabledSnapshot() },
        ingest = { samples -> ingestService.ingest(samples, awaitFanOut = false) },
        resolveSender = { receiver, context -> XdripBroadcastSender.identity(receiver, context) },
        allowlistedInstalled = { installedAppsCache.get() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val sinkRef = AtomicReference<CgmPushSink?>(null)
    private val recentLock = Any()
    private val recent = ArrayDeque<SeenDelivery>()

    fun setSink(sink: CgmPushSink?) {
        sinkRef.set(sink)
    }

    fun onReceive(receiver: BroadcastReceiver, context: Context, intent: Intent) {
        if (!XdripBroadcastIntents.isCgmAction(intent.action)) return
        val pending = receiver.goAsync()
        val identity = testIdentityOverride ?: resolveSender(receiver, context)
        val installed = testAllowlistedInstalledOverride ?: allowlistedInstalled(context)
        enqueueReceive(
            payload = intent.toXdripBroadcastPayload(),
            identity = identity,
            allowlistedInstalled = installed,
            finish = { pending.finish() },
        )
    }

    /**
     * Launches [handle] with a timeout and finishes [finish] exactly once.
     */
    internal fun enqueueReceive(
        payload: XdripBroadcastPayload,
        identity: XdripSenderIdentity,
        allowlistedInstalled: Boolean,
        finish: () -> Unit,
    ) {
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                runCatching { finish() }
            }
        }
        scope.launch {
            try {
                withTimeout(receiveTimeoutMs) {
                    handle(payload, identity, allowlistedInstalled)
                }
            } catch (_: TimeoutCancellationException) {
                logWarning("CGM broadcast ingest timed out")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                logWarning("CGM broadcast ingest failed")
            } finally {
                finishOnce()
            }
        }
    }

    internal suspend fun handle(
        payload: XdripBroadcastPayload,
        identity: XdripSenderIdentity,
        allowlistedInstalled: Boolean,
    ) {
        if (!isEnabled()) return
        if (!XdripBroadcastAllowlist.acceptsSender(identity, allowlistedInstalled)) {
            logWarning("dropped CGM broadcast from untrusted sender")
            return
        }
        val samples = XdripBroadcastMapper.map(
            payload = payload,
            sourcePackage = XdripBroadcastSender.reportedPackage(identity),
        )
        if (samples.isEmpty()) return
        val action = payload.action.orEmpty()
        val timestamps = samples.map { it.timestamp }
        if (!claimDelivery(action, timestamps, clock())) return
        var committed = false
        try {
            val sink = sinkRef.get()
            if (sink != null) {
                sink.accept(samples)
            } else {
                ingest(samples)
            }
            committed = true
        } finally {
            if (!committed) {
                releaseDelivery(action, timestamps)
            }
        }
    }

    private fun claimDelivery(action: String, timestamps: List<Long>, now: Long): Boolean {
        synchronized(recentLock) {
            pruneLocked(now)
            if (timestamps.isEmpty()) return false
            val allSeen = timestamps.all { ts ->
                recent.any { it.action == action && it.timestampMillis == ts }
            }
            if (allSeen) return false
            for (ts in timestamps) {
                if (recent.none { it.action == action && it.timestampMillis == ts }) {
                    recent.addLast(SeenDelivery(action, ts, now))
                }
            }
            return true
        }
    }

    private fun releaseDelivery(action: String, timestamps: List<Long>) {
        synchronized(recentLock) {
            recent.removeAll { seen ->
                seen.action == action && seen.timestampMillis in timestamps
            }
        }
    }

    private fun pruneLocked(now: Long) {
        while (recent.isNotEmpty() && now - recent.first().atMillis > DELIVERY_DEDUP_TTL_MS) {
            recent.removeFirst()
        }
    }

    private data class SeenDelivery(
        val action: String,
        val timestampMillis: Long,
        val atMillis: Long,
    )

    companion object {
        private const val TAG = "XdripBroadcast"

        /**
         * Well under the ~10 s foreground BroadcastReceiver window so a
         * hung Room/Keystore path finishes the pending result instead of
         * ANRing.
         */
        const val RECEIVE_TIMEOUT_MS: Long = 8_000L

        /** Roughly one Dexcom/xDrip CGM interval. */
        const val DELIVERY_DEDUP_TTL_MS: Long = 5L * 60L * 1000L

        /**
         * Instrumentation-only overrides. Null in production. Never log
         * these values.
         */
        @Volatile
        internal var testIdentityOverride: XdripSenderIdentity? = null

        @Volatile
        internal var testAllowlistedInstalledOverride: Boolean? = null
    }
}
