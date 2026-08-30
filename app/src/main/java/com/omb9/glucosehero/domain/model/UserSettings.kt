package com.omb9.glucosehero.domain.model

enum class ThemeMode { SYSTEM, LIGHT, AMOLED }

enum class GlucoseUnit(val label: String) {
    MGDL("mg/dL"),
    MMOL("mmol/L");

    companion object {
        const val MGDL_PER_MMOL = 18.0182
    }
}

/** Selectable accent colors. Default per spec: Light Red #FF5252. */
enum class AccentColor(val argb: Long, val label: String) {
    LIGHT_RED(0xFFFF5252, "Light Red"),
    OCEAN(0xFF4FC3F7, "Ocean"),
    MINT(0xFF69F0AE, "Mint"),
    VIOLET(0xFFB388FF, "Violet"),
    AMBER(0xFFFFD740, "Amber"),
    ROSE(0xFFFF80AB, "Rose"),
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.LIGHT_RED,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val use24HourTime: Boolean = false,
    val targetLowMgdl: Float = 70f,
    val targetHighMgdl: Float = 180f,
    val isHeroAiEnabled: Boolean = true,
)
