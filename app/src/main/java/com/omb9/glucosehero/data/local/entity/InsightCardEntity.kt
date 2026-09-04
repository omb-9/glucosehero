package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recurring metabolic pattern detected by the nightly background analysis.
 *
 * Severity is stored as a plain Int so the table needs no type converters and
 * future UI layers can map the level to whatever visual treatment they like.
 */
@Entity(
    tableName = "insight_cards",
    indices = [Index("created_at")],
)
data class InsightCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "severity_level") val severityLevel: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        const val SEVERITY_INFO = 0
        const val SEVERITY_WARNING = 1
        const val SEVERITY_CRITICAL = 2
    }
}
