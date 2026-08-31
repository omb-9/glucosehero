package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucoseStats
import kotlinx.coroutines.flow.Flow

/** Single-column projection for distinct local calendar days with a logged entry. */
data class LoggedDayRow(
    val day: String,
)

@Dao
interface EntryDao {

    // ---------- Reactive reads ----------

    @Query("SELECT * FROM entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeEventsSince(since: Long): Flow<List<EntryEntity>>

    /**
     * Any event carrying a glucose reading, regardless of what else it also
     * logged (insulin/carbs/exercise can now ride on the same row). There is
     * no `type` column to filter on — `glucose_mgdl IS NOT NULL` alone is the
     * predicate, which is also why that column is indexed.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    fun observeGlucoseEventsSince(since: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryEntity?>

    // ---------- Writes ----------

    @Insert
    suspend fun insert(entity: EntryEntity): Long

    @Update
    suspend fun update(entity: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(entity: EntryEntity)

    // ---------- SQL-level aggregates (offloaded AI context + stats) ----------

    @Query(
        """
        SELECT AVG(glucose_mgdl) FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    suspend fun averageGlucoseSince(since: Long): Double?

    /**
     * Reactive 90-day aggregate for the estimated A1c card. The three values
     * travel together so the ViewModel never has to reconcile three separate
     * emissions into a single state.
     */
    @Query(
        """
        SELECT AVG(glucose_mgdl) AS avgMgdl,
               COUNT(*) AS readingCount,
               COUNT(DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime'))
                   AS loggedDays
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    fun observeGlucoseStatsSince(since: Long): Flow<GlucoseStats>

    /**
     * Distinct local calendar days (yyyy-MM-dd) that have at least one
     * streak-qualifying entry: glucose, insulin, or meal. Notes and exercise
     * alone do not count toward a logging streak.
     */
    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day
        FROM entries
        WHERE (
                   glucose_mgdl IS NOT NULL
                OR insulin_basal_units IS NOT NULL
                OR insulin_bolus_units IS NOT NULL
                OR carbs_grams IS NOT NULL
                OR protein_grams IS NOT NULL
                OR fat_grams IS NOT NULL
                OR (meal_description IS NOT NULL AND meal_description != '')
              )
              AND timestamp >= :since
        ORDER BY day DESC
        """
    )
    fun observeLoggedDays(since: Long): Flow<List<LoggedDayRow>>

    /**
     * One-shot snapshot of the same streak-qualifying distinct days, bound by
     * [since] so the save path never triggers a full-table scan.
     */
    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day
        FROM entries
        WHERE (
                   glucose_mgdl IS NOT NULL
                OR insulin_basal_units IS NOT NULL
                OR insulin_bolus_units IS NOT NULL
                OR carbs_grams IS NOT NULL
                OR protein_grams IS NOT NULL
                OR fat_grams IS NOT NULL
                OR (meal_description IS NOT NULL AND meal_description != '')
              )
              AND timestamp >= :since
        ORDER BY day DESC
        """
    )
    suspend fun loggedDaysSince(since: Long): List<LoggedDayRow>


    @Query(
        """
        SELECT CAST(SUM(CASE WHEN glucose_mgdl BETWEEN :low AND :high THEN 1 ELSE 0 END) AS REAL)
               / COUNT(*)
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    suspend fun timeInRangeSince(since: Long, low: Double, high: Double): Double?

    @Query(
        """
        SELECT AVG(glucose_mgdl) FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :startMillis AND timestamp < :endMillis
        """
    )
    suspend fun averageGlucoseBetween(startMillis: Long, endMillis: Long): Double?

    @Query(
        """
        SELECT CAST(SUM(CASE WHEN glucose_mgdl BETWEEN :low AND :high THEN 1 ELSE 0 END) AS REAL)
               / COUNT(*)
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :startMillis AND timestamp < :endMillis
        """
    )
    suspend fun timeInRangeBetween(
        startMillis: Long,
        endMillis: Long,
        low: Double,
        high: Double,
    ): Double?

    /**
     * Daily averages computed entirely inside SQLite: native GROUP BY day,
     * ORDER BY, LIMIT. This is the 14-day payload Hero sees — assembled in one
     * query instead of streaming raw rows through Kotlin.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               AVG(glucose_mgdl) AS avgMgdl,
               MIN(glucose_mgdl) AS minMgdl,
               MAX(glucose_mgdl) AS maxMgdl,
               COUNT(*) AS readings
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        GROUP BY day
        ORDER BY day DESC
        LIMIT :limit
        """
    )
    suspend fun dailySummaries(since: Long, limit: Int): List<DailyGlucoseSummary>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentEntries(limit: Int): List<EntryEntity>

    /** One-shot snapshot of everything since [since], oldest first (exports). */
    @Query("SELECT * FROM entries WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun entriesSince(since: Long): List<EntryEntity>

    /** The single newest entry that carries a glucose reading (home-screen widget). */
    @Query("SELECT * FROM entries WHERE glucose_mgdl IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestGlucoseEntry(): EntryEntity?
}
