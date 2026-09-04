package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable

enum class ChatRole { USER, ASSISTANT, SYSTEM }

@Immutable
data class ChatTurn(
    val id: Long = 0L,
    val role: ChatRole,
    val content: String,
    val timestamp: Long,
)

/** Events emitted while streaming an LLM reply over SSE. */
sealed interface StreamEvent {
    data class Token(val text: String) : StreamEvent

    /**
     * A function/tool call requested by the model. [arguments] is the raw JSON
     * object payload (possibly empty while the model streams the schema).
     */
    data class FunctionCall(
        val name: String,
        val arguments: String,
    ) : StreamEvent

    data class Done(val fullText: String) : StreamEvent
    data class Failure(val throwable: Throwable) : StreamEvent
}
