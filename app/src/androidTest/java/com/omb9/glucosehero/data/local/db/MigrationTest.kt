package com.omb9.glucosehero.data.local.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.db.migration.Migration7To8
import com.omb9.glucosehero.data.local.db.migration.Migration8To9
import com.omb9.glucosehero.data.local.db.migration.Migration9To10
import com.omb9.glucosehero.data.local.db.migration.Migration10To11
import com.omb9.glucosehero.data.local.db.migration.Migration11To12
import com.omb9.glucosehero.data.local.db.migration.Migration12To13
import com.omb9.glucosehero.data.local.db.migration.Migration14To15
import com.omb9.glucosehero.data.local.db.migration.MigrationPendingAiTtl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GlucoseHeroDatabase::class.java,
    )

    @Test
    fun migrate7To8_preservesEntriesAndCreatesGlucoseReadingsView() {
        // 1. Seed a v7 database with rows exercising every nullable column combination.
        // Schema 7 nullable columns in entries:
        // glucose_mgdl, meal_context, insulin_basal_units, insulin_bolus_units,
        // carbs_grams, protein_grams, fat_grams, meal_description,
        // exercise_minutes, exercise_intensity, note.
        // Schema 7 nullable columns in supplies: replaced_at.
        helper.createDatabase(testDb, 7).use { db ->
            // Row 1: All 11 nullable columns are NULL (minimal entry).
            insertEntry(
                db,
                timestamp = 100_000L,
            )
            // Row 2: Pure glucose reading.
            insertEntry(
                db,
                timestamp = 200_000L,
                glucoseMgdl = 110.0,
            )
            // Row 3: Pure meal entry (context, macros, description).
            insertEntry(
                db,
                timestamp = 300_000L,
                mealContext = "BEFORE_MEAL",
                carbsGrams = 45,
                proteinGrams = 15,
                fatGrams = 5,
                mealDescription = "Breakfast",
            )
            // Row 4: Pure insulin entry (basal and bolus).
            insertEntry(
                db,
                timestamp = 400_000L,
                insulinBasalUnits = 1.2,
                insulinBolusUnits = 3.5,
            )
            // Row 5: Pure exercise entry (minutes and intensity).
            insertEntry(
                db,
                timestamp = 500_000L,
                exerciseMinutes = 45,
                exerciseIntensity = "INTENSE",
            )
            // Row 6: Pure note entry.
            insertEntry(
                db,
                timestamp = 600_000L,
                note = "Felt dizzy",
            )
            // Row 7: Fully-populated row exercising all 11 nullable columns non-null.
            insertEntry(
                db,
                timestamp = 700_000L,
                glucoseMgdl = 180.0,
                mealContext = "AFTER_MEAL",
                insulinBasalUnits = 0.8,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = 30,
                exerciseIntensity = "MODERATE",
                note = "walk",
            )
            // Row 8: Glucose + note partial combination.
            insertEntry(
                db,
                timestamp = 800_000L,
                glucoseMgdl = 95.0,
                note = "Bedtime check",
            )

            // Supplies in v7: active supply (replaced_at = null) and replaced supply (replaced_at != null).
            insertSupply(db, type = "SENSOR", startedAt = 1_000L, expectedLifespanDays = 10, replacedAt = null)
            insertSupply(db, type = "PUMP_SITE", startedAt = 2_000L, expectedLifespanDays = 3, replacedAt = 2_010L)
        }

        // Migrate 7 -> 8 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 8, true, Migration7To8)

        // 2. Seeded rows survive with correct values; new columns default correctly.
        db.query(
            "SELECT timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, source, hc_record_id " +
                "FROM entries ORDER BY timestamp"
        ).use { c ->
            assertEquals(8, c.count)

            val timestamp = c.getColumnIndexOrThrow("timestamp")
            val glucose = c.getColumnIndexOrThrow("glucose_mgdl")
            val mealContext = c.getColumnIndexOrThrow("meal_context")
            val basal = c.getColumnIndexOrThrow("insulin_basal_units")
            val bolus = c.getColumnIndexOrThrow("insulin_bolus_units")
            val carbs = c.getColumnIndexOrThrow("carbs_grams")
            val protein = c.getColumnIndexOrThrow("protein_grams")
            val fat = c.getColumnIndexOrThrow("fat_grams")
            val mealDescription = c.getColumnIndexOrThrow("meal_description")
            val exerciseMinutes = c.getColumnIndexOrThrow("exercise_minutes")
            val exerciseIntensity = c.getColumnIndexOrThrow("exercise_intensity")
            val note = c.getColumnIndexOrThrow("note")
            val source = c.getColumnIndexOrThrow("source")
            val hcRecordId = c.getColumnIndexOrThrow("hc_record_id")

            // Row 1: All-null row.
            assertTrue(c.moveToFirst())
            assertEquals(100_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertTrue(c.isNull(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertTrue(c.isNull(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 2: Glucose-only row.
            assertTrue(c.moveToNext())
            assertEquals(200_000L, c.getLong(timestamp))
            assertEquals(110.0, c.getDouble(glucose), 0.0)
            assertTrue(c.isNull(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertTrue(c.isNull(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 3: Meal-only row.
            assertTrue(c.moveToNext())
            assertEquals(300_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertEquals("BEFORE_MEAL", c.getString(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertEquals(45, c.getInt(carbs))
            assertEquals(15, c.getInt(protein))
            assertEquals(5, c.getInt(fat))
            assertEquals("Breakfast", c.getString(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertTrue(c.isNull(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 4: Insulin-only row.
            assertTrue(c.moveToNext())
            assertEquals(400_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertTrue(c.isNull(mealContext))
            assertEquals(1.2, c.getDouble(basal), 0.0)
            assertEquals(3.5, c.getDouble(bolus), 0.0)
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertTrue(c.isNull(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 5: Exercise-only row.
            assertTrue(c.moveToNext())
            assertEquals(500_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertTrue(c.isNull(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertEquals(45, c.getInt(exerciseMinutes))
            assertEquals("INTENSE", c.getString(exerciseIntensity))
            assertTrue(c.isNull(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 6: Note-only row.
            assertTrue(c.moveToNext())
            assertEquals(600_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertTrue(c.isNull(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertEquals("Felt dizzy", c.getString(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 7: Fully-populated row.
            assertTrue(c.moveToNext())
            assertEquals(700_000L, c.getLong(timestamp))
            assertEquals(180.0, c.getDouble(glucose), 0.0)
            assertEquals("AFTER_MEAL", c.getString(mealContext))
            assertEquals(0.8, c.getDouble(basal), 0.0)
            assertEquals(4.5, c.getDouble(bolus), 0.0)
            assertEquals(40, c.getInt(carbs))
            assertEquals(20, c.getInt(protein))
            assertEquals(10, c.getInt(fat))
            assertEquals("Lunch", c.getString(mealDescription))
            assertEquals(30, c.getInt(exerciseMinutes))
            assertEquals("MODERATE", c.getString(exerciseIntensity))
            assertEquals("walk", c.getString(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            // Row 8: Glucose + note row.
            assertTrue(c.moveToNext())
            assertEquals(800_000L, c.getLong(timestamp))
            assertEquals(95.0, c.getDouble(glucose), 0.0)
            assertTrue(c.isNull(mealContext))
            assertTrue(c.isNull(basal))
            assertTrue(c.isNull(bolus))
            assertTrue(c.isNull(carbs))
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(mealDescription))
            assertTrue(c.isNull(exerciseMinutes))
            assertTrue(c.isNull(exerciseIntensity))
            assertEquals("Bedtime check", c.getString(note))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))

            assertFalse(c.moveToNext())
        }

        // 3. Supplies survive with replaced_at preserved (null vs non-null).
        db.query("SELECT id, type, started_at, expected_lifespan_days, replaced_at FROM supplies ORDER BY id").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("SENSOR", c.getString(1))
            assertEquals(1_000L, c.getLong(2))
            assertEquals(10, c.getInt(3))
            assertTrue(c.isNull(4))

            assertTrue(c.moveToNext())
            assertEquals("PUMP_SITE", c.getString(1))
            assertEquals(2_000L, c.getLong(2))
            assertEquals(3, c.getInt(3))
            assertEquals(2_010L, c.getLong(4))
        }

        // 4. The view exposes exactly the 3 glucose-carrying entries, excluding non-glucose rows.
        db.query("SELECT timestamp, glucose_mgdl FROM glucose_readings ORDER BY timestamp").use { c ->
            assertEquals(3, c.count)
            assertTrue(c.moveToFirst())
            assertEquals(200_000L, c.getLong(0))
            assertEquals(110.0, c.getDouble(1), 0.0)
            assertTrue(c.moveToNext())
            assertEquals(700_000L, c.getLong(0))
            assertEquals(180.0, c.getDouble(1), 0.0)
            assertTrue(c.moveToNext())
            assertEquals(800_000L, c.getLong(0))
            assertEquals(95.0, c.getDouble(1), 0.0)
            assertFalse(c.moveToNext())
        }

        // 5. A freshly-inserted device sample appears in the view.
        insertSample(db, timestamp = 900_000L, glucoseMgdl = 102.0, hcRecordId = "hc-sample-1")
        db.query("SELECT timestamp, glucose_mgdl FROM glucose_readings ORDER BY timestamp").use { c ->
            assertEquals(4, c.count)
            assertTrue(c.moveToLast())
            assertEquals(900_000L, c.getLong(0))
            assertEquals(102.0, c.getDouble(1), 0.0)
        }

        // 6. The unique index on hc_record_id rejects a duplicate import.
        assertThrows(SQLiteConstraintException::class.java) {
            insertSample(db, timestamp = 900_002L, glucoseMgdl = 96.0, hcRecordId = "hc-sample-1")
        }
    }


    @Test
    fun migrate8To9_backfillsUuidAndCreatesFoodsTable() {
        // 1. Seed a v8 database exercising all nullable columns across entries, supplies, and glucose_samples.
        // In v8 entries: glucose_mgdl, meal_context, insulin_basal_units, insulin_bolus_units,
        // carbs_grams, protein_grams, fat_grams, meal_description, exercise_minutes,
        // exercise_intensity, note, hc_record_id.
        // In v8 supplies: replaced_at.
        // In v8 glucose_samples: source_package.
        helper.createDatabase(testDb, 8).use { db ->
            // Row 1: All nullable columns null.
            insertEntryV8(db, timestamp = 100_000L)
            // Row 2: Glucose only (manual).
            insertEntryV8(db, timestamp = 200_000L, glucoseMgdl = 110.0)
            // Row 3: Health Connect imported glucose with non-null hc_record_id and source.
            insertEntryV8(
                db,
                timestamp = 300_000L,
                glucoseMgdl = 125.0,
                source = "HEALTH_CONNECT",
                hcRecordId = "hc-entry-1",
            )
            // Row 4: Meal only.
            insertEntryV8(
                db,
                timestamp = 400_000L,
                mealContext = "AFTER_MEAL",
                carbsGrams = 50,
                proteinGrams = 25,
                fatGrams = 12,
                mealDescription = "Dinner",
            )
            // Row 5: Insulin only.
            insertEntryV8(
                db,
                timestamp = 500_000L,
                insulinBasalUnits = 1.0,
                insulinBolusUnits = 5.0,
            )
            // Row 6: Exercise only.
            insertEntryV8(
                db,
                timestamp = 600_000L,
                exerciseMinutes = 20,
                exerciseIntensity = "LIGHT",
                note = "Evening walk",
            )
            // Row 7: Fully-populated row.
            insertEntryV8(
                db,
                timestamp = 700_000L,
                glucoseMgdl = 180.0,
                mealContext = "AFTER_MEAL",
                insulinBasalUnits = 0.8,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = 30,
                exerciseIntensity = "MODERATE",
                note = "walk",
            )

            // Supplies: active (replaced_at = null) vs replaced (replaced_at non-null).
            insertSupply(db, type = "SENSOR", startedAt = 1_000L, expectedLifespanDays = 10, replacedAt = null)
            insertSupply(db, type = "PUMP_SITE", startedAt = 2_000L, expectedLifespanDays = 3, replacedAt = 2_010L)

            // Glucose samples: source_package non-null vs null.
            insertSample(
                db,
                timestamp = 10_000L,
                glucoseMgdl = 95.0,
                hcRecordId = "hc-samp-1",
                sourcePackage = "com.dexcom.g7",
            )
            insertSample(
                db,
                timestamp = 20_000L,
                glucoseMgdl = 105.0,
                hcRecordId = "hc-samp-2",
                sourcePackage = null,
            )
        }

        // Migrate 8 -> 9 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 9, true, Migration8To9)

        // 2. Every migrated entry has a distinct, non-empty uuid and null food_id, and preserves all columns.
        val entryUuids = mutableListOf<String>()
        db.query(
            "SELECT timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, source, hc_record_id, food_id, uuid " +
                "FROM entries ORDER BY timestamp"
        ).use { c ->
            assertEquals(7, c.count)
            val timestampCol = c.getColumnIndexOrThrow("timestamp")
            val glucoseCol = c.getColumnIndexOrThrow("glucose_mgdl")
            val sourceCol = c.getColumnIndexOrThrow("source")
            val hcRecordIdCol = c.getColumnIndexOrThrow("hc_record_id")
            val foodIdCol = c.getColumnIndexOrThrow("food_id")
            val uuidCol = c.getColumnIndexOrThrow("uuid")

            // Row 1: all-null entry.
            assertTrue(c.moveToFirst())
            assertEquals(100_000L, c.getLong(timestampCol))
            assertTrue(c.isNull(glucoseCol))
            assertEquals("MANUAL", c.getString(sourceCol))
            assertTrue(c.isNull(hcRecordIdCol))
            assertTrue(c.isNull(foodIdCol))
            val u1 = c.getString(uuidCol)
            assertFalse(u1.isNullOrEmpty())
            entryUuids += u1

            // Row 2: glucose-only.
            assertTrue(c.moveToNext())
            assertEquals(200_000L, c.getLong(timestampCol))
            assertEquals(110.0, c.getDouble(glucoseCol), 0.0)
            assertEquals("MANUAL", c.getString(sourceCol))
            assertTrue(c.isNull(hcRecordIdCol))
            assertTrue(c.isNull(foodIdCol))
            val u2 = c.getString(uuidCol)
            assertFalse(u2.isNullOrEmpty())
            entryUuids += u2

            // Row 3: Health Connect provenance preserved.
            assertTrue(c.moveToNext())
            assertEquals(300_000L, c.getLong(timestampCol))
            assertEquals(125.0, c.getDouble(glucoseCol), 0.0)
            assertEquals("HEALTH_CONNECT", c.getString(sourceCol))
            assertEquals("hc-entry-1", c.getString(hcRecordIdCol))
            assertTrue(c.isNull(foodIdCol))
            val u3 = c.getString(uuidCol)
            assertFalse(u3.isNullOrEmpty())
            entryUuids += u3

            // Rows 4-6: meal, insulin, exercise.
            assertTrue(c.moveToNext())
            assertEquals(400_000L, c.getLong(timestampCol))
            val u4 = c.getString(uuidCol)
            assertFalse(u4.isNullOrEmpty())
            entryUuids += u4

            assertTrue(c.moveToNext())
            assertEquals(500_000L, c.getLong(timestampCol))
            val u5 = c.getString(uuidCol)
            assertFalse(u5.isNullOrEmpty())
            entryUuids += u5

            assertTrue(c.moveToNext())
            assertEquals(600_000L, c.getLong(timestampCol))
            val u6 = c.getString(uuidCol)
            assertFalse(u6.isNullOrEmpty())
            entryUuids += u6

            // Row 7: Fully populated.
            assertTrue(c.moveToNext())
            assertEquals(700_000L, c.getLong(timestampCol))
            assertEquals(180.0, c.getDouble(glucoseCol), 0.0)
            assertEquals("AFTER_MEAL", c.getString(c.getColumnIndexOrThrow("meal_context")))
            assertEquals(0.8, c.getDouble(c.getColumnIndexOrThrow("insulin_basal_units")), 0.0)
            assertEquals(4.5, c.getDouble(c.getColumnIndexOrThrow("insulin_bolus_units")), 0.0)
            assertEquals(40, c.getInt(c.getColumnIndexOrThrow("carbs_grams")))
            assertEquals(20, c.getInt(c.getColumnIndexOrThrow("protein_grams")))
            assertEquals(10, c.getInt(c.getColumnIndexOrThrow("fat_grams")))
            assertEquals("Lunch", c.getString(c.getColumnIndexOrThrow("meal_description")))
            assertEquals(30, c.getInt(c.getColumnIndexOrThrow("exercise_minutes")))
            assertEquals("MODERATE", c.getString(c.getColumnIndexOrThrow("exercise_intensity")))
            assertEquals("walk", c.getString(c.getColumnIndexOrThrow("note")))
            assertEquals("MANUAL", c.getString(sourceCol))
            assertTrue(c.isNull(hcRecordIdCol))
            assertTrue(c.isNull(foodIdCol))
            val u7 = c.getString(uuidCol)
            assertFalse(u7.isNullOrEmpty())
            entryUuids += u7
        }

        // 3. Every migrated supply has a distinct, non-empty uuid and preserves replaced_at.
        val supplyUuids = mutableListOf<String>()
        db.query("SELECT id, type, started_at, expected_lifespan_days, replaced_at, uuid FROM supplies ORDER BY id").use { c ->
            assertEquals(2, c.count)
            val replacedAtCol = c.getColumnIndexOrThrow("replaced_at")
            val uuidCol = c.getColumnIndexOrThrow("uuid")

            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(replacedAtCol))
            val s1 = c.getString(uuidCol)
            assertFalse(s1.isNullOrEmpty())
            supplyUuids += s1

            assertTrue(c.moveToNext())
            assertEquals(2_010L, c.getLong(replacedAtCol))
            val s2 = c.getString(uuidCol)
            assertFalse(s2.isNullOrEmpty())
            supplyUuids += s2
        }

        // 4. All uuids across both tables are distinct from each other.
        val allUuids = entryUuids + supplyUuids
        assertEquals(allUuids.size, allUuids.toSet().size)

        // 5. Existing glucose_samples survive with source_package preserved.
        db.query("SELECT hc_record_id, source_package FROM glucose_samples ORDER BY timestamp").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("hc-samp-1", c.getString(0))
            assertEquals("com.dexcom.g7", c.getString(1))
            assertTrue(c.moveToNext())
            assertEquals("hc-samp-2", c.getString(0))
            assertTrue(c.isNull(1))
        }

        // 6. The unique index on entries.uuid rejects a duplicate.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO entries (timestamp, uuid) VALUES (?, ?)",
                arrayOf<Any?>(99_000_000L, entryUuids.first()),
            )
        }

        // 7. The unique index on supplies.uuid rejects a duplicate.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO supplies (type, started_at, expected_lifespan_days, uuid) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("SENSOR", 99_000L, 10, supplyUuids.first()),
            )
        }

        // 8. entries.food_id accepts a value post-migration.
        db.execSQL(
            "UPDATE entries SET food_id = ? WHERE timestamp = ?",
            arrayOf<Any?>(42L, 200_000L),
        )
        db.query("SELECT food_id FROM entries WHERE timestamp = 200000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42L, c.getLong(0))
        }

        // 9. The new foods table accepts inserts exercising all nullable columns non-null and null.
        // Food A: all nullable columns populated (including off_fetched_at).
        insertFood(
            db,
            id = 1L,
            uuid = "11111111-1111-4111-a111-111111111111",
            name = "Oatmeal",
            brand = "Brand X",
            barcode = "1234567890123",
            carbsGrams = 27.0,
            proteinGrams = 5.0,
            fatGrams = 3.0,
            kcal = 160.0,
            servingGrams = 40.0,
            servingLabel = "1 cup",
            source = "OPEN_FOOD_FACTS",
            offFetchedAt = 1_000_000L,
            userCorrected = 0,
            useCount = 1,
            lastUsedAt = 5_000_000L,
            isFavorite = 1,
            createdAt = 4_000_000L,
        )
        // Food B: all nullable columns null.
        insertFood(
            db,
            id = 2L,
            uuid = "22222222-2222-4222-a222-222222222222",
            name = "Banana",
            carbsGrams = 23.0,
            source = "MANUAL",
            createdAt = 4_000_001L,
        )
        db.query("SELECT COUNT(*) FROM foods").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        // Unique index on foods.barcode rejects duplicate barcode.
        assertThrows(SQLiteConstraintException::class.java) {
            insertFood(
                db,
                id = 3L,
                uuid = "33333333-3333-4333-a333-333333333333",
                name = "Duplicate Barcode Food",
                barcode = "1234567890123",
                carbsGrams = 10.0,
                source = "MANUAL",
                createdAt = 4_000_002L,
            )
        }
    }

    @Test
    fun migrate9To10_createsTagAnalyticsTableAndPreservesEntriesAndFoods() {
        // 1. Seed a v9 database with foods, entries, supplies, and samples exercising every nullable column.
        // In v9 foods: brand, barcode, protein_grams, fat_grams, kcal, serving_grams,
        // serving_label, off_fetched_at, last_used_at.
        // In v9 entries: glucose_mgdl, meal_context, insulin_basal_units, insulin_bolus_units,
        // carbs_grams, protein_grams, fat_grams, meal_description, exercise_minutes,
        // exercise_intensity, note, food_id, hc_record_id.
        // In v9 supplies: replaced_at.
        // In v9 glucose_samples: source_package.
        helper.createDatabase(testDb, 9).use { db ->
            // Food 1: Manual food with all nullable fields populated except off_fetched_at.
            insertFood(
                db,
                id = 1L,
                uuid = "11111111-1111-4111-a111-111111111111",
                name = "Oatmeal",
                brand = "Brand X",
                barcode = "1234567890123",
                carbsGrams = 27.0,
                proteinGrams = 5.0,
                fatGrams = 3.0,
                kcal = 160.0,
                servingGrams = 40.0,
                servingLabel = "1 cup",
                source = "MANUAL",
                offFetchedAt = null,
                userCorrected = 0,
                useCount = 1,
                lastUsedAt = 5_000_000L,
                isFavorite = 1,
                createdAt = 4_000_000L,
            )
            // Food 2: Minimal food with every nullable column null.
            insertFood(
                db,
                id = 2L,
                uuid = "22222222-2222-4222-a222-222222222222",
                name = "Banana",
                carbsGrams = 23.0,
                source = "MANUAL",
                createdAt = 4_000_001L,
            )
            // Food 3: Open Food Facts product with non-null off_fetched_at and barcode.
            insertFood(
                db,
                id = 3L,
                uuid = "33333333-3333-4333-a333-333333333333",
                name = "Greek Yogurt",
                brand = "Dairy Co",
                barcode = "9876543210987",
                carbsGrams = 15.0,
                proteinGrams = 10.0,
                fatGrams = 4.0,
                kcal = 140.0,
                servingGrams = 150.0,
                servingLabel = "1 pot",
                source = "OPEN_FOOD_FACTS",
                offFetchedAt = 4_500_000L,
                userCorrected = 1,
                useCount = 3,
                lastUsedAt = 4_800_000L,
                isFavorite = 0,
                createdAt = 4_000_002L,
            )

            // Entry 1: Minimal entry (all nullable null).
            insertEntryV9(
                db,
                timestamp = 100_000L,
                uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            )
            // Entry 2: Glucose only (manual).
            insertEntryV9(
                db,
                timestamp = 200_000L,
                glucoseMgdl = 110.0,
                uuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            )
            // Entry 3: Health Connect imported glucose (hc_record_id non-null, source = HEALTH_CONNECT).
            insertEntryV9(
                db,
                timestamp = 300_000L,
                glucoseMgdl = 125.0,
                source = "HEALTH_CONNECT",
                hcRecordId = "hc-entry-1",
                uuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
            )
            // Entry 4: Meal entry referencing Food 1 (food_id = 1L) with all metrics populated.
            insertEntryV9(
                db,
                timestamp = 400_000L,
                glucoseMgdl = 180.0,
                mealContext = "AFTER_MEAL",
                insulinBasalUnits = 0.8,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = 30,
                exerciseIntensity = "MODERATE",
                note = "walk",
                foodId = 1L,
                uuid = "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
            )
            // Entry 5: Meal entry referencing Food 3 (food_id = 3L).
            insertEntryV9(
                db,
                timestamp = 500_000L,
                carbsGrams = 15,
                foodId = 3L,
                uuid = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
            )
            // Entry 6: Exercise only.
            insertEntryV9(
                db,
                timestamp = 600_000L,
                exerciseMinutes = 15,
                exerciseIntensity = "LIGHT",
                note = "stretch",
                uuid = "ffffffff-ffff-4fff-8fff-ffffffffffff",
            )
            // Entry 7: Note only.
            insertEntryV9(
                db,
                timestamp = 700_000L,
                note = "Headache",
                uuid = "gggggggg-gggg-4ggg-8ggg-gggggggggggg",
            )
            // Entry 8: Insulin only.
            insertEntryV9(
                db,
                timestamp = 800_000L,
                insulinBasalUnits = 1.5,
                insulinBolusUnits = 4.0,
                uuid = "hhhhhhhh-hhhh-4hhh-8hhh-hhhhhhhhhhhh",
            )

            // Supplies: active (replaced_at null) vs replaced (replaced_at non-null).
            insertSupplyV9(
                db,
                uuid = "supp-uuid-1",
                type = "SENSOR",
                startedAt = 1_000L,
                expectedLifespanDays = 10,
                replacedAt = null,
            )
            insertSupplyV9(
                db,
                uuid = "supp-uuid-2",
                type = "PUMP_SITE",
                startedAt = 2_000L,
                expectedLifespanDays = 3,
                replacedAt = 2_010L,
            )

            // Glucose samples: source_package non-null vs null.
            insertSample(
                db,
                timestamp = 10_000L,
                glucoseMgdl = 95.0,
                hcRecordId = "hc-samp-1",
                sourcePackage = "com.dexcom.g7",
            )
            insertSample(
                db,
                timestamp = 20_000L,
                glucoseMgdl = 105.0,
                hcRecordId = "hc-samp-2",
                sourcePackage = null,
            )
        }

        // Migrate 9 -> 10 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 10, true, Migration9To10)

        // 2. The new tag_analytics table exists.
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'tag_analytics'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("tag_analytics", c.getString(0))
        }

        // 3. tag_analytics accepts both non-null and null for its nullable columns (food_id, avg_carbs, avg_bolus).
        db.execSQL(
            "INSERT INTO tag_analytics " +
                "(tag, kind, food_id, occurrences, median_delta_mgdl, p25_delta_mgdl, " +
                "p75_delta_mgdl, avg_carbs_grams, avg_bolus_units, last_seen_at, computed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                "oatmeal",
                "FOOD",
                1L,
                5,
                10.0,
                2.0,
                18.0,
                27.0,
                0.8,
                6_000_000L,
                6_000_001L,
            ),
        )
        db.execSQL(
            "INSERT INTO tag_analytics " +
                "(tag, kind, food_id, occurrences, median_delta_mgdl, p25_delta_mgdl, " +
                "p75_delta_mgdl, avg_carbs_grams, avg_bolus_units, last_seen_at, computed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                "walk",
                "HASHTAG",
                null,
                2,
                -15.0,
                -25.0,
                -5.0,
                null,
                null,
                7_000_000L,
                7_000_001L,
            ),
        )
        db.query("SELECT COUNT(*) FROM tag_analytics").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        // 4. The unique index on tag rejects a duplicate tag.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO tag_analytics " +
                    "(tag, kind, occurrences, median_delta_mgdl, p25_delta_mgdl, " +
                    "p75_delta_mgdl, last_seen_at, computed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("oatmeal", "HASHTAG", 1, 1.0, 1.0, 1.0, 8_000_000L, 8_000_001L),
            )
        }

        // 5. Existing entries survive untouched.
        db.query(
            "SELECT timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, food_id, source, hc_record_id, uuid " +
                "FROM entries ORDER BY timestamp"
        ).use { c ->
            assertEquals(8, c.count)

            val timestamp = c.getColumnIndexOrThrow("timestamp")
            val glucose = c.getColumnIndexOrThrow("glucose_mgdl")
            val mealContext = c.getColumnIndexOrThrow("meal_context")
            val basal = c.getColumnIndexOrThrow("insulin_basal_units")
            val bolus = c.getColumnIndexOrThrow("insulin_bolus_units")
            val carbs = c.getColumnIndexOrThrow("carbs_grams")
            val protein = c.getColumnIndexOrThrow("protein_grams")
            val fat = c.getColumnIndexOrThrow("fat_grams")
            val mealDescription = c.getColumnIndexOrThrow("meal_description")
            val exerciseMinutes = c.getColumnIndexOrThrow("exercise_minutes")
            val exerciseIntensity = c.getColumnIndexOrThrow("exercise_intensity")
            val note = c.getColumnIndexOrThrow("note")
            val foodId = c.getColumnIndexOrThrow("food_id")
            val source = c.getColumnIndexOrThrow("source")
            val hcRecordId = c.getColumnIndexOrThrow("hc_record_id")
            val uuid = c.getColumnIndexOrThrow("uuid")

            // Entry 1: Minimal all-null row.
            assertTrue(c.moveToFirst())
            assertEquals(100_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertTrue(c.isNull(foodId))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("MANUAL", c.getString(source))
            assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", c.getString(uuid))

            // Entry 2: Glucose-only row.
            assertTrue(c.moveToNext())
            assertEquals(200_000L, c.getLong(timestamp))
            assertEquals(110.0, c.getDouble(glucose), 0.0)
            assertTrue(c.isNull(foodId))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("MANUAL", c.getString(source))
            assertEquals("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", c.getString(uuid))

            // Entry 3: Health Connect row.
            assertTrue(c.moveToNext())
            assertEquals(300_000L, c.getLong(timestamp))
            assertEquals(125.0, c.getDouble(glucose), 0.0)
            assertEquals("HEALTH_CONNECT", c.getString(source))
            assertEquals("hc-entry-1", c.getString(hcRecordId))
            assertTrue(c.isNull(foodId))
            assertEquals("cccccccc-cccc-4ccc-8ccc-cccccccccccc", c.getString(uuid))

            // Entry 4: Fully-populated row referencing Food 1.
            assertTrue(c.moveToNext())
            assertEquals(400_000L, c.getLong(timestamp))
            assertEquals(180.0, c.getDouble(glucose), 0.0)
            assertEquals("AFTER_MEAL", c.getString(mealContext))
            assertEquals(0.8, c.getDouble(basal), 0.0)
            assertEquals(4.5, c.getDouble(bolus), 0.0)
            assertEquals(40, c.getInt(carbs))
            assertEquals(20, c.getInt(protein))
            assertEquals(10, c.getInt(fat))
            assertEquals("Lunch", c.getString(mealDescription))
            assertEquals(30, c.getInt(exerciseMinutes))
            assertEquals("MODERATE", c.getString(exerciseIntensity))
            assertEquals("walk", c.getString(note))
            assertEquals(1L, c.getLong(foodId))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("dddddddd-dddd-4ddd-8ddd-dddddddddddd", c.getString(uuid))

            // Entry 5: Carbs referencing Food 3.
            assertTrue(c.moveToNext())
            assertEquals(500_000L, c.getLong(timestamp))
            assertTrue(c.isNull(glucose))
            assertEquals(15, c.getInt(carbs))
            assertEquals(3L, c.getLong(foodId))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", c.getString(uuid))

            // Entry 6: Exercise only.
            assertTrue(c.moveToNext())
            assertEquals(600_000L, c.getLong(timestamp))
            assertEquals(15, c.getInt(exerciseMinutes))
            assertEquals("LIGHT", c.getString(exerciseIntensity))
            assertEquals("stretch", c.getString(note))
            assertTrue(c.isNull(foodId))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("ffffffff-ffff-4fff-8fff-ffffffffffff", c.getString(uuid))

            // Entry 7: Note only.
            assertTrue(c.moveToNext())
            assertEquals(700_000L, c.getLong(timestamp))
            assertEquals("Headache", c.getString(note))
            assertTrue(c.isNull(foodId))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("gggggggg-gggg-4ggg-8ggg-gggggggggggg", c.getString(uuid))

            // Entry 8: Insulin only.
            assertTrue(c.moveToNext())
            assertEquals(800_000L, c.getLong(timestamp))
            assertEquals(1.5, c.getDouble(basal), 0.0)
            assertEquals(4.0, c.getDouble(bolus), 0.0)
            assertTrue(c.isNull(foodId))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(hcRecordId))
            assertEquals("hhhhhhhh-hhhh-4hhh-8hhh-hhhhhhhhhhhh", c.getString(uuid))

            assertFalse(c.moveToNext())
        }

        // 6. Existing foods survive untouched (including off_fetched_at non-null vs null).
        db.query(
            "SELECT id, uuid, name, brand, barcode, carbs_grams, protein_grams, fat_grams, " +
                "kcal, serving_grams, serving_label, source, off_fetched_at, user_corrected, " +
                "use_count, last_used_at, is_favorite, created_at FROM foods ORDER BY id"
        ).use { c ->
            assertEquals(3, c.count)

            val id = c.getColumnIndexOrThrow("id")
            val uuid = c.getColumnIndexOrThrow("uuid")
            val name = c.getColumnIndexOrThrow("name")
            val brand = c.getColumnIndexOrThrow("brand")
            val barcode = c.getColumnIndexOrThrow("barcode")
            val carbs = c.getColumnIndexOrThrow("carbs_grams")
            val protein = c.getColumnIndexOrThrow("protein_grams")
            val fat = c.getColumnIndexOrThrow("fat_grams")
            val kcal = c.getColumnIndexOrThrow("kcal")
            val servingGrams = c.getColumnIndexOrThrow("serving_grams")
            val servingLabel = c.getColumnIndexOrThrow("serving_label")
            val source = c.getColumnIndexOrThrow("source")
            val offFetchedAt = c.getColumnIndexOrThrow("off_fetched_at")
            val userCorrected = c.getColumnIndexOrThrow("user_corrected")
            val useCount = c.getColumnIndexOrThrow("use_count")
            val lastUsedAt = c.getColumnIndexOrThrow("last_used_at")
            val isFavorite = c.getColumnIndexOrThrow("is_favorite")
            val createdAt = c.getColumnIndexOrThrow("created_at")

            // Food 1: Fully-populated manual food.
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(id))
            assertEquals("11111111-1111-4111-a111-111111111111", c.getString(uuid))
            assertEquals("Oatmeal", c.getString(name))
            assertEquals("Brand X", c.getString(brand))
            assertEquals("1234567890123", c.getString(barcode))
            assertEquals(27.0, c.getDouble(carbs), 0.0)
            assertEquals(5.0, c.getDouble(protein), 0.0)
            assertEquals(3.0, c.getDouble(fat), 0.0)
            assertEquals(160.0, c.getDouble(kcal), 0.0)
            assertEquals(40.0, c.getDouble(servingGrams), 0.0)
            assertEquals("1 cup", c.getString(servingLabel))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(offFetchedAt))
            assertEquals(0, c.getInt(userCorrected))
            assertEquals(1, c.getInt(useCount))
            assertEquals(5_000_000L, c.getLong(lastUsedAt))
            assertEquals(1, c.getInt(isFavorite))
            assertEquals(4_000_000L, c.getLong(createdAt))

            // Food 2: Minimal food exercising all nullable columns as null.
            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(id))
            assertEquals("22222222-2222-4222-a222-222222222222", c.getString(uuid))
            assertEquals("Banana", c.getString(name))
            assertTrue(c.isNull(brand))
            assertTrue(c.isNull(barcode))
            assertEquals(23.0, c.getDouble(carbs), 0.0)
            assertTrue(c.isNull(protein))
            assertTrue(c.isNull(fat))
            assertTrue(c.isNull(kcal))
            assertTrue(c.isNull(servingGrams))
            assertTrue(c.isNull(servingLabel))
            assertEquals("MANUAL", c.getString(source))
            assertTrue(c.isNull(offFetchedAt))
            assertEquals(0, c.getInt(userCorrected))
            assertEquals(0, c.getInt(useCount))
            assertTrue(c.isNull(lastUsedAt))
            assertEquals(0, c.getInt(isFavorite))
            assertEquals(4_000_001L, c.getLong(createdAt))

            // Food 3: Open Food Facts product with off_fetched_at non-null.
            assertTrue(c.moveToNext())
            assertEquals(3L, c.getLong(id))
            assertEquals("33333333-3333-4333-a333-333333333333", c.getString(uuid))
            assertEquals("Greek Yogurt", c.getString(name))
            assertEquals("Dairy Co", c.getString(brand))
            assertEquals("9876543210987", c.getString(barcode))
            assertEquals(15.0, c.getDouble(carbs), 0.0)
            assertEquals(10.0, c.getDouble(protein), 0.0)
            assertEquals(4.0, c.getDouble(fat), 0.0)
            assertEquals(140.0, c.getDouble(kcal), 0.0)
            assertEquals(150.0, c.getDouble(servingGrams), 0.0)
            assertEquals("1 pot", c.getString(servingLabel))
            assertEquals("OPEN_FOOD_FACTS", c.getString(source))
            assertEquals(4_500_000L, c.getLong(offFetchedAt))
            assertEquals(1, c.getInt(userCorrected))
            assertEquals(3, c.getInt(useCount))
            assertEquals(4_800_000L, c.getLong(lastUsedAt))
            assertEquals(0, c.getInt(isFavorite))
            assertEquals(4_000_002L, c.getLong(createdAt))

            assertFalse(c.moveToNext())
        }

        // 7. Existing supplies survive untouched.
        db.query("SELECT uuid, type, started_at, expected_lifespan_days, replaced_at FROM supplies ORDER BY id").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("supp-uuid-1", c.getString(0))
            assertEquals("SENSOR", c.getString(1))
            assertTrue(c.isNull(4))

            assertTrue(c.moveToNext())
            assertEquals("supp-uuid-2", c.getString(0))
            assertEquals("PUMP_SITE", c.getString(1))
            assertEquals(2_010L, c.getLong(4))
        }

        // 8. Existing glucose_samples survive untouched.
        db.query("SELECT hc_record_id, source_package FROM glucose_samples ORDER BY timestamp").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("hc-samp-1", c.getString(0))
            assertEquals("com.dexcom.g7", c.getString(1))

            assertTrue(c.moveToNext())
            assertEquals("hc-samp-2", c.getString(0))
            assertTrue(c.isNull(1))
        }

        // 9. The glucose_readings view continues to function.
        db.query("SELECT COUNT(*) FROM glucose_readings").use { c ->
            assertTrue(c.moveToFirst())
            // 2 samples + 2 entries with non-null glucose and null hc_record_id (Entry 2 & Entry 4).
            // (Entry 3 has hc_record_id non-null, so it is excluded from the view).
            assertEquals(4, c.getInt(0))
        }
    }

    @Test
    fun migrate10To11_addsMoodColumnsAndPreservesEntries() {
        // 1. Seed a v10 database. entries has not changed since v9 (mood is added in v11).
        helper.createDatabase(testDb, 10).use { db ->
            insertEntryV10(
                db,
                timestamp = 1_000_000L,
                glucoseMgdl = 110.0,
                uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            )
            insertEntryV10(
                db,
                timestamp = 2_000_000L,
                glucoseMgdl = 180.0,
                mealContext = "AFTER_MEAL",
                insulinBasalUnits = 0.8,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = 30,
                exerciseIntensity = "MODERATE",
                note = "walk",
                foodId = 1L,
                uuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
            )
            insertEntryV10(
                db,
                timestamp = 3_000_000L,
                exerciseMinutes = 15,
                exerciseIntensity = "LIGHT",
                note = "stretch",
                uuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
            )
        }

        // Migrate 10 -> 11 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 11, true, Migration10To11)

        // 2. Seeded rows survive with null mood values.
        db.query(
            "SELECT timestamp, note, mood_score, mood_label FROM entries ORDER BY timestamp"
        ).use { c ->
            assertEquals(3, c.count)

            val timestamp = c.getColumnIndexOrThrow("timestamp")
            val note = c.getColumnIndexOrThrow("note")
            val moodScore = c.getColumnIndexOrThrow("mood_score")
            val moodLabel = c.getColumnIndexOrThrow("mood_label")

            assertTrue(c.moveToFirst())
            assertEquals(1_000_000L, c.getLong(timestamp))
            assertTrue(c.isNull(note))
            assertTrue(c.isNull(moodScore))
            assertTrue(c.isNull(moodLabel))

            assertTrue(c.moveToNext())
            assertEquals(2_000_000L, c.getLong(timestamp))
            assertEquals("walk", c.getString(note))
            assertTrue(c.isNull(moodScore))
            assertTrue(c.isNull(moodLabel))

            assertTrue(c.moveToNext())
            assertEquals(3_000_000L, c.getLong(timestamp))
            assertEquals("stretch", c.getString(note))
            assertTrue(c.isNull(moodScore))
            assertTrue(c.isNull(moodLabel))

            assertFalse(c.moveToNext())
        }

        // 3. A post-migration insert accepts both new fields.
        db.execSQL(
            "INSERT INTO entries (timestamp, mood_score, mood_label) VALUES (?, ?, ?)",
            arrayOf<Any?>(4_000_000L, 4, "Calm"),
        )
        db.query("SELECT mood_score, mood_label FROM entries WHERE timestamp = 4000000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
            assertEquals("Calm", c.getString(1))
        }
    }

    @Test
    fun migrate11To12_createsUniqueIndexOnHcRecordIdAndCollapsesDuplicates() {
        // 1. Seed a v11 database with several NULL hc_record_id rows, one with
        // a value, and a duplicate of that value (to prove the cleanup runs).
        helper.createDatabase(testDb, 11).use { db ->
            insertEntryV11(db, timestamp = 1_000_000L, uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            insertEntryV11(db, timestamp = 2_000_000L, uuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
            insertEntryV11(db, timestamp = 3_000_000L, uuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc")
            insertEntryV11(
                db,
                timestamp = 4_000_000L,
                uuid = "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                hcRecordId = "hc-entry-1",
            )
            // Duplicate hc_record_id with a higher rowid; the migration keeps
            // the lowest id and deletes this one.
            insertEntryV11(
                db,
                timestamp = 5_000_000L,
                uuid = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
                hcRecordId = "hc-entry-1",
            )
        }

        // Migrate 11 -> 12 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 12, true, Migration11To12)

        // 2. All rows with a NULL hc_record_id survive and coexist; the single
        // non-null value survives with its duplicate collapsed to one row.
        db.query("SELECT COUNT(*) FROM entries").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
        }

        // 3. Multiple NULL hc_record_id rows coexist (unique index does not
        // collapse them).
        db.query("SELECT COUNT(*) FROM entries WHERE hc_record_id IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
        }

        // 4. The non-null value survives exactly once, keeping the lowest id.
        db.query("SELECT id FROM entries WHERE hc_record_id = 'hc-entry-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4L, c.getLong(0))
            assertFalse(c.moveToNext())
        }

        // 5. The unique index rejects a new duplicate hc_record_id insert.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO entries (timestamp, uuid, hc_record_id) VALUES (?, ?, ?)",
                arrayOf<Any?>(6_000_000L, "ffffffff-ffff-4fff-8fff-ffffffffffff", "hc-entry-1"),
            )
        }
    }

    @Test
    fun migrate12To13_addsEndTimeColumnAndPreservesEntries() {
        // 1. Seed a v12 database (schema 12 == schema 11 + unique hc_record_id index).
        helper.createDatabase(testDb, 12).use { db ->
            insertEntryV11(db, timestamp = 1_000_000L, uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            insertEntryV11(
                db,
                timestamp = 2_000_000L,
                uuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                hcRecordId = "hc-sleep-1",
            )
        }

        // Migrate 12 -> 13 and let Room validate the resulting schema.
        val db = helper.runMigrationsAndValidate(testDb, 13, true, Migration12To13)

        // 2. Seeded rows survive with a NULL end_time.
        db.query("SELECT timestamp, end_time FROM entries ORDER BY timestamp").use { c ->
            assertEquals(2, c.count)
            val timestamp = c.getColumnIndexOrThrow("timestamp")
            val endTime = c.getColumnIndexOrThrow("end_time")

            assertTrue(c.moveToFirst())
            assertEquals(1_000_000L, c.getLong(timestamp))
            assertTrue(c.isNull(endTime))

            assertTrue(c.moveToNext())
            assertEquals(2_000_000L, c.getLong(timestamp))
            assertTrue(c.isNull(endTime))
        }

        // 3. A post-migration insert accepts the new interval column.
        db.execSQL(
            "INSERT INTO entries (timestamp, end_time) VALUES (?, ?)",
            arrayOf<Any?>(3_000_000L, 3_000_000L + 28_800_000L),
        )
        db.query("SELECT end_time FROM entries WHERE timestamp = 3000000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3_000_000L + 28_800_000L, c.getLong(0))
        }
    }

    @Test
    fun migrate13To14_addsTtlSecondsAndExpiresExistingQueuedRows() {
        helper.createDatabase(testDb, 13).use { db ->
            db.execSQL(
                "INSERT INTO pending_ai_queries (user_message_id, prompt, created_at) VALUES (?, ?, ?)",
                arrayOf<Any?>(7L, "sugar is 65", 1_000L),
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 14, true, MigrationPendingAiTtl)

        db.query("SELECT user_message_id, prompt, created_at, ttl_seconds FROM pending_ai_queries").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(7L, c.getLong(c.getColumnIndexOrThrow("user_message_id")))
            assertEquals("sugar is 65", c.getString(c.getColumnIndexOrThrow("prompt")))
            assertEquals(1_000L, c.getLong(c.getColumnIndexOrThrow("created_at")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("ttl_seconds")))
        }
    }

    @Test
    fun migrate14To15_generalizesGlucoseSampleIdentity() {
        helper.createDatabase(testDb, 14).use { db ->
            insertSample(
                db,
                timestamp = 10_000L,
                glucoseMgdl = 95.0,
                hcRecordId = "hc-samp-1",
                sourcePackage = "com.dexcom.g7",
            )
            insertSample(
                db,
                timestamp = 20_000L,
                glucoseMgdl = 105.0,
                hcRecordId = "hc-samp-2",
                sourcePackage = null,
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 15, true, Migration14To15)

        db.query(
            "SELECT timestamp, glucose_mgdl, source, external_id, hc_record_id, trend_arrow, " +
                "source_package FROM glucose_samples ORDER BY timestamp",
        ).use { c ->
            assertEquals(2, c.count)
            val source = c.getColumnIndexOrThrow("source")
            val externalId = c.getColumnIndexOrThrow("external_id")
            val hcRecordId = c.getColumnIndexOrThrow("hc_record_id")
            val trendArrow = c.getColumnIndexOrThrow("trend_arrow")
            val sourcePackage = c.getColumnIndexOrThrow("source_package")

            assertTrue(c.moveToFirst())
            assertEquals(10_000L, c.getLong(c.getColumnIndexOrThrow("timestamp")))
            assertEquals(95.0, c.getDouble(c.getColumnIndexOrThrow("glucose_mgdl")), 0.0)
            assertEquals("HEALTH_CONNECT", c.getString(source))
            assertEquals("hc-samp-1", c.getString(externalId))
            assertEquals("hc-samp-1", c.getString(hcRecordId))
            assertTrue(c.isNull(trendArrow))
            assertEquals("com.dexcom.g7", c.getString(sourcePackage))

            assertTrue(c.moveToNext())
            assertEquals(20_000L, c.getLong(c.getColumnIndexOrThrow("timestamp")))
            assertEquals(105.0, c.getDouble(c.getColumnIndexOrThrow("glucose_mgdl")), 0.0)
            assertEquals("HEALTH_CONNECT", c.getString(source))
            assertEquals("hc-samp-2", c.getString(externalId))
            assertEquals("hc-samp-2", c.getString(hcRecordId))
            assertTrue(c.isNull(trendArrow))
            assertTrue(c.isNull(sourcePackage))

            assertFalse(c.moveToNext())
        }

        db.query("SELECT COUNT(*) FROM glucose_samples").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        db.execSQL(
            "INSERT OR IGNORE INTO glucose_samples " +
                "(timestamp, glucose_mgdl, source, external_id, hc_record_id, " +
                "source_package, recording_method, imported_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(30_000L, 99.0, "HEALTH_CONNECT", "hc-samp-1", "hc-samp-1", null, 1, 31_000L),
        )
        db.query("SELECT COUNT(*) FROM glucose_samples").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        insertSampleV15(
            db,
            timestamp = 40_000L,
            glucoseMgdl = 110.0,
            source = "NIGHTSCOUT",
            externalId = "x",
            hcRecordId = null,
        )
        insertSampleV15(
            db,
            timestamp = 50_000L,
            glucoseMgdl = 111.0,
            source = "XDRIP_BROADCAST",
            externalId = "x",
            hcRecordId = null,
        )
        db.query("SELECT COUNT(*) FROM glucose_samples").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
        }

        db.query(
            "SELECT source, external_id FROM glucose_samples WHERE external_id = 'x' ORDER BY source",
        ).use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("NIGHTSCOUT", c.getString(0))
            assertEquals("x", c.getString(1))
            assertTrue(c.moveToNext())
            assertEquals("XDRIP_BROADCAST", c.getString(0))
            assertEquals("x", c.getString(1))
        }
    }

    private fun insertEntry(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        glucoseMgdl: Double? = null,
        mealContext: String? = null,
        insulinBasalUnits: Double? = null,
        insulinBolusUnits: Double? = null,
        carbsGrams: Int? = null,
        proteinGrams: Int? = null,
        fatGrams: Int? = null,
        mealDescription: String? = null,
        exerciseMinutes: Int? = null,
        exerciseIntensity: String? = null,
        note: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                timestamp,
                glucoseMgdl,
                mealContext,
                insulinBasalUnits,
                insulinBolusUnits,
                carbsGrams,
                proteinGrams,
                fatGrams,
                mealDescription,
                exerciseMinutes,
                exerciseIntensity,
                note,
            ),
        )
    }

    private fun insertSupply(
        db: SupportSQLiteDatabase,
        type: String,
        startedAt: Long,
        expectedLifespanDays: Int,
        replacedAt: Long? = null,
    ) {
        db.execSQL(
            "INSERT INTO supplies (type, started_at, expected_lifespan_days, replaced_at) " +
                "VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(type, startedAt, expectedLifespanDays, replacedAt),
        )
    }

    private fun insertEntryV8(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        glucoseMgdl: Double? = null,
        mealContext: String? = null,
        insulinBasalUnits: Double? = null,
        insulinBolusUnits: Double? = null,
        carbsGrams: Int? = null,
        proteinGrams: Int? = null,
        fatGrams: Int? = null,
        mealDescription: String? = null,
        exerciseMinutes: Int? = null,
        exerciseIntensity: String? = null,
        note: String? = null,
        source: String = "MANUAL",
        hcRecordId: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, source, hc_record_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                timestamp,
                glucoseMgdl,
                mealContext,
                insulinBasalUnits,
                insulinBolusUnits,
                carbsGrams,
                proteinGrams,
                fatGrams,
                mealDescription,
                exerciseMinutes,
                exerciseIntensity,
                note,
                source,
                hcRecordId,
            ),
        )
    }

    private fun insertSupplyV9(
        db: SupportSQLiteDatabase,
        uuid: String,
        type: String,
        startedAt: Long,
        expectedLifespanDays: Int,
        replacedAt: Long? = null,
    ) {
        db.execSQL(
            "INSERT INTO supplies (uuid, type, started_at, expected_lifespan_days, replaced_at) " +
                "VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>(uuid, type, startedAt, expectedLifespanDays, replacedAt),
        )
    }

    private fun insertSample(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        glucoseMgdl: Double,
        hcRecordId: String,
        sourcePackage: String? = "com.dexcom.g7",
    ) {
        db.execSQL(
            "INSERT INTO glucose_samples " +
                "(timestamp, glucose_mgdl, hc_record_id, source_package, recording_method, imported_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(timestamp, glucoseMgdl, hcRecordId, sourcePackage, 1, timestamp + 1L),
        )
    }

    private fun insertSampleV15(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        glucoseMgdl: Double,
        source: String,
        externalId: String,
        hcRecordId: String? = null,
        sourcePackage: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO glucose_samples " +
                "(timestamp, glucose_mgdl, source, external_id, hc_record_id, " +
                "source_package, recording_method, imported_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                timestamp,
                glucoseMgdl,
                source,
                externalId,
                hcRecordId,
                sourcePackage,
                1,
                timestamp + 1L,
            ),
        )
    }

    private fun insertEntryV9(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        uuid: String,
        glucoseMgdl: Double? = null,
        mealContext: String? = null,
        insulinBasalUnits: Double? = null,
        insulinBolusUnits: Double? = null,
        carbsGrams: Int? = null,
        proteinGrams: Int? = null,
        fatGrams: Int? = null,
        mealDescription: String? = null,
        exerciseMinutes: Int? = null,
        exerciseIntensity: String? = null,
        note: String? = null,
        foodId: Long? = null,
        source: String = "MANUAL",
        hcRecordId: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, uuid, food_id, source, hc_record_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                timestamp,
                glucoseMgdl,
                mealContext,
                insulinBasalUnits,
                insulinBolusUnits,
                carbsGrams,
                proteinGrams,
                fatGrams,
                mealDescription,
                exerciseMinutes,
                exerciseIntensity,
                note,
                uuid,
                foodId,
                source,
                hcRecordId,
            ),
        )
    }

    private fun insertEntryV10(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        uuid: String,
        glucoseMgdl: Double? = null,
        mealContext: String? = null,
        insulinBasalUnits: Double? = null,
        insulinBolusUnits: Double? = null,
        carbsGrams: Int? = null,
        proteinGrams: Int? = null,
        fatGrams: Int? = null,
        mealDescription: String? = null,
        exerciseMinutes: Int? = null,
        exerciseIntensity: String? = null,
        note: String? = null,
        foodId: Long? = null,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, glucose_mgdl, meal_context, insulin_basal_units, " +
                "insulin_bolus_units, carbs_grams, protein_grams, fat_grams, meal_description, " +
                "exercise_minutes, exercise_intensity, note, uuid, food_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                timestamp,
                glucoseMgdl,
                mealContext,
                insulinBasalUnits,
                insulinBolusUnits,
                carbsGrams,
                proteinGrams,
                fatGrams,
                mealDescription,
                exerciseMinutes,
                exerciseIntensity,
                note,
                uuid,
                foodId,
            ),
        )
    }

    private fun insertEntryV11(
        db: SupportSQLiteDatabase,
        timestamp: Long,
        uuid: String,
        hcRecordId: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO entries (timestamp, uuid, hc_record_id) VALUES (?, ?, ?)",
            arrayOf<Any?>(timestamp, uuid, hcRecordId),
        )
    }

    private fun insertFood(
        db: SupportSQLiteDatabase,
        id: Long,
        uuid: String,
        name: String,
        brand: String? = null,
        barcode: String? = null,
        carbsGrams: Double,
        proteinGrams: Double? = null,
        fatGrams: Double? = null,
        kcal: Double? = null,
        servingGrams: Double? = null,
        servingLabel: String? = null,
        source: String,
        offFetchedAt: Long? = null,
        userCorrected: Int = 0,
        useCount: Int = 0,
        lastUsedAt: Long? = null,
        isFavorite: Int = 0,
        createdAt: Long,
    ) {
        db.execSQL(
            "INSERT INTO foods (id, uuid, name, brand, barcode, carbs_grams, protein_grams, " +
                "fat_grams, kcal, serving_grams, serving_label, source, off_fetched_at, " +
                "user_corrected, use_count, last_used_at, is_favorite, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                id,
                uuid,
                name,
                brand,
                barcode,
                carbsGrams,
                proteinGrams,
                fatGrams,
                kcal,
                servingGrams,
                servingLabel,
                source,
                offFetchedAt,
                userCorrected,
                useCount,
                lastUsedAt,
                isFavorite,
                createdAt,
            ),
        )
    }
}
