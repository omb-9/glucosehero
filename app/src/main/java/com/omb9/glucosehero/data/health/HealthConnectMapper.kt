package com.omb9.glucosehero.data.health

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.Macros
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.util.TagExtractor
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

object HealthConnectMapper {

    fun BloodGlucoseRecord.toSample(importedAt: Long = System.currentTimeMillis()): GlucoseSampleEntity =
        GlucoseSampleEntity(
            timestamp = time.toEpochMilli(),
            glucoseMgdl = level.inMilligramsPerDeciliter, // library converts; do not multiply by 18.0182
            source = GlucoseSampleSource.HEALTH_CONNECT,
            externalId = metadata.id,
            hcRecordId = metadata.id,
            sourcePackage = metadata.dataOrigin.packageName,
            recordingMethod = metadata.recordingMethod,
            importedAt = importedAt,
        )

    /**
     * A Health Connect meal is a user-authored event, so it maps onto the
     * shared [EntryEntity] log (not a separate table). Calories and meal-type
     * have no column on the entity and are intentionally dropped.
     */
    fun NutritionRecord.toEntry(): EntryEntity =
        EntryEntity(
            timestamp = startTime.toEpochMilli(),
            carbsGrams = totalCarbohydrate?.inGrams?.roundToInt(),
            proteinGrams = protein?.inGrams?.roundToInt(),
            fatGrams = totalFat?.inGrams?.roundToInt(),
            mealDescription = name?.takeIf { it.isNotBlank() },
            source = EntrySource.HEALTH_CONNECT,
            hcRecordId = metadata.id,
        )

    /**
     * Maps [NutritionRecord] into [Macros] extracting carbohydrate, protein, and fat
     * via [Mass.inGrams] and energy via [Energy.inKilocalories].
     */
    fun NutritionRecord.toMacros(): Macros =
        Macros(
            carbsGrams = totalCarbohydrate?.inGrams ?: 0.0,
            proteinGrams = protein?.inGrams,
            fatGrams = totalFat?.inGrams,
            kcal = energy?.inKilocalories,
        )

    val NutritionRecord.energyKcal: Double?
        get() = energy?.inKilocalories

    val NutritionRecord.carbsGrams: Double?
        get() = totalCarbohydrate?.inGrams

    val NutritionRecord.proteinGrams: Double?
        get() = protein?.inGrams

    val NutritionRecord.fatGrams: Double?
        get() = totalFat?.inGrams

    /**
     * Maps [Macros] to a Health Connect [NutritionRecord] using [Mass.grams] and
     * [Energy.kilocalories].
     */
    fun Macros.toNutritionRecord(
        startTime: Instant,
        endTime: Instant = startTime.plusSeconds(60),
        startZoneOffset: ZoneOffset? = null,
        endZoneOffset: ZoneOffset? = null,
        name: String? = null,
        metadata: Metadata = Metadata.manualEntry(),
    ): NutritionRecord =
        NutritionRecord(
            startTime = startTime,
            startZoneOffset = startZoneOffset,
            endTime = endTime,
            endZoneOffset = endZoneOffset,
            totalCarbohydrate = Mass.grams(carbsGrams),
            protein = proteinGrams?.let { Mass.grams(it) },
            totalFat = fatGrams?.let { Mass.grams(it) },
            energy = kcal?.let { Energy.kilocalories(it) },
            name = name,
            metadata = metadata,
        )

    /**
     * A workout session is a user-authored event, so it maps onto the shared
     * [EntryEntity] log. Intensity is deliberately left null — Health Connect
     * has no clean equivalent and a guessed value would feed the bolus
     * calculator and analytics as if the user had stated it.
     */
    fun ExerciseSessionRecord.toEntry(): EntryEntity =
        EntryEntity(
            timestamp = startTime.toEpochMilli(),
            exerciseMinutes = Duration.between(startTime, endTime).toMinutes().toInt(),
            note = title?.takeIf { it.isNotBlank() },
            source = EntrySource.HEALTH_CONNECT,
            hcRecordId = metadata.id,
        )

    /**
     * A sleep session is a time-based interval, not a glucose event, so it
     * lands on the shared [EntryEntity] log with a fixed marker in the note
     * that [TagExtractor] maps to
     * [com.omb9.glucosehero.domain.model.TagKind.SLEEP].
     */
    fun SleepSessionRecord.toEntry(): EntryEntity =
        EntryEntity(
            timestamp = startTime.toEpochMilli(),
            endTime = endTime.toEpochMilli(),
            note = listOfNotNull(
                TagExtractor.SLEEP_TAG,
                title?.takeIf { it.isNotBlank() },
            ).joinToString(" "),
            source = EntrySource.HEALTH_CONNECT,
            hcRecordId = metadata.id,
        )

    /**
     * A menstruation period is a time-based interval, so it lands on the shared
     * [EntryEntity] log with a fixed marker in the note that [TagExtractor]
     * maps to [com.omb9.glucosehero.domain.model.TagKind.CYCLE].
     */
    fun MenstruationPeriodRecord.toEntry(): EntryEntity =
        EntryEntity(
            timestamp = startTime.toEpochMilli(),
            endTime = endTime.toEpochMilli(),
            note = TagExtractor.CYCLE_TAG,
            source = EntrySource.HEALTH_CONNECT,
            hcRecordId = metadata.id,
        )

