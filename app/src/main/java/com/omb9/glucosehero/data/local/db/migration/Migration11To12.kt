package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v11 → v12: adds a unique index on `entries.hc_record_id`.
 *
 * Health Connect re-imports previously relied on a Kotlin-side dedup because
 * this column had no unique index. That index now lets the import use a plain
 * conflict-ignore insert, and it also turns `deleteByHcRecordId` into an
 * indexed lookup instead of a full table scan.
 *
 * The column is nullable (every manually logged entry has `NULL`), and SQLite
 * treats NULLs as distinct in a unique index, so manual entries are unaffected.
 * Duplicates must still be collapsed first: the Kotlin dedup ran without a
 * constraint enforcing it, so any two rows sharing a non-null `hc_record_id`
 * are removed here (keeping the lowest `id`) before the unique index is built.
 */
object Migration11To12 : Migration(11, 12) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM `entries` WHERE `hc_record_id` IS NOT NULL AND `id` NOT IN " +
                "(SELECT MIN(`id`) FROM `entries` WHERE `hc_record_id` IS NOT NULL GROUP BY `hc_record_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_hc_record_id` ON `entries` (`hc_record_id`)"
        )
    }
}
