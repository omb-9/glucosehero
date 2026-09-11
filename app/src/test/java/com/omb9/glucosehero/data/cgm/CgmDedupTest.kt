package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmDedupTest {

    private val window = CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS
    private val t0 = 1_700_000_000_000L

    @Test
    fun `exact timestamp tie keeps higher priority`() {
        val existing = listOf(sample(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0))
        val incoming = listOf(sample(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0))

        val plan = CgmDedup.plan(incoming, existing, window)

        assertEquals(listOf(incoming[0]), plan.accept)
        assertEquals(listOf(existing[0]), plan.delete)
    }

    @Test
    fun `lower priority arriving first is replaced by later higher priority`() {
        val existing = listOf(sample(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0))
        val incoming = listOf(
            sample(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0 + 30_000L),
        )

        val plan = CgmDedup.plan(incoming, existing, window)

        assertEquals(1, plan.accept.size)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, plan.accept[0].source)
        assertEquals(listOf(existing[0]), plan.delete)
    }

    @Test
    fun `lower priority arriving late is dropped`() {
        val existing = listOf(sample(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0))
        val incoming = listOf(
            sample(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0 + 40_000L),
        )

        val plan = CgmDedup.plan(incoming, existing, window)

        assertTrue(plan.accept.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `samples outside the window are both kept`() {
        val existing = listOf(sample(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0))
        val incoming = listOf(
            sample(GlucoseSampleSource.NIGHTSCOUT, "ns", t0 + window + 1L),
        )

        val plan = CgmDedup.plan(incoming, existing, window)

        assertEquals(incoming, plan.accept)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `same source same external id is skipped`() {
        val existing = listOf(sample(GlucoseSampleSource.NIGHTSCOUT, "ns-1", t0))
        val incoming = listOf(sample(GlucoseSampleSource.NIGHTSCOUT, "ns-1", t0 + 5_000L))

        val plan = CgmDedup.plan(incoming, existing, window)

        assertTrue(plan.accept.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `equal priority within window keeps existing`() {
        val existing = listOf(sample(GlucoseSampleSource.NIGHTSCOUT, "a", t0))
        val incoming = listOf(sample(GlucoseSampleSource.NIGHTSCOUT, "b", t0 + 10_000L))

        val plan = CgmDedup.plan(incoming, existing, window)

        assertTrue(plan.accept.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `mixed incoming batch prefers xDrip over Health Connect`() {
        val incoming = listOf(
            sample(GlucoseSampleSource.HEALTH_CONNECT, "hc", t0),
            sample(GlucoseSampleSource.XDRIP_BROADCAST, "xd", t0),
        )

        val plan = CgmDedup.plan(incoming, existing = emptyList(), windowMillis = window)

        assertEquals(1, plan.accept.size)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, plan.accept[0].source)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `window boundary is inclusive`() {
        val existing = listOf(sample(GlucoseSampleSource.MANUAL_IMPORT, "m", t0))
        val incoming = listOf(
            sample(GlucoseSampleSource.LIBRE_LINK_UP, "ll", t0 + window),
        )

        val plan = CgmDedup.plan(incoming, existing, window)

        assertEquals(incoming, plan.accept)
        assertEquals(existing, plan.delete)
    }

    private fun sample(
        source: GlucoseSampleSource,
        externalId: String,
        timestamp: Long,
    ) = CgmDedupSample(source, externalId, timestamp)
}
