package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Hero query captured while offline, waiting for the connectivity worker. */
@Entity(tableName = "pending_ai_queries")
data class PendingAiQueryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "user_message_id") val userMessageId: Long,
    @ColumnInfo(name = "prompt") val prompt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /**
     * Strict validity window in seconds. 0 means unknown age and must expire.
     * New inserts use [PendingAiQueryTtl.DEFAULT_TTL_SECONDS] (30 minutes).
     *
     * FEATURE: pending-query-ttl
     */
    @ColumnInfo(name = "ttl_seconds", defaultValue = "0") val ttlSeconds: Int = 0,
) {
    fun isExpired(nowMillis: Long): Boolean =
        PendingAiQueryTtl.isExpired(createdAt, ttlSeconds, nowMillis)
}
