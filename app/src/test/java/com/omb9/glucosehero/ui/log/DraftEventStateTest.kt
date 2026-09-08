package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.data.local.entity.toEntity
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DraftEventStateTest {

    private val mgdl = UserSettings(unit = GlucoseUnit.MGDL)
    private val mmol = UserSettings(unit = GlucoseUnit.MMOL)
    private val now = 1_700_000_000_000L

    @Test
    fun `all four metrics filled yields one LogEvent with four non-null columns`() {
        val event = DraftEventState(
            glucose = "120",
            insulinBasal = "4",
            carbsGrams = "45",
            mealDescription = "oats",
            exerciseMinutes = "30",
            note = "morning",
        ).toLogEvent(mgdl, now)
        assertNotNull(event)
        assertEquals(120.0, event!!.glucoseMgdl!!, 1e-6)
        assertEquals(4.0, event.insulinBasalUnits!!, 1e-6)
        assertEquals(45, event.carbsGrams)
        assertEquals("oats", event.mealDescription)
        assertEquals(30, event.exerciseMinutes)
        assertEquals("morning", event.note)
        assertEquals(now, event.timestamp)
    }

    @Test
    fun `glucose only leaves other metrics null`() {
        val event = DraftEventState(glucose = "99").toLogEvent(mgdl, now)
        assertNotNull(event)
        assertEquals(99.0, event!!.glucoseMgdl!!, 1e-6)
        assertNull(event.insulinBasalUnits)
        assertNull(event.insulinBolusUnits)
        assertNull(event.carbsGrams)
        assertNull(event.mealDescription)
        assertNull(event.exerciseMinutes)
        assertNull(event.note)
    }

    @Test
    fun `abc in insulin returns null — no partial save`() {
        val event = DraftEventState(glucose = "120", insulinBasal = "abc").toLogEvent(mgdl, now)
        assertNull(event)
    }

    @Test
    fun `zero in exercise returns null`() {
        assertNull(DraftEventState(exerciseMinutes = "0").toLogEvent(mgdl, now))
        assertNull(DraftEventState(exerciseMinutes = "-5").toLogEvent(mgdl, now))
        assertNull(DraftEventState(glucose = "0").toLogEvent(mgdl, now))
        assertNull(DraftEventState(insulinBasal = "0").toLogEvent(mgdl, now))
        assertNull(DraftEventState(insulinBolus = "0").toLogEvent(mgdl, now))
        // An explicit "0" carbs is a real statement (0 carbs eaten) and must save as 0,
        // not be discarded like an empty field.
        assertEquals(0, DraftEventState(carbsGrams = "0").toLogEvent(mgdl, now)?.carbsGrams)
    }

    @Test
    fun `blank insulin plus valid glucose saves with insulin null`() {
        val event = DraftEventState(glucose = "110", insulinBasal = "").toLogEvent(mgdl, now)
        assertNotNull(event)
        assertNull(event!!.insulinBasalUnits)
        assertNull(event.insulinBolusUnits)
        assertEquals(110.0, event.glucoseMgdl!!, 1e-6)
    }

    @Test
    fun `mmol unit stores mgdL canonically`() {
        // 5.5 mmol/L ≈ 99.1 mg/dL
        val event = DraftEventState(glucose = "5,5").toLogEvent(mmol, now)
        assertNotNull(event)
        assertEquals(5.5 * GlucoseUnit.MGDL_PER_MMOL, event!!.glucoseMgdl!!, 1e-3)
    }

    @Test
    fun `all blank returns null`() {
        assertNull(DraftEventState().toLogEvent(mgdl, now))
        assertNull(DraftEventState(note = "   ").toLogEvent(mgdl, now))
    }

    @Test
    fun `carbs negative returns null`() {
        assertNull(DraftEventState(carbsGrams = "-1").toLogEvent(mgdl, now))
    }

    @Test
    fun `copied foodId survives toEntity`() {
        val event = DraftEventState(carbsGrams = "30", mealDescription = "Pizza")
            .toLogEvent(mgdl, now)!!
            .copy(foodId = 42L)
        val entity = event.toEntity()
        assertEquals(42L, entity.foodId)
        assertEquals("Pizza", entity.mealDescription)
        assertEquals(30, entity.carbsGrams)
    }

    @Test
    fun `filledMetrics is based on non-blank text not parse success`() {
        val draft = DraftEventState(glucose = "abc", insulinBasal = " ", carbsGrams = "45")
        val metrics = draft.filledMetrics
        assert(metrics.any { it.name == "GLUCOSE" })
        assert(metrics.any { it.name == "CARBS" })
        assert(metrics.none { it.name == "INSULIN" })
    }
}
