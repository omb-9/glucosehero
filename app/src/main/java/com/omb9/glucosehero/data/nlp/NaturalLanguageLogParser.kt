package com.omb9.glucosehero.data.nlp

import com.omb9.glucosehero.domain.model.QuickLogFood
import com.omb9.glucosehero.domain.model.QuickLogParseResult

/**
 * On-device fallback for [QuickLogParseResult] when Hero AI is unavailable
 * or returns unusable JSON.
 *
 * Handles the common diabetes-log sentence shape:
 * "Logged 40 grams of oatmeal, 2 eggs, and 3.5 units Humalog 15 minutes ago".
 * It does not try to be a general NLP parser: unknown phrasing simply leaves
 * slots blank so the user can finish the sheet by hand.
 */
object NaturalLanguageLogParser {

    private val MINUTES_AGO = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(minutes?|mins?|min)\s+ago\b""",
    )
    private val HOURS_AGO = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(hours?|hrs?|hr)\s+ago\b""",
    )
    private val INSULIN_UNITS = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(?:units?|u)\s+(?:of\s+)?([A-Za-z][A-Za-z0-9-]*)""",
    )
    private val BARE_UNITS = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(?:units?|u)\b""",
    )
    private val GRAMS_OF_FOOD = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(?:grams?|g)\s+(?:of\s+)?([A-Za-z][A-Za-z0-9\s]*?)(?=,|\band\b|$|\.|;)""",
    )
    private val EGGS = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s+eggs?\b""",
    )
    private val GLUCOSE_UNIT = Regex(
        """(?i)\b(\d+(?:\.\d+)?)\s*(mg\s*/?\s*dL|mmol(?:\s*/\s*L)?)\b""",
    )
    private val EXERCISE = Regex(
        """(?i)\b(\d+)\s*(?:minutes?|mins?|min)\s+(?:of\s+)?(?:walk|run|jog|cycle|bike|swim|exercise|workout)""",
    )
    private val EXERCISE_FLIPPED = Regex(
        """(?i)\b(?:walked|ran|jogged|cycled|biked|swam|exercised|worked out)\s+(?:for\s+)?(\d+)\s*(?:minutes?|mins?|min)\b""",
    )

    private val BOLUS_NAMES = setOf(
        "humalog", "novolog", "novorapid", "apidra", "fiasp", "lyumjev",
        "admelog", "lispro", "aspart", "glulisine", "rapid", "bolus",
    )
    private val BASAL_NAMES = setOf(
        "lantus", "levemir", "tresiba", "toujeo", "basaglar", "semglee",
        "glargine", "detemir", "degludec", "nph", "basal", "long",
    )

    fun parse(utterance: String): QuickLogParseResult {
        val text = utterance.trim()
        if (text.isBlank()) return QuickLogParseResult()

        val minutesAgo = parseMinutesAgo(text)
        val (basal, bolus) = parseInsulin(text)
        val foods = parseFoods(text)
        // "40 grams of oatmeal" is plate carbohydrate. Eggs still contribute
        // protein/fat (and their own food-row carbs), matching the Hero AI
        // example where plate carbs stay 40 rather than 40.8.
        val spokenGramCarbs = foods
            .filter { it.name.lowercase() != EGGS_FOOD_KEY }
            .mapNotNull { it.carbsGrams }
        val carbsFromFoods = when {
            spokenGramCarbs.isNotEmpty() -> spokenGramCarbs.sum()
            else -> foods.mapNotNull { it.carbsGrams }.sum().takeIf { foods.any { it.carbsGrams != null } }
        }
        val proteinFromFoods = foods.mapNotNull { it.proteinGrams }.sum().takeIf { foods.any { it.proteinGrams != null } }
        val fatFromFoods = foods.mapNotNull { it.fatGrams }.sum().takeIf { foods.any { it.fatGrams != null } }
        val (glucoseValue, glucoseUnit) = parseGlucose(text)
        val exercise = parseExercise(text)
        val description = foods.joinToString(", ") { food ->
            food.portionLabel?.takeIf { it.isNotBlank() } ?: food.name
        }.ifBlank { null }

        return QuickLogParseResult(
            glucoseValue = glucoseValue,
            glucoseUnit = glucoseUnit,
            insulinBasalUnits = basal,
            insulinBolusUnits = bolus,
            carbsGrams = carbsFromFoods,
            proteinGrams = proteinFromFoods,
            fatGrams = fatFromFoods,
            mealDescription = description,
            foods = foods,
            exerciseMinutes = exercise,
            minutesAgo = minutesAgo,
        )
    }

    private fun parseMinutesAgo(text: String): Int? {
        MINUTES_AGO.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            return it.toInt().coerceAtLeast(0)
        }
        HOURS_AGO.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            return (it * 60.0).toInt().coerceAtLeast(0)
        }
        return null
    }

    private fun parseInsulin(text: String): Pair<Double?, Double?> {
        var basal: Double? = null
        var bolus: Double? = null
        val named = INSULIN_UNITS.findAll(text).toList()
        for (match in named) {
            val units = match.groupValues[1].toDoubleOrNull() ?: continue
            val name = match.groupValues[2].lowercase()
            when {
                name in BASAL_NAMES -> if (basal == null) basal = units
                name in BOLUS_NAMES -> if (bolus == null) bolus = units
                else -> if (bolus == null) bolus = units
            }
        }
        if (basal == null && bolus == null) {
            BARE_UNITS.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
                bolus = it
            }
        }
        return basal to bolus
    }

    private fun parseFoods(text: String): List<QuickLogFood> {
        val foods = LinkedHashMap<String, QuickLogFood>()

        for (match in GRAMS_OF_FOOD.findAll(text)) {
            val grams = match.groupValues[1].toDoubleOrNull() ?: continue
            val name = cleanFoodName(match.groupValues[2]) ?: continue
            if (name in setOf("protein", "fat", "carbs", "carbohydrates", "insulin")) continue
            foods[name.lowercase()] = QuickLogFood(
                name = name,
                portionLabel = "${trimNumber(grams)} g $name",
                portionGrams = grams,
                carbsGrams = grams,
            )
        }

        EGGS.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { count ->
            val label = if (count == 1.0) "1 egg" else "${trimNumber(count)} eggs"
            foods[EGGS_FOOD_KEY] = QuickLogFood(
                name = "eggs",
                portionLabel = label,
                portionGrams = count * LARGE_EGG_GRAMS,
                carbsGrams = count * LARGE_EGG_CARBS,
                proteinGrams = count * LARGE_EGG_PROTEIN,
                fatGrams = count * LARGE_EGG_FAT,
            )
        }

        return foods.values.toList()
    }

    private fun parseGlucose(text: String): Pair<Double?, String?> {
        val match = GLUCOSE_UNIT.find(text) ?: return null to null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null to null
        val unitRaw = match.groupValues[2].lowercase().replace(" ", "")
        val unit = if (unitRaw.startsWith("mmol")) "mmol/L" else "mg/dL"
        return value to unit
    }

    private fun parseExercise(text: String): Int? {
        EXERCISE.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        EXERCISE_FLIPPED.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun cleanFoodName(raw: String): String? {
        val cleaned = raw.trim()
            .trim(',', '.', ';')
            .lowercase()
            .removePrefix("of ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.length < 2) return null
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private const val EGGS_FOOD_KEY = "eggs"
    private const val LARGE_EGG_GRAMS = 50.0
    private const val LARGE_EGG_CARBS = 0.4
    private const val LARGE_EGG_PROTEIN = 6.3
    private const val LARGE_EGG_FAT = 5.0
}
