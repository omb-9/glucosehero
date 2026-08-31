package com.omb9.glucosehero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.omb9.glucosehero.domain.model.ThemeMode
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
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = Color.Black,
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
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = Color.White,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