    /**
     * Maps [MealContext] onto Health Connect's [BloodGlucoseRecord.RELATION_TO_MEAL_*] constants.
     * [MealContext.BEDTIME] falls back to [BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL] as
     * Health Connect has no bedtime-specific equivalent.
     */
    fun MealContext?.toRelationToMeal(): Int = when (this) {
        MealContext.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
        MealContext.BEFORE_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
        MealContext.AFTER_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
        MealContext.BEDTIME -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
        MealContext.NONE, null -> BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
    }

    /**
     * Maps Health Connect's [BloodGlucoseRecord.RELATION_TO_MEAL_*] constants onto [MealContext].
     */
    fun Int.toMealContext(): MealContext = when (this) {
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> MealContext.FASTING
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> MealContext.BEFORE_MEAL
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> MealContext.AFTER_MEAL
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> MealContext.NONE
        BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN -> MealContext.NONE
        else -> MealContext.NONE
    }

    /**
     * Maps a Health Connect [BloodGlucoseRecord] into a user [EntryEntity],
     * converting [BloodGlucoseRecord.relationToMeal] onto [MealContext].
     */
    fun BloodGlucoseRecord.toEntry(): EntryEntity =
        EntryEntity(
            timestamp = time.toEpochMilli(),
            glucoseMgdl = level.inMilligramsPerDeciliter,
            mealContext = relationToMeal.toMealContext().takeIf { it != MealContext.NONE },
            source = EntrySource.HEALTH_CONNECT,
            hcRecordId = metadata.id,
        )

    /**
     * Maps a user [EntryEntity] with glucose into a Health Connect [BloodGlucoseRecord],
     * mapping [EntryEntity.mealContext] onto [BloodGlucoseRecord.relationToMeal].
     */
    fun EntryEntity.toBloodGlucoseRecord(
        zoneOffset: ZoneOffset? = null,
        metadata: Metadata = Metadata.manualEntry(),
    ): BloodGlucoseRecord? {
        val glucose = glucoseMgdl ?: return null
        return BloodGlucoseRecord(
            time = Instant.ofEpochMilli(timestamp),
            zoneOffset = zoneOffset,
            level = BloodGlucose.milligramsPerDeciliter(glucose),
            relationToMeal = mealContext.toRelationToMeal(),
            metadata = metadata,
        )
    }

    /**
     * Maps a user [EntryEntity] meal into a Health Connect [NutritionRecord].
     */
    fun EntryEntity.toNutritionRecord(
        zoneOffset: ZoneOffset? = null,
        metadata: Metadata = Metadata.manualEntry(),
    ): NutritionRecord? {
        if (carbsGrams == null && proteinGrams == null && fatGrams == null && mealDescription.isNullOrBlank()) {
            return null
        }
        val instant = Instant.ofEpochMilli(timestamp)
        return NutritionRecord(
            startTime = instant,
            startZoneOffset = zoneOffset,
            endTime = instant.plusSeconds(60),
            endZoneOffset = zoneOffset,
            totalCarbohydrate = carbsGrams?.toDouble()?.let { Mass.grams(it) },
            protein = proteinGrams?.toDouble()?.let { Mass.grams(it) },
            totalFat = fatGrams?.toDouble()?.let { Mass.grams(it) },
            name = mealDescription,
            metadata = metadata,
        )
    }

    /**
     * Maps a user [EntryEntity] workout into a Health Connect [ExerciseSessionRecord].
     */
    fun EntryEntity.toExerciseRecord(
        zoneOffset: ZoneOffset? = null,
        metadata: Metadata = Metadata.manualEntry(),
    ): ExerciseSessionRecord? {
        val minutes = exerciseMinutes?.takeIf { it > 0 } ?: return null
        val instant = Instant.ofEpochMilli(timestamp)
        return ExerciseSessionRecord(
            startTime = instant,
            startZoneOffset = zoneOffset,
            endTime = instant.plusSeconds(minutes.toLong() * 60),
            endZoneOffset = zoneOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            title = note?.takeIf { it.isNotBlank() },
            metadata = metadata,
        )
    }

    /**
     * Creates Health Connect [Metadata] using [Metadata.manualEntry] with
     * `clientRecordId = "glucosehero:$entryId"` and `clientRecordVersion = updatedAtMillis`.
     * Health Connect uses `clientRecordId` to upsert records in place.
     */
    fun createMetadata(
        entryId: Long,
        updatedAtMillis: Long,
    ): Metadata = Metadata.manualEntry(
        clientRecordId = "glucosehero:$entryId",
        clientRecordVersion = updatedAtMillis,
    )

    /**
     * Maps a user [EntryEntity] into all matching Health Connect [Record] instances
     * using [Metadata.manualEntry] with `clientRecordId = "glucosehero:$id"` and
     * `clientRecordVersion = updatedAtMillis`.
     *
     * Returns an empty list if the entry is unpersisted (`id == 0L`) or originated
     * from Health Connect (`source == EntrySource.HEALTH_CONNECT`) to avoid echo.
     */
    fun EntryEntity.toRecords(
        updatedAtMillis: Long = timestamp,
        zoneOffset: ZoneOffset? = null,
    ): List<Record> {
        if (id == 0L || source == EntrySource.HEALTH_CONNECT) return emptyList()
        val metadata = createMetadata(id, updatedAtMillis)
        return listOfNotNull(
            toBloodGlucoseRecord(zoneOffset = zoneOffset, metadata = metadata),
            toNutritionRecord(zoneOffset = zoneOffset, metadata = metadata),
            toExerciseRecord(zoneOffset = zoneOffset, metadata = metadata),
        )
    }
}
