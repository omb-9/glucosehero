package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import java.util.UUID

/**
 * Flat, multi-metric table: a single row can carry glucose, insulin, carbs
 * and exercise together under one timestamp. There is no `type` column —
 * "what did this event log" is always derived from which columns are
 * non-null (see [LogEvent.presentMetrics]), never from a denormalised label
 * that could contradict them. No `@TypeConverters` are required: every
 * column is a primitive or a nullable enum, which Room maps to TEXT/INTEGER
 * natively.
 */
@Entity(
    tableName = "entries",
    indices = [
        Index("timestamp"),
        Index("glucose_mgdl"),
        Index(value = ["glucose_mgdl", "timestamp"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["hc_record_id"], unique = true),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "glucose_mgdl") val glucoseMgdl: Double? = null,
    @ColumnInfo(name = "meal_context") val mealContext: MealContext? = null,
    @ColumnInfo(name = "insulin_basal_units") val insulinBasalUnits: Double? = null,
    @ColumnInfo(name = "insulin_bolus_units") val insulinBolusUnits: Double? = null,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Int? = null,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Int? = null,
    @ColumnInfo(name = "fat_grams") val fatGrams: Int? = null,
    @ColumnInfo(name = "meal_description") val mealDescription: String? = null,
    @ColumnInfo(name = "exercise_minutes") val exerciseMinutes: Int? = null,
    @ColumnInfo(name = "exercise_intensity") val exerciseIntensity: ActivityIntensity? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "source", defaultValue = "MANUAL") val source: EntrySource = EntrySource.MANUAL,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String? = null,
    /** End of a Health Connect interval (sleep/cycle); null for instant events. */
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "food_id") val foodId: Long? = null,
    @ColumnInfo(name = "uuid", defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "mood_score") val moodScore: Int? = null,
    @ColumnInfo(name = "mood_label") val moodLabel: String? = null,
)

fun EntryEntity.toDomain() = LogEvent(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
    moodScore = moodScore,
    moodLabel = moodLabel,
)

fun LogEvent.toEntity() = EntryEntity(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
    moodScore = moodScore,
    moodLabel = moodLabel,
)
