package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v12 → v13: adds a nullable `end_time` column to `entries`.
 *
 * Health Connect interval records (sleep sessions and menstruation periods)
 * need their end timestamp so the nightly analytics worker can compute a
 * window-based glucose delta rather than the 2-hour post-event delta used for
 * food/exercise/mood tags. Every pre-existing row is an instant event (manual
 * entries, nutrition, exercise) and therefore keeps `NULL`.
 */
object Migration12To13 : Migration(12, 13) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `end_time` INTEGER")
    }
}