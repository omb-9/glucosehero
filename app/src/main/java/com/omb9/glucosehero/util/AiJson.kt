package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.InvalidAiJsonException
import kotlinx.serialization.DeserializationStrategy

/**
 * Helpers for the structured JSON the meal-photo and quick-log prompts demand.
 *
 * Providers sometimes wrap the object in markdown fences or a short preface;
 * [extractObject] peels that away so [decodeStrict] can require a real object.
 */
object AiJson {

    fun extractObject(raw: String): String? {
        val trimmed = stripFences(raw.trim())
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return null
    }

    fun <T> decodeStrict(raw: String, deserializer: DeserializationStrategy<T>): T {
        val candidates = buildList {
            val trimmed = stripFences(raw.trim())
            if (trimmed.isNotEmpty()) add(trimmed)
            extractObject(raw)?.let { add(it) }
        }.distinct()
        if (candidates.isEmpty()) {
            throw InvalidAiJsonException()
        }
        var lastError: Throwable? = null
        for (candidate in candidates) {
            val parsed = runCatching {
                AppJson.decodeFromString(deserializer, candidate)
            }
            parsed.getOrNull()?.let { return it }
            lastError = parsed.exceptionOrNull()
        }
        throw InvalidAiJsonException(
            lastError?.message?.let { "The AI returned data that could not be parsed: $it" }
                ?: "The AI returned data that could not be parsed.",
        )
    }

    private fun stripFences(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val close = text.lastIndexOf("```")
            if (close >= 0) text = text.substring(0, close)
            text = text.trim()
        }
        return text
    }
}
