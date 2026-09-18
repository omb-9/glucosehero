package com.omb9.glucosehero.data.remote.sse

import com.omb9.glucosehero.data.remote.dto.ChatCompletionChunk
import com.omb9.glucosehero.data.remote.dto.ReasoningDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningDeltaTest {

    @Test
    fun prefersPlainReasoningString() {
        val delta = ChatCompletionChunk.Delta(
            content = "answer",
            reasoning = "step one",
            reasoningDetails = listOf(ReasoningDetail(type = "reasoning.text", text = "ignored")),
        )
        assertEquals("step one", ReasoningDelta.plaintext(delta))
    }

    @Test
    fun concatenatesTextAndSummaryDetails() {
        val delta = ChatCompletionChunk.Delta(
            reasoningDetails = listOf(
                ReasoningDetail(type = "reasoning.text", text = "A"),
                ReasoningDetail(type = "reasoning.summary", summary = "B"),
                ReasoningDetail(type = "reasoning.encrypted", text = null),
            ),
        )
        assertEquals("AB", ReasoningDelta.plaintext(delta))
    }

    @Test
    fun dropsRedactedAndEmpty() {
        val delta = ChatCompletionChunk.Delta(
            reasoning = "[REDACTED]",
            reasoningDetails = listOf(
                ReasoningDetail(text = "[REDACTED]"),
                ReasoningDetail(summary = ""),
            ),
        )
        assertNull(ReasoningDelta.plaintext(delta))
    }

    @Test
    fun noReasoningReturnsNull() {
        val delta = ChatCompletionChunk.Delta(content = "hi")
        assertNull(ReasoningDelta.plaintext(delta))
    }
}
