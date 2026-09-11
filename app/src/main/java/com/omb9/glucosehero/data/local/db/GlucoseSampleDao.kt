package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseReadingBounds
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(samples: List<GlucoseSampleEntity>): List<Long>

    /**
     * Health Connect change-sync deletion. Scoped to [GlucoseSampleSource.HEALTH_CONNECT]
     * so a Nightscout/xDrip row that happens to share the same vendor id is not
     * removed. FEATURE: cgm-direct-ingest
     */
    @Query(
        "DELETE FROM glucose_samples WHERE hc_record_id = :hcRecordId AND source = 'HEALTH_CONNECT'",
    )
    suspend fun deleteByHcRecordId(hcRecordId: String)

    /** Source-generic delete used by later CGM ingest paths. FEATURE: cgm-direct-ingest */
    @Query("DELETE FROM glucose_samples WHERE source = :source AND external_id = :externalId")
    suspend fun deleteByExternalId(source: GlucoseSampleSource, externalId: String)

    /**
     * Drops every row for [source]. Health Connect "clear imported data" must
     * use this with [GlucoseSampleSource.HEALTH_CONNECT] so Nightscout /
     * xDrip rows survive. FEATURE: cgm-direct-ingest
     */
    @Query("DELETE FROM glucose_samples WHERE source = :source")
    suspend fun deleteBySource(source: GlucoseSampleSource)

    /**
     * Rows whose timestamp falls in `[fromInclusive, toInclusive]`, used by
     * [com.omb9.glucosehero.data.cgm.CgmIngestService] to load the collapse
     * window. FEATURE: cgm-direct-ingest
     */
    @Query(
        "SELECT * FROM glucose_samples WHERE timestamp BETWEEN :fromInclusive AND :toInclusive",
    )
    suspend fun samplesBetween(fromInclusive: Long, toInclusive: Long): List<GlucoseSampleEntity>

    /**
     * Newest sample timestamp for [source], for incremental fetch cursors.
     * FEATURE: cgm-direct-ingest
     */
    @Query("SELECT MAX(timestamp) FROM glucose_samples WHERE source = :source")
    suspend fun latestSampleTimestamp(source: GlucoseSampleSource): Long?

    @Query("SELECT COUNT(*) FROM glucose_samples")
    suspend fun count(): Int

    /**
     * All-time rows for [source]. Health Connect "imported samples" must use
     * [GlucoseSampleSource.HEALTH_CONNECT] so Nightscout / xDrip rows are
     * not counted. FEATURE: cgm-direct-ingest
     */
    @Query("SELECT COUNT(*) FROM glucose_samples WHERE source = :source")
    suspend fun countBySource(source: GlucoseSampleSource): Int

    /**
     * Rows for [source] whose sample timestamp is at least [sinceMillis].
     * Data Sources uses this for a trailing 24 h count. This is sample time,
     * not last-poll time. FEATURE: cgm-direct-ingest
     */
    @Query(
        "SELECT COUNT(*) FROM glucose_samples WHERE source = :source AND timestamp >= :sinceMillis",
    )
    suspend fun countSince(source: GlucoseSampleSource, sinceMillis: Long): Int

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
