package com.omb9.glucosehero.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nutrition estimate returned by Hero AI for a captured meal photo.
 *
 * Every field is optional so a provider that can only name the dish (or only
 * count carbs) still produces a usable prefill; missing fields leave the
 * corresponding slot in the Add Entry sheet untouched. The bytes of the
 * source photo are never part of this value — only the estimates derived from
 * it — so nothing image-related is persisted locally.
 */
@Serializable
data class MealPhotoAnalysis(
    @SerialName("carbs_grams")
    val carbsGrams: Int? = null,
    @SerialName("protein_grams")
    val proteinGrams: Int? = null,
    @SerialName("fat_grams")
    val fatGrams: Int? = null,
    @SerialName("description")
    val description: String? = null,
)
