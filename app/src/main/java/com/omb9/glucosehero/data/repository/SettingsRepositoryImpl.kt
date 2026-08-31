package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.security.KeystoreManager
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ResolvedAiConfig
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val keystoreManager: KeystoreManager,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.settings
    override val aiConfig: Flow<AiConfig> = dataStore.aiConfig
    override val profile: Flow<UserProfile> = dataStore.profile

    override suspend fun profileSnapshot(): UserProfile = dataStore.profileSnapshot()

    override suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    override suspend fun setAccent(accent: AccentColor) = dataStore.setAccent(accent)
    override suspend fun setUnit(unit: GlucoseUnit) = dataStore.setUnit(unit)
    override suspend fun setUse24HourTime(enabled: Boolean) = dataStore.setUse24HourTime(enabled)
    override suspend fun setIsHeroAiEnabled(enabled: Boolean) = dataStore.setIsHeroAiEnabled(enabled)
    override suspend fun setShowAdvancedMacros(enabled: Boolean) = dataStore.setShowAdvancedMacros(enabled)
    override suspend fun setPostMealRemindersEnabled(enabled: Boolean) =
        dataStore.setPostMealRemindersEnabled(enabled)

    override suspend fun setProfileTarget(target: ProfileTarget) =
        dataStore.setProfileTarget(target)

    override suspend fun setProfileName(name: String) = dataStore.setProfileName(name)

    override suspend fun setProfileAge(age: Int?) = dataStore.setProfileAge(age)

    override suspend fun setProfileDiabetesType(type: String?) =
        dataStore.setProfileDiabetesType(type)

    override suspend fun setProfileHeightCm(heightCm: Float?) =
        dataStore.setProfileHeightCm(heightCm)

    override suspend fun setProfileWeightKg(weightKg: Float?) =
        dataStore.setProfileWeightKg(weightKg)

    override suspend fun setTargetRange(lowMgdl: Float, highMgdl: Float) =
        dataStore.setTargetRange(lowMgdl, highMgdl)

    override suspend fun setAiProvider(provider: AiProvider) = dataStore.setAiProvider(provider)
    override suspend fun setAiBaseUrl(url: String) = dataStore.setAiBaseUrl(url)
    override suspend fun setAiModel(model: String) = dataStore.setAiModel(model)

    override suspend fun setApiKey(plainKey: String) {
        val trimmed = plainKey.trim()
        dataStore.setEncryptedApiKey(
            if (trimmed.isEmpty()) null else keystoreManager.encrypt(trimmed)
        )
    }

    override suspend fun resolveAiConfig(): ResolvedAiConfig {
        val config = dataStore.aiConfigSnapshot()
        val encrypted = dataStore.encryptedApiKey()
        val key = when {
            // Decrypt failures (corrupt blob, invalidated KeyStore key) are
            // translated into ApiKeyMissingException: it is IOException-typed,
            // so it can safely cross the OkHttp interceptor boundary, and the
            // remedy — re-entering the key in Settings — is the same.
            encrypted != null -> try {
                keystoreManager.decrypt(encrypted)
            } catch (e: GeneralSecurityException) {
                throw ApiKeyMissingException(
                    "Stored API key could not be decrypted. " +
                        "Re-enter it in Settings → Hero AI."
                )
            } catch (e: IllegalArgumentException) {
                throw ApiKeyMissingException(
                    "Stored API key is corrupt. Re-enter it in Settings → Hero AI."
                )
            }
            // Self-hosted endpoints (Ollama, llama.cpp) typically need no key.
            config.provider == AiProvider.CUSTOM -> ""
            else -> throw ApiKeyMissingException()
        }
        return ResolvedAiConfig(
            baseUrl = config.baseUrl,
            model = config.model,
            apiKey = key,
        )
    }
}
