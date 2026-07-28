package com.pickuppass.android.ui.schooladmin.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchoolBroadcastUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val includeTeachers: Boolean = true,
    val includeParents: Boolean = true,
)

@HiltViewModel
class SchoolBroadcastViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchoolBroadcastUiState())
    val uiState: StateFlow<SchoolBroadcastUiState> = _uiState

    fun setIncludeTeachers(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeTeachers = value)
    }

    fun setIncludeParents(value: Boolean) {
        _uiState.value = _uiState.value.copy(includeParents = value)
    }

    fun send(title: String, body: String) {
        val audience = buildList {
            if (_uiState.value.includeTeachers) add("teacher")
            if (_uiState.value.includeParents) add("parent")
        }
        if (title.isBlank() || body.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a title and message")
            return
        }
        if (audience.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Select at least one audience")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)

            when (val result = schoolAdminRepository.broadcastToSchool(title, body, audience)) {
                is ApiResult.Success -> {
                    val count = result.data
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = "Sent to $count ${if (count == 1) "person" else "people"}."
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
                }
            }
        }
    }
}
