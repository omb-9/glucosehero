package com.omb9.glucosehero.crisis

/**
 * Splits `dumpsys alarm` so tests can tell a live RTC_WAKEUP batch from
 * history. Alarm Stats / Recent lines still mention HYPO_SOS_TIMEOUT after
 * [HypoSosManager] cancels; that is not a pending alarm.
 */
internal object HypoSosAlarmDump {

    fun pendingBatchesSection(dump: String): String {
        val start = dump.indexOf("Pending alarm batches")
        if (start < 0) return ""
        val fromPending = dump.substring(start)
        val endRel = indexOfHistoryStart(fromPending)
        return if (endRel < 0) fromPending else fromPending.substring(0, endRel)
    }

    fun afterPendingBatches(dump: String): String {
        val start = dump.indexOf("Pending alarm batches")
        if (start < 0) return dump
        val fromPending = dump.substring(start)
        val endRel = indexOfHistoryStart(fromPending)
        return if (endRel < 0) "" else fromPending.substring(endRel)
    }

    fun actionInPendingBatches(dump: String, action: String): Boolean =
        action in pendingBatchesSection(dump)

    fun actionOnlyInHistory(dump: String, action: String): Boolean =
        action !in pendingBatchesSection(dump) && action in afterPendingBatches(dump)

    private fun indexOfHistoryStart(fromPending: String): Int {
        val markers = listOf(
            "\n  Recent wakeup history",
            "\n  Recent alarm",
            "\n  Recent Alarms",
            "\n  Alarm Stats",
            "\n  Top Alarms",
        )
        return markers.map { fromPending.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
    }
}
