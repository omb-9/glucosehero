package com.omb9.glucosehero.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x AA minimum contrast for normal text. */
const val MIN_TEXT_CONTRAST_RATIO = 4.5f

/**
 * Relative luminance where black and white have equal contrast against a
 * background: (L + 0.05) / 0.05 = 1.05 / (L + 0.05) => L ≈ 0.179.
 * Surfaces at or above this get black content; darker surfaces get white.
 */
const val ON_COLOR_LUMINANCE_THRESHOLD = 0.179f

/**
 * WCAG 2 relative luminance of an sRGB color. Alpha is ignored; composite
 * translucent colors onto an opaque canvas before calling this.
 */
fun relativeLuminance(color: Color): Float {
    val r = linearizeSrgb(color.red)
    val g = linearizeSrgb(color.green)
    val b = linearizeSrgb(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * WCAG 2 contrast ratio of two colors, (L1 + 0.05) / (L2 + 0.05) with L1
 * the lighter relative luminance. Order of arguments does not matter.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Black or white content color that maximizes contrast on [background]. */
fun onColorFor(background: Color): Color =
    if (relativeLuminance(background) >= ON_COLOR_LUMINANCE_THRESHOLD) {
        Color.Black
    } else {
        Color.White
    }

/**
 * Keeps [preferred] when it already meets [minContrast] against [background],
 * otherwise falls back to [onColorFor]. Used for onPrimaryContainer so dark
 * schemes can keep chromatic accent text when it already passes AA.
 */
fun contrastingContentColor(
    preferred: Color,
    background: Color,
    minContrast: Float = MIN_TEXT_CONTRAST_RATIO,
): Color {
    if (contrastRatio(preferred, background) >= minContrast) return preferred
    return onColorFor(background)
}

private fun linearizeSrgb(channel: Float): Float =
    if (channel <= 0.04045f) {
        channel / 12.92f
    } else {
        ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
