package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseReadingBounds
import com.omb9.glucosehero.domain.model.GlucoseUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmIngestServiceTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `higher priority ingest replaces Health Connect in window and fans out`() = runBlocking {
        val dao = FakeIngestDao()
        dao.upsertAll(listOf(entity(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0, 120.0)))
        var fanOut = 0
        val service = CgmIngestService(dao, settingsDataStore = null) { fanOut++ }

        val result = service.ingest(
            listOf(entity(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0 + 1_000L, 121.0)),
        )

        assertEquals(1, result.inserted)
        assertEquals(1, result.replaced)
        assertEquals(1, fanOut)
        assertEquals(
            listOf(GlucoseSampleSource.XDRIP_BROADCAST),
            dao.inserted.map { it.source },
        )
    }

    @Test
    fun `lower priority ingest is skipped and does not fan out`() = runBlocking {
        val dao = FakeIngestDao()
        dao.upsertAll(listOf(entity(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0, 120.0)))
        var fanOut = 0
        val service = CgmIngestService(dao, settingsDataStore = null) { fanOut++ }

        val result = service.ingest(
            listOf(entity(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0, 119.0)),
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals(0, fanOut)
        assertEquals(1, dao.inserted.size)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, dao.inserted.single().source)
    }

    @Test
    fun `same source external id is idempotent`() = runBlocking {
        val dao = FakeIngestDao()
        val first = entity(GlucoseSampleSource.NIGHTSCOUT, "ns-1", t0, 130.0)
        var fanOut = 0
        val service = CgmIngestService(dao, settingsDataStore = null) { fanOut++ }

        assertEquals(1, service.ingest(listOf(first)).inserted)
        assertEquals(1, fanOut)
        val second = service.ingest(listOf(first.copy(glucoseMgdl = 131.0)))
        assertEquals(0, second.inserted)
        assertEquals(1, fanOut)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `out of range glucose is dropped`() = runBlocking {
        val dao = FakeIngestDao()
        val service = CgmIngestService(dao, settingsDataStore = null) {}

        val result = service.ingest(
            listOf(entity(GlucoseSampleSource.NIGHTSCOUT, "ns", t0, 5.0)),
        )

        assertEquals(1, result.droppedInvalid)
        assertEquals(0, result.inserted)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `mmol unit is converted before insert`() = runBlocking {
        val dao = FakeIngestDao()
        val service = CgmIngestService(dao, settingsDataStore = null) {}

        val result = service.ingest(
            samples = listOf(entity(GlucoseSampleSource.LIBRE_LINK_UP, "ll", t0, 6.4)),
            unit = GlucoseUnit.MMOL,
        )

        assertEquals(1, result.inserted)
        assertEquals(
            CgmGlucose.toCanonicalMgdl(6.4, GlucoseUnit.MMOL)!!,
            dao.inserted.single().glucoseMgdl,
            1e-9,
        )
    }

    @Test
    fun `countSince is scoped by source and timestamp`() = runBlocking {
        val dao = FakeIngestDao()
        dao.upsertAll(
            listOf(
                entity(GlucoseSampleSource.XDRIP_BROADCAST, "xd-new", t0, 120.0),
                entity(GlucoseSampleSource.XDRIP_BROADCAST, "xd-old", t0 - 25L * 60L * 60L * 1000L, 118.0),
                entity(GlucoseSampleSource.NIGHTSCOUT, "ns-new", t0, 130.0),
            ),
        )
        val since = t0 - 24L * 60L * 60L * 1000L
        assertEquals(1, dao.countSince(GlucoseSampleSource.XDRIP_BROADCAST, since))
        assertEquals(1, dao.countSince(GlucoseSampleSource.NIGHTSCOUT, since))
        assertEquals(0, dao.countSince(GlucoseSampleSource.HEALTH_CONNECT, since))
        assertEquals(2, dao.countBySource(GlucoseSampleSource.XDRIP_BROADCAST))
    }

    @Test
    fun `isSourceEnabled is false without settings`() = runBlocking {
        val dao = FakeIngestDao()
        val service = CgmIngestService(dao, settingsDataStore = null) {}
        assertTrue(!service.isSourceEnabled(GlucoseSampleSource.NIGHTSCOUT))
        assertTrue(!service.isSourceEnabled(GlucoseSampleSource.XDRIP_BROADCAST))
        assertTrue(!service.isSourceEnabled(GlucoseSampleSource.LIBRE_LINK_UP))
    }

    private fun entity(
        source: GlucoseSampleSource,
        externalId: String,
        timestamp: Long,
        glucoseMgdl: Double,
    ) = GlucoseSampleEntity(
        timestamp = timestamp,
        glucoseMgdl = glucoseMgdl,
        source = source,
        externalId = externalId,
        recordingMethod = 0,
        importedAt = t0,
    )
}

private class FakeIngestDao : GlucoseSampleDao {
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

    override suspend fun deleteByHcRecordId(hcRecordId: String) {
        inserted.removeAll {
            it.source == GlucoseSampleSource.HEALTH_CONNECT && it.hcRecordId == hcRecordId
        }
        rebuildKeys()
    }

    override suspend fun deleteByExternalId(source: GlucoseSampleSource, externalId: String) {
        inserted.removeAll { it.source == source && it.externalId == externalId }
        existingKeys.remove(source to externalId)
    }

    override suspend fun deleteBySource(source: GlucoseSampleSource) {
        inserted.removeAll { it.source == source }
        rebuildKeys()
    }

    override suspend fun samplesBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): List<GlucoseSampleEntity> =
        inserted.filter { it.timestamp in fromInclusive..toInclusive }

    override suspend fun latestSampleTimestamp(source: GlucoseSampleSource): Long? =
        inserted.filter { it.source == source }.maxOfOrNull { it.timestamp }

    override suspend fun count(): Int = inserted.size

    override suspend fun countBySource(source: GlucoseSampleSource): Int =
        inserted.count { it.source == source }

    override suspend fun countSince(source: GlucoseSampleSource, sinceMillis: Long): Int =
        inserted.count { it.source == source && it.timestamp >= sinceMillis }

    override suspend fun clear() {
        inserted.clear()
        existingKeys.clear()
    }

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

    private fun rebuildKeys() {
        existingKeys.clear()
        existingKeys.addAll(inserted.map { it.source to it.externalId })
    }
}
