package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext

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
    indices = [Index("timestamp"), Index("glucose_mgdl")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "glucose_mgdl") val glucoseMgdl: Double? = null,
    @ColumnInfo(name = "meal_context") val mealContext: MealContext? = null,
    @ColumnInfo(name = "insulin_basal_units") val insulinBasalUnits: Double? = null,
    @ColumnInfo(name = "insulin_bolus_units") val insulinBolusUnits: Double? = null,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Int? = null,
    @ColumnInfo(name = "meal_description") val mealDescription: String? = null,
    @ColumnInfo(name = "exercise_minutes") val exerciseMinutes: Int? = null,
    @ColumnInfo(name = "exercise_intensity") val exerciseIntensity: ActivityIntensity? = null,
    @ColumnInfo(name = "note") val note: String? = null,
)

fun EntryEntity.toDomain() = LogEvent(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
)

fun LogEvent.toEntity() = EntryEntity(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
)
