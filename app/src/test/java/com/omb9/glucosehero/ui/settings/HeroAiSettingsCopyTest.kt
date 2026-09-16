package com.omb9.glucosehero.ui.settings

import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroAiSettingsCopyTest {

    @Test
    fun managedOpenRouterDoesNotNeedProviderSetup() {
        val config = AiConfig(
            provider = AiProvider.OPENROUTER,
            hasApiKey = false,
        )
        assertFalse(HeroAiSettingsCopy.needsProviderSetup(config))
        assertTrue(HeroAiSettingsCopy.isManagedModel(config))
        assertEquals("Using GlucoseHero's model", HeroAiSettingsCopy.byokSubtitle(config))
    }

    @Test
    fun geminiWithoutKeyNeedsSetup() {
        val config = AiConfig(
            provider = AiProvider.GEMINI,
            model = "gemini-2.0-flash",
            hasApiKey = false,
        )
        assertTrue(HeroAiSettingsCopy.needsProviderSetup(config))
        assertEquals(
            "Google Gemini, gemini-2.0-flash",
            HeroAiSettingsCopy.byokSubtitle(config),
        )
    }

    @Test
    fun customWithoutKeyCanChat() {
        val config = AiConfig(
            provider = AiProvider.CUSTOM,
            model = "llama3.1",
            hasApiKey = false,
        )
        assertFalse(HeroAiSettingsCopy.needsProviderSetup(config))
        assertEquals(
            "Custom (OpenAI-compatible), llama3.1",
            HeroAiSettingsCopy.byokSubtitle(config),
        )
    }

    @Test
    fun byokOpenRouterShowsProviderAndModel() {
        val config = AiConfig(
            provider = AiProvider.OPENROUTER,
            model = "deepseek-v4-flash",
            hasApiKey = true,
        )
        assertFalse(HeroAiSettingsCopy.needsProviderSetup(config))
        assertFalse(HeroAiSettingsCopy.isManagedModel(config))
        assertEquals(
            "OpenRouter, deepseek-v4-flash",
            HeroAiSettingsCopy.byokSubtitle(config),
        )
    }

    @Test
    fun remainingCallsHasNoEmDash() {
        val settingsZero = HeroAiSettingsCopy.remainingCallsSettings(0)
        val bannerZero = HeroAiSettingsCopy.remainingCallsBanner(0)
        assertFalse(settingsZero.contains("—"))
        assertFalse(bannerZero.contains("—"))
        assertEquals("1 AI call left today.", HeroAiSettingsCopy.remainingCallsSettings(1))
        assertEquals("7 AI calls left today.", HeroAiSettingsCopy.remainingCallsBanner(7))
    }

    @Test
    fun connectionFailurePrefersProviderMessage() {
        val body = """{"error":{"message":"Invalid API key"}}"""
        assertEquals(
            "HTTP 401: Invalid API key",
            HeroAiSettingsCopy.connectionFailure(httpCode = 401, httpBody = body),
        )
    }

    @Test
    fun connectionFailureFallsBackToHttpCode() {
        assertEquals(
            "HTTP 503",
            HeroAiSettingsCopy.connectionFailure(httpCode = 503),
        )
    }

    @Test
    fun connectionFailureMissingKeyUsesFallback() {
        assertEquals(
            "No API key configured. Add one on this page.",
            HeroAiSettingsCopy.connectionFailure(isMissingKey = true),
        )
        assertEquals(
            "Stored key is gone.",
            HeroAiSettingsCopy.connectionFailure(
                message = "Stored key is gone.",
                isMissingKey = true,
            ),
        )
    }

    @Test
    fun connectionFailureUsesIoMessageWhenNoHttp() {
        assertEquals(
            "Failed to connect",
            HeroAiSettingsCopy.connectionFailure(message = "Failed to connect"),
        )
    }
}
