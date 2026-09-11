package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingSegment
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class GlucoseForecastEngineDosingProfileTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-06-15T11:50:00Z")

    private fun samples(): List<GlucoseForecastInput.GlucoseSample> =
        (0..6).map { i ->
            GlucoseForecastInput.GlucoseSample(
                now.minusSeconds((6 - i) * 5L * 60L).toEpochMilli(),
                140.0,
            )
        }

    @Test
    fun `single segment profile is bit identical to scalar input`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 2.0))
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 30.0))
        val scalar = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples(),
                boluses = boluses,
                meals = meals,
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        val profile = DosingProfile(
            diaHours = 4.0f,
            segments = listOf(
                DosingSegment(LocalTime.MIDNIGHT, 50f, 10f, 100f),
            ),
        )
        val withProfile = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples(),
                boluses = boluses,
                meals = meals,
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
                dosingProfile = profile,
                zoneId = zone,
            ),
            now,
        )
        assertEquals(scalar.points.size, withProfile.points.size)
        scalar.points.indices.forEach { i ->
            assertEquals(scalar.points[i].glucoseMgdl, withProfile.points[i].glucoseMgdl, 0.0)
        }
    }

    @Test
    fun `horizon crossing a segment uses per step ISF not ISF at now`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 3.0))
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 40.0))
        val atNow = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples(),
                boluses = boluses,
                meals = meals,
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
            ),
            now,
        )
        val profile = DosingProfile(
            diaHours = 4.0f,
            segments = listOf(
                DosingSegment(LocalTime.MIDNIGHT, 50f, 10f, 100f),
                DosingSegment(LocalTime.NOON, 25f, 5f, 100f),
            ),
        )
        val stepped = GlucoseForecastEngine.forecast(
            GlucoseForecastInput(
                samples = samples(),
                boluses = boluses,
                meals = meals,
                diaHours = 4.0,
                cirRatio = 10.0,
                isfMgdl = 50.0,
                dosingProfile = profile,
                zoneId = zone,
            ),
            now,
        )
        val first = atNow.points.first()
        val firstStepped = stepped.points.first()
        assertEquals(first.minutesAhead, 5)
        assertEquals(first.glucoseMgdl, firstStepped.glucoseMgdl, 1e-9)
        val delta60 = kotlin.math.abs(
            stepped.at60Min!!.glucoseMgdl - atNow.at60Min!!.glucoseMgdl,
        )
        assertTrue(delta60 > 0.05)
        assertTrue(stepped.horizonCrossedSegmentBoundary)
        assertTrue(stepped.dosingSegmentsUsed.size > 1)
        assertFalse(stepped.claimsSingleIsf)
        val explanation = ForecastDisplayFormatter.format(
            stepped,
            com.omb9.glucosehero.domain.model.GlucoseUnit.MGDL,
            use24HourTime = true,
        )
        assertTrue(explanation.horizonCrossedSegmentBoundary)
        assertFalse(explanation.claimsSingleIsf)
        val spoken = explanation.spokenSentenceForTest(
            "On-device estimate from trend, insulin, and carbs. Not a medical device.",
        )
        assertTrue(spoken.contains("not a single ISF"))
        assertTrue(spoken.contains("50"))
        assertTrue(spoken.contains("25"))
    }
}
