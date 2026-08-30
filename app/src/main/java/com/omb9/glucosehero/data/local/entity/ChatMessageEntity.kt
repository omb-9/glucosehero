package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn

@Entity(tableName = "chat_messages", indices = [Index("timestamp")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "role") val role: ChatRole,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
)

fun ChatMessageEntity.toDomain() = ChatTurn(id, role, content, timestamp)
