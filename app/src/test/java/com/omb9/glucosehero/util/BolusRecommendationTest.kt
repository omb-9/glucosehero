package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileIssue
import com.omb9.glucosehero.domain.model.DosingProfileIssueCode
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.DosingSegment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class BolusRecommendationTest {

    private val zone = ZoneOffset.UTC
    private val sevenAm = Instant.parse("2026-06-15T07:00:00Z")

    private fun twoBlock() = DosingProfile(
        diaHours = 4f,
        segments = listOf(
            DosingSegment(LocalTime.MIDNIGHT, 50f, 10f, 100f),
            DosingSegment(LocalTime.of(6, 0), 30f, 8f, 110f),
        ),
    )

    @Test
    fun `ready carries the 06_00 segment at 07_00 not the overnight block`() {
        val rec = recommendBolus(
            load = DosingProfileLoad.Valid(twoBlock()),
            now = sevenAm,
            zoneId = zone,
            currentGlucoseMgdl = 180.0,
            carbsGrams = 0.0,
            insulinOnBoard = 0.0,
        )
        val ready = rec as BolusRecommendation.Ready
        assertEquals(LocalTime.of(6, 0), ready.segment.start)
        assertEquals(30f, ready.segment.isfMgdl)
        assertEquals(LocalTime.MIDNIGHT, ready.segmentEnd)
        assertEquals(70.0 / 30.0, ready.units, 1e-9)
        assertEquals(ready.breakdown.units, ready.units, 0.0)
    }

    @Test
    fun `invalid profile is refused without a number`() {
        val rec = recommendBolus(
            load = DosingProfileLoad.Invalid(
                issues = listOf(DosingProfileIssue(DosingProfileIssueCode.ISF_OUT_OF_BOUNDS, 1)),
                diaHours = 4f,
            ),
            now = sevenAm,
            zoneId = zone,
            currentGlucoseMgdl = 180.0,
            carbsGrams = 40.0,
            insulinOnBoard = 0.0,
        )
        val refused = rec as BolusRecommendation.Refused
        assertEquals(DosingProfileIssueCode.ISF_OUT_OF_BOUNDS, refused.issues.first().code)
        assertEquals(1, refused.issues.first().segmentIndex)
    }

    @Test
    fun `flat profile names the midnight block`() {
        val rec = recommendBolus(
            load = DosingProfileLoad.Valid(DosingProfile.single(BolusSettings())),
            now = sevenAm,
            zoneId = zone,
            currentGlucoseMgdl = 180.0,
            carbsGrams = 40.0,
            insulinOnBoard = 0.0,
        )
        val ready = rec as BolusRecommendation.Ready
        assertEquals(LocalTime.MIDNIGHT, ready.segment.start)
        assertEquals(LocalTime.MIDNIGHT, ready.segmentEnd)
    }
}
