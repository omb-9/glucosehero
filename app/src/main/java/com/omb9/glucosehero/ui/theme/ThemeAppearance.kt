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
 * Splash stays up until DataStore settings are known *and* the log's first
 * page (rows or empty) is decided. A `null` settings seed is unread; a
 * [UserSettings] value (including fail-open defaults) is a real load.
 * [logFirstContentReady] is the non-loading branch of the log list, not a
 * draw callback (splash OnPreDraw would deadlock if we waited for a draw).
 */
fun shouldKeepSplashScreen(
    loadedSettings: UserSettings?,
    logFirstContentReady: Boolean,
): Boolean = loadedSettings == null || !logFirstContentReady
