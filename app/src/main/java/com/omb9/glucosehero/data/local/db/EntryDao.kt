package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

/** One row of the correlated-subquery payload for per-tag delta analytics. */
data class TagAnalyticsRow(
    val entryId: Long,
    val timestamp: Long,
    val note: String?,
    val mealDescription: String?,
    val moodLabel: String?,
    val foodId: Long?,
    val foodName: String?,
    val carbsGrams: Int?,
    val bolusUnits: Double?,
    val baselineMgdl: Double?,
    val followUpMgdl: Double?,
)

/** Five-bucket time-in-range counts over the `glucose_readings` view. */
data class TimeInRangeCounts(
    val veryLow: Int,
    val low: Int,
    val inRange: Int,
    val high: Int,
    val veryHigh: Int,
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
     * Chart projection sourced from the `glucose_readings` view (CGM samples plus user-authored
     * readings), so the trend line includes sensor data.
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    fun observeGlucoseReadingsPoints(since: Long): Flow<List<GlucosePointRow>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryEntity?>

    /**
     * Free-text search across the note, meal description, and mood label
     * columns. Bounded to 200 rows newest-first: the entries table grows
     * without bound, so search must never stream the whole table through
     * Kotlin. LIKE is case-insensitive for ASCII and matches substrings.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE (note LIKE '%' || :query || '%'
            OR meal_description LIKE '%' || :query || '%'
            OR mood_label LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT 200
        """
    )
    suspend fun searchEntries(query: String): List<EntryEntity>

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

    /**
     * Rolling average over the `glucose_readings` view (CGM samples plus
     * user-authored readings), so AI-context averages match the time-in-range
     * and daily summaries sourced from the same view.
     */
    @Query(
        """
        SELECT AVG(glucose_mgdl) FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    suspend fun averageGlucoseReadingsSince(since: Long): Double?

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

    /**
     * Five time-in-range bucket counts over `glucose_readings`. The 54 and 250 thresholds are
     * fixed clinical constants; only [low] and [high] come from the user's targets.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN glucose_mgdl < 54 THEN 1 ELSE 0 END), 0) AS veryLow,
            COALESCE(SUM(CASE WHEN glucose_mgdl >= 54 AND glucose_mgdl < :low THEN 1 ELSE 0 END), 0) AS low,
            COALESCE(SUM(CASE WHEN glucose_mgdl BETWEEN :low AND :high THEN 1 ELSE 0 END), 0) AS inRange,
            COALESCE(SUM(CASE WHEN glucose_mgdl > :high AND glucose_mgdl <= 250 THEN 1 ELSE 0 END), 0) AS high,
            COALESCE(SUM(CASE WHEN glucose_mgdl > 250 THEN 1 ELSE 0 END), 0) AS veryHigh
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """
    )
    suspend fun timeInRangeCountsSince(since: Long, low: Double, high: Double): TimeInRangeCounts

    /**
     * View-based previous-window average for trend deltas, matching the current-window average
     * (which is computed over `glucose_readings`).
     */
    @Query(
        """
        SELECT AVG(glucose_mgdl) FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :startMillis AND timestamp < :endMillis
        """
    )
    suspend fun averageGlucoseReadingsBetween(startMillis: Long, endMillis: Long): Double?

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

    /**
     * Health Connect interval events (sleep sessions / menstruation periods)
     * that carry an end timestamp, oldest first, for window-based analytics.
     */
    @Query("SELECT * FROM entries WHERE end_time IS NOT NULL AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun windowedEntriesSince(since: Long): List<EntryEntity>

    /**
     * The single newest glucose reading from the `glucose_readings` view
     * (home-screen widget). The view has no `id`, so this is a projection.
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun latestGlucoseReading(): GlucosePointRow?

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
     * One-shot payload for per-tag glucose deltas.
     *
     * The correlated subquery resolves each entry's ~2-hour follow-up glucose
     * reading in a single query. Reading follow-ups from the `glucose_readings`
     * view (not `entries`) picks up CGM samples automatically — a manual
     * follow-up landing inside the ±30-minute window around the two-hour mark
     * is rare, while a sensor sample there is nearly guaranteed.
     */
    @Query(
        """
        SELECT
            e.id                   AS entryId,
            e.timestamp            AS timestamp,
            e.note                 AS note,
            e.meal_description     AS mealDescription,
            e.mood_label           AS moodLabel,
            e.food_id              AS foodId,
            f.name                 AS foodName,
            e.carbs_grams          AS carbsGrams,
            e.insulin_bolus_units  AS bolusUnits,
            e.glucose_mgdl         AS baselineMgdl,
            (
                SELECT follow.glucose_mgdl
                FROM (
                    SELECT r.glucose_mgdl AS glucose_mgdl,
                           ABS(r.timestamp - (e.timestamp + :target)) AS dist
                    FROM glucose_readings r
                    WHERE r.timestamp BETWEEN e.timestamp + :windowStart AND e.timestamp + :windowEnd
                ) AS follow
                ORDER BY follow.dist ASC
                LIMIT 1
            ) AS followUpMgdl
        FROM entries e
        LEFT JOIN foods f ON f.id = e.food_id
        WHERE e.glucose_mgdl IS NOT NULL
          AND e.timestamp >= :since
          AND (e.food_id IS NOT NULL OR e.note LIKE '%#%' OR e.meal_description IS NOT NULL OR e.mood_label IS NOT NULL)
        """
    )
    suspend fun tagAnalyticsRows(
        since: Long,
        windowStart: Long,
        windowEnd: Long,
        target: Long,
    ): List<TagAnalyticsRow>

    // ---------- Nightly pattern-recognition aggregates ----------

    /**
     * One-shot version of [observeGlucoseReadingsPoints] for background
     * analysis. Sourced from the `glucose_readings` view so overnight-low
     * detection sees CGM samples as well as manual readings.
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """
    )
    suspend fun glucoseReadingPointsSince(since: Long): List<GlucosePointRow>

    /**
     * Glucose readings within an inclusive timestamp range, oldest first, for
     * window-based tag analytics (baseline + in-window averages).
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY timestamp ASC
        """
    )
    suspend fun glucoseReadingPointsBetween(startMillis: Long, endMillis: Long): List<GlucosePointRow>

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

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM entries ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY timestamp ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun pageByTimestampForExport(limit: Int, offset: Int): List<EntryEntity>

    @Query(
        "SELECT * FROM entries WHERE timestamp >= :since ORDER BY timestamp ASC, id ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun pageSinceByTimestampForExport(
        since: Long,
        limit: Int,
        offset: Int,
    ): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun countAll(): Int

    @Query("SELECT * FROM entries ORDER BY id")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT uuid FROM entries")
    suspend fun allUuids(): List<String>

    @Query("DELETE FROM entries")
    suspend fun clear()

    @Insert
    suspend fun insertAll(entities: List<EntryEntity>): List<Long>

    /**
     * Conflict-ignore bulk insert keyed on the unique `hc_record_id` index.
     * Re-importing the same Health Connect nutrition/exercise records silently
     * skips rows whose `hc_record_id` already exists, so re-imports stay
     * idempotent without the previous Kotlin-side dedup.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(entities: List<EntryEntity>): List<Long>

    /** Removes an imported entry by its Health Connect record id. */
    @Query("DELETE FROM entries WHERE hc_record_id = :hcRecordId")
    suspend fun deleteByHcRecordId(hcRecordId: String)
}
