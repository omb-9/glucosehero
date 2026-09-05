package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseStats
import kotlinx.coroutines.flow.Flow

/** Single-column projection for distinct local calendar days with a logged entry. */
data class LoggedDayRow(
    val day: String,
)

/** Hour-of-day average used to spot recurring time-based lows/highs. */
data class HourlyGlucoseAverageRow(
    val hour: Int,
    val avgMgdl: Double,
    val readings: Int,
)

/** Hour-of-day avg(x) and avg(x*x) so variance can be derived in Kotlin. */
data class HourlyGlucoseVarianceRow(
    val hour: Int,
    val avgMgdl: Double,
    val avgSqMgdl: Double,
    val readings: Int,
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

    /**
     * Chart-only projection: timestamps and glucose values without dragging
     * every nullable column into memory for the trend path.
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    fun observeGlucosePoints(since: Long): Flow<List<GlucosePointRow>>

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
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    fun observeGlucoseStatsSince(since: Long): Flow<GlucoseStats>

    /**
     * Counts high-frequency device samples (CGM) in the window. Together with
     * [manualReadingCountSince] this powers the 70%-CGM threshold for the
     * estimated-A1c card and Hero's glucose-source summary line.
     */
    @Query("SELECT COUNT(*) FROM glucose_samples WHERE timestamp >= :since")
    suspend fun cgmReadingCountSince(since: Long): Int

    /**
     * Counts user-authored glucose readings in the window. The
     * `hc_record_id IS NULL` predicate matches the `entries` side of the
     * `glucose_readings` view so a reading imported from Health Connect is
     * never counted twice.
     */
    @Query(
        "SELECT COUNT(*) FROM entries WHERE glucose_mgdl IS NOT NULL AND hc_record_id IS NULL AND timestamp >= :since"
    )
    suspend fun manualReadingCountSince(since: Long): Int

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
     * One-shot snapshot of the same streak-qualifying distinct days. Streak
     * arithmetic needs the full history, so streak callers intentionally pass
     * 0 as [since] even though that scans every logged day.
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
        FROM glucose_readings
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
        FROM glucose_readings
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

    // ---------- Hashtag analytics ----------

    /**
     * Every log entry whose free-text note contains a hashtag (`#`). The
     * LIKE predicate is intentionally coarse: actual tag extraction is done in
     * Kotlin via [com.omb9.glucosehero.util.HashtagExtractor], keeping SQL free
     * of regex semantics.
     */
    @Query("SELECT * FROM entries WHERE note IS NOT NULL AND note LIKE '%#%'")
    suspend fun taggedEntries(): List<EntryEntity>

    /**
     * The glucose reading nearest to [targetMillis] within the closed window
     * [startMillis, endMillis]. Used to find the ~2-hour post-event reading
     * when computing per-hashtag glucose deltas.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE glucose_mgdl IS NOT NULL
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY ABS(timestamp - :targetMillis) ASC
        LIMIT 1
        """
    )
    suspend fun glucoseReadingNearestTo(
        startMillis: Long,
        endMillis: Long,
        targetMillis: Long,
    ): EntryEntity?

    // ---------- Nightly pattern-recognition aggregates ----------

    /** One-shot version of [observeGlucosePoints] for background analysis. */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM entries
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    suspend fun glucosePointsSince(since: Long): List<GlucosePointRow>

    /**
     * Average glucose grouped by hour of the day. The WHERE clause on
     * `glucose_mgdl` and `timestamp` is served by the composite
     * `(glucose_mgdl, timestamp)` index.
     */
    @Query(
        """
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               AVG(glucose_mgdl) AS avgMgdl,
               COUNT(*) AS readings
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        GROUP BY hour
        ORDER BY hour ASC
        """
    )
    suspend fun hourlyAveragesSince(since: Long): List<HourlyGlucoseAverageRow>

    /**
     * Per-hour E[x] and E[x^2] aggregates. Standard deviation is derived in
     * Kotlin as sqrt(E[x^2] - E[x]^2), avoiding SQLite's missing STDDEV.
     */
    @Query(
        """
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               AVG(glucose_mgdl) AS avgMgdl,
               AVG(glucose_mgdl * glucose_mgdl) AS avgSqMgdl,
               COUNT(*) AS readings
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        GROUP BY hour
        ORDER BY hour ASC
        """
    )
    suspend fun hourlyVarianceSince(since: Long): List<HourlyGlucoseVarianceRow>
}
