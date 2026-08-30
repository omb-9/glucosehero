package com.omb9.glucosehero.domain.model

/**
 * A single metric slot an event can carry. Used to derive presence-based UI
 * affordances (status dots, multi-metric subtitles) without a stored `type`
 * discriminator — "what did this event log" is always answered by which
 * columns are non-null, never by a denormalised label that could contradict
 * them.
 */
enum class Metric { GLUCOSE, INSULIN, CARBS, EXERCISE, NOTE }

/**
 * A single logged moment that can carry any combination of glucose, insulin,
 * carbs, exercise and a note under one timestamp. Replaces the old one-
 * category-per-save [Entry]/[EntryDetails] pair: EntryType survives only as a
 * UI-facing enum (category icon selection), never as a persisted column.
 *
 * [glucoseMgdl] is always stored canonically in mg/dL regardless of the
 * display unit, so SQL aggregates (AVG/MIN/MAX, time-in-range) never need
 * conversion.
 */
data class LogEvent(
    val id: Long = 0L,
    val timestamp: Long,
    val glucoseMgdl: Double? = null,
    val mealContext: MealContext? = null,
    val insulinBasalUnits: Double? = null,
    val insulinBolusUnits: Double? = null,
    val carbsGrams: Int? = null,
    val mealDescription: String? = null,
    val exerciseMinutes: Int? = null,
    val exerciseIntensity: ActivityIntensity? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = glucoseMgdl == null && insulinBasalUnits == null && insulinBolusUnits == null &&
            carbsGrams == null && exerciseMinutes == null &&
            mealDescription.isNullOrBlank() && note.isNullOrBlank()

    /** Which metric slots this event actually carries data for. */
    val presentMetrics: Set<Metric>
        get() = buildSet {
            if (glucoseMgdl != null) add(Metric.GLUCOSE)
            if (insulinBasalUnits != null || insulinBolusUnits != null) add(Metric.INSULIN)
            if (carbsGrams != null || !mealDescription.isNullOrBlank()) add(Metric.CARBS)
            if (exerciseMinutes != null) add(Metric.EXERCISE)
            if (!note.isNullOrBlank()) add(Metric.NOTE)
        }
}
