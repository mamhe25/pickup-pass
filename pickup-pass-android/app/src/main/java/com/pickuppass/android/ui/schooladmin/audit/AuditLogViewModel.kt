package com.pickuppass.android.ui.schooladmin.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.AuditEvent
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(val isLoading: Boolean = true, val events: List<AuditEvent> = emptyList(), val error: String? = null)

@HiltViewModel
class AuditLogViewModel @Inject constructor(private val repository: SchoolAdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState
    init { load() }
    fun load() = viewModelScope.launch {
        _uiState.value = AuditLogUiState(isLoading = true)
        when (val result = repository.listAuditEvents(100)) {
            is ApiResult.Success -> _uiState.value = AuditLogUiState(isLoading = false, events = result.data)
            is ApiResult.Failure -> _uiState.value = AuditLogUiState(isLoading = false, error = result.message)
        }
    }
}
