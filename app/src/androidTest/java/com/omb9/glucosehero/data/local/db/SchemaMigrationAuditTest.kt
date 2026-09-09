package com.omb9.glucosehero.data.local.db

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.db.migration.Migration1To2
import com.omb9.glucosehero.data.local.db.migration.Migration2To3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feature 17: every exported-schema hop, the full 1→current path, a fresh
 * install that must not run legacy migrations, and empty create of the
 * current schema. Schemas live in `app/schemas`. Production does not call
 * `fallbackToDestructiveMigration`.
 */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationAuditTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GlucoseHeroDatabase::class.java,
    )

    private val currentVersion = GlucoseHeroDatabase.ALL_MIGRATIONS.last().endVersion

    @Test
    fun eachConsecutiveMigration_matchesExportedSchema() {
        for (migration in GlucoseHeroDatabase.ALL_MIGRATIONS) {
            val name = "audit-hop-${migration.startVersion}-${migration.endVersion}"
            helper.createDatabase(name, migration.startVersion).close()
            helper.runMigrationsAndValidate(
                name,
                migration.endVersion,
                true,
                migration,
            ).close()
        }
    }

    @Test
    fun migrate1To2_liftsLegacyDetailsJson() {
        val name = "audit-1-2"
        helper.createDatabase(name, 1).use { db ->
            insertV1Entry(
                db,
                timestamp = 100L,
                type = "GLUCOSE",
                glucoseMgdl = 110.0,
                details = """{"kind":"glucose","context":"BEFORE_MEAL"}""",
            )
            insertV1Entry(
                db,
                timestamp = 200L,
                type = "INSULIN",
                details = """{"kind":"insulin","insulinType":"BASAL","units":1.2}""",
            )
            insertV1Entry(
                db,
                timestamp = 300L,
                type = "INSULIN",
                details = """{"kind":"insulin","insulinType":"BOLUS","units":3.5}""",
            )
            insertV1Entry(
                db,
                timestamp = 400L,
                type = "MEAL",
                details = """{"kind":"meal","carbsGrams":45,"description":"Oats"}""",
            )
            insertV1Entry(
                db,
                timestamp = 500L,
                type = "ACTIVITY",
                details = """{"kind":"activity","durationMinutes":30,"intensity":"MODERATE"}""",
            )
            insertV1Entry(
                db,
                timestamp = 600L,
                type = "NOTE",
                note = "hello",
                details = """{"kind":"note"}""",
            )
            insertV1Entry(
                db,
                timestamp = 700L,
                type = "NOTE",
                details = "not-json",
            )
        }

        helper.runMigrationsAndValidate(name, 2, true, Migration1To2).use { db ->
            db.query(
                "SELECT timestamp, glucose_mgdl, meal_context, insulin_units, insulin_type, " +
                    "carbs_grams, meal_description, exercise_minutes, exercise_intensity, note " +
                    "FROM entries ORDER BY timestamp",
            ).use { c ->
                assertEquals(7, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(100L, c.getLong(0))
                assertEquals(110.0, c.getDouble(1), 0.0)
                assertEquals("BEFORE_MEAL", c.getString(2))

                assertTrue(c.moveToNext())
                assertEquals(200L, c.getLong(0))
                assertEquals(1.2, c.getDouble(3), 0.0)
                assertEquals("BASAL", c.getString(4))

                assertTrue(c.moveToNext())
                assertEquals(300L, c.getLong(0))
                assertEquals(3.5, c.getDouble(3), 0.0)
                assertEquals("BOLUS", c.getString(4))

                assertTrue(c.moveToNext())
                assertEquals(400L, c.getLong(0))
                assertEquals(45, c.getInt(5))
                assertEquals("Oats", c.getString(6))

                assertTrue(c.moveToNext())
                assertEquals(500L, c.getLong(0))
                assertEquals(30, c.getInt(7))
                assertEquals("MODERATE", c.getString(8))

                assertTrue(c.moveToNext())
                assertEquals(600L, c.getLong(0))
                assertEquals("hello", c.getString(9))

                assertTrue(c.moveToNext())
                assertEquals(700L, c.getLong(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    fun migrate2To3_splitsBasalAndBolus() {
        val name = "audit-2-3"
        helper.createDatabase(name, 2).use { db ->
            db.execSQL(
                "INSERT INTO entries (timestamp, insulin_units, insulin_type) VALUES (?, ?, ?)",
                arrayOf<Any?>(1L, 1.2, "BASAL"),
            )
            db.execSQL(
                "INSERT INTO entries (timestamp, insulin_units, insulin_type) VALUES (?, ?, ?)",
                arrayOf<Any?>(2L, 3.5, "BOLUS"),
            )
        }
        helper.runMigrationsAndValidate(name, 3, true, Migration2To3).use { db ->
            db.query(
                "SELECT timestamp, insulin_basal_units, insulin_bolus_units FROM entries ORDER BY timestamp",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1.2, c.getDouble(1), 0.0)
                assertTrue(c.isNull(2))
                assertTrue(c.moveToNext())
                assertTrue(c.isNull(1))
                assertEquals(3.5, c.getDouble(2), 0.0)
            }
        }
    }

    @Test
    fun migrate1ToCurrent_preservesRowsAndMatchesCurrentSchema() {
        val name = "audit-1-to-current"
        helper.createDatabase(name, 1).use { db ->
            insertV1Entry(
                db,
                timestamp = 111L,
                type = "GLUCOSE",
                glucoseMgdl = 99.0,
                note = "wake",
                details = """{"kind":"glucose","context":"FASTING"}""",
            )
            db.execSQL(
                "INSERT INTO chat_messages (role, content, timestamp) VALUES (?, ?, ?)",
                arrayOf<Any?>("USER", "hi", 10L),
            )
            db.execSQL(
                "INSERT INTO pending_ai_queries (user_message_id, prompt, created_at) VALUES (?, ?, ?)",
                arrayOf<Any?>(1L, "what is my IOB?", 20L),
            )
        }

        helper.runMigrationsAndValidate(
            name,
            currentVersion,
            true,
            *GlucoseHeroDatabase.ALL_MIGRATIONS,
        ).use { db ->
            assertCurrentSchema(db)
            db.query(
                "SELECT timestamp, glucose_mgdl, meal_context, source, uuid, mood_score, end_time " +
                    "FROM entries",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(111L, c.getLong(0))
                assertEquals(99.0, c.getDouble(1), 0.0)
                assertEquals("FASTING", c.getString(2))
                assertEquals("MANUAL", c.getString(3))
                assertFalse(c.getString(4).isNullOrEmpty())
                assertTrue(c.isNull(5))
                assertTrue(c.isNull(6))
            }
            db.query("SELECT content FROM chat_messages").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("hi", c.getString(0))
            }
            db.query("SELECT prompt, ttl_seconds FROM pending_ai_queries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("what is my IOB?", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test
    fun migrate13ToCurrent_addsTtlSecondsDefaultZero() {
        val name = "audit-13-to-current"
        helper.createDatabase(name, 13).use { db ->
            db.execSQL(
                "INSERT INTO pending_ai_queries (user_message_id, prompt, created_at) VALUES (?, ?, ?)",
                arrayOf<Any?>(2L, "queued", 30L),
            )
        }
        val rest = GlucoseHeroDatabase.ALL_MIGRATIONS
            .filter { it.startVersion >= 13 }
            .toTypedArray()
        helper.runMigrationsAndValidate(name, currentVersion, true, *rest).use { db ->
            db.query("SELECT prompt, ttl_seconds FROM pending_ai_queries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("queued", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test
    fun createFromExportedCurrentSchema_doesNotNeedMigrations() {
        val name = "audit-exported-current"
        helper.createDatabase(name, currentVersion).use { db ->
            assertCurrentSchema(db)
        }
    }

    @Test
    fun emptyRoomCreate_skipsLegacyMigrationsAndOpensAtCurrentVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audit-fresh-${System.nanoTime()}"
        context.deleteDatabase(name)
        var ran = 0
        val counting = GlucoseHeroDatabase.ALL_MIGRATIONS.map { migration ->
            object : Migration(migration.startVersion, migration.endVersion) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    ran += 1
                    migration.migrate(db)
                }
            }
        }.toTypedArray()

        val db = Room.databaseBuilder(context, GlucoseHeroDatabase::class.java, name)
            .addMigrations(*counting)
            .build()
        try {
            val sqlite = db.openHelper.writableDatabase
            assertEquals(0, ran)
            assertEquals(currentVersion, sqlite.version)
            assertCurrentSchema(sqlite)
            sqlite.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { c ->
                assertTrue(c.moveToFirst())
                assertFalse(c.getString(0).isNullOrEmpty())
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun assertCurrentSchema(db: SupportSQLiteDatabase) {
        val tables = queryNames(
            db,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'android_%' AND name != 'room_master_table' ORDER BY name",
        )
        assertEquals(
            listOf(
                "chat_messages",
                "entries",
                "foods",
                "glucose_samples",
                "insight_cards",
                "pending_ai_queries",
                "supplies",
                "tag_analytics",
            ),
            tables,
        )
        val views = queryNames(
            db,
            "SELECT name FROM sqlite_master WHERE type = 'view' ORDER BY name",
        )
        assertEquals(listOf("glucose_readings"), views)

        val entryCols = queryNames(db, "SELECT name FROM pragma_table_info('entries')").toSet()
        assertTrue(entryCols.containsAll(listOf("uuid", "mood_score", "mood_label", "end_time", "hc_record_id")))

        val pendingCols = queryNames(db, "SELECT name FROM pragma_table_info('pending_ai_queries')")
        assertTrue(pendingCols.contains("ttl_seconds"))
    }

    private fun queryNames(db: SupportSQLiteDatabase, sql: String): List<String> {
        val names = mutableListOf<String>()
        db.query(sql).use { c ->
            val nameIndex = 0
            while (c.moveToNext()) {
                val value = c.getString(nameIndex)
                if (value != null) names += value
            }
        }
        return names
    }

    private fun insertV1Entry(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        type: String,
        glucoseMgdl: Double? = null,
        note: String? = null,
        details: String,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, type, glucose_mgdl, note, details) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>(timestamp, type, glucoseMgdl, note, details),
        )
    }
}
