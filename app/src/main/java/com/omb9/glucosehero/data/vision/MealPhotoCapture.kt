package com.omb9.glucosehero.data.vision

import android.graphics.Bitmap
import android.util.Base64
import com.omb9.glucosehero.domain.model.InvalidAiJsonException
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.util.AiJson
import java.io.ByteArrayOutputStream

/**
 * In-memory-only image handling and the multimodal prompt/JSON contract for
 * the meal-photo nutrition feature.
 *
 * A captured [Bitmap] is compressed to JPEG bytes held entirely in a
 * [ByteArrayOutputStream], then Base64-encoded into a `data:` URI that can be
 * posted to Hero AI. No file is written and no image bytes touch the local
 * database, honouring the app's privacy promise.
 *
 * [SYSTEM_PROMPT] / [USER_PROMPT] are the exact strings sent with the image.
 * [parseAnalysis] is the only decoder the capture pipeline uses, so invalid
 * JSON fails closed instead of stuffing model prose into the meal field.
 */
object MealPhotoCapture {

    private const val JPEG_QUALITY = 85

    /**
     * Encodes [bitmap] as a `data:image/jpeg;base64,…` URI without touching disk.
     * Returns null when compression produces no bytes.
     */
    fun toDataUri(bitmap: Bitmap): String? {
        val bytes = ByteArrayOutputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                return null
            }
            stream.toByteArray()
        }
        if (bytes.isEmpty()) return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Parses a model reply into [MealPhotoAnalysis]. Throws
     * [InvalidAiJsonException] when the body is not the required JSON object
     * or decodes to an empty estimate.
     */
    fun parseAnalysis(raw: String): MealPhotoAnalysis {
        val parsed = AiJson.decodeStrict(raw, MealPhotoAnalysis.serializer())
        if (parsed.isEmpty) {
            throw InvalidAiJsonException(
                "The AI returned JSON without portion, carbohydrate, fiber, protein, or fat estimates.",
            )
        }
        return parsed
    }

    /**
     * Builds the meal-description string that is written into the Add Entry
     * sheet: dish name, portion list, fiber, and a late-spike note when fat
     * is high enough to delay carbohydrate absorption.
     */
    fun draftMealDescription(analysis: MealPhotoAnalysis): String? {
        val parts = mutableListOf<String>()
        analysis.description?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (analysis.portions.isNotEmpty()) {
            val portionLine = analysis.portions.joinToString(", ") { portion ->
                buildString {
                    append(portion.name.trim())
                    val label = portion.portionLabel?.trim().orEmpty()
                    val grams = portion.portionGrams?.let { "${trimNumber(it)} g" }
                    val detail = listOf(label, grams).filter { !it.isNullOrBlank() }.joinToString(", ")
                    if (detail.isNotBlank()) append(" (").append(detail).append(")")
                }
            }
            if (parts.none { it.equals(portionLine, ignoreCase = true) }) {
                parts += portionLine
            }
        }
        analysis.fiberGramsInt?.takeIf { it > 0 }?.let { parts += "Fiber ${it} g" }
        val delayNote = analysis.lateSpikeNote?.trim().orEmpty().ifBlank {
            if (analysis.fatDelaysCarbAbsorption == true || (analysis.fatGrams ?: 0.0) >= FAT_DELAY_GRAMS) {
                FAT_DELAY_DEFAULT_NOTE
            } else {
                ""
            }
        }
        if (delayNote.isNotBlank()) parts += delayNote
        return parts.joinToString(". ").ifBlank { null }
    }

    /** System prompt sent with every meal-photo completion. Return JSON only. */
    const val SYSTEM_PROMPT =
        """You are Hero, the nutrition assistant inside GlucoseHero, a personal glucose logging app.

Analyze the meal photo and estimate what is on the plate. Return ONLY a JSON object. No markdown, no code fences, no prose before or after the object.

The JSON MUST match this schema exactly (all keys present; use null for unknown numbers and [] for no items):
{
  "description": string | null,
  "portions": [
    {
      "name": string,
      "portion_label": string | null,
      "portion_grams": number | null,
      "carbs_grams": number | null,
      "fiber_grams": number | null,
      "protein_grams": number | null,
      "fat_grams": number | null
    }
  ],
  "total_carbs_grams": number | null,
  "dietary_fiber_grams": number | null,
  "protein_grams": number | null,
  "dietary_fat_grams": number | null,
  "fat_delays_carb_absorption": boolean | null,
  "late_spike_note": string | null
}

Rules:
- Estimate every visible edible item in "portions" with a household portion_label (for example "1 cup", "2 eggs", "medium plate") and an edible portion_grams weight.
- total_carbs_grams is total carbohydrate for the whole plate in grams (including fiber).
- dietary_fiber_grams is dietary fiber for the whole plate in grams.
- protein_grams is protein for the whole plate in grams.
- dietary_fat_grams is dietary fat for the whole plate in grams. Dietary fat delays gastric emptying and carbohydrate absorption, which can shift the glucose peak later (a late spike) rather than lowering the eventual rise. If dietary_fat_grams is 15 or more, set fat_delays_carb_absorption to true and put one short sentence in late_spike_note explaining that fat may delay the carb peak. Otherwise set fat_delays_carb_absorption to false and late_spike_note to null.
- Numbers must be JSON numbers, not strings. Integers are preferred when you are confident.
- Never invent barcodes. Never give insulin doses. Never wrap the JSON in markdown."""

    const val USER_PROMPT =
        "Estimate portions and macros for this meal photo. Return only the JSON object specified in the system prompt."

    private const val FAT_DELAY_GRAMS = 15.0

    const val FAT_DELAY_DEFAULT_NOTE =
        "Fat in this meal may delay carbohydrate absorption and shift the glucose peak later"

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.0f".format(value)
}
