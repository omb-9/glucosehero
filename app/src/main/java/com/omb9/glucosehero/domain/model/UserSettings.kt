package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, AMOLED }

enum class GlucoseUnit(val label: String) {
    MGDL("mg/dL"),
    MMOL("mmol/L");

    companion object {
        const val MGDL_PER_MMOL = 18.0182

        /**
         * Locale pre-selection for the first-run unit question only. Never
         * applied silently to saved prefs.
         *
         * mg/dL: US, DE, AT, and most of Latin America (countries not listed
         * as mmol/L). mmol/L: UK, CA, AU, NZ, IE, and most of Europe.
         */
        fun defaultForLocale(locale: Locale = Locale.getDefault()): GlucoseUnit {
            val country = locale.country.uppercase(Locale.ROOT)
            return if (country in MMOL_COUNTRIES) MMOL else MGDL
        }

        private val MMOL_COUNTRIES = setOf(
            // Specified: UK, CA, AU, plus NZ (same clinical convention).
            "GB", "CA", "AU", "NZ",
            // Most of Europe. DE and AT use mg/dL and are omitted.
            "AD", "AL", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DK",
            "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IE", "IS", "IT",
            "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL",
            "NO", "PL", "PT", "RO", "RS", "SE", "SI", "SK", "SM", "UA",
            "VA", "XK",
        )
    }
}

enum class UnitSystem(val label: String) {
    METRIC("Metric (cm, kg)"),
    IMPERIAL("Imperial (ft/in, lb)");

    companion object {
        /** First-launch default: Imperial for US locales, Metric otherwise. */
        fun default(): UnitSystem =
            if (Locale.getDefault().country.equals("US", ignoreCase = true)) IMPERIAL else METRIC
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

@Immutable
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accent: AccentColor = AccentColor.LIGHT_RED,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val unitSystem: UnitSystem = UnitSystem.default(),
    val use24HourTime: Boolean = false,
    val targetLowMgdl: Float = 70f,
    val targetHighMgdl: Float = 180f,
    val isHeroAiEnabled: Boolean = true,
    val showAdvancedMacros: Boolean = false,
    val postMealRemindersEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val sendMealPhotosToHeroAi: Boolean = false,
)
