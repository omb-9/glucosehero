package com.omb9.glucosehero.ui.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Pager driving the four top-level tabs. Provided only while the HOME route
 * is composed so the glucose chart can hand leftover horizontal drag to the
 * pager at either scroll edge. Fullscreen chart is a separate route and must
 * not see this.
 */
internal val LocalHomePagerState = staticCompositionLocalOf<PagerState?> { null }
