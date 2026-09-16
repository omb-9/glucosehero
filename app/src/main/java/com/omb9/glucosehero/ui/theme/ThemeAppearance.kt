package com.omb9.glucosehero.ui.theme

import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings

/**
 * Resolves whether the app canvas is dark from the user's [themeMode], not from
 * system `uiMode` alone. [ThemeMode.LIGHT] is always light, [ThemeMode.AMOLED]
 * is always dark, and [ThemeMode.SYSTEM] follows the platform night flag.
 */
fun resolveAppIsDark(themeMode: ThemeMode, systemInDarkTheme: Boolean): Boolean =
    when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

/**
 * [WindowInsetsControllerCompat.isAppearanceLightStatusBars] / navigation bars:
 * `true` draws dark icons (for a light canvas), `false` draws light icons (for
 * a dark canvas). Driven from the resolved app theme, never from system uiMode.
 */
fun isAppearanceLightSystemBars(appIsDark: Boolean): Boolean = !appIsDark

/**
 * Splash stays up while settings are still unknown. A `null` seed is the
 * sentinel; a [UserSettings] value (including DataStore fail-open defaults)
 * is a real load and must not keep the splash forever.
 */
fun shouldKeepSplashScreen(loadedSettings: UserSettings?): Boolean = loadedSettings == null
