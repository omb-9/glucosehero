package com.omb9.glucosehero.util

/**
 * Joins already-localized clauses into one TalkBack string so an equation
 * split across [androidx.compose.material3.Text] nodes is not announced as
 * disconnected fragments.
 */
object WhyThisNumberCopy {
    fun spokenSentence(clauses: List<String>): String =
        clauses.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
