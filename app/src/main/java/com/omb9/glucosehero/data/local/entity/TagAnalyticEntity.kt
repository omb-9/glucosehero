package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.TagKind

/**
 * Cached per-tag glucose analytics, refreshed wholesale by the nightly
 * analytics worker.
 *
 * `food_id` deliberately has no foreign key — the same reasoning as
 * `entries.food_id`: deleting a food must not cascade into deleting its own
 * analytics history.
 */
@Entity(
    tableName = "tag_analytics",
    indices = [Index(value = ["tag"], unique = true)],
)
data class TagAnalyticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "kind") val kind: TagKind,
    @ColumnInfo(name = "food_id") val foodId: Long? = null,
    @ColumnInfo(name = "occurrences") val occurrences: Int,
    @ColumnInfo(name = "median_delta_mgdl") val medianDeltaMgdl: Double,
    @ColumnInfo(name = "p25_delta_mgdl") val p25DeltaMgdl: Double,
    @ColumnInfo(name = "p75_delta_mgdl") val p75DeltaMgdl: Double,
    @ColumnInfo(name = "avg_carbs_grams") val avgCarbsGrams: Double? = null,
    @ColumnInfo(name = "avg_bolus_units") val avgBolusUnits: Double? = null,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)
