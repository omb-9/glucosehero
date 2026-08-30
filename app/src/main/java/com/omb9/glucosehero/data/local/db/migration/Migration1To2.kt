package com.omb9.glucosehero.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omb9.glucosehero.util.AppJson

/**
 * v1 → v2: replaces the one-category-per-row `entries` table (`type` NOT NULL
 * TEXT + `details` NOT NULL JSON TEXT) with a flat, multi-metric table where
 * insulin/carbs/exercise each get their own nullable column.
 *
 * The three hot columns (id, timestamp, glucose_mgdl, note) survive as a
 * direct SQL copy. Everything that used to live inside the polymorphic
 * `details` JSON blob is lifted out by parsing that JSON in Kotlin — NOT via
 * SQLite's `json_extract`, which is not guaranteed to be compiled into the
 * system SQLite on every OEM build at minSdk 26 — and backfilled with
 * per-row UPDATEs.
 *
 * Room already wraps [migrate] in a transaction; this class must NOT call
 * beginTransaction()/setTransactionSuccessful() itself. `chat_messages` and
 * `pending_ai_queries` are unchanged in v2 and are never touched here.
 */
object Migration1To2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries_new (
                id                 INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp          INTEGER NOT NULL,
                glucose_mgdl       REAL,
                meal_context       TEXT,
                insulin_units      REAL,
                insulin_type       TEXT,
                carbs_grams        INTEGER,
                meal_description   TEXT,
                exercise_minutes   INTEGER,
                exercise_intensity TEXT,
                note               TEXT
            )
            """.trimIndent()
        )

        // The three hot columns survive as-is; id is preserved (no renumbering).
        db.execSQL(
            """
            INSERT INTO entries_new (id, timestamp, glucose_mgdl, note)
            SELECT id, timestamp, glucose_mgdl, note FROM entries
            """.trimIndent()
        )

        backfillLegacyDetails(db)

        db.execSQL("DROP TABLE entries")
        db.execSQL("ALTER TABLE entries_new RENAME TO entries")

        db.execSQL("CREATE INDEX index_entries_timestamp ON entries (timestamp)")
        db.execSQL("CREATE INDEX index_entries_glucose_mgdl ON entries (glucose_mgdl)")
    }

    /**
     * Iterates every v1 row's `details` JSON and lifts its fields into the
     * matching v2 columns via a targeted per-row UPDATE. A single corrupt
     * row (bad JSON, unknown "kind" discriminator) must not abort the whole
     * migration: on parse failure we leave that row's metric columns null —
     * its timestamp/glucose/note already survived the bulk copy above — and
     * move on to the next row.
     */
    private fun backfillLegacyDetails(db: SupportSQLiteDatabase) {
        db.query("SELECT id, type, details FROM entries").use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val detailsIndex = cursor.getColumnIndexOrThrow("details")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val detailsJson = cursor.getString(detailsIndex)

                val legacy = try {
                    AppJson.decodeFromString(LegacyEntryDetails.serializer(), detailsJson)
                } catch (_: Exception) {
                    // Covers SerializationException and any malformed-input
                    // failure. Leave this row's metric columns null; do not
                    // rethrow, and do not abort the rest of the migration.
                    continue
                }

                when (legacy) {
                    is LegacyEntryDetails.Glucose -> db.execSQL(
                        "UPDATE entries_new SET meal_context = ? WHERE id = ?",
                        arrayOf<Any?>(legacy.context.name, id),
                    )

                    is LegacyEntryDetails.Insulin -> db.execSQL(
                        "UPDATE entries_new SET insulin_type = ?, insulin_units = ? WHERE id = ?",
                        arrayOf<Any?>(legacy.insulinType.name, legacy.units, id),
                    )

                    is LegacyEntryDetails.Meal -> db.execSQL(
                        "UPDATE entries_new SET carbs_grams = ?, meal_description = ? WHERE id = ?",
                        arrayOf<Any?>(legacy.carbsGrams, legacy.description, id),
                    )

                    is LegacyEntryDetails.Activity -> db.execSQL(
                        "UPDATE entries_new SET exercise_minutes = ?, exercise_intensity = ? " +
                            "WHERE id = ?",
                        arrayOf<Any?>(legacy.durationMinutes, legacy.intensity.name, id),
                    )

                    is LegacyEntryDetails.Note -> Unit // No-op: nothing extra to lift.
                }
            }
        }
    }
}
