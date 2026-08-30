package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingAiQueryDao {

    @Insert
    suspend fun insert(query: PendingAiQueryEntity): Long

    @Query("SELECT * FROM pending_ai_queries ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingAiQueryEntity>

    @Query("DELETE FROM pending_ai_queries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_ai_queries")
    fun observeCount(): Flow<Int>
}
