package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.Supply
import com.omb9.glucosehero.domain.model.SupplyType

/**
 * Persisted supply lifecycle row. [replacedAt] is null while the item is
 * active; logging a newer supply of the same type stamps the old row, so
 * "active supplies" is a clean `replaced_at IS NULL` query rather than a
 * guess based on the expected lifespan alone.
 */
@Entity(
    tableName = "supplies",
    indices = [Index("started_at")],
)
data class SupplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "type") val type: SupplyType,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "expected_lifespan_days") val expectedLifespanDays: Int,
    @ColumnInfo(name = "replaced_at") val replacedAt: Long? = null,
)

fun SupplyEntity.toDomain() = Supply(
    id = id,
    type = type,
    startedAt = startedAt,
    expectedLifespanDays = expectedLifespanDays,
)
