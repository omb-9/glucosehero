package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 → v9: adds the `foods` table, a `food_id` reference and stable `uuid`
 * identity to `entries`, and a stable `uuid` identity to `supplies`.
 *
 * Both `entries` and `supplies` already contain rows, so `uuid` cannot be
 * added with a single shared value — every existing row needs its own
 * distinct UUIDv4-shaped value. The column is added `NOT NULL` with an
 * empty-string default (the only way `ADD COLUMN` accepts a `NOT NULL`
 * column), each existing row is backfilled with a distinct value, and only
 * then is the unique index created.
 */
object Migration8To9 : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `foods` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `barcode` TEXT,
                `carbs_grams` REAL NOT NULL,
                `protein_grams` REAL,
                `fat_grams` REAL,
                `kcal` REAL,
                `serving_grams` REAL,
                `serving_label` TEXT,
                `source` TEXT NOT NULL,
                `off_fetched_at` INTEGER,
                `user_corrected` INTEGER NOT NULL,
                `use_count` INTEGER NOT NULL,
                `last_used_at` INTEGER,
                `is_favorite` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_barcode` ON `foods` (`barcode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_last_used_at` ON `foods` (`last_used_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_foods_name` ON `foods` (`name`)")

        db.execSQL("ALTER TABLE `entries` ADD COLUMN `food_id` INTEGER")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            UPDATE entries SET uuid =
                substr(lower(hex(randomblob(16))),1,8) || '-' ||
                substr(lower(hex(randomblob(16))),1,4) || '-4' ||
                substr(lower(hex(randomblob(16))),1,3) || '-a' ||
                substr(lower(hex(randomblob(16))),1,3) || '-' ||
                substr(lower(hex(randomblob(16))),1,12)
            WHERE uuid IS NULL OR uuid = ''
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_uuid` ON `entries` (`uuid`)")

        db.execSQL("ALTER TABLE `supplies` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            UPDATE supplies SET uuid =
                substr(lower(hex(randomblob(16))),1,8) || '-' ||
                substr(lower(hex(randomblob(16))),1,4) || '-4' ||
                substr(lower(hex(randomblob(16))),1,3) || '-a' ||
                substr(lower(hex(randomblob(16))),1,3) || '-' ||
                substr(lower(hex(randomblob(16))),1,12)
            WHERE uuid IS NULL OR uuid = ''
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplies_uuid` ON `supplies` (`uuid`)")
    }
}
