package com.omb9.glucosehero.domain.repository

import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ResolvedAiConfig
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>
    val aiConfig: Flow<AiConfig>
    val profile: Flow<UserProfile>
    val bolusSettings: Flow<BolusSettings>

    suspend fun profileSnapshot(): UserProfile

    /** Reads the latest config from DataStore without decrypting the API key. */
    suspend fun aiConfigSnapshot(): AiConfig

    /** Insulin-dosing parameters (DIA, CIR, ISF, target glucose). */
    suspend fun bolusSettingsSnapshot(): BolusSettings

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAccent(accent: AccentColor)
    suspend fun setUnit(unit: GlucoseUnit)
    suspend fun setUse24HourTime(enabled: Boolean)
    suspend fun setIsHeroAiEnabled(enabled: Boolean)
    suspend fun setShowAdvancedMacros(enabled: Boolean)
    suspend fun setPostMealRemindersEnabled(enabled: Boolean)

    suspend fun setProfileTarget(target: ProfileTarget)
    suspend fun setProfileName(name: String)
    suspend fun setProfileAge(age: Int?)
    suspend fun setProfileDiabetesType(type: String?)
    suspend fun setProfileHeightCm(heightCm: Float?)
    suspend fun setProfileWeightKg(weightKg: Float?)

    suspend fun setTargetRange(lowMgdl: Float, highMgdl: Float)

    suspend fun setDiaHours(diaHours: Float)
    suspend fun setCirRatio(ratio: Float)
    suspend fun setIsfMgdl(isf: Float)
    suspend fun setTargetGlucoseMgdl(target: Float)

    suspend fun setAiProvider(provider: AiProvider)
    suspend fun setAiBaseUrl(url: String)
    suspend fun setAiModel(model: String)

    /** Encrypts via Android KeyStore before persisting. Blank clears the key. */
    suspend fun setApiKey(plainKey: String)

    /**
     * Reads the latest config from DataStore and decrypts the key in memory.
     * Called per-request so provider switches apply immediately.
     * @throws com.omb9.glucosehero.domain.model.ApiKeyMissingException
     */
    suspend fun resolveAiConfig(): ResolvedAiConfig
}
