package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * Union of high-frequency device samples with user-authored glucose readings.
 * The `hc_record_id IS NULL` filter on the `entries` side is required: without
 * it, a reading that was both logged locally and imported from Health Connect
 * would be counted twice in every aggregate.
 */
@DatabaseView(
    viewName = "glucose_readings",
    value = """
        SELECT timestamp, glucose_mgdl FROM glucose_samples
        UNION ALL
        SELECT timestamp, glucose_mgdl FROM entries
        WHERE glucose_mgdl IS NOT NULL AND hc_record_id IS NULL
    """,
)
data class GlucoseReadingView(
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "glucose_mgdl") val glucoseMgdl: Double,
)
