package com.omb9.glucosehero.data.cgm.xdrip

import com.omb9.glucosehero.data.cgm.CgmGlucose
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastMapperTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `well formed xdrip extras map to sample`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 121.0,
                XdripBroadcastIntents.EXTRA_BG_SLOPE_NAME to "SingleUp",
            ),
            sourcePackage = "com.eveningoutpost.dexdrip",
        )
        val sample = samples.single()
        assertEquals(t0, sample.timestamp)
        assertEquals(121.0, sample.glucoseMgdl, 1e-9)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, sample.source)
        assertEquals("xdrip:$t0", sample.externalId)
        assertNull(sample.hcRecordId)
        assertEquals("SingleUp", sample.trendArrow)
        assertEquals("com.eveningoutpost.dexdrip", sample.sourcePackage)
        assertEquals(0, sample.recordingMethod)
    }

    @Test
    fun `external id is stable for the same timestamp`() {
        val first = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 100.0,
            ),
        ).single()
        val second = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 101.0,
            ),
        ).single()
        assertEquals(first.externalId, second.externalId)
        assertEquals(XdripBroadcastMapper.externalId(t0), first.externalId)
    }

    @Test
    fun `empty extras are discarded`() {
        val samples = XdripBroadcastMapper.map(
            XdripBroadcastPayload(XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `malformed extras are discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to "not-a-time",
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to "nope",
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `missing timestamp is discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 110.0,
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `missing glucose is discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `zero timestamp is discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to 0L,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 110.0,
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `out of range glucose is discarded`() {
        val low = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 19.9,
            ),
        )
        val high = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 600.1,
            ),
        )
        assertTrue(low.isEmpty())
        assertTrue(high.isEmpty())
        assertNull(CgmGlucose.toCanonicalMgdl(19.9, com.omb9.glucosehero.domain.model.GlucoseUnit.MGDL))
    }

    @Test
    fun `status and no-data actions are ignored`() {
        val noData = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE_NO_DATA,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 110.0,
            ),
        )
        val status = XdripBroadcastMapper.map(
            payload(XdripBroadcastIntents.ACTION_STATUS_UPDATE),
        )
        assertTrue(noData.isEmpty())
        assertTrue(status.isEmpty())
    }

    @Test
    fun `aaps sgvs json maps mills mgdl and direction`() {
        val json = """[{"mills":$t0,"mgdl":169.0,"direction":"Flat"}]"""
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_SGV,
                XdripBroadcastIntents.EXTRA_SGVS to json,
            ),
            sourcePackage = "info.nightscout.androidaps",
        )
        val sample = samples.single()
        assertEquals(t0, sample.timestamp)
        assertEquals(169.0, sample.glucoseMgdl, 1e-9)
        assertEquals("Flat", sample.trendArrow)
        assertEquals("xdrip:$t0", sample.externalId)
        assertEquals("info.nightscout.androidaps", sample.sourcePackage)
    }

    @Test
    fun `malformed aaps json is discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_SGV,
                XdripBroadcastIntents.EXTRA_SGVS to "{not-json",
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `empty aaps sgvs array is discarded`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_SGV,
                XdripBroadcastIntents.EXTRA_SGVS to "[]",
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `aaps entry missing timestamp is discarded`() {
        val json = """[{"mgdl":120.0,"direction":"Flat"}]"""
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_SGV,
                XdripBroadcastIntents.EXTRA_SGVS to json,
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `aaps out of range glucose is discarded`() {
        val json = """[{"mills":$t0,"mgdl":5.0,"direction":"Flat"}]"""
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_SGV,
                XdripBroadcastIntents.EXTRA_SGVS to json,
            ),
        )
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `integer glucose extra is accepted as mgdl`() {
        val samples = XdripBroadcastMapper.map(
            payload(
                XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE,
                XdripBroadcastIntents.EXTRA_TIMESTAMP to t0,
                XdripBroadcastIntents.EXTRA_BG_ESTIMATE to 110,
            ),
        )
        assertEquals(1, samples.size)
        assertEquals(110.0, samples.single().glucoseMgdl, 1e-9)
    }

    private fun payload(action: String, vararg extras: Pair<String, Any?>) =
        XdripBroadcastPayload(action = action, extras = extras.toMap())
}
