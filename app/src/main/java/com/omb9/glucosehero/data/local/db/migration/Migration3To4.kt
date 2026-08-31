package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4: adds the optional advanced-macros columns to `entries` so the
 * Protein and Fat fields in the meal logger can persist alongside carbs.
 *
 * Both columns are nullable INTEGERs, so existing rows simply default to
 * NULL and no backfill is required. The two existing indices are preserved;
 * Room will verify the resulting schema against the v4 entity definition.
 */
object Migration3To4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `protein_grams` INTEGER")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `fat_grams` INTEGER")
    }
}
