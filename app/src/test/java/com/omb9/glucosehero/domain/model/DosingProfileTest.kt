package com.omb9.glucosehero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DosingProfileTest {

    private val midnight = LocalTime.MIDNIGHT
    private val sixAm = LocalTime.of(6, 0)
    private val flat = BolusSettings(
        diaHours = 4.0f,
        cirRatio = 10.0f,
        isfMgdl = 50.0f,
        targetGlucoseMgdl = 100.0f,
    )

    private fun twoSegment(
        nightIsf: Float = 50f,
        morningIsf: Float = 30f,
        nightCir: Float = 10f,
        morningCir: Float = 8f,
        nightTarget: Float = 100f,
        morningTarget: Float = 110f,
    ) = DosingProfile(
        diaHours = 4.0f,
        segments = listOf(
            DosingSegment(midnight, nightIsf, nightCir, nightTarget),
            DosingSegment(sixAm, morningIsf, morningCir, morningTarget),
        ),
    )

    @Test
    fun `06_00 ISF change alters 07_00 correction versus overnight`() {
        val profile = twoSegment(nightIsf = 50f, morningIsf = 30f, nightTarget = 100f, morningTarget = 100f)
        val overnight = profile.toBolusSettings(LocalTime.of(5, 0))
        val morning = profile.toBolusSettings(LocalTime.of(7, 0))
        val at5am = com.omb9.glucosehero.util.BolusCalculator.recommend(
            currentGlucoseMgdl = 180.0,
            targetGlucoseMgdl = overnight.targetGlucoseMgdl.toDouble(),
            carbsGrams = 0.0,
            carbRatio = overnight.cirRatio.toDouble(),
            insulinSensitivityMgdl = overnight.isfMgdl.toDouble(),
            insulinOnBoard = 0.0,
        )
        val at7am = com.omb9.glucosehero.util.BolusCalculator.recommend(
            currentGlucoseMgdl = 180.0,
            targetGlucoseMgdl = morning.targetGlucoseMgdl.toDouble(),
            carbsGrams = 0.0,
            carbRatio = morning.cirRatio.toDouble(),
            insulinSensitivityMgdl = morning.isfMgdl.toDouble(),
            insulinOnBoard = 0.0,
        )
        assertEquals(1.6, at5am.units, 1e-9)
        assertEquals(80.0 / 30.0, at7am.units, 1e-9)
        assertTrue(at7am.units > at5am.units)
    }

    @Test
    fun `single segment at 00_00 matches flat BolusSettings`() {
        val profile = DosingProfile.single(flat)
        assertTrue(profile.validate() is DosingProfileValidation.Valid)
        val atMidnight = profile.toBolusSettings(midnight)
        val atNoon = profile.toBolusSettings(LocalTime.NOON)
        assertEquals(flat, atMidnight)
        assertEquals(flat, atNoon)
    }

    @Test
    fun `exactly at a start uses that segment`() {
        val profile = twoSegment()
        assertEquals(30f, profile.at(sixAm).isfMgdl)
        assertEquals(8f, profile.at(sixAm).cirRatio)
        assertEquals(110f, profile.at(sixAm).targetGlucoseMgdl)
    }

    @Test
    fun `one millisecond before a start uses the previous segment`() {
        val profile = twoSegment()
        val justBefore = sixAm.minusNanos(1_000_000L)
        assertEquals(50f, profile.at(justBefore).isfMgdl)
        assertEquals(10f, profile.at(justBefore).cirRatio)
        assertEquals(100f, profile.at(justBefore).targetGlucoseMgdl)
    }

    @Test
    fun `midnight wrap uses last segment then first`() {
        val profile = twoSegment()
        assertEquals(30f, profile.at(LocalTime.of(23, 59, 59, 999_000_000)).isfMgdl)
        assertEquals(50f, profile.at(midnight).isfMgdl)
    }

    @Test
    fun `empty segments is invalid`() {
        val result = validateDosingProfile(4f, emptyList())
        val invalid = result as DosingProfileValidation.Invalid
        assertTrue(invalid.issues.any { it.code == DosingProfileIssueCode.EMPTY_SEGMENTS })
    }

    @Test
    fun `missing 00_00 is invalid`() {
        val result = validateDosingProfile(
            4f,
            listOf(DosingSegment(sixAm, 50f, 10f, 100f)),
        )
        val invalid = result as DosingProfileValidation.Invalid
        assertTrue(invalid.issues.any { it.code == DosingProfileIssueCode.FIRST_SEGMENT_NOT_MIDNIGHT })
    }

    @Test
    fun `duplicate starts are invalid`() {
        val result = validateDosingProfile(
            4f,
            listOf(
                DosingSegment(midnight, 50f, 10f, 100f),
                DosingSegment(sixAm, 40f, 10f, 100f),
                DosingSegment(sixAm, 30f, 10f, 100f),
            ),
        )
        val invalid = result as DosingProfileValidation.Invalid
        assertTrue(invalid.issues.any { it.code == DosingProfileIssueCode.DUPLICATE_START })
    }

    @Test
    fun `unsorted starts are treated as overlap or gap`() {
        val result = validateDosingProfile(
            4f,
            listOf(
                DosingSegment(midnight, 50f, 10f, 100f),
                DosingSegment(LocalTime.of(12, 0), 40f, 10f, 100f),
                DosingSegment(sixAm, 30f, 10f, 100f),
            ),
        )
        val invalid = result as DosingProfileValidation.Invalid
        assertTrue(invalid.issues.any { it.code == DosingProfileIssueCode.STARTS_NOT_STRICTLY_INCREASING })
    }

    @Test
    fun `out of bounds ISF CIR and target are invalid`() {
        val result = validateDosingProfile(
            4f,
            listOf(
                DosingSegment(midnight, isfMgdl = 1f, cirRatio = 0.5f, targetGlucoseMgdl = 40f),
            ),
        )
        val invalid = result as DosingProfileValidation.Invalid
        val codes = invalid.issues.map { it.code }.toSet()
        assertTrue(DosingProfileIssueCode.ISF_OUT_OF_BOUNDS in codes)
        assertTrue(DosingProfileIssueCode.CIR_OUT_OF_BOUNDS in codes)
        assertTrue(DosingProfileIssueCode.TARGET_OUT_OF_BOUNDS in codes)
    }

    @Test
    fun `resolvedBolusSettings refuses an invalid profile`() {
        val profile = DosingProfile(
            diaHours = 4f,
            segments = listOf(DosingSegment(sixAm, 50f, 10f, 100f)),
        )
        val result = profile.resolvedBolusSettings(LocalTime.NOON)
        assertTrue(result is DosingResolveResult.Refused)
    }

    @Test
    fun `spring forward gap 02_30 uses the adjusted instant's segment`() {
        val zone = ZoneId.of("America/New_York")
        // 2026-03-08 02:00 EST -> 03:00 EDT. 02:30 does not exist.
        val profile = DosingProfile(
            diaHours = 4f,
            segments = listOf(
                DosingSegment(midnight, 50f, 10f, 100f),
                DosingSegment(LocalTime.of(2, 0), 40f, 10f, 100f),
                DosingSegment(LocalTime.of(3, 0), 30f, 10f, 100f),
            ),
        )
        assertTrue(profile.validate() is DosingProfileValidation.Valid)
        val date = LocalDate.of(2026, 3, 8)
        val segment = profile.atWallClock(date, LocalTime.of(2, 30), zone)
        // ofLocal shifts 02:30 forward by 1h -> 03:30, which is the 03:00 segment.
        assertEquals(LocalTime.of(3, 0), segment.start)
        assertEquals(30f, segment.isfMgdl)
    }

    @Test
    fun `fall back overlap 01_30 uses the earlier offset first occurrence`() {
        val zone = ZoneId.of("America/New_York")
        // 2026-11-01 02:00 EDT -> 01:00 EST. 01:30 occurs twice.
        val profile = DosingProfile(
            diaHours = 4f,
            segments = listOf(
                DosingSegment(midnight, 50f, 10f, 100f),
                DosingSegment(LocalTime.of(1, 0), 45f, 10f, 100f),
                DosingSegment(LocalTime.of(2, 0), 35f, 10f, 100f),
            ),
        )
        val date = LocalDate.of(2026, 11, 1)
        val instant = resolveWallClock(date, LocalTime.of(1, 30), zone)
        val zoned = instant.atZone(zone)
        // First occurrence is still on EDT (UTC-4).
        assertEquals(-4 * 3600, zoned.offset.totalSeconds)
        assertEquals(LocalTime.of(1, 30), zoned.toLocalTime())
        assertEquals(45f, profile.at(instant, zone).isfMgdl)
    }

    @Test
    fun `travel same instant different zones can hit different segments`() {
        val profile = twoSegment()
        // 2026-06-15 09:30Z = 05:30 EDT (night segment) and 10:30 BST (morning).
        val instant = Instant.parse("2026-06-15T09:30:00Z")
        val ny = ZoneId.of("America/New_York")
        val london = ZoneId.of("Europe/London")
        assertEquals(50f, profile.at(instant, ny).isfMgdl)
        assertEquals(30f, profile.at(instant, london).isfMgdl)
    }

    @Test
    fun `historical instant uses zone rules at that instant not today's offset`() {
        val profile = DosingProfile(
            diaHours = 4f,
            segments = listOf(
                DosingSegment(midnight, 50f, 10f, 100f),
                DosingSegment(LocalTime.of(3, 0), 30f, 10f, 100f),
            ),
        )
        val zone = ZoneId.of("America/New_York")
        val winter = Instant.parse("2026-01-15T07:00:00Z") // 02:00 EST
        val summer = Instant.parse("2026-07-15T07:00:00Z") // 03:00 EDT
        assertEquals(50f, profile.at(winter, zone).isfMgdl)
        assertEquals(30f, profile.at(summer, zone).isfMgdl)
    }

    @Test
    fun `codec migrates absent json from flat keys losslessly`() {
        val load = DosingProfileCodec.load(
            profileJson = null,
            diaHours = 5f,
            cirRatio = 8f,
            isfMgdl = 40f,
            targetGlucoseMgdl = 110f,
        ) as DosingProfileLoad.Valid
        val settings = load.profile.toBolusSettings(LocalTime.NOON)
        assertEquals(5f, settings.diaHours)
        assertEquals(8f, settings.cirRatio)
        assertEquals(40f, settings.isfMgdl)
        assertEquals(110f, settings.targetGlucoseMgdl)
        assertEquals(1, load.profile.segments.size)
        assertEquals(midnight, load.profile.segments.first().start)
    }

    @Test
    fun `unparseable json refuses and does not fall back to flat ISF`() {
        val load = DosingProfileCodec.load(
            profileJson = "{not-json",
            diaHours = 5f,
            cirRatio = 8f,
            isfMgdl = 40f,
            targetGlucoseMgdl = 110f,
        )
        val invalid = load as DosingProfileLoad.Invalid
        assertTrue(invalid.issues.any { it.code == DosingProfileIssueCode.UNPARSEABLE })
        val refused = DosingProfile.single(flat).copy(
            segments = emptyList(),
        ).resolvedBolusSettings(LocalTime.NOON)
        assertTrue(refused is DosingResolveResult.Refused)
    }

    @Test
    fun `valid json round trip is exact`() {
        val profile = twoSegment()
        val json = DosingProfileCodec.encode(profile)
        val loaded = DosingProfileCodec.load(
            profileJson = json,
            diaHours = 4f,
            cirRatio = 10f,
            isfMgdl = 50f,
            targetGlucoseMgdl = 100f,
        ) as DosingProfileLoad.Valid
        assertEquals(profile, loaded.profile)
    }
}
