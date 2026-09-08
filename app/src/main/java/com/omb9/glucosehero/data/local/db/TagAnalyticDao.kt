package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagAnalyticDao {

    @Query("SELECT * FROM tag_analytics ORDER BY ABS(median_delta_mgdl) DESC")
    fun observeAll(): Flow<List<TagAnalyticEntity>>

    @Insert
    suspend fun insertAll(entities: List<TagAnalyticEntity>)

    @Query("DELETE FROM tag_analytics")
    suspend fun clear()

    /** Atomically clears and repopulates the cache in a single transaction. */
    @Transaction
    suspend fun replaceAll(entities: List<TagAnalyticEntity>) {
        clear()
        insertAll(entities)
    }
}
