package com.omb9.glucosehero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunPolicyTest {

    @Test
    fun completedFirstRunIsNoOp() {
        assertEquals(
            FirstRunAction.NO_OP,
            FirstRunPolicy.decide(
                firstRunCompleted = true,
                defaultsSeeded = true,
                preferenceKeyNames = emptySet(),
                hasExistingUserData = true,
            ),
        )
    }

    @Test
    fun emptyStoreSeedsTimeAndPromptsUnit() {
        assertEquals(
            FirstRunAction.SEED_TIME_AND_PROMPT_UNIT,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = false,
                preferenceKeyNames = emptySet(),
                hasExistingUserData = false,
            ),
        )
    }

    @Test
    fun afterSeedingShowsUnitPrompt() {
        assertEquals(
            FirstRunAction.PROMPT_UNIT,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = true,
                preferenceKeyNames = setOf(
                    FirstRunPolicy.DEFAULTS_SEEDED_KEY,
                    FirstRunPolicy.USE_24H_KEY,
                ),
                hasExistingUserData = false,
            ),
        )
    }

    @Test
    fun existingSettingsWithoutUnitAreGrandfathered() {
        assertEquals(
            FirstRunAction.GRANDFATHER,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = false,
                preferenceKeyNames = setOf("theme_mode"),
                hasExistingUserData = false,
            ),
        )
    }

    @Test
    fun existingUse24hWithoutSeedFlagIsGrandfathered() {
        assertEquals(
            FirstRunAction.GRANDFATHER,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = false,
                preferenceKeyNames = setOf(FirstRunPolicy.USE_24H_KEY),
                hasExistingUserData = false,
            ),
        )
    }

    @Test
    fun roomDataWithoutSettingsIsGrandfathered() {
        assertEquals(
            FirstRunAction.GRANDFATHER,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = false,
                preferenceKeyNames = emptySet(),
                hasExistingUserData = true,
            ),
        )
    }

    @Test
    fun explicitUnitKeyGrandfathersEvenIfSeeded() {
        assertEquals(
            FirstRunAction.GRANDFATHER,
            FirstRunPolicy.decide(
                firstRunCompleted = false,
                defaultsSeeded = true,
                preferenceKeyNames = setOf(
                    FirstRunPolicy.DEFAULTS_SEEDED_KEY,
                    FirstRunPolicy.UNIT_KEY,
                ),
                hasExistingUserData = false,
            ),
        )
    }
}
