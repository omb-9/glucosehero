package com.omb9.glucosehero.ui.entrydetail

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.ui.log.toLogEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the EntryDetail seeding race: the detail form must be
 * seeded only after BOTH the Room entry and the persisted settings have loaded.
 *
 * Seeding from the entry alone (with the fabricated-default `UserSettings()`)
 * formatted a stored 110 mg/dL as "110" for an mmol/L user and never re-seeded;
 * a subsequent save then re-parsed "110" against mmol/L and wrote back
 * ~1982 mg/dL. Exercised through the Android-free [awaitEntryAndSettings] /
 * [seedEntryDetailForm] seam the ViewModel now delegates to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailSeedingTest {

    private val timestamp = 1_700_000_000_000L

    @Test
    fun `seeding waits for real settings and formats the stored value in mmol per L`() = runTest {
        val entryFlow = MutableStateFlow<LogEvent?>(null)
        val settingsFlow = MutableSharedFlow<UserSettings>(replay = 1)

        val seeding = async(UnconfinedTestDispatcher(testScheduler)) {
            awaitEntryAndSettings(entryFlow, settingsFlow, 5_000L)
        }

        // The Room entry resolves first ...
        entryFlow.value = LogEvent(id = 7L, timestamp = timestamp, glucoseMgdl = 110.0)
        // ... and the persisted (mmol/L) settings arrive afterwards.
        settingsFlow.emit(UserSettings(unit = GlucoseUnit.MMOL))

        val form = seeding.await()!!
        assertEquals("6.1", form.glucose)
    }

    @Test
    fun `a no-edit save round-trips the stored 110 mg per dL`() = runTest {
        val mmol = UserSettings(unit = GlucoseUnit.MMOL)
        val entryFlow = MutableStateFlow<LogEvent?>(null)
        val settingsFlow = MutableSharedFlow<UserSettings>(replay = 1)

        val seeding = async(UnconfinedTestDispatcher(testScheduler)) {
            awaitEntryAndSettings(entryFlow, settingsFlow, 5_000L)
        }
        entryFlow.value = LogEvent(id = 7L, timestamp = timestamp, glucoseMgdl = 110.0)
        settingsFlow.emit(mmol)

        val form = seeding.await()!!
        // A save with no user edit re-parses the seeded string against the real unit.
        val saved = form.toDraft().toLogEvent(mmol, form.timestamp)
        assertEquals(110.0, saved!!.glucoseMgdl!!, 0.5)
    }
}