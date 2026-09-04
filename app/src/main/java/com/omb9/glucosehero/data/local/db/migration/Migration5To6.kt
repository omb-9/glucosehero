package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: adds the composite `(glucose_mgdl, timestamp)` index to `entries`.
 *
 * The index name matches the name Room would generate from
 * `@Entity(indices = [Index(value = ["glucose_mgdl", "timestamp"])])` so the
 * post-migration schema validation succeeds.
 */
object Migration5To6 : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_entries_glucose_mgdl_timestamp`
            ON `entries` (`glucose_mgdl`, `timestamp`)
            """.trimIndent()
        )
    }
}
