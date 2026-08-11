package com.pickuppass.android.ui.parent.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pickuppass.android.data.model.DeviceSessionItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SessionRepository
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyDevicesViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val auth: FirebaseAuth,
    private val sessionExpiryManager: SessionExpiryManager
) : ViewModel() {
    data class UiState(
        val loading: Boolean = false,
        val devices: List<DeviceSessionItem> = emptyList(),
        val error: String? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val currentDeviceId: String get() = repository.currentDeviceId

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        when (val result = repository.listDevices()) {
            is ApiResult.Success -> _uiState.value = UiState(devices = result.data)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = result.message)
        }
    }

    fun revoke(device: DeviceSessionItem) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null, message = null)
        when (val result = repository.revoke(device.deviceId)) {
            is ApiResult.Success -> {
                if (device.deviceId == repository.currentDeviceId) {
                    auth.signOut()
                    sessionExpiryManager.notifySessionEnded(SessionEndReason.EXPIRED_OR_REVOKED)
                } else {
                    refresh()
                }
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = result.message)
        }
    }

    fun revokeOthers() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null, message = null)
        when (val result = repository.revokeOthers()) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(message = "Signed out ${result.data} other device(s).")
                refresh()
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = result.message)
        }
    }
}
