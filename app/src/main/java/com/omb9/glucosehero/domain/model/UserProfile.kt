package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable

/** Who the logged-in user is tracking glucose data for. */
enum class ProfileTarget(val displayName: String) {
    SELF("Me"),
    CHILD("My Child"),
    PARTNER("My Partner"),
    PARENT("My Parent"),
    PATIENT("My Patient"),
}

/**
 * The complete set of diabetes types offered by the Profile settings dropdown.
 *
 * [label] is the human-readable value shown in the UI and persisted to the
 * local Datastore, keeping the stored string backwards-compatible with values
 * previously captured as free text.
 */
enum class DiabetesType(val label: String) {
    TYPE_1("Type 1"),
    TYPE_2("Type 2"),
    GESTATIONAL("Gestational"),
    LADA("LADA (Latent Autoimmune Diabetes in Adults)"),
    MODY("MODY (Maturity-Onset Diabetes of the Young)"),
    PREDIABETES("Prediabetes"),
    OTHER("Other"),
    ;

    companion object {
        /** Resolves a previously-persisted value back to a menu entry, if it matches. */
        fun fromStored(value: String?): DiabetesType? {
            val normalized = value?.trim().orEmpty()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.label.equals(normalized, ignoreCase = true) }
        }
    }
}

/**
 * Optional biometric profile injected into Hero AI's system prompt so it can
 * tailor tone, pronouns, and clinical context to the logged-in user's role.
 */
@Immutable
data class UserProfile(
    val profileTarget: ProfileTarget = ProfileTarget.SELF,
    val name: String = "",
    val age: Int? = null,
    val diabetesType: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
)
