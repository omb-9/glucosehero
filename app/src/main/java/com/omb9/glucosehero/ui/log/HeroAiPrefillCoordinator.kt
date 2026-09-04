package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * In-memory bridge between the Hero AI chat screen and the Log screen.
 *
 * The two screens live in different navigation destinations, so [ChatViewModel]
 * cannot reach [LogViewModel] directly. Chat publishes a parsed prefill here;
 * the Log screen is then navigated to via [openLogRequests] and consumes the
 * pending payload the first time it composes.
 *
 * The prefill is normalised to canonical mg/dL on its way through here, so
 * the Log screen never has to know which display unit the model used.
 */
@ActivityRetainedScoped
class HeroAiPrefillCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    private val _pendingPrefill = MutableStateFlow<HeroAiPrefill?>(null)
    val pendingPrefill: StateFlow<HeroAiPrefill?> = _pendingPrefill.asStateFlow()

    private val _openLogRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openLogRequests: SharedFlow<Unit> = _openLogRequests.asSharedFlow()

    fun requestPrefill(prefill: HeroAiPrefill) {
        // A missing or unrecognised unit falls back to the user's configured
        // display unit, so resolve it from DataStore. This is a bounded read
        // that only runs when the model actually invokes the prefill tool.
        val displayUnit = runBlocking(Dispatchers.IO) {
            settingsRepository.settings.first().unit
        }
        _pendingPrefill.value = prefill.toCanonical(displayUnit)
        _openLogRequests.tryEmit(Unit)
    }

    fun consumePrefill() {
        _pendingPrefill.value = null
    }
}
