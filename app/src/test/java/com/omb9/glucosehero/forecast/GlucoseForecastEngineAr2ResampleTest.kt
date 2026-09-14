package com.omb9.glucosehero.forecast

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseForecastEngineAr2ResampleTest {

    private val now = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun `uniform 5 minute series is unchanged by resampling`() {
        val series = (0..11).map { i ->
            sample(now.minusSeconds((11 - i) * 5L * 60L), 100.0 + i)
        }
        val gridded = GlucoseForecastEngine.resampleOntoGrid(series)
        assertEquals(series.size, gridded.size)
        series.indices.forEach { i ->
            assertEquals(series[i].timestampMillis, gridded[i].timestampMillis)
            assertEquals(series[i].glucoseMgdl, gridded[i].glucoseMgdl, 1e-12)
        }
    }

    @Test
    fun `backfill gap is linearly interpolated onto a 5 minute grid`() {
        val gapped = gappedSamples()
        val gridded = GlucoseForecastEngine.resampleOntoGrid(gapped)
        assertTrue(gridded.size > gapped.size)
        for (i in 1 until gridded.size) {
            val dt = gridded[i].timestampMillis - gridded[i - 1].timestampMillis
            assertEquals(5L * 60_000L, dt)
        }
        assertEquals(gapped.last().timestampMillis, gridded.last().timestampMillis)
        assertEquals(gapped.last().glucoseMgdl, gridded.last().glucoseMgdl, 1e-12)
    }

    @Test
    fun `AR2 runs on 4 irregular points after they fill a 5 minute grid`() {
        val irregular = listOf(
            sample(now.minusSeconds(60L * 60L), 100.0),
            sample(now.minusSeconds(40L * 60L), 108.0),
            sample(now.minusSeconds(20L * 60L), 116.0),
            sample(now, 124.0),
        )
        assertTrue(irregular.size < GlucoseForecastEngine.MIN_AR_GRIDDED_POINTS)
        val gridded = GlucoseForecastEngine.resampleOntoGrid(irregular)
        assertTrue(gridded.size >= GlucoseForecastEngine.MIN_AR_GRIDDED_POINTS)
        assertNotNull(GlucoseForecastEngine.fitAr2Velocity(irregular))
    }

    @Test
    fun `steady and rising fixtures are unchanged after 5 minute resampling`() {
        // Before resampling (irregular-lag AR/Kalman on already-uniform 5 min data):
        // steady velocity=0.0 ar=0.0 at30=120.0 at60=120.0
        // rising velocity=0.3998171760620368 ar=0.4 at30=133.9945152818611 at60=145.98903056372222
        val steady = forecastOf(steadySamples())
        assertEquals(0.0, steady.velocityMgdlPerMin, 0.0)
        assertEquals(0.0, GlucoseForecastEngine.fitAr2Velocity(steadySamples())!!, 0.0)
        assertEquals(120.0, steady.at30Min!!.glucoseMgdl, 0.0)
        assertEquals(120.0, steady.at60Min!!.glucoseMgdl, 0.0)

        val rising = forecastOf(risingSamples())
        val risingAr = GlucoseForecastEngine.fitAr2Velocity(risingSamples())!!
        assertEquals(0.3998171760620368, rising.velocityMgdlPerMin, 1e-12)
        assertEquals(0.4, risingAr, 1e-12)
        assertEquals(133.9945152818611, rising.at30Min!!.glucoseMgdl, 1e-12)
        assertEquals(145.98903056372222, rising.at60Min!!.glucoseMgdl, 1e-12)
    }

    @Test
    fun `gapped backfill fixture damps velocity without flipping sign`() {
        // Before resampling:
        // velocity=0.31569301968572094 ar=0.33529411764705724
        // at30=131.97079059057162 at60=141.44158118114325
        val gapped = gappedSamples()
        val snap = forecastOf(gapped)
        val ar = GlucoseForecastEngine.fitAr2Velocity(gapped)!!
        assertEquals(0.3058329266999602, snap.velocityMgdlPerMin, 1e-12)
        assertEquals(0.31621621621621465, ar, 1e-12)
        assertEquals(131.6749878009988, snap.at30Min!!.glucoseMgdl, 1e-12)
        assertEquals(140.8499756019976, snap.at60Min!!.glucoseMgdl, 1e-12)
        assertTrue(snap.velocityMgdlPerMin > 0.0)
        assertTrue(ar > 0.0)
    }

    private fun forecastOf(samples: List<GlucoseForecastInput.GlucoseSample>) =
        GlucoseForecastEngine.forecast(emptyDosingInput(samples), now)

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

    private fun steadySamples(): List<GlucoseForecastInput.GlucoseSample> =
        (0..11).map { i ->
            sample(now.minusSeconds((11 - i) * 5L * 60L), 120.0)
        }

    private fun risingSamples(): List<GlucoseForecastInput.GlucoseSample> =
        (0..11).map { i ->
            sample(now.minusSeconds((11 - i) * 5L * 60L), 100.0 + i * 2.0)
        }

    private fun gappedSamples(): List<GlucoseForecastInput.GlucoseSample> = buildList {
        for (i in 0..2) {
            add(sample(now.minusSeconds((11 - i) * 5L * 60L), 110.0 + i * 1.0))
        }
        for (i in 8..11) {
            add(sample(now.minusSeconds((11 - i) * 5L * 60L), 118.0 + (i - 8) * 1.5))
        }
    }

    private fun sample(time: Instant, mgdl: Double) =
        GlucoseForecastInput.GlucoseSample(time.toEpochMilli(), mgdl)
}
