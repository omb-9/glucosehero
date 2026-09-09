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

    @Query("SELECT * FROM pending_ai_queries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingAiQueryEntity?

    @Query("DELETE FROM pending_ai_queries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_ai_queries")
    fun observeCount(): Flow<Int>

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM pending_ai_queries ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<PendingAiQueryEntity>

    @Query("SELECT COUNT(*) FROM pending_ai_queries")
    suspend fun countAll(): Int

    @Query("DELETE FROM pending_ai_queries")
    suspend fun clear()

    @Insert
    suspend fun insertAll(queries: List<PendingAiQueryEntity>): List<Long>
}
