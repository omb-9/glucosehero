package com.omb9.glucosehero.ui.settings

import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.util.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * User-facing Hero AI / BYOK copy. Pure so unit tests can pin strings without
 * Compose or a provider round-trip.
 */
object HeroAiSettingsCopy {

    const val DATA_USE_DESCRIPTION =
        "When Hero AI is on, your questions include a snapshot of recent logs " +
            "(glucose, meals, insulin, and related notes) so answers can be grounded " +
            "in your data. That prompt is sent to the AI provider for this account. " +
            "GlucoseHero's model is used unless you add your own API key. A key you " +
            "enter is stored encrypted on this device and is only sent to the provider " +
            "you choose."

    const val CHAT_SETUP_BANNER =
        "Hero AI uses GlucoseHero's model by default. This provider needs an API key. " +
            "Add one under Settings → Hero AI → Use your own API key, or switch back to " +
            "GlucoseHero's model."

    const val CONNECTION_SUCCESS = "Connection succeeded."

    const val CONNECTION_RUNNING = "Testing connection..."

    /**
     * OpenRouter without a user key is the managed GlucoseHero model. Custom
     * endpoints typically need no key. Any other provider without a stored key
     * cannot start a chat until BYOK is configured.
     */
    fun needsProviderSetup(config: AiConfig): Boolean {
        if (config.hasApiKey) return false
        if (config.provider == AiProvider.CUSTOM) return false
        if (config.provider == AiProvider.OPENROUTER) return false
        return true
    }

    fun isManagedModel(config: AiConfig): Boolean =
        config.provider == AiProvider.OPENROUTER && !config.hasApiKey

    fun byokSubtitle(config: AiConfig): String {
        if (isManagedModel(config)) return "Using GlucoseHero's model"
        val trimmedModel = config.model.trim()
        return if (trimmedModel.isEmpty()) {
            config.provider.label
        } else {
            "${config.provider.label}, $trimmedModel"
        }
    }

    fun remainingCallsSettings(remaining: Int): String = when (remaining) {
        0 -> "No AI calls left today. Resets at midnight."
        1 -> "1 AI call left today."
        else -> "$remaining AI calls left today."
    }

    fun remainingCallsBanner(remaining: Int): String = when (remaining) {
        0 -> "You've used all your AI calls today. Resets at midnight."
        1 -> "1 AI call left today."
        else -> "$remaining AI calls left today."
    }

    fun remainingCallsTopBar(remaining: Int): String = when (remaining) {
        0 -> "None left"
        1 -> "1 left"
        else -> "$remaining left"
    }

    const val REASONING_DESCRIPTION =
        "When on, Hero can show real model reasoning if the provider sends it. " +
            "Reasoning uses extra tokens and can be slower. Off is the default " +
            "because managed calls are capped daily. This is not a clinical assessment. " +
            "If your model never emits reasoning, Hero shows only real pipeline status."

    fun connectionFailure(
        message: String? = null,
        httpCode: Int? = null,
        httpBody: String? = null,
        isMissingKey: Boolean = false,
    ): String {
        if (isMissingKey) {
            return message?.trim()?.takeIf { it.isNotEmpty() }
                ?: "No API key configured. Add one on this page."
        }
        val providerDetail = providerErrorDetail(httpBody)
        val detail = providerDetail
            ?: httpBody?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_ERROR_CHARS)
            ?: message?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            httpCode != null && !detail.isNullOrBlank() -> "HTTP $httpCode: $detail"
            httpCode != null -> "HTTP $httpCode"
            !detail.isNullOrBlank() -> detail
            else -> "Couldn't reach the provider."
        }
    }

    internal fun providerErrorDetail(httpBody: String?): String? {
        if (httpBody.isNullOrBlank()) return null
        val parsed = runCatching {
            val root = AppJson.parseToJsonElement(httpBody).jsonObject
            val error = root["error"]
            val fromError = when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
            fromError ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return parsed?.take(MAX_ERROR_CHARS)
    }

    private const val MAX_ERROR_CHARS = 280
}
