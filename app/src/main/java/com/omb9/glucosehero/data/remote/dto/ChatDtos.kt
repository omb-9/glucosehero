package com.omb9.glucosehero.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- OpenAI-compatible /chat/completions wire format.
// Gemini's OpenAI-compat endpoint, OpenAI, OpenRouter, and self-hosted
// runtimes (Ollama, llama.cpp, vLLM) all speak this dialect.

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val tools: List<ApiTool>? = null,
)

@Serializable
data class ApiTool(
    val type: String = "function",
    val function: ApiFunction,
)

@Serializable
data class ApiFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

// Non-streaming response (background worker path)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(
        val message: ApiChatMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )
}

// Streaming SSE chunk (live chat path)

@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
) {
    @Serializable
    data class ChunkChoice(
        val delta: Delta = Delta(),
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        @SerialName("tool_calls") val toolCalls: List<DeltaToolCall>? = null,
    )
}

@Serializable
data class DeltaToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: DeltaFunction? = null,
)

@Serializable
data class DeltaFunction(
    val name: String? = null,
    val arguments: String? = null,
)
