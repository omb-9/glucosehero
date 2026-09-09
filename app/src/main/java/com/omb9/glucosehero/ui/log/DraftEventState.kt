package com.omb9.glucosehero.ui.log

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.Metric
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.util.Formatters
import kotlin.math.roundToInt

@Immutable
data class DraftEventState(
    val activeCategory: EntryType = EntryType.GLUCOSE,
    val glucose: String = "",
    val mealContext: MealContext = MealContext.NONE,
    val insulinBasal: String = "",
    val insulinBolus: String = "",
    val carbsGrams: String = "",
    val proteinGrams: String = "",
    val fatGrams: String = "",
    val mealDescription: String = "",
    val exerciseMinutes: String = "",
    val exerciseIntensity: ActivityIntensity = ActivityIntensity.MODERATE,
    val note: String = "",
    val moodScore: Int? = null,
    val moodLabel: String? = null,
    val postMealReminderEnabled: Boolean = true,
    val isSaving: Boolean = false,
    /** When set, Save writes this timestamp instead of now (from "15 minutes ago"). */
    val occurredAtMillis: Long? = null,
)

/**
 * Which icon badges should render the "has data" dot, driven purely by
 * whether the user actually typed something in that slot — not by whether
 * the typed text would survive validation. A half-typed "5." still shows
 * the dot so the user doesn't lose their place.
 */
val DraftEventState.filledMetrics: Set<Metric>
    get() = buildSet {
        if (glucose.isNotBlank()) add(Metric.GLUCOSE)
        if (insulinBasal.isNotBlank() || insulinBolus.isNotBlank()) add(Metric.INSULIN)
        if (carbsGrams.isNotBlank() || proteinGrams.isNotBlank() || fatGrams.isNotBlank() ||
            mealDescription.isNotBlank()
        ) add(Metric.CARBS)
        if (exerciseMinutes.isNotBlank()) add(Metric.EXERCISE)
        if (note.isNotBlank()) add(Metric.NOTE)
        if (moodScore != null) add(Metric.MOOD)
    }

/**
 * Single source of truth for "can this draft actually be saved?" — the
 * function that collapses the old `canSave`↔`toEntry` mirror bug into one
 * code path. Both the sheet's Save button and the persistence path [saveDraft]
 * call this same function, so UI and storage can never disagree again.
 *
 * Each field is parsed independently. An empty field yields null (no error);
 * a **non-blank but unparsable or non-positive** field makes the entire build
 * return null so the caller never silently discards a typo the user intended
 * to save.
 */
fun DraftEventState.toLogEvent(settings: UserSettings, now: Long): LogEvent? {
    val glucoseMgdl: Double? = when {
        glucose.isBlank() -> null
        else -> {
            val display = Formatters.parseDecimal(glucose)?.takeIf { it > 0 } ?: return null
            Formatters.displayToMgdl(display, settings.unit)
        }
    }

    val basalValue: Double? = when {
        insulinBasal.isBlank() -> null
        else -> Formatters.parseDecimal(insulinBasal)?.takeIf { it > 0 } ?: return null
    }
    val bolusValue: Double? = when {
        insulinBolus.isBlank() -> null
        else -> Formatters.parseDecimal(insulinBolus)?.takeIf { it > 0 } ?: return null
    }

    val carbsValue: Int? = when {
        carbsGrams.isBlank() -> null
        // Zero is an explicit, meaningful statement ("I ate 0 carbs") and must
        // survive as 0 — distinct from the empty/blank case above, which is null.
        else -> {
            val parsed = Formatters.parseDecimal(carbsGrams)
            if (parsed != null && parsed >= 0.0) {
                parsed.roundToInt()
            } else {
                carbsGrams.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
            }
        }
    }

    val proteinValue: Int? = when {
        proteinGrams.isBlank() -> null
        else -> {
            val parsed = Formatters.parseDecimal(proteinGrams)
            if (parsed != null && parsed > 0.0) {
                parsed.roundToInt()
            } else {
                proteinGrams.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
            }
        }
    }

    val fatValue: Int? = when {
        fatGrams.isBlank() -> null
        else -> {
            val parsed = Formatters.parseDecimal(fatGrams)
            if (parsed != null && parsed > 0.0) {
                parsed.roundToInt()
            } else {
                fatGrams.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
            }
        }
    }

    val exerciseValue: Int? = when {
        exerciseMinutes.isBlank() -> null
        else -> exerciseMinutes.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
    }

    val mealDescriptionClean = mealDescription.trim().ifBlank { null }
    val noteClean = note.trim().ifBlank { null }
    val moodScoreValue = moodScore?.takeIf { it in 1..5 }
    val moodLabelClean = moodLabel?.trim()?.ifBlank { null }

    // Attach qualifiers only when their metric is actually present — a
    // mealContext without glucose or an insulinType without an insulin dose
    // would be contradiction-prone denormalised state otherwise.
    val resolvedMealContext = if (glucoseMgdl != null) mealContext else null
    val resolvedExerciseIntensity = if (exerciseValue != null) exerciseIntensity else null
    val resolvedMoodLabel = if (moodScoreValue != null) moodLabelClean else null

    // Meal is the odd one out: it is the only metric where a pure string
    // (`mealDescription`) counts as "present" on its own (free-form meals
    // without a carb count). That empty-check must ride on `carbsGrams` alongside
    // `mealDescription` and `note` in isEmpty — otherwise a description-only
    // meal silently collapses to nothing.
    val event = LogEvent(
        timestamp = now,
        glucoseMgdl = glucoseMgdl,
        mealContext = resolvedMealContext,
        insulinBasalUnits = basalValue,
        insulinBolusUnits = bolusValue,
        carbsGrams = carbsValue,
        proteinGrams = proteinValue,
        fatGrams = fatValue,
        mealDescription = mealDescriptionClean,
        exerciseMinutes = exerciseValue,
        exerciseIntensity = resolvedExerciseIntensity,
        note = noteClean,
        moodScore = moodScoreValue,
        moodLabel = resolvedMoodLabel,
    )

    return if (event.isEmpty) null else event
}
