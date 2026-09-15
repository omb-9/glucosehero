package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    /** Newest 200 messages, newest-first; callers reverse to keep oldest-first display order. */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id <= :maxId ORDER BY timestamp ASC, id ASC")
    suspend fun getUpTo(maxId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM chat_messages WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    suspend fun pageForExport(lastId: Long, limit: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun countAll(): Int

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>): List<Long>
}
