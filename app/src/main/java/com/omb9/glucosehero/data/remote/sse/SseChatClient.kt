package com.omb9.glucosehero.data.remote.sse

import com.omb9.glucosehero.data.remote.dto.ApiChatMessage
import com.omb9.glucosehero.data.remote.dto.ApiTool
import com.omb9.glucosehero.data.remote.dto.ChatCompletionChunk
import com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest
import com.omb9.glucosehero.domain.model.ResolvedAiConfig
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.util.AppJson
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Streams /chat/completions responses token-by-token over Server-Sent Events
 * and exposes them as a cold [Flow] of [StreamEvent]s — the ViewModel folds the
 * tokens into a StateFlow which Compose renders as a live typewriter effect.
 *
 * Function/tool calls (streamed as `delta.tool_calls`) are accumulated by their
 * `index` and emitted as [StreamEvent.FunctionCall] events once the stream
 * finishes, so callers receive complete JSON arguments rather than fragments.
 */
@Singleton
class SseChatClient @Inject constructor(
    @Named("sse") private val client: OkHttpClient,
) {

    fun stream(
        config: ResolvedAiConfig,
        messages: List<ApiChatMessage>,
        tools: List<ApiTool> = emptyList(),
    ): Flow<StreamEvent> = callbackFlow {
        val body = AppJson.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = config.model,
                messages = messages,
                stream = true,
                tools = tools.takeIf { it.isNotEmpty() },
            ),
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val accumulated = StringBuilder()
        val toolCalls = linkedMapOf<Int, ToolCallAccumulator>()
        val finished = AtomicBoolean(false)

        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            toolCalls.values.forEach { call ->
                val name = call.name?.takeIf { it.isNotBlank() } ?: return@forEach
                trySend(StreamEvent.FunctionCall(name, call.arguments.toString()))
            }
            trySend(StreamEvent.Done(accumulated.toString()))
            close()
        }

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data.trim() == "[DONE]") {
                    finish()
                    return
                }

                runCatching {
                    AppJson.decodeFromString(ChatCompletionChunk.serializer(), data)
                }.getOrNull()?.let { chunk ->
                    val delta = chunk.choices.firstOrNull()?.delta ?: return@let

                    delta.content
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { token ->
                            accumulated.append(token)
                            trySend(StreamEvent.Token(token))
                        }

                    delta.toolCalls?.forEach { call ->
                        val index = call.index ?: 0
                        val accumulator = toolCalls.getOrPut(index) { ToolCallAccumulator() }
                        call.id?.let { accumulator.id = it }
                        call.function?.name?.let { accumulator.name = it }
                        call.function?.arguments?.let { accumulator.arguments.append(it) }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Providers that omit [DONE] simply close the stream.
                finish()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                if (!finished.compareAndSet(false, true)) return
                val error = when {
                    response != null && !response.isSuccessful -> {
                        // Over HTTP/2, response.message is always "" — the
                        // provider's actual error (bad key, wrong model name)
                        // lives in the body. Read defensively: it may already
                        // be consumed or closed by the SSE machinery.
                        val bodyExcerpt = runCatching {
                            response.body?.string()?.take(200)
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        IOException(
                            "HTTP ${response.code}" +
                                (bodyExcerpt?.let { ": $it" } ?: "")
                        )
                    }
                    t != null -> t
                    else -> IOException("SSE stream failed")
                }
                trySend(StreamEvent.Failure(error))
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    private class ToolCallAccumulator {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
}
