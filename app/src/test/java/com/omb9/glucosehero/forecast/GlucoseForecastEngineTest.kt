package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertFalse(snapshot.staleAnchor)
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
        assertEquals(0L, decoded.anchorAgeMillis)
        assertEquals(false, decoded.staleAnchor)
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
        val ageMinutes = snapshot.anchorAgeMillis / 60_000.0
        assertEquals(
            snapshot.velocityMgdlPerMin *
                GlucoseForecastEngine.minutesFromAnchor(60.0, ageMinutes),
            snapshot.trendEffectMgdl60,
            1e-9,
        )
        assertBreakdownIdentity(snapshot)
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

    @Test
    fun `minutesFromAnchor at 25 minutes stale uses 55 minutes of 30 minute trend`() {
        val trendMinutes = GlucoseForecastEngine.minutesFromAnchor(30.0, 25.0)
        assertEquals(55.0, trendMinutes, 0.0)
        val latest = 100.0
        val velocity = -1.0
        val trendEffect = velocity * trendMinutes
        val unclamped = latest + trendEffect - 0.0 + 0.0
        assertEquals(45.0, unclamped, 0.0)
        val sixtyUnclamped = latest + velocity *
            GlucoseForecastEngine.minutesFromAnchor(60.0, 25.0)
        assertEquals(15.0, sixtyUnclamped, 0.0)
        assertEquals(70.0, latest + velocity * 30.0, 0.0)
    }

    @Test
    fun `breakdown identity holds for a fresh anchor at 30 and 60 minutes`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(
                samples = (0..8).map { i ->
                    sample(now.minusSeconds((8 - i) * 5L * 60L), 160.0 - i * 4.0)
                },
            ),
            now,
        )
        assertEquals(0L, snapshot.anchorAgeMillis)
        assertFalse(snapshot.staleAnchor)
        assertBreakdownIdentity(snapshot)
    }

    @Test
    fun `breakdown identity holds for a 19 minute stale anchor at 30 and 60 minutes`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(linearSamples(lastAgeMinutes = 19, lastGlucose = 100.0, velocityMgdlPerMin = -1.0)),
            now,
        )
        assertFalse(snapshot.staleAnchor)
        assertEquals(19L * 60_000L, snapshot.anchorAgeMillis)
        assertBreakdownIdentity(snapshot)
    }

    @Test
    fun `19 minute stale falling series projects 49 minutes of trend at 30 minutes`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(linearSamples(lastAgeMinutes = 19, lastGlucose = 100.0, velocityMgdlPerMin = -1.0)),
            now,
        )
        assertFalse(snapshot.staleAnchor)
        assertTrue(snapshot.points.isNotEmpty())
        assertTrue(
            "expected falling velocity near -1, got ${snapshot.velocityMgdlPerMin}",
            snapshot.velocityMgdlPerMin < -0.5,
        )
        val trendMinutes = GlucoseForecastEngine.minutesFromAnchor(30.0, 19.0)
        assertEquals(49.0, trendMinutes, 0.0)
        assertEquals(
            snapshot.currentMgdl + snapshot.velocityMgdlPerMin * trendMinutes,
            snapshot.unclampedMgdl30,
            1e-9,
        )
        val horizonOnly = snapshot.currentMgdl + snapshot.velocityMgdlPerMin * 30.0
        assertTrue(
            abs(snapshot.unclampedMgdl30 - horizonOnly) > 5.0,
        )
    }

    @Test
    fun `fresh anchor 30 and 60 minute trend matches horizon only`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(
                samples = (0..8).map { i ->
                    sample(now.minusSeconds((8 - i) * 5L * 60L), 160.0 - i * 4.0)
                },
            ),
            now,
        )
        assertEquals(0L, snapshot.anchorAgeMillis)
        assertFalse(snapshot.staleAnchor)
        assertTrue(snapshot.points.isNotEmpty())
        assertEquals(snapshot.velocityMgdlPerMin * 30.0, snapshot.trendEffectMgdl30, 1e-9)
        assertEquals(snapshot.velocityMgdlPerMin * 60.0, snapshot.trendEffectMgdl60, 1e-9)
        assertEquals(
            snapshot.currentMgdl + snapshot.trendEffectMgdl30 -
                snapshot.insulinEffectMgdl30 + snapshot.carbEffectMgdl30,
            snapshot.unclampedMgdl30,
            1e-9,
        )
    }

    @Test
    fun `25 minute stale anchor is gated and does not emit points`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(linearSamples(lastAgeMinutes = 25, lastGlucose = 100.0, velocityMgdlPerMin = -1.0)),
            now,
        )
        assertTrue(snapshot.staleAnchor)
        assertFalse(snapshot.insufficientData)
        assertTrue(snapshot.points.isEmpty())
        assertEquals(25L * 60_000L, snapshot.anchorAgeMillis)
        assertEquals(100.0, snapshot.currentMgdl, 1e-9)
    }

    @Test
    fun `21 minute stale anchor refuses points and keeps iob cob`() {
        val snapshot = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = linearSamples(lastAgeMinutes = 21, lastGlucose = 140.0, velocityMgdlPerMin = -1.0),
                boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 2.0)),
                meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 30.0)),
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        assertTrue(snapshot.staleAnchor)
        assertFalse(snapshot.insufficientData)
        assertTrue(snapshot.points.isEmpty())
        assertEquals(140.0, snapshot.currentMgdl, 1e-9)
        assertEquals(21L * 60_000L, snapshot.anchorAgeMillis)
        assertTrue(snapshot.iobUnits > 0.0)
        assertTrue(snapshot.cobGrams > 0.0)
        val explanation = ForecastDisplayFormatter.format(
            snapshot,
            GlucoseUnit.MGDL,
            use24HourTime = true,
        )
        assertTrue(explanation.staleAnchor)
        assertTrue(
            explanation.spokenSentenceForTest("On-device estimate.").contains("Latest reading 21 min ago"),
        )
    }

    @Test
    fun `19 minute stale anchor still emits projection points`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(linearSamples(lastAgeMinutes = 19, lastGlucose = 140.0, velocityMgdlPerMin = -1.0)),
            now,
        )
        assertFalse(snapshot.staleAnchor)
        assertFalse(snapshot.insufficientData)
        assertTrue(snapshot.points.isNotEmpty())
        assertEquals(19L * 60_000L, snapshot.anchorAgeMillis)
    }

    @Test
    fun `anchor age of exactly 20 minutes still forecasts`() {
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(linearSamples(lastAgeMinutes = 20, lastGlucose = 140.0, velocityMgdlPerMin = -1.0)),
            now,
        )
        assertFalse(snapshot.staleAnchor)
        assertTrue(snapshot.points.isNotEmpty())
        assertEquals(GlucoseForecastEngine.MAX_ANCHOR_AGE_MILLIS, snapshot.anchorAgeMillis)
    }

    @Test
    fun `days old samples outside lookback are insufficient data not a forecast`() {
        val old = Instant.parse("2026-09-01T12:00:00Z")
        val snapshot = GlucoseForecastEngine.forecast(
            emptyDosingInput(
                samples = (0..2).map { i ->
                    sample(old.plusSeconds(i * 5L * 60L), 120.0)
                },
            ),
            now,
        )
        assertTrue(snapshot.insufficientData)
        assertFalse(snapshot.staleAnchor)
        assertTrue(snapshot.points.isEmpty())
    }

    private fun assertBreakdownIdentity(snapshot: GlucoseForecastSnapshot) {
        val ageMinutes = snapshot.anchorAgeMillis / 60_000.0
        assertEquals(
            snapshot.velocityMgdlPerMin *
                GlucoseForecastEngine.minutesFromAnchor(30.0, ageMinutes),
            snapshot.trendEffectMgdl30,
            1e-9,
        )
        assertEquals(
            snapshot.velocityMgdlPerMin *
                GlucoseForecastEngine.minutesFromAnchor(60.0, ageMinutes),
            snapshot.trendEffectMgdl60,
            1e-9,
        )
        val reconstructed30 = snapshot.currentMgdl + snapshot.trendEffectMgdl30 -
            snapshot.insulinEffectMgdl30 + snapshot.carbEffectMgdl30
        assertEquals(reconstructed30, snapshot.unclampedMgdl30, 1e-9)
        val reconstructed60 = snapshot.currentMgdl + snapshot.trendEffectMgdl60 -
            snapshot.insulinEffectMgdl60 + snapshot.carbEffectMgdl60
        assertEquals(reconstructed60, snapshot.unclampedMgdl60, 1e-9)
        assertEquals(
            reconstructed30.coerceIn(
                GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
                GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
            ),
            snapshot.at30Min!!.glucoseMgdl,
            1e-9,
        )
        assertEquals(
            reconstructed60.coerceIn(
                GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
                GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
            ),
            snapshot.at60Min!!.glucoseMgdl,
            1e-9,
        )
        assertEquals(
            snapshot.unclampedMgdl30 < GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
            snapshot.clampMin30,
        )
        assertEquals(
            snapshot.unclampedMgdl30 > GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
            snapshot.clampMax30,
        )
        assertEquals(
            snapshot.unclampedMgdl60 < GlucoseForecastEngine.MIN_GLUCOSE_MGDL,
            snapshot.clampMin60,
        )
        assertEquals(
            snapshot.unclampedMgdl60 > GlucoseForecastEngine.MAX_GLUCOSE_MGDL,
            snapshot.clampMax60,
        )
    }

    private fun emptyDosingInput(
        samples: List<GlucoseForecastInput.GlucoseSample>,
    ) = GlucoseForecastInput(
        samples = samples,
        boluses = emptyList(),
        meals = emptyList(),
        diaHours = 4.0,
        cirRatio = 10.0,
        isfMgdl = 50.0,
    )

    private fun linearSamples(
        lastAgeMinutes: Long,
        lastGlucose: Double,
        velocityMgdlPerMin: Double,
        count: Int = 12,
        stepMinutes: Long = 5L,
    ): List<GlucoseForecastInput.GlucoseSample> {
        val lastTime = now.minusSeconds(lastAgeMinutes * 60L)
        return (0 until count).map { i ->
            val stepsFromLast = (count - 1 - i).toLong()
            val t = lastTime.minusSeconds(stepsFromLast * stepMinutes * 60L)
            val g = lastGlucose - velocityMgdlPerMin * stepsFromLast * stepMinutes
            sample(t, g)
        }
    }

    private fun sample(time: Instant, mgdl: Double) =
        GlucoseForecastInput.GlucoseSample(time.toEpochMilli(), mgdl)
}
