package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v9 → v10: adds the `tag_analytics` cache table for the nightly analytics
 * worker. Brand-new table with no existing rows, so no backfill is required.
 *
 * `food_id` is intentionally left with no foreign key so deleting a food never
 * cascades into deleting its own analytics history.
 */
object Migration9To10 : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tag_analytics` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tag` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `food_id` INTEGER,
                `occurrences` INTEGER NOT NULL,
                `median_delta_mgdl` REAL NOT NULL,
                `p25_delta_mgdl` REAL NOT NULL,
                `p75_delta_mgdl` REAL NOT NULL,
                `avg_carbs_grams` REAL,
                `avg_bolus_units` REAL,
                `last_seen_at` INTEGER NOT NULL,
                `computed_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_analytics_tag` ON `tag_analytics` (`tag`)")
    }
}
