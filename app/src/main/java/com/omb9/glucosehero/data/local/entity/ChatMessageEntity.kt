package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.ChatContextSummary
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.util.AppJson

@Entity(tableName = "chat_messages", indices = [Index("timestamp")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "role") val role: ChatRole,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "message_kind", defaultValue = "NORMAL")
    val messageKind: ChatTurnKind = ChatTurnKind.NORMAL,
    @ColumnInfo(name = "context_summary_json")
    val contextSummaryJson: String? = null,
)

fun ChatMessageEntity.toDomain() = ChatTurn(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp,
    kind = messageKind,
    contextSummary = contextSummaryJson?.let { raw ->
        runCatching { AppJson.decodeFromString<ChatContextSummary>(raw) }.getOrNull()
    },
)
