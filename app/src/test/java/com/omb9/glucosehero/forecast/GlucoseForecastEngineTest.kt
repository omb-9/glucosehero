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

    @Test
    fun `legacy snapshot json defaults new explanation fields`() {
        val json = """
            {
              "generatedAtMillis": 1,
              "currentMgdl": 120.0,
              "currentTimestampMillis": 1,
              "velocityMgdlPerMin": 0.1,
              "iobUnits": 1.0,
              "cobGrams": 10.0,
              "points": [],
              "sampleCount": 3
            }
        """.trimIndent()
        val decoded = com.omb9.glucosehero.util.AppJson.decodeFromString(
            GlucoseForecastSnapshot.serializer(),
            json,
        )
        assertEquals(0.0, decoded.insulinEffectMgdl60, 0.0)
        assertEquals(0.0, decoded.carbEffectMgdl60, 0.0)
        assertEquals(0.0, decoded.trendEffectMgdl60, 0.0)
        assertEquals(false, decoded.clampMin60)
        assertEquals(false, decoded.horizonCrossedSegmentBoundary)
        assertTrue(decoded.dosingSegmentsUsed.isEmpty())
        assertTrue(decoded.claimsSingleIsf)
    }

    @Test
    fun `insulin and carb contributions match the engine internals`() {
        val samples = (0..6).map { i ->
            sample(now.minusSeconds((6 - i) * 5L * 60L), 140.0)
        }
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 2.0))
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 40.0))
        val snapshot = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = boluses,
                meals = meals,
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        val horizonEnd = now.plusMillis(60L * 60_000L)
        val expectedInsulin = IobCalculator.insulinAbsorbedBetween(
            boluses, 4.0, now, horizonEnd,
        ) * 50.0
        val expectedCarbs = CarbAbsorptionCalculator.carbsAbsorbedBetween(
            meals, CarbAbsorptionCalculator.DEFAULT_ACTION_HOURS, now, horizonEnd,
        ) * GlucoseForecastEngine.mgdlPerGramCarb(50.0, 10.0)
        assertEquals(expectedInsulin, snapshot.insulinEffectMgdl60, 1e-9)
        assertEquals(expectedCarbs, snapshot.carbEffectMgdl60, 1e-9)
        assertEquals(snapshot.velocityMgdlPerMin * 60.0, snapshot.trendEffectMgdl60, 1e-9)
        val reconstructed = snapshot.currentMgdl + snapshot.trendEffectMgdl60 -
            snapshot.insulinEffectMgdl60 + snapshot.carbEffectMgdl60
        assertEquals(reconstructed, snapshot.unclampedMgdl60, 1e-9)
        val expectedProjected = reconstructed.coerceIn(
            GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
            GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
        )
        assertEquals(expectedProjected, snapshot.at60Min!!.glucoseMgdl, 1e-9)
        assertEquals(
            snapshot.unclampedMgdl60 < GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
            snapshot.clampMin60,
        )
        assertEquals(
            snapshot.unclampedMgdl60 > GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
            snapshot.clampMax60,
        )
    }

    @Test
    fun `clamp flag is set when the unclamped 60 minute value is out of range`() {
        val samples = (0..6).map { i ->
            sample(now.minusSeconds((6 - i) * 5L * 60L), 45.0 - i * 8.0)
        }
        val snapshot = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples,
                boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 8.0)),
                meals = emptyList(),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        if (snapshot.unclampedMgdl60 < GlucoseForecastEngine.MIN_GLUCOSE_MGDL) {
            assertTrue(snapshot.clampMin60)
            assertEquals(GlucoseForecastEngine.MIN_GLUCOSE_MGDL, snapshot.at60Min!!.glucoseMgdl, 1e-9)
        } else {
            val high = (0..6).map { i ->
                sample(now.minusSeconds((6 - i) * 5L * 60L), 380.0 + i * 8.0)
            }
            val highSnap = GlucoseForecastEngine.forecast(
                GlucoseForecastInput(
                    samples = high,
                    boluses = emptyList(),
                    meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 80.0)),
                    diaHours = 4.0,
                    cirRatio = 10.0,
                    isfMgdl = 50.0,
                ),
                now,
            )
            assertTrue(highSnap.unclampedMgdl60 > GlucoseForecastEngine.MAX_GLUCOSE_MGDL || highSnap.clampMax60)
            if (highSnap.clampMax60) {
                assertEquals(GlucoseForecastEngine.MAX_GLUCOSE_MGDL, highSnap.at60Min!!.glucoseMgdl, 1e-9)
            }
        }
    }

    private fun sample(time: Instant, mgdl: Double) =
        GlucoseForecastInput.GlucoseSample(time.toEpochMilli(), mgdl)
}
