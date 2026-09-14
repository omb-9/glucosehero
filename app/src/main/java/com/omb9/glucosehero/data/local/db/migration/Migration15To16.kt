package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v15 → v16: drop two indexes that are prefixes of existing composite indexes.
 *
 * `index_entries_glucose_mgdl` is covered by `index_entries_glucose_mgdl_timestamp`.
 * `index_glucose_samples_timestamp` is covered by
 * `index_glucose_samples_timestamp_glucose_mgdl`.
 *
 * Keep `index_entries_timestamp`: it is not a prefix of another entries index.
 */
object Migration15To16 : Migration(15, 16) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_entries_glucose_mgdl`")
        db.execSQL("DROP INDEX IF EXISTS `index_glucose_samples_timestamp`")
    }
}
