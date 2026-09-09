package com.omb9.glucosehero.crisis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.util.CrisisDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class EmergencySosUiState(
    val enabled: Boolean = false,
    val timeoutMinutes: Int = CrisisDetector.DEFAULT_SOS_TIMEOUT_MINUTES,
    val caregivers: List<CaregiverContact> = emptyList(),
    val draftName: String = "",
    val draftPhone: String = "",
    val hasSmsPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
)

@HiltViewModel
class EmergencySosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val manager: HypoSosManager,
) : ViewModel() {

    private val _draftName = MutableStateFlow("")
    private val _draftPhone = MutableStateFlow("")
    private val _permTick = MutableStateFlow(0)
    private val drafts = combine(_draftName, _draftPhone) { name, phone -> name to phone }

    val uiState: StateFlow<EmergencySosUiState> = combine(
        settingsDataStore.hypoSosEnabled,
        settingsDataStore.hypoSosTimeoutMinutes,
        settingsDataStore.caregiverContacts,
        drafts,
        _permTick,
    ) { enabled, timeout, caregivers, draft, _ ->
        EmergencySosUiState(
            enabled = enabled,
            timeoutMinutes = timeout,
            caregivers = caregivers,
            draftName = draft.first,
            draftPhone = draft.second,
            hasSmsPermission = hasPermission(Manifest.permission.SEND_SMS),
            hasLocationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencySosUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHypoSosEnabled(enabled) }
    }

    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.setHypoSosTimeoutMinutes(
                CrisisDetector.clampSosTimeoutMinutes(minutes),
            )
        }
    }

    fun onDraftName(value: String) { _draftName.value = value }
    fun onDraftPhone(value: String) { _draftPhone.value = value }

    fun addCaregiver() {
        val name = _draftName.value.trim()
        val phone = _draftPhone.value.trim()
        if (phone.isBlank()) return
        viewModelScope.launch {
            val next = settingsDataStore.caregiverContactsSnapshot() + CaregiverContact(
                name = name.ifBlank { phone },
                phone = phone,
            )
            settingsDataStore.setCaregiverContacts(next)
            _draftName.value = ""
            _draftPhone.value = ""
        }
    }

    fun removeCaregiver(id: String) {
        viewModelScope.launch {
            val next = settingsDataStore.caregiverContactsSnapshot().filterNot { it.id == id }
            settingsDataStore.setCaregiverContacts(next)
        }
    }

    fun refreshPermissions() {
        _permTick.value++
    }

    fun dismissPendingSos() {
        viewModelScope.launch { manager.dismissPrompt() }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
