package com.omb9.glucosehero.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HealthConnectSyncWorkerTest {

    @Test
    fun `unique worker names are distinct and match conventions`() {
        assertEquals("health_connect_sync_periodic", HealthConnectSyncWorker.UNIQUE_NAME)
        assertEquals("health_connect_sync_expedited", HealthConnectSyncWorker.EXPEDITED_UNIQUE_NAME)

        assertNotEquals(
            HealthConnectSyncWorker.UNIQUE_NAME,
            HealthConnectSyncWorker.EXPEDITED_UNIQUE_NAME,
        )
    }

    @Test
    fun `periodic unique name differs from other background workers`() {
        assertNotEquals(
            HealthConnectSyncWorker.UNIQUE_NAME,
            PendingQueryWorker.UNIQUE_NAME,
        )
        assertNotEquals(
            HealthConnectSyncWorker.UNIQUE_NAME,
            AutoBackupWorker.UNIQUE_NAME,
        )
        assertNotEquals(
            HealthConnectSyncWorker.UNIQUE_NAME,
            PatternRecognitionWorker.UNIQUE_NAME,
        )
    }

    @Test
    fun `cancelPeriodic cancels the unique periodic work name`() {
        assertEquals("health_connect_sync_periodic", HealthConnectSyncWorker.UNIQUE_NAME)
    }
}
