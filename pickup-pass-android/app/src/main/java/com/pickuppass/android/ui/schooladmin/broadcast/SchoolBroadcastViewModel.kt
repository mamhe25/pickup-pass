package com.pickuppass.android.ui.schooladmin.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.BroadcastHistoryItem
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchoolBroadcastUiState(
    val isSubmitting: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val cancellingId: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val includeTeachers: Boolean = true,
    val includeParents: Boolean = true,
    val history: List<BroadcastHistoryItem> = emptyList()
)

@HiltViewModel
class SchoolBroadcastViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchoolBroadcastUiState())
    val uiState: StateFlow<SchoolBroadcastUiState> = _uiState

    init { refreshHistory() }

    fun setIncludeTeachers(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeTeachers = value)
    }

    fun setIncludeParents(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeParents = value)
    }

    fun send(title: String, body: String) {
        val audience = audienceOrError(title, body) ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)
            when (val result = schoolAdminRepository.broadcastToSchool(title.trim(), body.trim(), audience)) {
                is ApiResult.Success -> {
                    val count = result.data
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Sent to $count ${if (count == 1) "person" else "people"}."
                    )
                    refreshHistory()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    fun schedule(title: String, body: String, scheduledAtUtc: String?) {
        val audience = audienceOrError(title, body) ?: return
        if (scheduledAtUtc.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "Choose a date and time")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)
            when (val result = schoolAdminRepository.scheduleBroadcast(title.trim(), body.trim(), audience, scheduledAtUtc)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Announcement scheduled."
                    )
                    refreshHistory()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cancellingId = id, error = null, successMessage = null)
            when (val result = schoolAdminRepository.cancelScheduledBroadcast(id)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(cancellingId = null, successMessage = "Scheduled announcement cancelled.")
                    refreshHistory()
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(cancellingId = null, error = result.message)
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistory = true)
            when (val result = schoolAdminRepository.getBroadcastHistory()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(isLoadingHistory = false, history = result.data)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoadingHistory = false)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    private fun audienceOrError(title: String, body: String): List<String>? {
        val audience = buildList {
            if (_uiState.value.includeTeachers) add("teacher")
            if (_uiState.value.includeParents) add("parent")
        }
        if (title.isBlank() || body.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a title and message")
            return null
        }
        if (title.length > 120) {
            _uiState.value = _uiState.value.copy(error = "Title must be 120 characters or fewer")
            return null
        }
        if (body.length > 2000) {
            _uiState.value = _uiState.value.copy(error = "Message must be 2000 characters or fewer")
            return null
        }
        if (audience.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Select at least one audience")
            return null
        }
        return audience
    }
}
