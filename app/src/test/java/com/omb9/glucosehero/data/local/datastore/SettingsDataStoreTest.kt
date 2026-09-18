package com.omb9.glucosehero.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsEmitsOnceWhenGlucoseForecastJsonWrittenFiveTimes() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        val emissions = mutableListOf<UserSettings>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.settings.collect { emissions.add(it) }
        }

        testScheduler.runCurrent()
        assertEquals(1, emissions.size)

        repeat(5) { index ->
            store.setGlucoseForecastJson("""{"sequence":$index}""")
        }

        testScheduler.advanceUntilIdle()
        assertEquals(1, emissions.size)

        collectJob.cancel()
        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reasoningEffortDefaultsOffAndRoundTrips() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        assertEquals(com.omb9.glucosehero.domain.model.ReasoningEffort.OFF, store.settings.first().reasoningEffort)

        store.setReasoningEffort(com.omb9.glucosehero.domain.model.ReasoningEffort.LOW)
        testScheduler.advanceUntilIdle()
        assertEquals(com.omb9.glucosehero.domain.model.ReasoningEffort.LOW, store.settings.first().reasoningEffort)

        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun firstRunSeeds24HourTimeWithoutWritingUnit() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        store.seedFirstRunDefaults(use24HourTime = true, hasExistingUserData = false)
        testScheduler.advanceUntilIdle()

        val settings = store.settings.first()
        assertTrue(settings.use24HourTime)
        assertEquals(GlucoseUnit.MGDL, settings.unit)
        assertTrue(store.needsGlucoseUnitChoice.first())

        store.completeFirstRun(GlucoseUnit.MMOL)
        testScheduler.advanceUntilIdle()
        assertEquals(GlucoseUnit.MMOL, store.settings.first().unit)
        assertTrue(store.settings.first().use24HourTime)
        assertFalse(store.needsGlucoseUnitChoice.first())

        store.seedFirstRunDefaults(use24HourTime = false, hasExistingUserData = false)
        testScheduler.advanceUntilIdle()
        assertTrue(store.settings.first().use24HourTime)
        assertEquals(GlucoseUnit.MMOL, store.settings.first().unit)

        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun existingUserWithSavedPrefsIsNotOverwritten() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        store.setThemeMode(ThemeMode.AMOLED)
        store.setUse24HourTime(false)
        testScheduler.advanceUntilIdle()

        store.seedFirstRunDefaults(use24HourTime = true, hasExistingUserData = false)
        testScheduler.advanceUntilIdle()

        val settings = store.settings.first()
        assertFalse(settings.use24HourTime)
        assertEquals(GlucoseUnit.MGDL, settings.unit)
        assertEquals(ThemeMode.AMOLED, settings.themeMode)
        assertFalse(store.needsGlucoseUnitChoice.first())

        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun existingUserWithLogDataAndEmptyPrefsIsNotOverwritten() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        store.seedFirstRunDefaults(use24HourTime = true, hasExistingUserData = true)
        testScheduler.advanceUntilIdle()

        val settings = store.settings.first()
        assertFalse(settings.use24HourTime)
        assertEquals(GlucoseUnit.MGDL, settings.unit)
        assertFalse(store.needsGlucoseUnitChoice.first())

        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun enabledMarkerCategoriesRoundTripPersistsExplicitSelection() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = createStore(scope)

        assertEquals(null, store.enabledMarkerCategoryNames.first())
        assertEquals(null, store.knownMarkerCategoryNames.first())

        store.setEnabledMarkerCategories(
            enabledNames = setOf("MEAL", "EXERCISE", "NOTE"),
            knownNames = setOf("MEAL", "EXERCISE", "NOTE", "MEDICATION"),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(
            setOf("MEAL", "EXERCISE", "NOTE"),
            store.enabledMarkerCategoryNames.first(),
        )
        assertEquals(
            setOf("MEAL", "EXERCISE", "NOTE", "MEDICATION"),
            store.knownMarkerCategoryNames.first(),
        )

        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createStore(scope: CoroutineScope): SettingsDataStore {
        val settingsFile = tempFolder.newFile("settings-${scope.hashCode()}.preferences_pb")
        val runtimeFile = tempFolder.newFile("runtime-${scope.hashCode()}.preferences_pb")
        return SettingsDataStore(
            clock = Clock.systemUTC(),
            settingsStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { settingsFile },
            ),
            runtimeStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { runtimeFile },
            ),
        )
    }
}
