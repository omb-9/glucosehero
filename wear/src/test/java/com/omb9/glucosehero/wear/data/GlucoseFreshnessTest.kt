package com.omb9.glucosehero.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseFreshnessTest {

    private val now = 1_700_000_000_000L
    private val freshCeiling = GlucoseFreshness.FRESH_MAX_AGE_MILLIS

    @Test
    fun `null timestamp is NoData`() {
        assertEquals(GlucoseFreshness.NoData, GlucoseFreshness.classify(null, now))
    }

    @Test
    fun `non positive timestamp is NoData`() {
        assertEquals(GlucoseFreshness.NoData, GlucoseFreshness.classify(0L, now))
        assertEquals(GlucoseFreshness.NoData, GlucoseFreshness.classify(-1L, now))
    }

    @Test
    fun `newest sample at now is Fresh`() {
        assertEquals(GlucoseFreshness.Fresh, GlucoseFreshness.classify(now, now))
    }

    @Test
    fun `age at the 8 minute ceiling is Fresh`() {
        assertEquals(
            GlucoseFreshness.Fresh,
            GlucoseFreshness.classify(now - freshCeiling, now),
        )
    }

    @Test
    fun `age one millisecond past the ceiling is Stale`() {
        val freshness = GlucoseFreshness.classify(now - freshCeiling - 1L, now)
        assertTrue(freshness is GlucoseFreshness.Stale)
        assertEquals(freshCeiling + 1L, (freshness as GlucoseFreshness.Stale).ageMillis)
    }

    @Test
    fun `seven minutes is Fresh`() {
        assertEquals(
            GlucoseFreshness.Fresh,
            GlucoseFreshness.classify(now - 7L * 60_000L, now),
        )
    }

    @Test
    fun `forty minutes is Stale with that age`() {
        val age = 40L * 60_000L
        assertEquals(
            GlucoseFreshness.Stale(age),
            GlucoseFreshness.classify(now - age, now),
        )
    }

    @Test
    fun `future timestamp is Fresh with no negative age`() {
        assertEquals(GlucoseFreshness.Fresh, GlucoseFreshness.classify(now + 60_000L, now))
    }

    @Test
    fun `formatAge uses minutes below one hour`() {
        assertEquals(
            GlucoseAgeParts(8L, GlucoseAgeUnit.MINUTES),
            formatGlucoseAge(8L * 60_000L + 1L),
        )
        assertEquals(
            GlucoseAgeParts(59L, GlucoseAgeUnit.MINUTES),
            formatGlucoseAge(59L * 60_000L),
        )
    }

    @Test
    fun `formatAge uses whole hours from 60 minutes until 24 hours`() {
        assertEquals(
            GlucoseAgeParts(1L, GlucoseAgeUnit.HOURS),
            formatGlucoseAge(60L * 60_000L),
        )
        assertEquals(
            GlucoseAgeParts(1L, GlucoseAgeUnit.HOURS),
            formatGlucoseAge(90L * 60_000L),
        )
        assertEquals(
            GlucoseAgeParts(23L, GlucoseAgeUnit.HOURS),
            formatGlucoseAge(23L * 60L * 60_000L),
        )
    }

    @Test
    fun `formatAge uses whole days from 24 hours`() {
        assertEquals(
            GlucoseAgeParts(1L, GlucoseAgeUnit.DAYS),
            formatGlucoseAge(24L * 60L * 60_000L),
        )
        assertEquals(
            GlucoseAgeParts(2L, GlucoseAgeUnit.DAYS),
            formatGlucoseAge(48L * 60L * 60_000L),
        )
    }

    @Test
    fun `formatAge never prints zero minutes`() {
        assertEquals(
            GlucoseAgeParts(1L, GlucoseAgeUnit.MINUTES),
            formatGlucoseAge(0L),
        )
    }
}
