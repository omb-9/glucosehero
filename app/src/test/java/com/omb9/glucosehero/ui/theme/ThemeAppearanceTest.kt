package com.omb9.glucosehero.ui.theme

import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAppearanceTest {

    @Test
    fun `splash stays up only while settings are still unknown`() {
        assertTrue(shouldKeepSplashScreen(null, logFirstContentReady = true))
        // Fail-open defaults after a completed DataStore read are a real emission.
        assertFalse(shouldKeepSplashScreen(UserSettings(), logFirstContentReady = true))
        assertFalse(
            shouldKeepSplashScreen(
                UserSettings(themeMode = ThemeMode.AMOLED),
                logFirstContentReady = true,
            ),
        )
        assertFalse(
            shouldKeepSplashScreen(
                UserSettings(themeMode = ThemeMode.SYSTEM),
                logFirstContentReady = true,
            ),
        )
    }

    @Test
    fun `splash stays up after settings until the first log page is decided`() {
        assertTrue(shouldKeepSplashScreen(UserSettings(), logFirstContentReady = false))
        assertFalse(shouldKeepSplashScreen(UserSettings(), logFirstContentReady = true))
        assertTrue(shouldKeepSplashScreen(null, logFirstContentReady = false))
    }

    @Test
    fun `resolved dark canvas and system-bar icon appearance for all themeMode x system combinations`() {
        val cases = listOf(
            Case(ThemeMode.LIGHT, systemDark = false, expectAppDark = false),
            Case(ThemeMode.LIGHT, systemDark = true, expectAppDark = false),
            Case(ThemeMode.SYSTEM, systemDark = false, expectAppDark = false),
            Case(ThemeMode.SYSTEM, systemDark = true, expectAppDark = true),
            Case(ThemeMode.AMOLED, systemDark = false, expectAppDark = true),
            Case(ThemeMode.AMOLED, systemDark = true, expectAppDark = true),
        )
        assertEquals(ThemeMode.entries.size * 2, cases.size)
        for (case in cases) {
            val appIsDark = resolveAppIsDark(case.themeMode, case.systemDark)
            assertEquals(
                "${case.themeMode} with systemDark=${case.systemDark}",
                case.expectAppDark,
                appIsDark,
            )
            assertEquals(
                "bar icons for ${case.themeMode} with systemDark=${case.systemDark}",
                !case.expectAppDark,
                isAppearanceLightSystemBars(appIsDark),
            )
        }
    }

    @Test
    fun `LIGHT and AMOLED ignore system night while SYSTEM follows it`() {
        assertFalse(resolveAppIsDark(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertTrue(resolveAppIsDark(ThemeMode.AMOLED, systemInDarkTheme = false))
        assertEquals(
            resolveAppIsDark(ThemeMode.SYSTEM, systemInDarkTheme = true),
            true,
        )
        assertEquals(
            resolveAppIsDark(ThemeMode.SYSTEM, systemInDarkTheme = false),
            false,
        )
    }

    private data class Case(
        val themeMode: ThemeMode,
        val systemDark: Boolean,
        val expectAppDark: Boolean,
    )
}
