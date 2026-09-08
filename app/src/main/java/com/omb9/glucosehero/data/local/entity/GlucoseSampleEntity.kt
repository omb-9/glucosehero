package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * High-frequency device data (a CGM writes roughly 288 readings per day),
 * kept deliberately separate from [EntryEntity], which remains the
 * user-authored event log. Re-importing the same Health Connect record is
 * idempotent thanks to the unique index on `hc_record_id`.
 */
@Entity(
    tableName = "glucose_samples",
    indices = [
        Index("timestamp"),
        Index(value = ["hc_record_id"], unique = true),
        Index(value = ["timestamp", "glucose_mgdl"]),
    ],
)
data class GlucoseSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** Canonical mg/dL, matching EntryEntity's storage convention. */
    @ColumnInfo(name = "glucose_mgdl") val glucoseMgdl: Double,
    /** Health Connect Metadata.id. The unique index makes re-import idempotent. */
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String,
    /** Originating app package, e.g. com.dexcom.g7. Nullable. */
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    /** Health Connect recordingMethod constant. */
    @ColumnInfo(name = "recording_method") val recordingMethod: Int,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)
