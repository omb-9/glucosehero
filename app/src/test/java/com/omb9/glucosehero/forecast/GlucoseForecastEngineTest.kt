package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GlucoseForecastEngineTest {

    private val now = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun `insufficient samples mark snapshot as incomplete`() {
        val snapshot = GlucoseForecastEngine.forecast(
            input = GlucoseForecastInput(
                samples = listOf(sample(now, 120.0)),
                boluses = emptyList(),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now = now,
        )
        assertTrue(snapshot.insufficientData)
        assertTrue(snapshot.points.isEmpty())
    }

    @Test
    fun `falling trend projects lower glucose at 30 and 60 minutes`() {
        val samples = (0..8).map { i ->
            sample(now.minusSeconds((8 - i) * 5L * 60L), 160.0 - i * 4.0)
        }
        val snapshot = GlucoseForecastEngine.forecast(
            input = GlucoseForecastInput(
                samples = samples,
                boluses = emptyList(),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now = now,
        )
        assertTrue(!snapshot.insufficientData)
        val at30 = snapshot.at30Min!!.glucoseMgdl
        val at60 = snapshot.at60Min!!.glucoseMgdl
        assertTrue(at30 < snapshot.currentMgdl)
        assertTrue(at60 <= at30 + 1.0)
    }

    @Test
    fun `active insulin pulls the 60 minute projection down`() {
        val samples = (0..6).map { i ->
            sample(now.minusSeconds((6 - i) * 5L * 60L), 140.0)
        }
        val withoutIob = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = emptyList(),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        val withIob = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 2.0)),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        assertTrue(withIob.at60Min!!.glucoseMgdl < withoutIob.at60Min!!.glucoseMgdl)
    }

    @Test
    fun `carb absorption raises the 30 minute projection`() {
        val samples = (0..6).map { i ->
            sample(now.minusSeconds((6 - i) * 5L * 60L), 100.0)
        }
        val withoutCarbs = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = emptyList(),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        val withCarbs = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = emptyList(),
                meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 40.0)),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        assertTrue(withCarbs.at30Min!!.glucoseMgdl > withoutCarbs.at30Min!!.glucoseMgdl)
    }

    @Test
    fun `mgdl per gram is ISF over CIR`() {
        assertEquals(5.0, GlucoseForecastEngine.mgdlPerGramCarb(50.0, 10.0), 1e-9)
        assertEquals(0.0, GlucoseForecastEngine.mgdlPerGramCarb(50.0, 0.0), 1e-9)
    }

    private fun sample(time: Instant, mgdl: Double) =
        GlucoseForecastInput.GlucoseSample(time.toEpochMilli(), mgdl)
}
