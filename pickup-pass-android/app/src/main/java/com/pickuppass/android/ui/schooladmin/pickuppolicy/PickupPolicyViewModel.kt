package com.pickuppass.android.ui.schooladmin.pickuppolicy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickupPolicyUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val restrictedToTimeWindow: Boolean = false,
    val startTime: String = "14:00",
    val endTime: String = "18:00",
    val allowManualOverride: Boolean = true,
    val timeZone: String = "Asia/Manila",
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class PickupPolicyViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickupPolicyUiState())
    val uiState: StateFlow<PickupPolicyUiState> = _uiState

    init { load() }

    fun setRestricted(value: Boolean) {
        _uiState.value = _uiState.value.copy(restrictedToTimeWindow = value, successMessage = null)
    }

    fun setStartTime(value: String) {
        _uiState.value = _uiState.value.copy(startTime = value.take(5), successMessage = null)
    }

    fun setEndTime(value: String) {
        _uiState.value = _uiState.value.copy(endTime = value.take(5), successMessage = null)
    }

    fun setManualOverride(value: Boolean) {
        _uiState.value = _uiState.value.copy(allowManualOverride = value, successMessage = null)
    }

    fun save() {
        val current = _uiState.value
        if (current.restrictedToTimeWindow && (!isValidTime(current.startTime) || !isValidTime(current.endTime))) {
            _uiState.value = current.copy(error = "Use 24-hour HH:mm format, for example 14:30.", successMessage = null)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, successMessage = null)
            val mode = if (_uiState.value.restrictedToTimeWindow) "time_window" else "unrestricted"
            when (val result = repository.updatePickupPolicy(
                mode = mode,
                earliestPickupTime = _uiState.value.startTime.takeIf { mode == "time_window" },
                latestPickupTime = _uiState.value.endTime.takeIf { mode == "time_window" },
                allowManualOverride = _uiState.value.allowManualOverride
            )) {
                is ApiResult.Success -> {
                    val p = result.data
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        restrictedToTimeWindow = p.mode == "time_window",
                        startTime = p.earliestPickupTime.ifBlank { _uiState.value.startTime },
                        endTime = p.latestPickupTime.ifBlank { _uiState.value.endTime },
                        allowManualOverride = p.allowManualOverride,
                        timeZone = p.timeZone,
                        error = null,
                        successMessage = "Pickup policy saved."
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getPickupPolicy()) {
                is ApiResult.Success -> {
                    val p = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        restrictedToTimeWindow = p.mode == "time_window",
                        startTime = p.earliestPickupTime.ifBlank { "14:00" },
                        endTime = p.latestPickupTime.ifBlank { "18:00" },
                        allowManualOverride = p.allowManualOverride,
                        timeZone = p.timeZone
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    private fun isValidTime(value: String): Boolean {
        if (!Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(value)) return false
        return true
    }
}
