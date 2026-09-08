package com.omb9.glucosehero.domain.model

import kotlin.math.round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroAiPrefillTest {

    @Test
    fun `mmol value converts to canonical mgdl`() {
        val prefill = HeroAiPrefill(
            glucoseValue = 6.4,
            glucoseUnit = GlucoseUnit.MMOL.label,
        ).toCanonical(GlucoseUnit.MGDL)

        assertEquals(round(6.4 * GlucoseUnit.MGDL_PER_MMOL), prefill.glucoseMgdl!!, 1e-9)
        assertNull(prefill.glucoseValue)
        assertNull(prefill.glucoseUnit)
    }

    @Test
    fun `mgdl value is kept as-is`() {
        val prefill = HeroAiPrefill(
            glucoseValue = 115.0,
            glucoseUnit = GlucoseUnit.MGDL.label,
        ).toCanonical(GlucoseUnit.MMOL)

        assertEquals(115.0, prefill.glucoseMgdl!!, 1e-9)
    }

    @Test
    fun `missing unit falls back to configured mmol`() {
        val prefill = HeroAiPrefill(glucoseValue = 6.4)
            .toCanonical(GlucoseUnit.MMOL)

        assertEquals(round(6.4 * GlucoseUnit.MGDL_PER_MMOL), prefill.glucoseMgdl!!, 1e-9)
    }

    @Test
    fun `missing unit falls back to configured mgdl`() {
        val prefill = HeroAiPrefill(glucoseValue = 115.0)
            .toCanonical(GlucoseUnit.MGDL)

        assertEquals(115.0, prefill.glucoseMgdl!!, 1e-9)
    }

    @Test
    fun `unrecognised unit falls back to configured unit`() {
        val prefill = HeroAiPrefill(glucoseValue = 6.4, glucoseUnit = "mmol")
            .toCanonical(GlucoseUnit.MMOL)

        assertEquals(round(6.4 * GlucoseUnit.MGDL_PER_MMOL), prefill.glucoseMgdl!!, 1e-9)
    }

    @Test
    fun `legacy glucose_mgdl is accepted as mgdl`() {
        val prefill = HeroAiPrefill(glucoseMgdl = 115.0)
            .toCanonical(GlucoseUnit.MMOL)

        assertEquals(115.0, prefill.glucoseMgdl!!, 1e-9)
    }

    @Test
    fun `out of range mgdl is rejected`() {
        val prefill = HeroAiPrefill(
            glucoseValue = 6.4,
            glucoseUnit = GlucoseUnit.MGDL.label,
        ).toCanonical(GlucoseUnit.MGDL)

        assertNull(prefill.glucoseMgdl)
    }

    @Test
    fun `out of range mmol is rejected`() {
        val prefill = HeroAiPrefill(
            glucoseValue = 0.3,
            glucoseUnit = GlucoseUnit.MMOL.label,
        ).toCanonical(GlucoseUnit.MGDL)

        assertNull(prefill.glucoseMgdl)
    }
}
