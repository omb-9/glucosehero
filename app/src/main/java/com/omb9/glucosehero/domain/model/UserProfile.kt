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
