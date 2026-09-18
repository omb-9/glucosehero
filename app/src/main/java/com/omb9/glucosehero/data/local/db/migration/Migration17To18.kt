package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v17 → v18: chat turn kind (error vs normal) and a compact per-turn
 * context summary of what left the device. Existing rows stay NORMAL
 * with a null summary so the UI omits the data-context chip rather than
 * guessing.
 */
object Migration17To18 : Migration(17, 18) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `chat_messages` ADD COLUMN `message_kind` TEXT NOT NULL DEFAULT 'NORMAL'",
        )
        db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `context_summary_json` TEXT")
    }
}
