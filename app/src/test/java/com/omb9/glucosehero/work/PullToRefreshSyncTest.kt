package com.omb9.glucosehero.work

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToRefreshSyncTest {

    @Test
    fun `terminal states are succeeded failed and cancelled`() {
        assertTrue(WorkInfo.State.SUCCEEDED.isTerminal())
        assertTrue(WorkInfo.State.FAILED.isTerminal())
        assertTrue(WorkInfo.State.CANCELLED.isTerminal())
        assertFalse(WorkInfo.State.ENQUEUED.isTerminal())
        assertFalse(WorkInfo.State.RUNNING.isTerminal())
        assertFalse(WorkInfo.State.BLOCKED.isTerminal())
    }

    @Test
    fun `refresh timing constants keep the animation floor and hung-work ceiling`() {
        assertEquals(600L, PullToRefreshSync.MIN_MILLIS)
        assertEquals(8000L, PullToRefreshSync.MAX_MILLIS)
    }
}
