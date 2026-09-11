package com.omb9.glucosehero.ui.settings

import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourcesCopyTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun `sample window is trailing 24 hours`() {
        assertEquals(now - 24L * hour, DataSourcesCopy.sampleWindowStart(now))
    }

    @Test
    fun `24h count is scoped by source and timestamp`() {
        val since = DataSourcesCopy.sampleWindowStart(now)
        val rows = listOf(
            GlucoseSampleSource.XDRIP_BROADCAST to now - hour,
            GlucoseSampleSource.XDRIP_BROADCAST to now - 25L * hour,
            GlucoseSampleSource.NIGHTSCOUT to now - hour,
            GlucoseSampleSource.HEALTH_CONNECT to now - hour,
        )
        assertEquals(
            1,
            DataSourcesCopy.countSamplesSince(rows, GlucoseSampleSource.XDRIP_BROADCAST, since),
        )
        assertEquals(
            1,
            DataSourcesCopy.countSamplesSince(rows, GlucoseSampleSource.NIGHTSCOUT, since),
        )
        assertEquals(
            0,
            DataSourcesCopy.countSamplesSince(rows, GlucoseSampleSource.LIBRE_LINK_UP, since),
        )
    }

    @Test
    fun `nightscout v1_only maps to the v1-only string`() {
        assertEquals(
            R.string.nightscout_error_v1_only,
            DataSourcesCopy.nightscoutErrorStringRes(NightscoutErrorCodes.V1_ONLY),
        )
        assertEquals(
            R.string.nightscout_error_v1_only,
            DataSourcesCopy.errorMessageRes(
                GlucoseSampleSource.NIGHTSCOUT,
                NightscoutErrorCodes.V1_ONLY,
            ),
        )
    }

    @Test
    fun `blank last error is omitted`() {
        assertNull(DataSourcesCopy.errorMessageRes(GlucoseSampleSource.NIGHTSCOUT, null))
        assertNull(DataSourcesCopy.errorMessageRes(GlucoseSampleSource.NIGHTSCOUT, "  "))
        assertNull(
            DataSourcesCopy.errorMessageRes(GlucoseSampleSource.LIBRE_LINK_UP, "anything"),
        )
    }

    @Test
    fun `health connect revoked is an error and not-connected is not`() {
        assertEquals(
            DataSourcesCopy.HC_REVOKED,
            DataSourcesCopy.healthConnectErrorCode(
                status = HealthConnectStatus.AVAILABLE,
                revoked = true,
                lastError = null,
            ),
        )
        assertNull(
            DataSourcesCopy.healthConnectErrorCode(
                status = HealthConnectStatus.AVAILABLE,
                revoked = false,
                lastError = null,
            ),
        )
        assertEquals(
            R.string.data_sources_hc_unavailable,
            DataSourcesCopy.errorMessageRes(
                GlucoseSampleSource.HEALTH_CONNECT,
                DataSourcesCopy.HC_UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `xdrip listen test prefers last broadcast over waiting`() {
        assertEquals(
            XdripListenResult.Disabled,
            DataSourcesCopy.classifyXdripListen(
                enabled = false,
                allowlistedInstalled = true,
                lastSuccessMillis = now,
            ),
        )
        val received = DataSourcesCopy.classifyXdripListen(
            enabled = true,
            allowlistedInstalled = false,
            lastSuccessMillis = now,
        )
        assertTrue(received is XdripListenResult.Received)
        assertEquals(
            XdripListenResult.NoAllowlistedApp,
            DataSourcesCopy.classifyXdripListen(
                enabled = true,
                allowlistedInstalled = false,
                lastSuccessMillis = null,
            ),
        )
        assertEquals(
            XdripListenResult.Waiting,
            DataSourcesCopy.classifyXdripListen(
                enabled = true,
                allowlistedInstalled = true,
                lastSuccessMillis = null,
            ),
        )
    }
}
