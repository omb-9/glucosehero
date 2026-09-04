package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {

    @Insert
    suspend fun insert(insight: InsightCardEntity): Long

    /** Removes insight cards older than [cutoff] (epoch millis). */
    @Query("DELETE FROM insight_cards WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /** Latest insights first, capped at [limit] for home/overview surfaces. */
    @Query("SELECT * FROM insight_cards ORDER BY created_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<InsightCardEntity>>

    /** One-shot snapshot used by the worker to avoid inserting duplicates. */
    @Query("SELECT * FROM insight_cards ORDER BY created_at DESC")
    suspend fun getAll(): List<InsightCardEntity>
}
