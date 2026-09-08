package com.omb9.glucosehero.util

import androidx.compose.ui.graphics.Color
import com.omb9.glucosehero.domain.model.ThemeMode

/** The glucose range bucket a value falls into relative to the user's target range. */
enum class RangeCategory(val label: String) {
    VERY_LOW("Very low"),
    LOW("Low"),
    IN_RANGE("In range"),
    HIGH("High"),
    VERY_HIGH("Very high"),
}

/**
 * Fixed, accent-independent range coloring.
 *
 * Range color is deliberately decoupled from [com.omb9.glucosehero.domain.model.AccentColor]:
 * a high reading must never render in whatever soft color happens to match the user's chosen
 * accent. These constants are chosen for clarity and for contrast on both the Light and AMOLED
 * canvases, and are paired with shape differences in the chart (circle = in range, up-triangle =
 * high, down-triangle = low) and left-to-right ordering in the time-in-range bar so hue is never
 * the only signal.
 */
object GlucoseRangeColor {

    /** Clinical fixed thresholds (mg/dL). These are not user-configurable. */
    const val VERY_LOW_MGDL = 54f
    const val VERY_HIGH_MGDL = 250f

    // Light canvas: darker, higher-contrast values against the near-white surface.
    private val LightVeryLow = Color(0xFF283593)
    private val LightLow = Color(0xFF1565C0)
    private val LightInRange = Color(0xFF2E7D32)
    private val LightHigh = Color(0xFFD84315)
    private val LightVeryHigh = Color(0xFFB71C1C)

    // AMOLED canvas: brighter values against pure black.
    private val DarkVeryLow = Color(0xFF5E35B1)
    private val DarkLow = Color(0xFF64B5F6)
    private val DarkInRange = Color(0xFF66BB6A)
    private val DarkHigh = Color(0xFFFF7043)
    private val DarkVeryHigh = Color(0xFFE53935)

    /**
     * Classifies [mgdl] against [targetLow]/[targetHigh] plus the fixed clinical thresholds
     * [VERY_LOW_MGDL] and [VERY_HIGH_MGDL]. Boundaries are inclusive of the in-range bucket:
     * exactly [targetLow] or exactly [targetHigh] is [RangeCategory.IN_RANGE], exactly
     * [VERY_LOW_MGDL] is [RangeCategory.LOW], and exactly [VERY_HIGH_MGDL] is [RangeCategory.HIGH].
     */
    fun forValue(mgdl: Float, targetLow: Float, targetHigh: Float): RangeCategory = when {
        mgdl < VERY_LOW_MGDL -> RangeCategory.VERY_LOW
        mgdl < targetLow -> RangeCategory.LOW
        mgdl > VERY_HIGH_MGDL -> RangeCategory.VERY_HIGH
        mgdl > targetHigh -> RangeCategory.HIGH
        else -> RangeCategory.IN_RANGE
    }

    /**
     * Returns the fixed color for [category]. [themeMode] selects the Light vs. AMOLED variant
     * for contrast; [ThemeMode.SYSTEM] should be resolved to Light/AMOLED by the caller before
     * reaching this function (AMOLED is used as the dark default here).
     */
    fun colorFor(category: RangeCategory, themeMode: ThemeMode): Color {
        val dark = themeMode == ThemeMode.AMOLED || themeMode == ThemeMode.SYSTEM
        return when (category) {
            RangeCategory.VERY_LOW -> if (dark) DarkVeryLow else LightVeryLow
            RangeCategory.LOW -> if (dark) DarkLow else LightLow
            RangeCategory.IN_RANGE -> if (dark) DarkInRange else LightInRange
            RangeCategory.HIGH -> if (dark) DarkHigh else LightHigh
            RangeCategory.VERY_HIGH -> if (dark) DarkVeryHigh else LightVeryHigh
        }
    }
}
