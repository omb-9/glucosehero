package com.omb9.glucosehero.data.local.db.migration

import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.InsulinType
import com.omb9.glucosehero.domain.model.MealContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Frozen copy of the pre-v2 polymorphic `EntryDetails` hierarchy, kept only so
 * [Migration1To2] can decode the JSON blobs stored in the v1 `details` TEXT
 * column while lifting their fields out into the v2 entries columns.
 *
 * The `@SerialName` values below MUST match the original hierarchy exactly
 * ("glucose", "insulin", "meal", "activity", "note") or every historical row
 * of that kind fails to parse and its extras are silently dropped during the
 * migration. Never change this file to "clean it up" — it is a historical
 * artifact, not part of the live domain model.
 */
@Serializable
internal sealed interface LegacyEntryDetails {

    @Serializable
    @SerialName("glucose")
    data class Glucose(
        val context: MealContext = MealContext.NONE,
    ) : LegacyEntryDetails

    @Serializable
    @SerialName("insulin")
    data class Insulin(
        val insulinType: InsulinType,
        val units: Double,
    ) : LegacyEntryDetails

    @Serializable
    @SerialName("meal")
    data class Meal(
        val carbsGrams: Int? = null,
        val description: String? = null,
    ) : LegacyEntryDetails

    @Serializable
    @SerialName("activity")
    data class Activity(
        val durationMinutes: Int,
        val intensity: ActivityIntensity = ActivityIntensity.MODERATE,
    ) : LegacyEntryDetails

    @Serializable
    @SerialName("note")
    data object Note : LegacyEntryDetails
}
