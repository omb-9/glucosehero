package com.omb9.glucosehero.data.cgm.xdrip

import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.CgmPushSink
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseReadingBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XdripBroadcastHandlerTest {

    private val t0 = 1_700_000_000_000L
    private val xdrip = XdripSenderIdentity.Known("com.eveningoutpost.dexdrip")

    @Test
    fun `toggle off does not ingest or call sink`() = runTest {
        var ingestCalls = 0
        var sinkCalls = 0
        val handler = handler(
            isEnabled = { false },
            ingest = { ingestCalls++ },
        )
        handler.setSink(CgmPushSink { sinkCalls++ })
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        assertEquals(0, ingestCalls)
        assertEquals(0, sinkCalls)
    }

    @Test
    fun `untrusted sender is dropped`() = runTest {
        var ingestCalls = 0
        val handler = handler(ingest = { ingestCalls++ })
        handler.handle(
            bgPayload(),
            XdripSenderIdentity.Known("com.malware.spoof"),
            allowlistedInstalled = true,
        )
        handler.handle(
            bgPayload(),
            XdripSenderIdentity.KnownNotAllowlisted,
            allowlistedInstalled = true,
        )
        assertEquals(0, ingestCalls)
    }

    @Test
    fun `empty mapper result does not ingest`() = runTest {
        var ingestCalls = 0
        val handler = handler(ingest = { ingestCalls++ })
        handler.handle(
            XdripBroadcastPayload(XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE),
            xdrip,
            allowlistedInstalled = true,
        )
        assertEquals(0, ingestCalls)
    }

    @Test
    fun `sink bound receives samples and ingest is not called`() = runTest {
        var ingestCalls = 0
        var sinkSamples = 0
        val handler = handler(ingest = { ingestCalls++ })
        handler.setSink(CgmPushSink { samples -> sinkSamples += samples.size })
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        assertEquals(1, sinkSamples)
        assertEquals(0, ingestCalls)
    }

    @Test
    fun `sink unbound calls ingest`() = runTest {
        var ingestCalls = 0
        val handler = handler(ingest = { ingestCalls++ })
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        assertEquals(1, ingestCalls)
    }

    @Test
    fun `double delivery inserts once and fans out once`() = runTest {
        val dao = HandlerFakeDao()
        var fanOut = 0
        val service = CgmIngestService(dao, settingsDataStore = null) { fanOut++ }
        val handler = handler(ingest = { service.ingest(it) })
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        assertEquals(1, dao.inserted.size)
        assertEquals(1, fanOut)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, dao.inserted.single().source)
        assertEquals(121.0, dao.inserted.single().glucoseMgdl, 1e-9)
    }

    @Test
    fun `same reading after ttl is ingested again`() = runTest {
        var now = 1_000L
        var ingestCalls = 0
        val handler = handler(
            ingest = { ingestCalls++ },
            clock = { now },
        )
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        now += XdripBroadcastHandler.DELIVERY_DEDUP_TTL_MS + 1L
        handler.handle(bgPayload(), xdrip, allowlistedInstalled = true)
        assertEquals(2, ingestCalls)
    }

    @Test
    fun `hanging ingest still finishes the pending result`() = runTest {
        val handler = handler(
            ingest = { delay(60_000) },
            receiveTimeoutMs = 1_000L,
        )
        var finished = 0
        handler.enqueueReceive(
            payload = bgPayload(),
            identity = xdrip,
            allowlistedInstalled = true,
            finish = { finished++ },
        )
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()
        assertEquals(1, finished)
    }

    @Test
    fun `ingest throwing still finishes the pending result once`() = runTest {
        val handler = handler(
            ingest = { error("ingest failed") },
        )
        var finished = 0
        handler.enqueueReceive(
            payload = bgPayload(),
            identity = xdrip,
            allowlistedInstalled = true,
            finish = { finished++ },
        )
        testScheduler.advanceUntilIdle()
        assertEquals(1, finished)
    }

    private fun TestScope.handler(
        isEnabled: suspend () -> Boolean = { true },
        ingest: suspend (List<GlucoseSampleEntity>) -> Unit = {},
        clock: () -> Long = { System.currentTimeMillis() },
        receiveTimeoutMs: Long = XdripBroadcastHandler.RECEIVE_TIMEOUT_MS,
    ) = XdripBroadcastHandler(
        isEnabled = isEnabled,
        ingest = ingest,
        resolveSender = { _, _ -> xdrip },
        allowlistedInstalled = { true },
        scope = this,
        logWarning = {},
        clock = clock,
        receiveTimeoutMs = receiveTimeoutMs,
    )

    private fun bgPayload() = XdripBroadcastPayload(
        action = XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
        extras = mapOf(
            XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
            XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 121.0,
        ),
    )
}

private class HandlerFakeDao : GlucoseSampleDao {
    val inserted = mutableListOf<GlucoseSampleEntity>()
    private val existingKeys = mutableSetOf<Pair<GlucoseSampleSource, String>>()

    override suspend fun upsertAll(samples: List<GlucoseSampleEntity>): List<Long> {
        return samples.map { sample ->
            val key = sample.source to sample.externalId
            if (existingKeys.contains(key)) {
                -1L
            } else {
                existingKeys.add(key)
                inserted.add(sample)
                inserted.size.toLong()
            }
        }
    }

    override suspend fun deleteByHcRecordId(hcRecordId: String) = Unit
    override suspend fun deleteByExternalId(source: GlucoseSampleSource, externalId: String) = Unit
    override suspend fun deleteBySource(source: GlucoseSampleSource) = Unit
    override suspend fun samplesBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): List<GlucoseSampleEntity> =
        inserted.filter { it.timestamp in fromInclusive..toInclusive }

    override suspend fun latestSampleTimestamp(source: GlucoseSampleSource): Long? = null
    override suspend fun count(): Int = inserted.size
    override suspend fun countBySource(source: GlucoseSampleSource): Int = inserted.size
    override suspend fun countSince(source: GlucoseSampleSource, sinceMillis: Long): Int = 0
    override suspend fun clear() = Unit
    override suspend fun pageForExport(limit: Int, offset: Int): List<GlucoseSampleEntity> = emptyList()
    override suspend fun getAll(): List<GlucoseSampleEntity> = inserted
    override suspend fun insertAll(samples: List<GlucoseSampleEntity>): List<Long> = upsertAll(samples)
    override fun observeReadingsSince(since: Long): Flow<List<GlucosePointRow>> = flowOf(emptyList())
    override fun observeBucketedReadingsSince(
        since: Long,
        bucketMillis: Long,
    ): Flow<List<GlucosePointRow>> = flowOf(emptyList())
    override fun observeBucketedSamplesSince(
        since: Long,
        bucketMillis: Long,
    ): Flow<List<GlucosePointRow>> = flowOf(emptyList())
    override suspend fun bucketedReadingsSince(
        since: Long,
        bucketMillis: Long,
    ): List<GlucosePointRow> = emptyList()
    override suspend fun readingBoundsSince(since: Long) = GlucoseReadingBounds(
        minMgdl = null,
        maxMgdl = null,
        avgMgdl = null,
        count = 0,
    )
}
