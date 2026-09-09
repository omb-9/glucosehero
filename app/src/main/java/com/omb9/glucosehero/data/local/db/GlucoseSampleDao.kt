package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseReadingBounds
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(samples: List<GlucoseSampleEntity>): List<Long>

    @Query("DELETE FROM glucose_samples WHERE hc_record_id = :hcRecordId")
    suspend fun deleteByHcRecordId(hcRecordId: String)

    @Query("SELECT COUNT(*) FROM glucose_samples")
    suspend fun count(): Int

    @Query("DELETE FROM glucose_samples")
    suspend fun clear()

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM glucose_samples ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<GlucoseSampleEntity>

    @Query("SELECT * FROM glucose_samples ORDER BY id")
    suspend fun getAll(): List<GlucoseSampleEntity>

    @Insert
    suspend fun insertAll(samples: List<GlucoseSampleEntity>): List<Long>

    // ---------- Chart downsampling (FEATURE: lttb-chart-perf) ----------

    /**
     * Lightweight (timestamp, mg/dL) projection from the `glucose_readings`
     * view so the 24h chart includes CGM samples and fingersticks without
     * loading [GlucoseSampleEntity] rows.
     */
    @Query(
        """
        SELECT timestamp, glucose_mgdl AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        ORDER BY timestamp ASC
        """,
    )
    fun observeReadingsSince(since: Long): Flow<List<GlucosePointRow>>

    /**
     * SQLite time buckets over `glucose_readings` (CGM union manuals). Used
     * for 7/14/30/90 day charts so Kotlin never sees 288–1440 points per day.
     * Pass 15 min or 1 hour millis for the long-range aggregations.
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS timestamp,
               AVG(glucose_mgdl) AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        GROUP BY timestamp / :bucketMillis
        ORDER BY timestamp ASC
        """,
    )
    fun observeBucketedReadingsSince(since: Long, bucketMillis: Long): Flow<List<GlucosePointRow>>

    /**
     * CGM-only buckets from `glucose_samples`. Same grouping as
     * [observeBucketedReadingsSince] without the entries-side union.
     */
    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS timestamp,
               AVG(glucose_mgdl) AS glucoseMgdl
        FROM glucose_samples
        WHERE timestamp >= :since
        GROUP BY timestamp / :bucketMillis
        ORDER BY timestamp ASC
        """,
    )
    fun observeBucketedSamplesSince(since: Long, bucketMillis: Long): Flow<List<GlucosePointRow>>

    @Query(
        """
        SELECT (timestamp / :bucketMillis) * :bucketMillis AS timestamp,
               AVG(glucose_mgdl) AS glucoseMgdl
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        GROUP BY timestamp / :bucketMillis
        ORDER BY timestamp ASC
        """,
    )
    suspend fun bucketedReadingsSince(since: Long, bucketMillis: Long): List<GlucosePointRow>

    /** True extrema and mean over every reading; never derived from LTTB output. */
    @Query(
        """
        SELECT MIN(glucose_mgdl) AS minMgdl,
               MAX(glucose_mgdl) AS maxMgdl,
               AVG(glucose_mgdl) AS avgMgdl,
               COUNT(*) AS count
        FROM glucose_readings
        WHERE glucose_mgdl IS NOT NULL AND timestamp >= :since
        """,
    )
    suspend fun readingBoundsSince(since: Long): GlucoseReadingBounds
}
