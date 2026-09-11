package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmSourcePriorityTest {

    @Test
    fun `rank order matches xDrip then Nightscout then Libre then Health Connect then manual`() {
        val ranked = GlucoseSampleSource.entries.sortedByDescending { CgmSourcePriority.rank(it) }
        assertEquals(
            listOf(
                GlucoseSampleSource.XDRIP_BROADCAST,
                GlucoseSampleSource.NIGHTSCOUT,
                GlucoseSampleSource.LIBRE_LINK_UP,
                GlucoseSampleSource.HEALTH_CONNECT,
                GlucoseSampleSource.MANUAL_IMPORT,
            ),
            ranked,
        )
    }

    @Test
    fun `xDrip outranks Health Connect`() {
        assertTrue(
            CgmSourcePriority.isHigherThan(
                GlucoseSampleSource.XDRIP_BROADCAST,
                GlucoseSampleSource.HEALTH_CONNECT,
            ),
        )
    }
}
