package com.omb9.glucosehero.domain.model

/**
 * First-launch seeding for locale-aware defaults.
 *
 * Glucose unit is never written until the user confirms a choice. 24-hour time
 * is seeded from the system formatter on a true first run only. Existing
 * installs (any other DataStore keys, or log/CGM rows) are grandfathered so
 * implicit MGDL / 12-hour defaults are not overwritten.
 */
enum class FirstRunAction {
    NO_OP,
    GRANDFATHER,
    SEED_TIME_AND_PROMPT_UNIT,
    PROMPT_UNIT,
}

object FirstRunPolicy {
    const val COMPLETED_KEY = "first_run_completed"
    const val DEFAULTS_SEEDED_KEY = "first_run_defaults_seeded"
    const val USE_24H_KEY = "use_24h"
    const val UNIT_KEY = "glucose_unit"

    private val firstRunKeys = setOf(COMPLETED_KEY, DEFAULTS_SEEDED_KEY)

    fun decide(
        firstRunCompleted: Boolean,
        defaultsSeeded: Boolean,
        preferenceKeyNames: Set<String>,
        hasExistingUserData: Boolean,
    ): FirstRunAction {
        if (firstRunCompleted) return FirstRunAction.NO_OP
        if (defaultsSeeded) {
            return if (UNIT_KEY in preferenceKeyNames) {
                FirstRunAction.GRANDFATHER
            } else {
                FirstRunAction.PROMPT_UNIT
            }
        }
        if (hasExistingUserData) return FirstRunAction.GRANDFATHER
        val remaining = preferenceKeyNames - firstRunKeys
        if (remaining.isNotEmpty()) return FirstRunAction.GRANDFATHER
        return FirstRunAction.SEED_TIME_AND_PROMPT_UNIT
    }
}
