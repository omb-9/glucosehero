package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.FoodSource
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keyset pagination must emit the same rows, in the same order, as the old
 * LIMIT/OFFSET loops. OFFSET scans and discards skipped rows; keyset does not.
 * The backup JSON format is unchanged.
 */
class BackupKeysetPaginationTest {

    @Test
    fun idKeysetMatchesOffsetAndIsCompletePast500Rows() {
        val items = (1L..650L).map { id ->
            BackupEntry(
                id = id,
                timestamp = 1_000L + id,
                glucoseMgdl = 100.0,
                uuid = "e-$id",
            )
        }
        val offset = pageByOffset(items, pageSize = 500)
        val keyset = pageByIdKeyset(items, pageSize = 500) { it.id }
        assertEquals(650, offset.size)
        assertEquals(items.map { it.id }, offset.map { it.id })
        assertEquals(offset.map { it.id }, keyset.map { it.id })
    }

    @Test
    fun timestampKeysetIsStableAcrossTies() {
        val items = (1L..520L).map { id ->
            // 20 rows share each timestamp so OFFSET order would be ambiguous
            // without the id tiebreaker.
            BackupEntry(
                id = id,
                timestamp = 10_000L + (id - 1) / 20,
                glucoseMgdl = 90.0 + (id % 7),
                uuid = "tie-$id",
            )
        }
        val ordered = items.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val offset = pageByTimestampOffset(ordered, pageSize = 500)
        val keyset = pageByTimestampKeyset(ordered, pageSize = 500)
        assertEquals(520, keyset.size)
        assertEquals(ordered.map { it.id }, offset.map { it.id })
        assertEquals(offset.map { it.id }, keyset.map { it.id })
        val tied = keyset.filter { it.timestamp == ordered[0].timestamp }
        assertTrue(tied.size > 1)
        assertEquals(tied.map { it.id }, tied.map { it.id }.sorted())
    }

    @Test
    fun exportBytesAreIdenticalForOffsetAndKeysetPagers() = runBlocking {
        val entries = (1L..650L).map { id ->
            BackupEntry(
                id = id,
                timestamp = if (id <= 40L) 5_000L else 5_000L + id,
                glucoseMgdl = 110.0,
                source = EntrySource.MANUAL,
                uuid = "byte-$id",
            )
        }
        val foods = (1L..10L).map { id ->
            BackupFood(
                id = id,
                uuid = "food-$id",
                name = "Food $id",
                carbsGrams = 10.0,
                source = FoodSource.MANUAL,
                createdAt = 1L,
            )
        }

        val offsetBytes = export(entries, foods, pageSize = 500) { list, lastId, limit ->
            offsetAfterLastId(list, lastId, limit) { it.id }
        }
        val keysetBytes = export(entries, foods, pageSize = 500) { list, lastId, limit ->
            list.filter { it.id > lastId }.take(limit)
        }
        val singlePageBytes = export(entries, foods, pageSize = 10_000) { list, lastId, limit ->
            list.filter { it.id > lastId }.take(limit)
        }

        assertArrayEquals(offsetBytes, keysetBytes)
        assertArrayEquals(offsetBytes, singlePageBytes)
        assertTrue(offsetBytes.isNotEmpty())
        val json = offsetBytes.toString(Charsets.UTF_8)
        assertTrue(json.contains("\"id\":650"))
        assertTrue(json.contains("\"id\":1"))
    }

    private suspend fun export(
        entries: List<BackupEntry>,
        foods: List<BackupFood>,
        pageSize: Int,
        page: (List<BackupEntry>, Long, Int) -> List<BackupEntry>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        streamBackupEnvelope(
            output = output,
            profile = BackupProfile(),
            settings = BackupSettings(),
            counts = BackupCounts(entries = entries.size, foods = foods.size),
            appVersion = "1.0.0",
            exportedAt = 1_700_000_000_000L,
            earliestEntry = entries.minOf { it.timestamp },
            latestEntry = entries.maxOf { it.timestamp },
            pageSize = pageSize,
            foods = { lastId, limit -> foods.filter { it.id > lastId }.take(limit) },
            entries = { lastId, limit -> page(entries, lastId, limit) },
            supplies = { _, _ -> emptyList() },
            glucoseSamples = { _, _ -> emptyList() },
            chat = { _, _ -> emptyList() },
            pendingAiQueries = { _, _ -> emptyList() },
            insights = { _, _ -> emptyList() },
        )
        return output.toByteArray()
    }

    private fun <T> pageByOffset(sorted: List<T>, pageSize: Int): List<T> {
        val out = ArrayList<T>()
        var offset = 0
        while (true) {
            if (offset >= sorted.size) break
            val page = sorted.subList(offset, minOf(offset + pageSize, sorted.size))
            if (page.isEmpty()) break
            out.addAll(page)
            offset += page.size
            if (page.size < pageSize) break
        }
        return out
    }

    private fun <T> pageByIdKeyset(sorted: List<T>, pageSize: Int, idOf: (T) -> Long): List<T> {
        val out = ArrayList<T>()
        var lastId = 0L
        while (true) {
            val page = sorted.filter { idOf(it) > lastId }.take(pageSize)
            if (page.isEmpty()) break
            out.addAll(page)
            lastId = idOf(page.last())
            if (page.size < pageSize) break
        }
        return out
    }

    private fun pageByTimestampOffset(sorted: List<BackupEntry>, pageSize: Int): List<BackupEntry> =
        pageByOffset(sorted, pageSize)

    private fun pageByTimestampKeyset(sorted: List<BackupEntry>, pageSize: Int): List<BackupEntry> {
        val out = ArrayList<BackupEntry>()
        var lastTimestamp = Long.MIN_VALUE
        var lastId = 0L
        while (true) {
            val page = sorted.filter { entry ->
                entry.timestamp > lastTimestamp ||
                    (entry.timestamp == lastTimestamp && entry.id > lastId)
            }.take(pageSize)
            if (page.isEmpty()) break
            out.addAll(page)
            lastTimestamp = page.last().timestamp
            lastId = page.last().id
            if (page.size < pageSize) break
        }
        return out
    }

    private fun <T> offsetAfterLastId(
        sorted: List<T>,
        lastId: Long,
        limit: Int,
        idOf: (T) -> Long,
    ): List<T> {
        val offset = if (lastId == 0L) {
            0
        } else {
            val index = sorted.indexOfFirst { idOf(it) == lastId }
            if (index < 0) 0 else index + 1
        }
        if (offset >= sorted.size) return emptyList()
        return sorted.subList(offset, minOf(offset + limit, sorted.size))
    }
}
