package com.omb9.glucosehero.crisis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypoSosAlarmDumpTest {

    private val action = HypoSosManager.ACTION_TIMEOUT

    @Test
    fun liveRtcWakeupIsInPendingBatches() {
        val dump = """
            Current Alarm Manager state:
              Pending alarm batches: 1
              Batch{abc num=1 start=123 end=123}:
                RTC_WAKEUP #0: Alarm{def type 0}
                  tag=*walarm*:$action
                  operation=PendingIntent{BroadcastRecord{HypoSosAlarmReceiver}}
              Recent wakeup history:
                12:00:00 $action
              Alarm Stats:
                $action +1ms
        """.trimIndent()

        assertTrue(HypoSosAlarmDump.actionInPendingBatches(dump, action))
        assertFalse(HypoSosAlarmDump.actionOnlyInHistory(dump, action))
    }

    @Test
    fun cancelledAlarmInRecentStatsIsHistoryNotALiveBatch() {
        val dump = """
            Current Alarm Manager state:
              Pending alarm batches: 0
              Recent wakeup history:
                12:05:00 RTC_WAKEUP $action
              Alarm Stats:
                RTC_WAKEUP $action cancelled
        """.trimIndent()

        assertFalse(HypoSosAlarmDump.actionInPendingBatches(dump, action))
        assertTrue(HypoSosAlarmDump.actionOnlyInHistory(dump, action))
    }
}
