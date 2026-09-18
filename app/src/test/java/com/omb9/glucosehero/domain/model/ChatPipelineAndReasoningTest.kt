package com.omb9.glucosehero.domain.model

import com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest
import com.omb9.glucosehero.data.remote.dto.ReasoningConfig
import com.omb9.glucosehero.util.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPipelineAndReasoningTest {

    @Test
    fun defaultReasoningEffortIsOff() {
        assertEquals(ReasoningEffort.OFF, UserSettings().reasoningEffort)
        assertEquals(null, ReasoningEffort.OFF.apiEffort)
        assertEquals("low", ReasoningEffort.LOW.apiEffort)
    }

    @Test
    fun pipelineLabelsAreSpecificAndHaveNoEmDash() {
        val counted = ChatPipelineCopy.readingGlucoseCounted(14, 312)
        assertEquals("Reading 14 days, 312 readings", counted)
        assertTrue(ChatPipelineCopy.computingIob().contains("insulin on board"))
        listOf(
            ChatPipelineCopy.preparing(),
            counted,
            ChatPipelineCopy.computingAverages(),
            ChatPipelineCopy.computingTimeInRange(14),
            ChatPipelineCopy.loadingRecentEntries(30),
            ChatPipelineCopy.computingIob(),
            ChatPipelineCopy.loadingFoodPatterns(),
            ChatPipelineCopy.connecting(),
            ChatPipelineCopy.waiting(),
            ChatPipelineCopy.streaming(),
        ).forEach { label ->
            assertFalse(label, label.contains("—"))
            assertFalse(label, label.contains("Analyzing your glucose"))
        }
    }

    @Test
    fun backupsAndMarkdownWhitelistOmitReasoning() {
        assertFalse(ExportWhitelist.chatKeys.contains("reasoning"))
        assertEquals(
            setOf("id", "role", "content", "timestamp", "messageKind", "contextSummaryJson"),
            ExportWhitelist.chatKeys,
        )
        assertFalse(ExportWhitelist.markdownColumns.any { it.contains("reasoning", ignoreCase = true) })
        assertFalse(ExportWhitelist.settingsKeys.contains("reasoningEffort"))
    }

    @Test
    fun agpAndMarkdownExportsDoNotIncludeChat() {
        assertFalse(ExportWhitelist.markdownColumns.any { it.equals("Chat", ignoreCase = true) })
        assertFalse(ExportWhitelist.csvColumns.any { it.equals("Chat", ignoreCase = true) })
        assertFalse(ExportWhitelist.markdownColumns.contains("content"))
        assertTrue(ExportWhitelist.chatKeys.contains("content"))
    }

    @Test
    fun reasoningConfigOmitsNulls() {
        val encoded = AppJson.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = "deepseek/deepseek-v4-flash",
                messages = emptyList(),
                stream = true,
                reasoning = ReasoningConfig(enabled = false),
            ),
        )
        assertTrue(encoded.contains("\"reasoning\""))
        assertTrue(encoded.contains("\"enabled\":false"))
        assertFalse(encoded.contains("max_tokens"))
    }

    @Test
    fun idleAndDoneAreNotInFlight() {
        assertFalse(ChatPipelineStatus.Idle.isInFlight)
        assertFalse(ChatPipelineStatus.Done.isInFlight)
        assertTrue(ChatPipelineStatus.Waiting.isInFlight)
        assertTrue(ChatPipelineStatus.GatheringContext("Reading 14 days, 1 readings").isInFlight)
    }
}
