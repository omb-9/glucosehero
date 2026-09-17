package com.omb9.glucosehero.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Called from [com.omb9.glucosehero.ui.log.LogScreen] via SideEffect once
 * settings are non-null and the first paging refresh is no longer Loading,
 * so MainActivity can drop the splash without waiting for a content draw.
 */
val LocalOnLogFirstContentReady = staticCompositionLocalOf { {} }
