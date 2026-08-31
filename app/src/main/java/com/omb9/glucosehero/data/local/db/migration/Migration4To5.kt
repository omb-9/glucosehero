package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: adds the `supplies` table for the Supply Tracker.
 *
 * No data exists to migrate — this is a pure table creation. The index name
 * (`index_supplies_started_at`) matches the name Room would generate from
 * `@Entity(indices = [Index("started_at")])` so the post-migration schema
 * validation succeeds.
 */
object Migration4To5 : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supplies` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `started_at` INTEGER NOT NULL,
                `expected_lifespan_days` INTEGER NOT NULL,
                `replaced_at` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_supplies_started_at` ON `supplies` (`started_at`)"
        )
    }
}
