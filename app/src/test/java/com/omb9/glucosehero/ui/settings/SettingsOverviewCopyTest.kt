package com.omb9.glucosehero.ui.settings

import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOverviewCopyTest {

    @Test
    fun profileFallsBackWhenNameIsBlank() {
        assertEquals("Not set", SettingsOverviewCopy.profileSubtitle(""))
        assertEquals("Not set", SettingsOverviewCopy.profileSubtitle("   "))
        assertEquals("Ada", SettingsOverviewCopy.profileSubtitle(" Ada "))
    }

    @Test
    fun glucoseTargetsIncludeUnitAndRange() {
        assertEquals(
            "70-180 mg/dL",
            SettingsOverviewCopy.glucoseTargetsSubtitle(GlucoseUnit.MGDL, 70f, 180f),
        )
    }

    @Test
    fun appearanceJoinsThemeAndAccent() {
        assertEquals(
            "Light · Light Red",
            SettingsOverviewCopy.appearanceSubtitle(ThemeMode.LIGHT, AccentColor.LIGHT_RED),
        )
        assertEquals("AMOLED", SettingsOverviewCopy.themeModeLabel(ThemeMode.AMOLED))
    }

    @Test
    fun heroAiShowsOnOrOff() {
        assertEquals("Off", SettingsOverviewCopy.heroAiSubtitle(false))
        assertEquals("On", SettingsOverviewCopy.heroAiSubtitle(true))
    }

    @Test
    fun dataSourcesCountCopy() {
        assertEquals("None connected", SettingsOverviewCopy.dataSourcesSubtitle(0))
        assertEquals("1 connected", SettingsOverviewCopy.dataSourcesSubtitle(1))
        assertEquals("3 connected", SettingsOverviewCopy.dataSourcesSubtitle(3))
        assertEquals(
            2,
            SettingsOverviewCopy.countConnectedSources(
                xdripEnabled = true,
                nightscoutEnabled = false,
                healthConnectConnected = true,
            ),
        )
    }

    @Test
    fun backupSubtitleAndLatestMillis() {
        assertEquals("No backup yet", SettingsOverviewCopy.backupSubtitle(null))
        assertEquals(20L, SettingsOverviewCopy.latestBackupMillis(10L, 20L))
        assertEquals(10L, SettingsOverviewCopy.latestBackupMillis(10L, null))
        val text = SettingsOverviewCopy.backupSubtitle(1_700_000_000_000L)
        assertTrue(text.startsWith("Last backup "))
    }
}
