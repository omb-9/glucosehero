package com.omb9.glucosehero.domain.repository

import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseStats
import com.omb9.glucosehero.domain.model.LogEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface EntryRepository {
    /** Events from [sinceMillis], newest first (log screen). */
    fun observeEntries(sinceMillis: Long): Flow<List<LogEvent>>

    /** Events carrying a glucose reading from [sinceMillis], oldest first (charting). */
    fun observeGlucose(sinceMillis: Long): Flow<List<LogEvent>>

    /** Chart-only projection: timestamp + glucose value from [sinceMillis], oldest first. */
    fun observeGlucosePoints(sinceMillis: Long): Flow<List<GlucosePointRow>>

    /** Distinct streak-qualifying local days across all history. */
    suspend fun distinctLoggedDays(): Set<LocalDate>

    /** Reactive glucose aggregate for the rolling 90-day eA1c window. */
    fun observeGlucoseStats(sinceMillis: Long): Flow<GlucoseStats>

    /** Continuous daily-logging streak (glucose, insulin, or meal days only). */
    fun observeCurrentStreak(): Flow<Int>

    /** One-shot current streak, used by the save flow to detect an extension. */
    suspend fun currentStreak(): Int

    fun observeEntry(id: Long): Flow<LogEvent?>

    suspend fun add(event: LogEvent): Long
    suspend fun update(event: LogEvent)
    suspend fun delete(id: Long)

    // --- SQL-level aggregates (AI context assembly + stats) ---
    suspend fun averageGlucoseSince(sinceMillis: Long): Double?
    suspend fun timeInRangeSince(sinceMillis: Long, lowMgdl: Double, highMgdl: Double): Double?

    // --- Previous-window aggregates (trend comparisons) ---
    suspend fun averageGlucoseBetween(startMillis: Long, endMillis: Long): Double?
    suspend fun timeInRangeBetween(
        startMillis: Long,
        endMillis: Long,
        lowMgdl: Double,
        highMgdl: Double,
    ): Double?

    suspend fun dailySummaries(sinceMillis: Long, limit: Int): List<DailyGlucoseSummary>
    suspend fun recentEntries(limit: Int): List<LogEvent>

    /** One-shot snapshot of all entries since [sinceMillis], oldest first. */
    suspend fun entriesSince(sinceMillis: Long): List<LogEvent>
}
