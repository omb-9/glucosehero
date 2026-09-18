package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v16 → v17: adds medication and feeling-sick columns to `entries`.
 *
 * All three columns are nullable and added with no backfill: existing rows
 * are simply missing a medication / feeling-sick value until the user
 * records one. `feeling_sick` is an entry-level flag, not medication-only.
 */
object Migration16To17 : Migration(16, 17) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `medication_name` TEXT")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `medication_dose` TEXT")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `feeling_sick` INTEGER")
    }
}
