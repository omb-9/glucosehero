package com.omb9.glucosehero.data.cgm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wear cannot depend on `:app`, so the classifier is copied. This test
 * fails if [GlucoseFreshness.FRESH_MAX_AGE_MILLIS] drifts from the Wear
 * copy, which would make "current" mean different things on phone vs watch.
 */
class GlucoseFreshnessLockstepTest {

    @Test
    fun `phone and Wear Fresh ceilings are the same expression`() {
        val appExpr = freshMaxAgeExpression(appFreshnessFile().readText())
        val wearExpr = freshMaxAgeExpression(wearFreshnessFile().readText())
        assertEquals(wearExpr, appExpr)
        assertEquals(8L * 60L * 1000L, GlucoseFreshness.FRESH_MAX_AGE_MILLIS)
    }

    private fun freshMaxAgeExpression(source: String): String {
        val match = Regex("""const val FRESH_MAX_AGE_MILLIS:\s*Long\s*=\s*(.+)""").find(source)
        assertTrue("missing FRESH_MAX_AGE_MILLIS in source", match != null)
        return match!!.groupValues[1].trim()
    }

    private fun appFreshnessFile(): File {
        val candidates = listOf(
            File("src/main/java/com/omb9/glucosehero/data/cgm/GlucoseFreshness.kt"),
            File("app/src/main/java/com/omb9/glucosehero/data/cgm/GlucoseFreshness.kt"),
        )
        return candidates.first { it.exists() }
    }

    private fun wearFreshnessFile(): File {
        val candidates = listOf(
            File("../wear/src/main/java/com/omb9/glucosehero/wear/data/GlucoseFreshness.kt"),
            File("wear/src/main/java/com/omb9/glucosehero/wear/data/GlucoseFreshness.kt"),
        )
        return candidates.first { it.exists() }
    }
}
