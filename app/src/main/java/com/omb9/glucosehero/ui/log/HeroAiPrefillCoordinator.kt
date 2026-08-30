package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.domain.model.HeroAiPrefill
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory bridge between the Hero AI chat screen and the Log screen.
 *
 * The two screens live in different navigation destinations, so [ChatViewModel]
 * cannot reach [LogViewModel] directly. Chat publishes a parsed prefill here;
 * the Log screen is then navigated to via [openLogRequests] and consumes the
 * pending payload the first time it composes.
 */
@Singleton
class HeroAiPrefillCoordinator @Inject constructor() {

    private val _pendingPrefill = MutableStateFlow<HeroAiPrefill?>(null)
    val pendingPrefill: StateFlow<HeroAiPrefill?> = _pendingPrefill.asStateFlow()

    private val _openLogRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openLogRequests: SharedFlow<Unit> = _openLogRequests.asSharedFlow()

    fun requestPrefill(prefill: HeroAiPrefill) {
        _pendingPrefill.value = prefill
        _openLogRequests.tryEmit(Unit)
    }

    fun consumePrefill() {
        _pendingPrefill.value = null
    }
}
