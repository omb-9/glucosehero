package com.omb9.glucosehero.data.nlp

import com.omb9.glucosehero.domain.model.InvalidAiJsonException
import com.omb9.glucosehero.domain.model.QuickLogParseResult
import com.omb9.glucosehero.util.AiJson

/**
 * Prompt + JSON contract for natural-language / voice quick-log.
 *
 * The completion is a one-shot structured extract into EntryEntity / FoodEntity
 * slots. [NaturalLanguageLogParser] is the on-device fallback when this parse
 * fails or Hero AI is unavailable.
 */
object QuickLogNlp {

    fun parseResponse(raw: String): QuickLogParseResult {
        val parsed = AiJson.decodeStrict(raw, QuickLogParseResult.serializer())
        if (parsed.isEmpty) {
            throw InvalidAiJsonException(
                "The AI returned JSON without a glucose, insulin, meal, or exercise value.",
            )
        }
        return parsed
    }

    const val SYSTEM_PROMPT =
        """You are Hero, the logging assistant inside GlucoseHero, a personal glucose logging app.

The user dictated or typed a log line. Extract the entities into ONE JSON object. Return ONLY that object. No markdown, no code fences, no prose.

The JSON MUST match this schema (all keys present; use null for unknown values and [] when no foods are named):
{
  "glucose_value": number | null,
  "glucose_unit": "mg/dL" | "mmol/L" | null,
  "insulin_basal_units": number | null,
  "insulin_bolus_units": number | null,
  "carbs_grams": number | null,
  "protein_grams": number | null,
  "fat_grams": number | null,
  "meal_description": string | null,
  "foods": [
    {
      "name": string,
      "portion_label": string | null,
      "portion_grams": number | null,
      "carbs_grams": number | null,
      "protein_grams": number | null,
      "fat_grams": number | null
    }
  ],
  "exercise_minutes": integer | null,
  "note": string | null,
  "minutes_ago": integer | null
}

Rules:
- Map rapid / bolus insulin (Humalog, NovoLog, NovoRapid, Apidra, Fiasp, Lyumjev, Admelog, lispro, aspart, glulisine) to insulin_bolus_units.
- Map long-acting / basal insulin (Lantus, Levemir, Tresiba, Toujeo, Basaglar, Semglee, glargine, detemir, degludec, NPH) to insulin_basal_units.
- If the user says "units" without a brand, treat it as bolus.
- "X grams of FOOD" is that food's carbohydrate grams unless they clearly meant plate weight. Still add the food to "foods" with portion_label copied from their words.
- Eggs: put them in foods (portion_label like "2 eggs") and add typical protein and fat for that count into the food object and the plate totals.
- minutes_ago is how many minutes before now the event happened ("15 minutes ago" -> 15, "an hour ago" -> 60). Use 0 or null if they did not say.
- glucose_value is exactly as spoken. glucose_unit is "mg/dL" or "mmol/L" if they said a unit, otherwise null.
- meal_description is a short phrase listing the foods (for example "oatmeal, 2 eggs").
- Never invent insulin doses the user did not state. Never wrap the JSON in markdown.

Example input: "Logged 40 grams of oatmeal, 2 eggs, and 3.5 units Humalog 15 minutes ago"
Example output:
{"glucose_value":null,"glucose_unit":null,"insulin_basal_units":null,"insulin_bolus_units":3.5,"carbs_grams":40,"protein_grams":12.6,"fat_grams":10,"meal_description":"oatmeal, 2 eggs","foods":[{"name":"oatmeal","portion_label":"40 g oatmeal","portion_grams":40,"carbs_grams":40,"protein_grams":null,"fat_grams":null},{"name":"eggs","portion_label":"2 eggs","portion_grams":100,"carbs_grams":0.8,"protein_grams":12.6,"fat_grams":10}],"exercise_minutes":null,"note":null,"minutes_ago":15}"""

    const val USER_PROMPT_PREFIX =
        "Extract a log entry from this text. Return only the JSON object specified in the system prompt:\n\n"
}
