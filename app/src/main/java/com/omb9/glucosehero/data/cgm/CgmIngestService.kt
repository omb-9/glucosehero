package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.crisis.HypoSosManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.forecast.GlucoseForecastRepository
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import com.omb9.glucosehero.wear.WearGlucosePushController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Outcome of a [CgmIngestService.ingest] or [CgmIngestService.pull] call.
 *
 * Counts only; never glucose values. [disabled] is true when [pull] short-
 * circuits because the source flag is off. [error] is a short class name
 * when fetch failed, never a URL or token.
 *
 * FEATURE: cgm-direct-ingest
 */
data class CgmIngestResult(
    val inserted: Int = 0,
    val replaced: Int = 0,
    val skipped: Int = 0,
    val droppedInvalid: Int = 0,
    val disabled: Boolean = false,
    val error: String? = null,
)

/**
 * Single write funnel for CGM samples.
 *
 * Why this exists: every later source (xDrip broadcast, Nightscout poller,
 * LibreLinkUp) must share conversion, identity checks, cross-source
 * collapse, Room upsert, and post-write fan-out. Health Connect keeps its
 * existing import path in Phase 1; new sources must not insert through
 * [GlucoseSampleDao] directly.
 *
 * Assumptions:
 * - Sources are opt-in and default off. [pull] checks the flag; [ingest]
 *   writes whatever the caller already decided to deliver (so tests and
 *   manual import can skip the flag).
 * - Canonical storage is mg/dL. Pass [unit] when [GlucoseSampleEntity.glucoseMgdl]
 *   is still in mmol/L.
 * - Fan-out matches [com.omb9.glucosehero.work.HealthConnectSyncWorker]: each
 *   consumer is wrapped in `runCatching` so one failure cannot fail the write.
 *   Wear also observes Room invalidation; the explicit notify is REPLACE-safe.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class CgmIngestService(
    private val glucoseSampleDao: GlucoseSampleDao,
    private val settingsDataStore: SettingsDataStore?,
    private val onNewRows: suspend () -> Unit,
) {
    @Inject
    constructor(
        glucoseSampleDao: GlucoseSampleDao,
        settingsDataStore: SettingsDataStore,
        forecastRepository: GlucoseForecastRepository,
        hypoSosManager: HypoSosManager,
        widgetRefresher: WidgetRefresher,
        wearGlucosePushController: WearGlucosePushController,
    ) : this(
        glucoseSampleDao = glucoseSampleDao,
        settingsDataStore = settingsDataStore,
        onNewRows = {
            // Same consumers as HealthConnectSyncWorker, plus widget/Wear
            // which that worker does not call (LogViewModel / Room observer
            // cover HC today). One failure must not fail ingest.
            runCatching { forecastRepository.refresh() }
            runCatching { hypoSosManager.evaluateLatest() }
            runCatching { widgetRefresher.refresh() }
            runCatching { wearGlucosePushController.notifyGlucoseChanged() }
        },
    )

    private val mutex = Mutex()
    private val fanOutScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Registers [source]'s push listener so broadcasts land in [ingest].
     * Phase 2 (xDrip) should still gate the receiver on the enabled flag.
     *
     * Push ingest does not await [onNewRows]: the xDrip BroadcastReceiver
     * window must end after the insert. Wear still gets a prompt update via
     * Room invalidation plus the scheduled [onNewRows] Wear notify.
     */
    fun bindPush(source: CgmSource) {
        source.registerPush { samples -> ingest(samples, awaitFanOut = false) }
    }

    fun unbindPush(source: CgmSource) {
        source.unregisterPush()
    }

    suspend fun isSourceEnabled(source: GlucoseSampleSource): Boolean {
        val settings = settingsDataStore ?: return false
        return settings.cgmIngestSettingsSnapshot().isEnabled(source)
    }

    /**
     * Pulls [source.fetch] since the newest local timestamp for that source
     * and writes through [ingest]. No-op when the opt-in flag is off.
     */
    suspend fun pull(source: CgmSource): CgmIngestResult {
        if (!isSourceEnabled(source.key)) {
            return CgmIngestResult(disabled = true)
        }
        return try {
            val since = glucoseSampleDao.latestSampleTimestamp(source.key) ?: 0L
            val fetched = source.fetch(since)
            val result = ingest(fetched)
            recordSuccess(source.key)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // Short public code when the source implements CgmPublicError.
            // Never persist Exception.message: Nightscout URLs can embed a token.
            val label = (e as? CgmPublicError)?.publicErrorCode
                ?: (e::class.simpleName ?: "Exception")
            settingsDataStore?.setCgmLastIngestError(source.key, label)
            CgmIngestResult(error = label)
        }
    }

    /**
     * Canonicalize, collapse against stored rows, upsert survivors, then
     * fan-out when at least one new row was inserted.
     *
     * @param awaitFanOut when false, [onNewRows] is scheduled on an
     * internal scope and this call returns after the insert. Default true
     * keeps Nightscout / test / Health Connect callers unchanged. xDrip
     * broadcast ingest uses false so widget / Wear / forecast / hypo work
     * is not charged to [android.content.BroadcastReceiver.goAsync].
     */
    suspend fun ingest(
        samples: List<GlucoseSampleEntity>,
        unit: GlucoseUnit = GlucoseUnit.MGDL,
        awaitFanOut: Boolean = true,
    ): CgmIngestResult {
        if (samples.isEmpty()) return CgmIngestResult()
        val now = System.currentTimeMillis()
        val prepared = ArrayList<GlucoseSampleEntity>(samples.size)
        var droppedInvalid = 0
        for (sample in samples) {
            val canonical = CgmGlucose.canonicalize(sample, unit = unit, importedAt = now)
            if (canonical == null) {
                droppedInvalid++
            } else {
                prepared += canonical
            }
        }
        val distinctIncoming = prepared.distinctBy { it.source to it.externalId }
        val skippedDuplicatesInBatch = prepared.size - distinctIncoming.size

        val outcome = mutex.withLock {
            val window = settingsDataStore?.cgmDedupWindowMillisSnapshot()
                ?: CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS
            val existing = if (distinctIncoming.isEmpty()) {
                emptyList()
            } else {
                val minTs = distinctIncoming.minOf { it.timestamp }
                val maxTs = distinctIncoming.maxOf { it.timestamp }
                glucoseSampleDao.samplesBetween(minTs - window, maxTs + window)
            }
            val plan = CgmDedup.plan(
                incoming = distinctIncoming.map {
                    CgmDedupSample(it.source, it.externalId, it.timestamp)
                },
                existing = existing.map {
                    CgmDedupSample(it.source, it.externalId, it.timestamp)
                },
                windowMillis = window,
            )
            val byKey = distinctIncoming.associateBy { it.source to it.externalId }
            for (row in plan.delete) {
                glucoseSampleDao.deleteByExternalId(row.source, row.externalId)
            }
            val toInsert = plan.accept.mapNotNull { byKey[it.source to it.externalId] }
            val upserted = if (toInsert.isEmpty()) {
                emptyList()
            } else {
                glucoseSampleDao.upsertAll(toInsert)
            }
            val inserted = upserted.count { it != -1L }
            val skippedByDedup = distinctIncoming.size - plan.accept.size
            Triple(inserted, plan.delete.size, skippedByDedup + skippedDuplicatesInBatch)
        }

        val (inserted, replaced, skipped) = outcome
        if (inserted > 0) {
            if (awaitFanOut) {
                runCatching { onNewRows() }
            } else {
                fanOutScope.launch { runCatching { onNewRows() } }
            }
        }
        val sources = distinctIncoming.map { it.source }.toSet()
        for (source in sources) {
            recordSuccess(source)
        }
        return CgmIngestResult(
            inserted = inserted,
            replaced = replaced,
            skipped = skipped,
            droppedInvalid = droppedInvalid,
        )
    }

    private suspend fun recordSuccess(source: GlucoseSampleSource) {
        val store = settingsDataStore ?: return
        store.setCgmLastIngestSuccess(source, System.currentTimeMillis())
        store.setCgmLastIngestError(source, null)
    }
}
