package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FormattersTest {

    // ---------- Height ----------

    @Test
    fun `feet and inches round trip through canonical cm`() {
        val cm = Formatters.feetInchesToCm(5, 10)
        assertEquals(177.8f, cm, 1e-6f)
        assertEquals(5 to 10, Formatters.cmToFeetInches(cm))
    }

    @Test
    fun `metric height formats as whole or decimal cm`() {
        assertEquals("175", Formatters.formatHeight(175f, UnitSystem.METRIC))
        assertEquals("177.8", Formatters.formatHeight(177.8f, UnitSystem.METRIC))
    }

    @Test
    fun `imperial height formats as feet and inches`() {
        assertEquals("5'10\"", Formatters.formatHeight(177.8f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `inch boundary carries into the next foot`() {
        // 182.5 cm == 71.85 in; rounding the total inches first yields 6'0".
        assertEquals(6 to 0, Formatters.cmToFeetInches(182.5f))
    }

    @Test
    fun `zero height formats blank in both systems`() {
        assertEquals("", Formatters.formatHeight(0f, UnitSystem.METRIC))
        assertEquals("", Formatters.formatHeight(0f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `null height formats as blank in both systems`() {
        assertEquals("", Formatters.formatHeight(null, UnitSystem.METRIC))
        assertEquals("", Formatters.formatHeight(null, UnitSystem.IMPERIAL))
    }

    // ---------- Weight ----------

    @Test
    fun `pounds round trip through canonical kg`() {
        val kg = Formatters.lbsToKg(150f)
        assertEquals(150f, Formatters.kgToLbs(kg), 1e-3f)
    }

    @Test
    fun `kg round trip through pounds`() {
        val lbs = Formatters.kgToLbs(70f)
        assertEquals(70f, Formatters.lbsToKg(lbs), 1e-3f)
    }

    @Test
    fun `metric weight formats as whole or decimal kg`() {
        assertEquals("70", Formatters.formatWeight(70f, UnitSystem.METRIC))
        assertEquals("70.5", Formatters.formatWeight(70.5f, UnitSystem.METRIC))
    }

    @Test
    fun `imperial weight formats as pounds`() {
        assertEquals("154.3", Formatters.formatWeight(70f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `zero weight formats blank in both systems`() {
        assertEquals("", Formatters.formatWeight(0f, UnitSystem.METRIC))
        assertEquals("", Formatters.formatWeight(0f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `null weight formats as blank in both systems`() {
        assertEquals("", Formatters.formatWeight(null, UnitSystem.METRIC))
        assertEquals("", Formatters.formatWeight(null, UnitSystem.IMPERIAL))
    }

    // ---------- Shared decimal parsing ----------

    @Test
    fun `height comma decimal parses like a dot`() {
        assertEquals(178.5, Formatters.parseDecimal("178,5")!!, 1e-9)
    }

    @Test
    fun `weight comma decimal parses like a dot`() {
        assertEquals(72.5, Formatters.parseDecimal("72,5")!!, 1e-9)
    }

    // ---------- Hour-of-day axis labels ----------

    @Test
    fun `12h hour labels are compact`() {
        assertEquals("6a", Formatters.hourOfDay(localHour(6), use24Hour = false))
        assertEquals("12p", Formatters.hourOfDay(localHour(12), use24Hour = false))
        assertEquals("6p", Formatters.hourOfDay(localHour(18), use24Hour = false))
        assertEquals("12a", Formatters.hourOfDay(localHour(0), use24Hour = false))
    }

    @Test
    fun `24h hour labels are compact`() {
        assertEquals("6", Formatters.hourOfDay(localHour(6), use24Hour = true))
        assertEquals("18", Formatters.hourOfDay(localHour(18), use24Hour = true))
        assertEquals("0", Formatters.hourOfDay(localHour(0), use24Hour = true))
    }

    // ---------- Count formatting ----------

    @Test
    fun `count uses comma thousands separators`() {
        assertEquals("26,104", Formatters.count(26_104))
        assertEquals("1,234,567", Formatters.count(1_234_567))
        assertEquals("0", Formatters.count(0))
    }

    // ---------- Glucose, bolus, carbs ----------

    @Test
    fun `mgdl glucose formats as a whole number`() {
        assertEquals("85", Formatters.glucose(85.0, GlucoseUnit.MGDL))
        assertEquals("85 mg/dL", Formatters.glucoseWithUnit(85.0, GlucoseUnit.MGDL))
    }

    @Test
    fun `mmol glucose formats to one decimal`() {
        assertEquals("4.7", Formatters.glucose(85.0, GlucoseUnit.MMOL))
        assertEquals("4.7 mmol/L", Formatters.glucoseWithUnit(85.0, GlucoseUnit.MMOL))
    }

    @Test
    fun `bolus formats to one decimal with units`() {
        assertEquals("4.2 units", Formatters.bolus(4.2))
        assertEquals("4.0 units", Formatters.bolus(4.0))
    }

    @Test
    fun `carbs format as whole grams`() {
        assertEquals("62 g", Formatters.carbs(62.4))
        assertEquals("41 g", Formatters.carbs(41.0))
    }

    private fun localHour(hour: Int): Long =
        LocalDate.now()
            .atTime(hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
