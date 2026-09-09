package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 → v14: adds `ttl_seconds` to `pending_ai_queries`.
 *
 * Existing queued rows have unknown remaining validity (the original
 * `created_at` may be hours old, or a restored backup). They are given
 * `ttl_seconds = 0` so [com.omb9.glucosehero.data.local.entity.PendingAiQueryTtl]
 * expires them without sending to the backend.
 *
 * FEATURE: pending-query-ttl
 *
 * If a parallel SQLCipher change also needs a version bump, keep this ALTER
 * TABLE and compose it with that migration rather than dropping the column.
 */
object MigrationPendingAiTtl : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // FEATURE: pending-query-ttl
        db.execSQL(
            "ALTER TABLE `pending_ai_queries` ADD COLUMN `ttl_seconds` INTEGER NOT NULL DEFAULT 0",
        )
    }
}
