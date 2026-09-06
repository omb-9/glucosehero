package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.local.entity.toEntity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucoseStats
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.util.StreakCalculator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepositoryImpl @Inject constructor(
    private val entryDao: EntryDao,
) : EntryRepository {

    override fun observeEntries(sinceMillis: Long): Flow<List<LogEvent>> =
        entryDao.observeEventsSince(sinceMillis).map { list -> list.map { it.toLogEvent() } }

    override fun observeGlucose(sinceMillis: Long): Flow<List<LogEvent>> =
        entryDao.observeGlucoseEventsSince(sinceMillis).map { list -> list.map { it.toLogEvent() } }

    override fun observeGlucoseStats(sinceMillis: Long): Flow<GlucoseStats> =
        entryDao.observeGlucoseStatsSince(sinceMillis)

    override fun observeCurrentStreak(): Flow<Int> =
        entryDao.observeLoggedDays(STREAK_SINCE_MILLIS).map { rows ->
            StreakCalculator.currentStreak(rows.map { it.day })
        }

    override suspend fun distinctLoggedDays(): Set<LocalDate> =
        entryDao.loggedDaysSince(0L)
            .mapNotNull { row -> runCatching { LocalDate.parse(row.day) }.getOrNull() }
            .toSet()

    override suspend fun currentStreak(): Int =
        entryDao.loggedDaysSince(STREAK_SINCE_MILLIS).let { rows ->
            StreakCalculator.currentStreak(rows.map { it.day })
        }

    override fun observeEntry(id: Long): Flow<LogEvent?> =
        entryDao.observeById(id).map { it?.toLogEvent() }

    override suspend fun add(event: LogEvent): Long = entryDao.insert(event.toEntity())

    override suspend fun update(event: LogEvent) = entryDao.update(event.toEntity())

    override suspend fun delete(id: Long) = entryDao.deleteById(id)

    override suspend fun timeInRangeSince(
        sinceMillis: Long,
        lowMgdl: Double,
        highMgdl: Double,
    ): Double? = entryDao.timeInRangeSince(sinceMillis, lowMgdl, highMgdl)

    override suspend fun dailySummaries(sinceMillis: Long, limit: Int): List<DailyGlucoseSummary> =
        entryDao.dailySummaries(sinceMillis, limit)

    override suspend fun recentEntries(limit: Int): List<LogEvent> =
        entryDao.recentEntries(limit).map { it.toLogEvent() }

    override suspend fun entriesSince(sinceMillis: Long): List<LogEvent> =
        entryDao.entriesSince(sinceMillis).map { it.toLogEvent() }

    private companion object {
        const val STREAK_SINCE_MILLIS = 0L
    }
}

private fun EntryEntity.toLogEvent(): LogEvent = toDomain().copy(source = source)
