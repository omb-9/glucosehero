package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v6 → v7: adds the `insight_cards` table for nightly pattern insights.
 *
 * The index name matches the name Room would generate from
 * `@Entity(indices = [Index("created_at")])` so the post-migration schema
 * validation succeeds.
 */
object Migration6To7 : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `insight_cards` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `severity_level` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_insight_cards_created_at` ON `insight_cards` (`created_at`)"
        )
    }
}
