package com.omb9.glucosehero.ui.settings

/**
 * Definitions for settings terms a non-specialist might not know.
 *
 * Every value below is a placeholder. These are clinical definitions (or
 * explanations of a technical term) and must be written or sourced by the
 * project owner and medically reviewed before release. Do not fill these in
 * from memory.
 */
object SettingsGlossary {
    // TODO(owner): every value below is a placeholder. These are clinical
    // definitions and must be written or sourced by the project owner and
    // medically reviewed before release. Do not fill these in from memory.
    const val GLUCOSE_UNIT = "The measurement standard for your blood sugar readings. The options are milligrams per deciliter (mg/dL) or millimoles per liter (mmol/L)."
    const val TARGET_RANGE = "Your ideal upper and lower blood sugar limits. The app uses these boundaries to calculate your Time in Range (TIR)."
    const val DURATION_OF_INSULIN_ACTION = "How long a dose of rapid-acting insulin remains active in your body. The app uses this to estimate insulin still working (IOB) from doses you already logged."
    const val CARB_RATIO = "The number of carbohydrate grams covered by one unit of insulin, as given by your care team. For example, a CIR of 10 g/U means one unit of insulin covers 10 grams of carbs. This is a record only; the app does not calculate a dose from it."
    const val INSULIN_SENSITIVITY_FACTOR = "How much one unit of insulin lowers your blood sugar, as given by your care team. This is also known as your correction factor. This is a record only; the app does not calculate a dose from it."
    const val TARGET_GLUCOSE = "The blood sugar number your care team gave you to aim for. This is a record only; the app does not calculate a correction dose from it."
    const val HEALTH_CONNECT = "Android's secure hub for health and fitness data. Connecting this allows GlucoseHero to seamlessly sync data like continuous glucose monitor (CGM) readings, nutrition, or exercise with your other apps."
    const val AMOLED = "The visual appearance of the app. Options typically include Light, Dark, or System Default. True dark (AMOLED) themes use deep blacks to reduce eye strain and save battery on supported screens."
}
