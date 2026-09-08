package com.omb9.glucosehero.data.health

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectAvailabilityTest {

    @Test
    fun `SDK_AVAILABLE maps to AVAILABLE`() {
        val status = HealthConnectAvailability.fromSdkStatus(HealthConnectClient.SDK_AVAILABLE)
        assertEquals(HealthConnectStatus.AVAILABLE, status)
        assertTrue(status.isAvailable)
    }

    @Test
    fun `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED maps to UPDATE_REQUIRED`() {
        val status = HealthConnectAvailability.fromSdkStatus(
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
        )
        assertEquals(HealthConnectStatus.UPDATE_REQUIRED, status)
        assertFalse(status.isAvailable)
    }

    @Test
    fun `SDK_UNAVAILABLE maps to UNAVAILABLE`() {
        val status = HealthConnectAvailability.fromSdkStatus(HealthConnectClient.SDK_UNAVAILABLE)
        assertEquals(HealthConnectStatus.UNAVAILABLE, status)
        assertFalse(status.isAvailable)
    }

    @Test
    fun `unknown SDK status codes default safely to UNAVAILABLE`() {
        val statusNegative = HealthConnectAvailability.fromSdkStatus(-1)
        assertEquals(HealthConnectStatus.UNAVAILABLE, statusNegative)
        assertFalse(statusNegative.isAvailable)

        val statusArbitrary = HealthConnectAvailability.fromSdkStatus(9999)
        assertEquals(HealthConnectStatus.UNAVAILABLE, statusArbitrary)
        assertFalse(statusArbitrary.isAvailable)
    }
}
