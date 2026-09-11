package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * High-frequency device data (a CGM writes roughly 288 readings per day),
 * kept deliberately separate from [EntryEntity], which remains the
 * user-authored event log.
 *
 * Identity is `(source, external_id)` so Health Connect is not the only
 * ingest path that can insert. Re-importing the same source-scoped id is
 * idempotent via the unique index. [hcRecordId] stays populated for Health
 * Connect rows so change-sync deletion can still match [Metadata.id]; it is
 * NULL for every other source.
 *
 * Canonical glucose is mg/dL, matching [EntryEntity].
 *
 * FEATURE: cgm-direct-ingest
 */
@Entity(
    tableName = "glucose_samples",
    indices = [
        Index("timestamp"),
        Index(value = ["source", "external_id"], unique = true),
        Index("hc_record_id"),
        Index(value = ["timestamp", "glucose_mgdl"]),
    ],
)
data class GlucoseSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** Canonical mg/dL, matching EntryEntity's storage convention. */
    @ColumnInfo(name = "glucose_mgdl") val glucoseMgdl: Double,
    /** [GlucoseSampleSource] name string. Room persists the enum name. */
    @ColumnInfo(name = "source") val source: GlucoseSampleSource,
    /** Stable id within [source] (Health Connect Metadata.id, Nightscout `_id`, …). */
    @ColumnInfo(name = "external_id") val externalId: String,
    /**
     * Health Connect Metadata.id. NULL for non-HC sources. Kept so HC
     * DeletionChange can still find the row after identity moved to
     * `(source, external_id)`.
     */
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String? = null,
    /** Nightscout direction / xDrip+ slope. Unused in Phase 0. */
    @ColumnInfo(name = "trend_arrow") val trendArrow: String? = null,
    /** Originating app package, e.g. com.dexcom.g7. Nullable. */
    @ColumnInfo(name = "source_package") val sourcePackage: String? = null,
    /** Health Connect recordingMethod constant. */
    @ColumnInfo(name = "recording_method") val recordingMethod: Int,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)
