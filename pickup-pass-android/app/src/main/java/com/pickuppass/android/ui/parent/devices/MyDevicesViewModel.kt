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
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val busyDeviceId: String? = null,
        val revokingOthers: Boolean = false,
        val devices: List<DeviceSessionItem> = emptyList(),
        val error: String? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val currentDeviceId: String
        get() = repository.currentDeviceId

    private var refreshInProgress = false

    init {
        refresh()
    }

    fun refresh() {
        if (refreshInProgress) return
        refreshInProgress = true

        val firstLoad = _uiState.value.devices.isEmpty()
        _uiState.value = _uiState.value.copy(
            initialLoading = firstLoad,
            refreshing = !firstLoad,
            error = null
        )

        viewModelScope.launch {
            try {
                when (val result = repository.listDevices()) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            initialLoading = false,
                            refreshing = false,
                            devices = result.data.sortedWith(
                                compareByDescending<DeviceSessionItem> { it.current }
                                    .thenByDescending { it.active }
                                    .thenByDescending { it.lastSeenAt.orEmpty() }
                            ),
                            error = null
                        )
                    }

                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            initialLoading = false,
                            refreshing = false,
                            error = result.message
                        )
                    }
                }
            } finally {
                refreshInProgress = false
            }
        }
    }

    fun revoke(device: DeviceSessionItem) {
        if (
            _uiState.value.busyDeviceId != null ||
            _uiState.value.revokingOthers
        ) return

        _uiState.value = _uiState.value.copy(
            busyDeviceId = device.deviceId,
            error = null,
            message = null
        )

        viewModelScope.launch {
            when (val result = repository.revoke(device.deviceId)) {
                is ApiResult.Success -> {
                    if (device.deviceId == repository.currentDeviceId) {
                        auth.signOut()
                        sessionExpiryManager.notifySessionEnded(
                            SessionEndReason.EXPIRED_OR_REVOKED
                        )
                    } else {
                        val refreshed = repository.listDevices()
                        when (refreshed) {
                            is ApiResult.Success -> {
                                _uiState.value = _uiState.value.copy(
                                    busyDeviceId = null,
                                    devices = refreshed.data.sortedWith(
                                        compareByDescending<DeviceSessionItem> { it.current }
                                            .thenByDescending { it.active }
                                            .thenByDescending { it.lastSeenAt.orEmpty() }
                                    ),
                                    message = "Device signed out",
                                    error = null
                                )
                            }

                            is ApiResult.Failure -> {
                                _uiState.value = _uiState.value.copy(
                                    busyDeviceId = null,
                                    message = "Device signed out",
                                    error = "The session list could not be refreshed"
                                )
                            }
                        }
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        busyDeviceId = null,
                        error = result.message
                    )
                }
            }
        }
    }

    fun revokeOthers() {
        if (
            _uiState.value.revokingOthers ||
            _uiState.value.busyDeviceId != null
        ) return

        _uiState.value = _uiState.value.copy(
            revokingOthers = true,
            error = null,
            message = null
        )

        viewModelScope.launch {
            when (val result = repository.revokeOthers()) {
                is ApiResult.Success -> {
                    val refreshed = repository.listDevices()
                    when (refreshed) {
                        is ApiResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                revokingOthers = false,
                                devices = refreshed.data.sortedWith(
                                    compareByDescending<DeviceSessionItem> { it.current }
                                        .thenByDescending { it.active }
                                        .thenByDescending { it.lastSeenAt.orEmpty() }
                                ),
                                message = "Signed out ${result.data} other device(s)",
                                error = null
                            )
                        }

                        is ApiResult.Failure -> {
                            _uiState.value = _uiState.value.copy(
                                revokingOthers = false,
                                message = "Signed out ${result.data} other device(s)",
                                error = "The session list could not be refreshed"
                            )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        revokingOthers = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            error = null,
            message = null
        )
    }
}
