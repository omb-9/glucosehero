package com.omb9.glucosehero.data.remote.sse

import com.omb9.glucosehero.data.remote.dto.ChatCompletionChunk

/**
 * Extracts plaintext reasoning from an OpenRouter (and OpenAI-compat) chat
 * completion delta. Live docs (2026):
 * https://openrouter.ai/docs/guides/best-practices/reasoning-tokens
 *
 * Streaming fields, in priority order:
 * 1. `choices[].delta.reasoning` (plain string OpenRouter emits)
 * 2. `choices[].delta.reasoning_details[]` of type `reasoning.text` / `reasoning.summary`
 *
 * Encrypted / redacted details are dropped. Never invent text.
 */
object ReasoningDelta {
    fun plaintext(delta: ChatCompletionChunk.Delta): String? {
        val plain = delta.reasoning?.takeIf { it.isNotEmpty() && it != REDACTED }
        if (plain != null) return plain

        val details = delta.reasoningDetails ?: return null
        val assembled = buildString {
            details.forEach { detail ->
                val piece = when {
                    !detail.text.isNullOrEmpty() && detail.text != REDACTED -> detail.text
                    !detail.summary.isNullOrEmpty() && detail.summary != REDACTED -> detail.summary
                    else -> null
                }
                if (piece != null) append(piece)
            }
        }
        return assembled.takeIf { it.isNotEmpty() }
    }

    private const val REDACTED = "[REDACTED]"
}
