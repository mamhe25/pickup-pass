package com.pickuppass.android.ui.schooladmin.guardianverification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.GuardianVerificationItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianVerificationUiState(
    val isLoading: Boolean = true,
    val verificationRequired: Boolean = false,
    val guardians: List<GuardianVerificationItem> = emptyList(),
    val busyUid: String? = null,
    val policyBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class GuardianVerificationViewModel @Inject constructor(
    private val repository: SchoolAdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GuardianVerificationUiState())
    val uiState: StateFlow<GuardianVerificationUiState> = _uiState

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        when (val result = repository.listGuardianVerification()) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false,
                verificationRequired = result.data.verificationRequired,
                guardians = result.data.guardians
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
        }
    }

    fun setPolicy(required: Boolean) = viewModelScope.launch {
        if (_uiState.value.policyBusy) return@launch
        _uiState.value = _uiState.value.copy(policyBusy = true, error = null, message = null)
        when (val result = repository.updateGuardianVerificationPolicy(required)) {
            is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                policyBusy = false,
                verificationRequired = required,
                message = if (required) "Guardian verification is now required for pickup" else "Guardian verification requirement disabled"
            )
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(policyBusy = false, error = result.message)
        }
    }

    fun updateStatus(guardian: GuardianVerificationItem, status: String, reason: String) = viewModelScope.launch {
        if (_uiState.value.busyUid != null) return@launch
        _uiState.value = _uiState.value.copy(busyUid = guardian.uid, error = null, message = null)
        when (val result = repository.updateGuardianVerificationStatus(guardian.uid, status, reason)) {
            is ApiResult.Success -> {
                val suffix = if (result.data.invalidatedTokens > 0) " · ${result.data.invalidatedTokens} active QR pass(es) invalidated" else ""
                _uiState.value = _uiState.value.copy(
                    busyUid = null,
                    guardians = _uiState.value.guardians.map { if (it.uid == guardian.uid) it.copy(status = status, verificationReason = reason) else it },
                    message = "${guardian.displayName} marked ${status.replaceFirstChar { it.uppercase() }}$suffix"
                )
            }
            is ApiResult.Failure -> _uiState.value = _uiState.value.copy(busyUid = null, error = result.message)
        }
    }
}
