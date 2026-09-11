package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import kotlin.math.round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CgmGlucoseTest {

    @Test
    fun `mmol converts with MGDL_PER_MMOL and is range gated`() {
        val mgdl = CgmGlucose.toCanonicalMgdl(6.4, GlucoseUnit.MMOL)
        assertEquals(round(6.4 * GlucoseUnit.MGDL_PER_MMOL), mgdl!!, 1e-9)
    }

    @Test
    fun `mgdl is kept as-is when inside range`() {
        assertEquals(115.0, CgmGlucose.toCanonicalMgdl(115.0, GlucoseUnit.MGDL)!!, 1e-9)
    }

    @Test
    fun `below 20 mgdl is rejected`() {
        assertNull(CgmGlucose.toCanonicalMgdl(19.9, GlucoseUnit.MGDL))
    }

    @Test
    fun `above 600 mgdl is rejected`() {
        assertNull(CgmGlucose.toCanonicalMgdl(600.1, GlucoseUnit.MGDL))
    }

    @Test
    fun `bounds 20 and 600 are accepted`() {
        assertEquals(20.0, CgmGlucose.toCanonicalMgdl(20.0, GlucoseUnit.MGDL)!!, 1e-9)
        assertEquals(600.0, CgmGlucose.toCanonicalMgdl(600.0, GlucoseUnit.MGDL)!!, 1e-9)
    }

    @Test
    fun `non finite values are rejected`() {
        assertNull(CgmGlucose.toCanonicalMgdl(Double.NaN, GlucoseUnit.MGDL))
        assertNull(CgmGlucose.toCanonicalMgdl(Double.POSITIVE_INFINITY, GlucoseUnit.MGDL))
    }

    @Test
    fun `blank external id is rejected`() {
        val sample = entity(externalId = "  ", glucoseMgdl = 110.0)
        assertNull(CgmGlucose.canonicalize(sample))
    }

    @Test
    fun `non positive timestamp is rejected`() {
        val sample = entity(timestamp = 0L, glucoseMgdl = 110.0)
        assertNull(CgmGlucose.canonicalize(sample))
    }

    private fun entity(
        timestamp: Long = 1_700_000_000_000L,
        glucoseMgdl: Double = 110.0,
        externalId: String = "id-1",
    ) = GlucoseSampleEntity(
        timestamp = timestamp,
        glucoseMgdl = glucoseMgdl,
        source = GlucoseSampleSource.NIGHTSCOUT,
        externalId = externalId,
        recordingMethod = 0,
        importedAt = 1L,
    )
}
