package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable
import java.io.IOException

/**
 * Bring-your-own-key provider presets. Every preset speaks the OpenAI-compatible
 * /chat/completions dialect so one client covers them all; CUSTOM unlocks the
 * base-URL field for self-hosted endpoints (e.g. an Ollama box exposing /v1).
 */
enum class AiProvider(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    GEMINI(
        label = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-2.0-flash",
    ),
    OPENAI(
        label = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1/",
        defaultModel = "gpt-4o-mini",
    ),
    OPENROUTER(
        label = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "minimax/minimax-m3:free",
    ),
    CUSTOM(
        label = "Custom (OpenAI-compatible)",
        defaultBaseUrl = "http://192.168.1.10:11434/v1/",
        defaultModel = "llama3.1",
    ),
}

/** Persisted AI configuration. [hasApiKey] is true when a KeyStore-wrapped key is stored. */
@Immutable
data class AiConfig(
    val provider: AiProvider = AiProvider.GEMINI,
    val baseUrl: String = AiProvider.GEMINI.defaultBaseUrl,
    val model: String = AiProvider.GEMINI.defaultModel,
    val hasApiKey: Boolean = false,
)

/** Fully resolved, in-memory-only config. The decrypted key never touches disk. */
data class ResolvedAiConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

/**
 * Extends [IOException] deliberately: this exception is raised inside
 * [com.omb9.glucosehero.data.remote.DynamicApiInterceptor], and OkHttp only
 * tolerates IOException from interceptors on enqueued calls (which Retrofit
 * suspend functions use). Any other Throwable is rethrown on OkHttp's
 * dispatcher thread and crashes the process.
 */
class ApiKeyMissingException(
    message: String = "No API key configured. Add one in Settings → Hero AI."
) : IOException(message)

/**
 * Raised when the provider answers with a non-2xx HTTP status. Unlike a plain
 * [IOException] (which the ViewModel interprets as "offline" and queues for
 * retry), this signals a misconfiguration — bad key, wrong model name, wrong
 * base URL — so callers can surface a settings warning instead of retrying.
 */
class ProviderHttpException(
    message: String,
) : IOException(message)

/**
 * Raised when a managed-tier request would exceed the local daily allowance.
 * This is a UX signal only, not a security boundary.
 */
class QuotaExhaustedException(
    message: String,
) : Exception(message)

/**
 * Raised when a structured AI call (meal photo, quick-log) returned text that
 * is not the JSON object the prompt required. Callers should surface this as
 * a parse failure rather than treating the raw prose as a meal name.
 */
class InvalidAiJsonException(
    message: String = "The AI returned data that could not be parsed.",
) : Exception(message)

