package com.omb9.glucosehero.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omb9.glucosehero.domain.model.UserSettings
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        val settingsFile = tempFolder.newFile("settings.preferences_pb")
        val runtimeFile = tempFolder.newFile("runtime_state.preferences_pb")
        val settingsStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { settingsFile },
        )
        val runtimeStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { runtimeFile },
        )
        val store = SettingsDataStore(
            clock = Clock.systemUTC(),
            settingsStore = settingsStore,
            runtimeStore = runtimeStore,
        )

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
}
