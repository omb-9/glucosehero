package com.omb9.glucosehero.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.omb9.glucosehero.domain.model.AccentColor
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

    @Test
    fun `WCAG luminance of white is 1 and black is 0`() {
        assertEquals(1f, relativeLuminance(Color.White), 1e-5f)
        assertEquals(0f, relativeLuminance(Color.Black), 1e-5f)
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
    }

    @Test
    fun `onColorFor picks black above the equal-contrast luminance and white below`() {
        assertEquals(Color.Black, onColorFor(Color.White))
        assertEquals(Color.White, onColorFor(Color.Black))
        // Navy is well below the 0.179 threshold, so filled surfaces get white content.
        val navy = Color(0xFF1A237E)
        assertTrue(relativeLuminance(navy) < ON_COLOR_LUMINANCE_THRESHOLD)
        assertEquals(Color.White, onColorFor(navy))
        assertTrue(contrastRatio(Color.White, navy) >= MIN_TEXT_CONTRAST_RATIO)
    }

    @Test
    fun `every accent meets AA for onPrimary and onSecondary in light and dark schemes`() {
        assertEquals(6, AccentColor.entries.size)
        for (accent in AccentColor.entries) {
            val color = Color(accent.argb)
            for (dark in listOf(false, true)) {
                val scheme = glucoseHeroColorScheme(color, dark)
                val label = "${accent.name} dark=$dark"
                assertEquals(color, scheme.primary)
                assertEquals(color, scheme.secondary)
                assertEquals("$label onSecondary must match onPrimary", scheme.onPrimary, scheme.onSecondary)
                val onPrimaryRatio = contrastRatio(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "$label onPrimary contrast $onPrimaryRatio",
                    onPrimaryRatio >= MIN_TEXT_CONTRAST_RATIO,
                )
                val onSecondaryRatio = contrastRatio(scheme.onSecondary, scheme.secondary)
                assertTrue(
                    "$label onSecondary contrast $onSecondaryRatio",
                    onSecondaryRatio >= MIN_TEXT_CONTRAST_RATIO,
                )
            }
        }
    }

    @Test
    fun `every accent meets AA for onPrimaryContainer on the drawn container`() {
        for (accent in AccentColor.entries) {
            val color = Color(accent.argb)
            for (dark in listOf(false, true)) {
                val scheme = glucoseHeroColorScheme(color, dark)
                val drawn = drawnPrimaryContainer(color, dark)
                assertEquals(
                    scheme.primaryContainer.compositeOver(schemeCanvas(dark)),
                    drawn,
                )
                val ratio = contrastRatio(scheme.onPrimaryContainer, drawn)
                assertTrue(
                    "${accent.name} dark=$dark onPrimaryContainer contrast $ratio",
                    ratio >= MIN_TEXT_CONTRAST_RATIO,
                )
            }
        }
    }

    @Test
    fun `light scheme uses black onPrimary for today's bright accents`() {
        for (accent in AccentColor.entries) {
            val scheme = glucoseHeroColorScheme(Color(accent.argb), dark = false)
            assertEquals(accent.name, Color.Black, scheme.onPrimary)
        }
    }

    @Test
    fun `dark scheme keeps chromatic onPrimaryContainer when the accent already passes AA`() {
        for (accent in AccentColor.entries) {
            val color = Color(accent.argb)
            val scheme = glucoseHeroColorScheme(color, dark = true)
            assertEquals(accent.name, color, scheme.onPrimaryContainer)
        }
    }

    @Test
    fun `a dark accent in light scheme gets white onPrimary`() {
        val navy = Color(0xFF1A237E)
        val scheme = glucoseHeroColorScheme(navy, dark = false)
        assertEquals(Color.White, scheme.onPrimary)
        assertEquals(Color.White, scheme.onSecondary)
        assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= MIN_TEXT_CONTRAST_RATIO)
    }

    private data class Case(
        val themeMode: ThemeMode,
        val systemDark: Boolean,
        val expectAppDark: Boolean,
    )
}
