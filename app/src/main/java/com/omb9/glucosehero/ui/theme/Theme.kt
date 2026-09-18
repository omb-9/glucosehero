package com.omb9.glucosehero.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.omb9.glucosehero.domain.model.UserSettings

/**
 * Light + strict AMOLED pure-black (#000000) themes, parameterized by the
 * user-selected accent (default Light Red #FF5252 per spec).
 *
 * Light is the out-of-the-box aesthetic: [UserSettings.themeMode] defaults
 * to [ThemeMode.LIGHT], so the bright, true-white Material 3 canvas is used
 * until the user explicitly opts into SYSTEM or AMOLED in Settings.
 */
@Composable
fun GlucoseHeroTheme(
    settings: UserSettings = UserSettings(),
    content: @Composable () -> Unit,
) {
    val accent = Color(settings.accent.argb)
    val dark = resolveAppIsDark(settings.themeMode, isSystemInDarkTheme())
    val colorScheme = glucoseHeroColorScheme(accent, dark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(dark) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            val lightBars = isAppearanceLightSystemBars(dark)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

internal const val PrimaryContainerAlphaDark = 0.22f
internal const val PrimaryContainerAlphaLight = 0.14f

internal fun schemeCanvas(dark: Boolean): Color =
    if (dark) AmoledBlack else LightBackground

internal fun primaryContainerColor(accent: Color, dark: Boolean): Color =
    accent.copy(alpha = if (dark) PrimaryContainerAlphaDark else PrimaryContainerAlphaLight)

/** [primaryContainerColor] composited over the theme canvas, for contrast math. */
internal fun drawnPrimaryContainer(accent: Color, dark: Boolean): Color =
    primaryContainerColor(accent, dark).compositeOver(schemeCanvas(dark))

/**
 * App color scheme for [accent]. [onPrimary] / [onSecondary] are black or white
 * from the accent's WCAG luminance. [onPrimaryContainer] keeps the accent when
 * it already meets AA against the drawn container, otherwise the same black or
 * white fallback (light-mode bubbles cannot use the raw accent as text).
 */
internal fun glucoseHeroColorScheme(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = onColorFor(accent)
    val primaryContainer = primaryContainerColor(accent, dark)
    val onPrimaryContainer = contrastingContentColor(
        preferred = accent,
        background = drawnPrimaryContainer(accent, dark),
    )
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = AmoledSurfaceContainerHigh,
            onSecondaryContainer = AmoledOnSurface,
            background = AmoledBlack,
            onBackground = AmoledOnSurface,
            surface = AmoledBlack,
            onSurface = AmoledOnSurface,
            surfaceVariant = AmoledSurfaceVariant,
            onSurfaceVariant = AmoledOnSurfaceVariant,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerLow = AmoledSurfaceContainer,
            surfaceContainerHigh = AmoledSurfaceContainerHigh,
            surfaceContainerHighest = AmoledSurfaceContainerHigh,
            outline = AmoledOutline,
            outlineVariant = AmoledSurfaceContainerHigh,
            error = Color(0xFFFF6E6E),
            onError = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = LightSurfaceContainerHigh,
            onSecondaryContainer = LightOnSurface,
            background = LightBackground,
            onBackground = LightOnSurface,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerLow = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHigh,
            outline = LightOutline,
            outlineVariant = LightSurfaceContainerHigh,
            error = Color(0xFFB3261E),
            onError = Color.White,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
