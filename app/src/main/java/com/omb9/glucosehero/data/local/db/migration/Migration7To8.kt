package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8: adds the `glucose_samples` table, two provenance columns to
 * `entries` (`source`, `hc_record_id`), and the `glucose_readings` view that
 * unions device samples with user-authored glucose readings.
 *
 * The view's CREATE statement is copied verbatim from the exported schema so
 * that Room's post-migration identity-hash validation succeeds.
 */
object Migration7To8 : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `glucose_samples` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `glucose_mgdl` REAL NOT NULL,
                `hc_record_id` TEXT NOT NULL,
                `source_package` TEXT,
                `recording_method` INTEGER NOT NULL,
                `imported_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_glucose_samples_timestamp` ON `glucose_samples` (`timestamp`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_glucose_samples_hc_record_id` ON `glucose_samples` (`hc_record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_glucose_samples_timestamp_glucose_mgdl` ON `glucose_samples` (`timestamp`, `glucose_mgdl`)"
        )

        db.execSQL("ALTER TABLE `entries` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `hc_record_id` TEXT")

        db.execSQL(
            """
            CREATE VIEW `glucose_readings` AS SELECT timestamp, glucose_mgdl FROM glucose_samples
                    UNION ALL
                    SELECT timestamp, glucose_mgdl FROM entries
                    WHERE glucose_mgdl IS NOT NULL AND hc_record_id IS NULL
            """.trimIndent()
        )
    }
}
