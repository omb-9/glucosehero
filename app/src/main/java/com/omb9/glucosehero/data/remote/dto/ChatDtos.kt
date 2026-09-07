package com.omb9.glucosehero.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// --- OpenAI-compatible /chat/completions wire format.
// Gemini's OpenAI-compat endpoint, OpenAI, OpenRouter, and self-hosted
// runtimes (Ollama, llama.cpp, vLLM) all speak this dialect.

@Serializable
data class ApiChatMessage(
    val role: String,
    val content: JsonElement,
) {
    /** Extracts the plain-text body when the provider answered with a string. */
    fun contentText(): String? = (content as? JsonPrimitive)?.contentOrNull

    companion object {
        /** A plain-text message (system/user/assistant) — the common case. */
        fun text(role: String, content: String): ApiChatMessage =
            ApiChatMessage(role = role, content = JsonPrimitive(content))

        /**
         * A multimodal user message: a text prompt plus one inline image.
         * The image is passed as a `data:` URI so its bytes stay in memory and
         * are never written to disk or the local database.
         */
        fun multimodal(role: String, prompt: String, imageDataUri: String): ApiChatMessage =
            ApiChatMessage(
                role = role,
                content = buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    })
                    add(buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject { put("url", imageDataUri) },
                        )
                    })
                },
            )
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val tools: List<ApiTool>? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val provider: OpenRouterProviderConfig? = null,
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

/**
 * OpenRouter routing controls. `data_collection = "deny"` excludes providers
 * that store user data or may train on it, which keeps health prompts from
 * being routed to training-on-input providers.
 */
@Serializable
data class OpenRouterProviderConfig(
    @SerialName("data_collection") val dataCollection: String = "deny",
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
