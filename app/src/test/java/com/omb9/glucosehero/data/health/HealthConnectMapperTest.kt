package com.omb9.glucosehero.data.health

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.populatedWithTestValues
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.omb9.glucosehero.data.health.HealthConnectMapper.carbsGrams
import com.omb9.glucosehero.data.health.HealthConnectMapper.createMetadata
import com.omb9.glucosehero.data.health.HealthConnectMapper.energyKcal
import com.omb9.glucosehero.data.health.HealthConnectMapper.fatGrams
import com.omb9.glucosehero.data.health.HealthConnectMapper.proteinGrams
import com.omb9.glucosehero.data.health.HealthConnectMapper.toBloodGlucoseRecord
import com.omb9.glucosehero.data.health.HealthConnectMapper.toEntry
import com.omb9.glucosehero.data.health.HealthConnectMapper.toExerciseRecord
import com.omb9.glucosehero.data.health.HealthConnectMapper.toMacros
import com.omb9.glucosehero.data.health.HealthConnectMapper.toMealContext
import com.omb9.glucosehero.data.health.HealthConnectMapper.toNutritionRecord
import com.omb9.glucosehero.data.health.HealthConnectMapper.toRecords
import com.omb9.glucosehero.data.health.HealthConnectMapper.toRelationToMeal
import com.omb9.glucosehero.data.health.HealthConnectMapper.toSample
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.Macros
import com.omb9.glucosehero.domain.model.MealContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HealthConnectMapperTest {

    private val instant = Instant.parse("2026-01-01T00:00:00Z")
    private val importedAt = 1_700_000_000_000L

    // ---------- Glucose (unchanged behavior) ----------

    @Test
    fun `millimoles per liter maps through the library conversion factor`() {
        val record = BloodGlucoseRecord(
            time = instant,
            zoneOffset = null,
            metadata = Metadata.manualEntry(),
            level = BloodGlucose.millimolesPerLiter(6.4),
        )

        val sample = record.toSample(importedAt)

        // The library's mg/dL factor is 18.0 (not 18.0182), so 6.4 mmol/L == 115.2 mg/dL.
        assertEquals(115.2, sample.glucoseMgdl, 1e-6)
    }

    @Test
    fun `milligrams per deciliter maps exactly`() {
        val record = BloodGlucoseRecord(
            time = instant,
            zoneOffset = null,
            metadata = Metadata.manualEntry(),
            level = BloodGlucose.milligramsPerDeciliter(120.0),
        )

        val sample = record.toSample(importedAt)

        assertEquals(120.0, sample.glucoseMgdl, 1e-9)
    }

    @Test
    fun `source package and record id come from metadata`() {
        val metadata = testMetadata(id = "test-id", packageName = "com.dexcom.g7")
        val record = BloodGlucoseRecord(
            time = instant,
            zoneOffset = null,
            metadata = metadata,
            level = BloodGlucose.milligramsPerDeciliter(120.0),
        )

        val sample = record.toSample(importedAt)

        assertEquals("com.dexcom.g7", sample.sourcePackage)
        assertEquals("test-id", sample.hcRecordId)
        assertEquals(GlucoseSampleSource.HEALTH_CONNECT, sample.source)
        assertEquals("test-id", sample.externalId)
    }

    // ---------- Nutrition -> shared entry ----------

    @Test
    fun `nutrition maps macros name and provenance onto an entry`() {
        val record = NutritionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(60),
            endZoneOffset = null,
            metadata = testMetadata(id = "meal-1", packageName = "com.other.app"),
            name = "Chicken salad",
            totalCarbohydrate = Mass.grams(10.0),
            protein = Mass.grams(20.0),
            totalFat = Mass.grams(5.0),
            energy = Energy.kilocalories(500.0),
        )

        val entry = record.toEntry()

        assertEquals(instant.toEpochMilli(), entry.timestamp)
        assertEquals(10, entry.carbsGrams)
        assertEquals(20, entry.proteinGrams)
        assertEquals(5, entry.fatGrams)
        assertEquals("Chicken salad", entry.mealDescription)
        assertEquals(EntrySource.HEALTH_CONNECT, entry.source)
        assertEquals("meal-1", entry.hcRecordId)
        // Glucose, insulin and exercise slots are never filled by a meal import.
        assertNull(entry.glucoseMgdl)
        assertNull(entry.insulinBolusUnits)
        assertNull(entry.exerciseMinutes)
    }

    @Test
    fun `nutrition null fields stay null`() {
        val record = NutritionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(60),
            endZoneOffset = null,
            metadata = testMetadata(id = "meal-2", packageName = "com.other.app"),
            totalCarbohydrate = Mass.grams(10.0),
        )

        val entry = record.toEntry()

        assertEquals(10, entry.carbsGrams)
        assertNull(entry.proteinGrams)
        assertNull(entry.fatGrams)
        assertNull(entry.mealDescription)
    }

    @Test
    fun `fractional grams round to the nearest whole gram`() {
        val record = NutritionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(60),
            endZoneOffset = null,
            metadata = testMetadata(id = "meal-3", packageName = "com.other.app"),
            totalCarbohydrate = Mass.grams(10.6),
        )

        assertEquals(11, record.toEntry().carbsGrams)
    }

    // ---------- Exercise -> shared entry ----------

    @Test
    fun `exercise duration and title map onto an entry`() {
        val record = ExerciseSessionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(30 * 60),
            endZoneOffset = null,
            metadata = testMetadata(id = "workout-1", packageName = "com.other.app"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Morning run",
        )

        val entry = record.toEntry()

        assertEquals(instant.toEpochMilli(), entry.timestamp)
        assertEquals(30, entry.exerciseMinutes)
        assertEquals("Morning run", entry.note)
        assertEquals(EntrySource.HEALTH_CONNECT, entry.source)
        assertEquals("workout-1", entry.hcRecordId)
        // Intensity is intentionally left null — Health Connect has no clean equivalent.
        assertNull(entry.exerciseIntensity)
        assertNull(entry.carbsGrams)
        assertNull(entry.glucoseMgdl)
    }

    @Test
    fun `exercise with no title leaves the note null`() {
        val record = ExerciseSessionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(10 * 60),
            endZoneOffset = null,
            metadata = testMetadata(id = "workout-2", packageName = "com.other.app"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        )

        val entry = record.toEntry()

        assertEquals(10, entry.exerciseMinutes)
        assertNull(entry.note)
    }

    // ---------- MealContext <-> RELATION_TO_MEAL_* mapping ----------

    @Test
    fun `meal context maps onto RELATION_TO_MEAL constants`() {
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_FASTING, MealContext.FASTING.toRelationToMeal())
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL, MealContext.BEFORE_MEAL.toRelationToMeal())
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL, MealContext.AFTER_MEAL.toRelationToMeal())
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN, MealContext.NONE.toRelationToMeal())
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN, (null as MealContext?).toRelationToMeal())
    }

    @Test
    fun `bedtime meal context falls back to RELATION_TO_MEAL_GENERAL`() {
        // Health Connect has no bedtime-specific equivalent, so it falls back to general
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL, MealContext.BEDTIME.toRelationToMeal())
    }

    @Test
    fun `RELATION_TO_MEAL constants map onto MealContext`() {
        assertEquals(MealContext.FASTING, BloodGlucoseRecord.RELATION_TO_MEAL_FASTING.toMealContext())
        assertEquals(MealContext.BEFORE_MEAL, BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL.toMealContext())
        assertEquals(MealContext.AFTER_MEAL, BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL.toMealContext())
        assertEquals(MealContext.NONE, BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL.toMealContext())
        assertEquals(MealContext.NONE, BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN.toMealContext())
        assertEquals(MealContext.NONE, 999.toMealContext())
    }

    // ---------- NutritionRecord <-> Macros mapping via Mass and Energy ----------

    @Test
    fun `nutrition record maps to macros via Mass inGrams and Energy inKilocalories`() {
        val record = NutritionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(60),
            endZoneOffset = null,
            metadata = testMetadata(id = "macros-1", packageName = "com.other.app"),
            name = "Protein shake",
            totalCarbohydrate = Mass.grams(25.5),
            protein = Mass.grams(30.0),
            totalFat = Mass.grams(4.5),
            energy = Energy.kilocalories(260.0),
        )

        val macros = record.toMacros()

        assertEquals(25.5, macros.carbsGrams, 1e-6)
        assertEquals(30.0, macros.proteinGrams!!, 1e-6)
        assertEquals(4.5, macros.fatGrams!!, 1e-6)
        assertEquals(260.0, macros.kcal!!, 1e-6)

        // Test extension properties
        assertEquals(25.5, record.carbsGrams!!, 1e-6)
        assertEquals(30.0, record.proteinGrams!!, 1e-6)
        assertEquals(4.5, record.fatGrams!!, 1e-6)
        assertEquals(260.0, record.energyKcal!!, 1e-6)
    }

    @Test
    fun `nutrition record with null values maps to macros with nulls`() {
        val record = NutritionRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant.plusSeconds(60),
            endZoneOffset = null,
            metadata = testMetadata(id = "macros-2", packageName = "com.other.app"),
        )

        val macros = record.toMacros()

        assertEquals(0.0, macros.carbsGrams, 1e-6)
        assertNull(macros.proteinGrams)
        assertNull(macros.fatGrams)
        assertNull(macros.kcal)

        assertNull(record.carbsGrams)
        assertNull(record.proteinGrams)
        assertNull(record.fatGrams)
        assertNull(record.energyKcal)
    }

    @Test
    fun `macros map to nutrition record using Mass grams and Energy kilocalories`() {
        val macros = Macros(
            carbsGrams = 40.0,
            proteinGrams = 20.0,
            fatGrams = 10.0,
            kcal = 330.0,
        )

        val record = macros.toNutritionRecord(
            startTime = instant,
            name = "Oatmeal",
        )

        assertEquals(40.0, record.totalCarbohydrate!!.inGrams, 1e-6)
        assertEquals(20.0, record.protein!!.inGrams, 1e-6)
        assertEquals(10.0, record.totalFat!!.inGrams, 1e-6)
        assertEquals(330.0, record.energy!!.inKilocalories, 1e-6)
        assertEquals("Oatmeal", record.name)
    }

    // ---------- BloodGlucoseRecord <-> EntryEntity mapping ----------

    @Test
    fun `blood glucose record with relation to meal maps onto entry entity`() {
        val record = BloodGlucoseRecord(
            time = instant,
            zoneOffset = null,
            metadata = testMetadata(id = "bg-1", packageName = "com.dexcom.g7"),
            level = BloodGlucose.milligramsPerDeciliter(145.0),
            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL,
        )

        val entry = record.toEntry()

        assertEquals(instant.toEpochMilli(), entry.timestamp)
        assertEquals(145.0, entry.glucoseMgdl!!, 1e-6)
        assertEquals(MealContext.AFTER_MEAL, entry.mealContext)
        assertEquals(EntrySource.HEALTH_CONNECT, entry.source)
        assertEquals("bg-1", entry.hcRecordId)
    }

    @Test
    fun `entry entity maps to blood glucose record with meal context`() {
        val entry = EntryEntity(
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 130.0,
            mealContext = MealContext.BEFORE_MEAL,
        )

        val record = entry.toBloodGlucoseRecord()

        assertNotNull(record)
        assertEquals(instant, record!!.time)
        assertEquals(130.0, record.level.inMilligramsPerDeciliter, 1e-6)
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL, record.relationToMeal)
    }

    @Test
    fun `entry entity with bedtime meal context maps to RELATION_TO_MEAL_GENERAL`() {
        val entry = EntryEntity(
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 110.0,
            mealContext = MealContext.BEDTIME,
        )

        val record = entry.toBloodGlucoseRecord()

        assertNotNull(record)
        assertEquals(BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL, record!!.relationToMeal)
    }

    @Test
    fun `entry entity without glucose returns null blood glucose record`() {
        val entry = EntryEntity(
            timestamp = instant.toEpochMilli(),
            carbsGrams = 50,
        )

        assertNull(entry.toBloodGlucoseRecord())
    }

    // ---------- EntryEntity -> ExerciseSessionRecord mapping ----------

    @Test
    fun `entry entity with exercise maps to exercise session record`() {
        val entry = EntryEntity(
            timestamp = instant.toEpochMilli(),
            exerciseMinutes = 45,
            note = "Afternoon Jog",
        )

        val record = entry.toExerciseRecord()

        assertNotNull(record)
        assertEquals(instant, record!!.startTime)
        assertEquals(instant.plusSeconds(45 * 60), record.endTime)
        assertEquals("Afternoon Jog", record.title)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, record.exerciseType)
    }

    @Test
    fun `entry entity without exercise or with non-positive minutes returns null exercise record`() {
        val noExercise = EntryEntity(
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 120.0,
        )
        assertNull(noExercise.toExerciseRecord())

        val zeroMinutes = EntryEntity(
            timestamp = instant.toEpochMilli(),
            exerciseMinutes = 0,
        )
        assertNull(zeroMinutes.toExerciseRecord())

        val negativeMinutes = EntryEntity(
            timestamp = instant.toEpochMilli(),
            exerciseMinutes = -10,
        )
        assertNull(negativeMinutes.toExerciseRecord())
    }

    // ---------- createMetadata & toRecords write-back helpers ----------

    @Test
    fun `createMetadata sets clientRecordId with glucosehero prefix and clientRecordVersion`() {
        val metadata = createMetadata(entryId = 42L, updatedAtMillis = 1_700_000_123L)

        assertEquals("glucosehero:42", metadata.clientRecordId)
        assertEquals(1_700_000_123L, metadata.clientRecordVersion)
    }

    @Test
    fun `entry entity toRecords creates records with clientRecordId and clientRecordVersion`() {
        val entry = EntryEntity(
            id = 77L,
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 125.0,
            mealContext = MealContext.BEFORE_MEAL,
            carbsGrams = 60,
            mealDescription = "Pasta dinner",
            exerciseMinutes = 30,
            note = "Walk after dinner",
            source = EntrySource.MANUAL,
        )

        val records = entry.toRecords(updatedAtMillis = 1_700_500_000L)

        assertEquals(3, records.size)

        val bgRecord = records.filterIsInstance<BloodGlucoseRecord>().first()
        assertEquals(125.0, bgRecord.level.inMilligramsPerDeciliter, 1e-6)
        assertEquals("glucosehero:77", bgRecord.metadata.clientRecordId)
        assertEquals(1_700_500_000L, bgRecord.metadata.clientRecordVersion)

        val nutritionRecord = records.filterIsInstance<NutritionRecord>().first()
        assertEquals(60.0, nutritionRecord.totalCarbohydrate!!.inGrams, 1e-6)
        assertEquals("Pasta dinner", nutritionRecord.name)
        assertEquals("glucosehero:77", nutritionRecord.metadata.clientRecordId)
        assertEquals(1_700_500_000L, nutritionRecord.metadata.clientRecordVersion)

        val exerciseRecord = records.filterIsInstance<ExerciseSessionRecord>().first()
        assertEquals(30 * 60L, java.time.Duration.between(exerciseRecord.startTime, exerciseRecord.endTime).seconds)
        assertEquals("Walk after dinner", exerciseRecord.title)
        assertEquals("glucosehero:77", exerciseRecord.metadata.clientRecordId)
        assertEquals(1_700_500_000L, exerciseRecord.metadata.clientRecordVersion)
    }

    @Test
    fun `entry entity toRecords returns empty list for HEALTH_CONNECT source or unpersisted id`() {
        val hcImported = EntryEntity(
            id = 12L,
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 120.0,
            source = EntrySource.HEALTH_CONNECT,
        )
        assertEquals(emptyList<androidx.health.connect.client.records.Record>(), hcImported.toRecords())

        val unpersisted = EntryEntity(
            id = 0L,
            timestamp = instant.toEpochMilli(),
            glucoseMgdl = 120.0,
            source = EntrySource.MANUAL,
        )
        assertEquals(emptyList<androidx.health.connect.client.records.Record>(), unpersisted.toRecords())
    }

    private fun testMetadata(id: String, packageName: String): Metadata =
        Metadata.manualEntry().populatedWithTestValues(
            id = id,
            dataOrigin = DataOrigin(packageName),
        )
}
