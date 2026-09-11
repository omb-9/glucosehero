package com.omb9.glucosehero.work

import androidx.work.ListenableWorker
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightscoutSyncWorkerTest {

    @Test
    fun uniqueNameIsStableAndDistinctFromHealthConnect() {
        assertEquals("nightscout_sync_periodic", NightscoutSyncWorker.UNIQUE_NAME)
        assertNotEquals(
            NightscoutSyncWorker.UNIQUE_NAME,
            HealthConnectSyncWorker.UNIQUE_NAME,
        )
    }

    @Test
    fun resultIfDisabled_shortCircuitsWhenOff() {
        assertEquals(
            ListenableWorker.Result.success(),
            NightscoutSyncWorker.resultIfDisabled(enabled = false),
        )
        assertNull(NightscoutSyncWorker.resultIfDisabled(enabled = true))
    }

    @Test
    fun mapPullError_retriesOnlyTransientCodes() {
        assertEquals(
            ListenableWorker.Result.success(),
            NightscoutSyncWorker.mapPullError(null),
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            NightscoutSyncWorker.mapPullError(NightscoutErrorCodes.NETWORK),
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            NightscoutSyncWorker.mapPullError(NightscoutErrorCodes.HTTP),
        )
        assertEquals(
            ListenableWorker.Result.success(),
            NightscoutSyncWorker.mapPullError(NightscoutErrorCodes.AUTH),
        )
        assertEquals(
            ListenableWorker.Result.success(),
            NightscoutSyncWorker.mapPullError(NightscoutErrorCodes.CLEARTEXT),
        )
        assertEquals(
            ListenableWorker.Result.success(),
            NightscoutSyncWorker.mapPullError(NightscoutErrorCodes.V1_ONLY),
        )
    }
}
