package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 → v15: generalizes `glucose_samples` identity away from Health Connect.
 *
 * SQLite on API 26 cannot drop a column, drop NOT NULL, or drop a unique
 * index in place, so the table is recreated. Every pre-existing row was
 * imported from Health Connect, so it is copied with
 * `source = 'HEALTH_CONNECT'` and `external_id = hc_record_id`. `hc_record_id`
 * stays populated (now nullable) so HC change-sync deletion still matches.
 * `trend_arrow` is added as NULL; later CGM sources will fill it.
 *
 * The unique index moves from `hc_record_id` to `(source, external_id)`. A
 * non-unique index remains on `hc_record_id` for deletion lookups. The
 * `glucose_readings` view is dropped and recreated because it depends on
 * `glucose_samples`; its UNION ALL rule is unchanged (the `entries.hc_record_id
 * IS NULL` filter is still on the entries table).
 *
 * FEATURE: cgm-direct-ingest
 */
object Migration14To15 : Migration(14, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // FEATURE: cgm-direct-ingest
        db.execSQL("DROP VIEW IF EXISTS `glucose_readings`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `glucose_samples_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `glucose_mgdl` REAL NOT NULL, `source` TEXT NOT NULL, `external_id` TEXT NOT NULL, `hc_record_id` TEXT, `trend_arrow` TEXT, `source_package` TEXT, `recording_method` INTEGER NOT NULL, `imported_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            """
            INSERT INTO `glucose_samples_new` (
                `id`, `timestamp`, `glucose_mgdl`, `source`, `external_id`,
                `hc_record_id`, `trend_arrow`, `source_package`,
                `recording_method`, `imported_at`
            )
            SELECT
                `id`, `timestamp`, `glucose_mgdl`,
                'HEALTH_CONNECT' AS source,
                `hc_record_id` AS external_id,
                `hc_record_id`,
                NULL AS trend_arrow,
                `source_package`,
                `recording_method`,
                `imported_at`
            FROM `glucose_samples`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `glucose_samples`")
        db.execSQL("ALTER TABLE `glucose_samples_new` RENAME TO `glucose_samples`")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_glucose_samples_timestamp` ON `glucose_samples` (`timestamp`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_glucose_samples_source_external_id` " +
                "ON `glucose_samples` (`source`, `external_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_glucose_samples_hc_record_id` ON `glucose_samples` (`hc_record_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_glucose_samples_timestamp_glucose_mgdl` " +
                "ON `glucose_samples` (`timestamp`, `glucose_mgdl`)",
        )

        db.execSQL(
            "CREATE VIEW `glucose_readings` AS SELECT timestamp, glucose_mgdl FROM glucose_samples\n" +
                "        UNION ALL\n" +
                "        SELECT timestamp, glucose_mgdl FROM entries\n" +
                "        WHERE glucose_mgdl IS NOT NULL AND hc_record_id IS NULL",
        )
    }
}
