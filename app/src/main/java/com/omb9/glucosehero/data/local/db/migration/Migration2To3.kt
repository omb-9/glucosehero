package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: splits the single `insulin_units` + `insulin_type` pair into two
 * independent columns `insulin_basal_units` and `insulin_bolus_units` so
 * basal and bolus can be logged simultaneously.
 *
 * Follows the same table-rebuild pattern as [Migration1To2].
 */
object Migration2To3 : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `glucose_mgdl` REAL, `meal_context` TEXT, `insulin_basal_units` REAL, `insulin_bolus_units` REAL, `carbs_grams` INTEGER, `meal_description` TEXT, `exercise_minutes` INTEGER, `exercise_intensity` TEXT, `note` TEXT)")

        db.execSQL(
            """
            INSERT INTO `entries_new` (
                `id`, `timestamp`, `glucose_mgdl`, `meal_context`, `carbs_grams`,
                `meal_description`, `exercise_minutes`, `exercise_intensity`, `note`
            )
            SELECT 
                `id`, `timestamp`, `glucose_mgdl`, `meal_context`, `carbs_grams`,
                `meal_description`, `exercise_minutes`, `exercise_intensity`, `note`
            FROM `entries`
            """.trimIndent()
        )

        backfillLegacyInsulin(db)

        db.execSQL("DROP TABLE `entries`")
        db.execSQL("ALTER TABLE `entries_new` RENAME TO `entries`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_timestamp` ON `entries` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_glucose_mgdl` ON `entries` (`glucose_mgdl`)")
    }

    private fun backfillLegacyInsulin(db: SupportSQLiteDatabase) {
        db.query("SELECT `id`, `insulin_type`, `insulin_units` FROM `entries` WHERE `insulin_units` IS NOT NULL").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val typeIndex = cursor.getColumnIndexOrThrow("insulin_type")
            val unitsIndex = cursor.getColumnIndexOrThrow("insulin_units")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val type = cursor.getString(typeIndex)
                val units = cursor.getDouble(unitsIndex)

                when (type) {
                    "BASAL" -> db.execSQL(
                        "UPDATE `entries_new` SET `insulin_basal_units` = ? WHERE `id` = ?",
                        arrayOf<Any?>(units, id),
                    )
                    else -> db.execSQL(
                        "UPDATE `entries_new` SET `insulin_bolus_units` = ? WHERE `id` = ?",
                        arrayOf<Any?>(units, id),
                    )
                }
            }
        }
    }
}
