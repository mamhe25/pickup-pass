package com.pickuppass.android.ui.schooladmin.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.LaunchReadinessResponse
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaunchReadinessUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val readiness: LaunchReadinessResponse? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class LaunchReadinessViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LaunchReadinessUiState())
    val uiState: StateFlow<LaunchReadinessUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        when (val result = repository.getLaunchReadiness()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(loading = false, readiness = result.data)
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(loading = false, error = result.message)
        }
    }

    fun setManualCheck(key: String, checked: Boolean) {
        val current = _uiState.value.readiness ?: return
        if (_uiState.value.saving) return
        val next = current.manualChecks.toMutableMap().apply { put(key, checked) }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val result = repository.updateLaunchManualChecks(next)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    readiness = result.data,
                    message = "Launch check updated"
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = result.message)
            }
        }
    }

    fun requestReview() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            when (val result = repository.requestLaunchReview()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    saving = false,
                    readiness = result.data,
                    message = "Launch review requested from the platform owner"
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(saving = false, error = result.message)
            }
        }
    }
}
