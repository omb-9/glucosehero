package com.omb9.glucosehero.ui.chat

import com.omb9.glucosehero.domain.model.ChatContextSummary
import com.omb9.glucosehero.util.AppJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPresentationTest {

    @Test
    fun nearBottomWhenLastVisibleIsWithinThreshold() {
        assertTrue(isNearBottom(lastVisibleIndex = 7, totalItems = 10, threshold = 3))
        assertFalse(isNearBottom(lastVisibleIndex = 4, totalItems = 10, threshold = 3))
        assertTrue(isNearBottom(lastVisibleIndex = null, totalItems = 10))
        assertTrue(isNearBottom(lastVisibleIndex = 0, totalItems = 0))
    }

    @Test
    fun followStreamOnlyWhenAlreadyNearBottom() {
        assertTrue(shouldFollowStream(nearBottom = true, streaming = true))
        assertFalse(shouldFollowStream(nearBottom = false, streaming = true))
        assertFalse(shouldFollowStream(nearBottom = true, streaming = false))
    }

    @Test
    fun groundedChatNeedsAtLeastThreeGlucosePoints() {
        assertFalse(hasEnoughDataForGroundedChat(0))
        assertFalse(hasEnoughDataForGroundedChat(2))
        assertTrue(hasEnoughDataForGroundedChat(3))
    }

    @Test
    fun streamingSkipsMarkdownUntilComplete() {
        assertFalse(shouldParseMarkdown(messageComplete = false))
        assertTrue(shouldParseMarkdown(messageComplete = true))
    }

    @Test
    fun stopPersistsNonBlankPartialOnly() {
        assertEquals("Hello", persistPartialOnStop("Hello"))
        assertNull(persistPartialOnStop(""))
        assertNull(persistPartialOnStop("   ".trim()))
    }

    @Test
    fun contextChipOmitsGuessedFieldsAndStaysCompact() {
        val summary = ChatContextSummary(
            historyDays = 14,
            cgmReadingCount = 120,
            manualReadingCount = 8,
            recentEntryCount = 30,
            includedIob = true,
            includedCarbs = true,
            includedInsulin = true,
            includedMeals = false,
            foodPatternCount = 2,
            conversationTurns = 6,
            includedProfile = true,
        )
        assertEquals("14 days · 128 readings · IOB · carbs", summary.chipLabel())
        val lines = summary.sheetLines()
        assertTrue(lines.any { it.contains("120 CGM") })
        assertTrue(lines.any { it.contains("Medication names: withheld") })
        assertTrue(lines.any { it.contains("Feeling-sick flags: not sent") })
        assertFalse(lines.any { it.contains("—") })
        val encoded = AppJson.encodeToString(summary)
        val decoded = AppJson.decodeFromString<ChatContextSummary>(encoded)
        assertEquals(summary, decoded)
    }

    @Test
    fun crisisComposerCanSendWithoutProviderOrQuota() {
        assertTrue(
            chatComposerSendEnabled(
                input = "I want to die",
                streamingActive = false,
                composerEnabled = false,
                remainingCalls = 0,
            ),
        )
        assertFalse(
            chatComposerSendEnabled(
                input = "what was my average?",
                streamingActive = false,
                composerEnabled = false,
                remainingCalls = 0,
            ),
        )
        assertTrue(
            chatComposerSendEnabled(
                input = "what was my average?",
                streamingActive = false,
                composerEnabled = true,
                remainingCalls = null,
            ),
        )
    }

    @Test
    fun historicalTurnsWithoutSummaryHaveNoChip() {
        assertNull(
            com.omb9.glucosehero.domain.model.ChatTurn(
                id = 1L,
                role = com.omb9.glucosehero.domain.model.ChatRole.USER,
                content = "old",
                timestamp = 1L,
            ).contextSummary,
        )
    }
}
