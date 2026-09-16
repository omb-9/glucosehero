package com.omb9.glucosehero.data.remote

import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.MealContext
import kotlinx.serialization.Serializable

/**
 * JSON body POSTed to the user-configured local webhook when a manual
 * glucose entry is saved, and by the Advanced settings test button.
 */
@Serializable
data class WebhookEntryPayload(
    val id: Long,
    val timestamp: Long,
    val glucoseMgdl: Double? = null,
    val mealContext: MealContext? = null,
    val insulinBasalUnits: Double? = null,
    val insulinBolusUnits: Double? = null,
    val carbsGrams: Int? = null,
    val proteinGrams: Int? = null,
    val fatGrams: Int? = null,
    val mealDescription: String? = null,
    val exerciseMinutes: Int? = null,
    val exerciseIntensity: ActivityIntensity? = null,
    val note: String? = null,
    val moodScore: Int? = null,
    val moodLabel: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
) {
    companion object {
        fun testSample(nowMillis: Long = System.currentTimeMillis()): WebhookEntryPayload =
            WebhookEntryPayload(
                id = 0,
                timestamp = nowMillis,
                glucoseMgdl = 100.0,
                note = "GlucoseHero webhook test",
                source = EntrySource.MANUAL,
            )
    }
}
