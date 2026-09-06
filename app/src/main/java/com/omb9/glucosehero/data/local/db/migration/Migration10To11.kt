package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v10 → v11: adds the mood columns to `entries`.
 *
 * Both columns are nullable and added with no backfill: existing rows are
 * simply missing a mood value until the user edits them.
 */
object Migration10To11 : Migration(10, 11) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `mood_score` INTEGER")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `mood_label` TEXT")
    }
}
