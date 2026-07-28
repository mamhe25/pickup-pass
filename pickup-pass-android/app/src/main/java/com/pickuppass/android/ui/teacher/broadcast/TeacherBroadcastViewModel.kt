package com.pickuppass.android.ui.teacher.broadcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeacherBroadcastUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class TeacherBroadcastViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherBroadcastUiState())
    val uiState: StateFlow<TeacherBroadcastUiState> = _uiState

    fun send(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a title and message")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null)

            when (val result = teacherRepository.broadcastToSection(title, body)) {
                is ApiResult.Success -> {
                    val count = result.data
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = if (count > 0) {
                            "Sent to $count ${if (count == 1) "guardian" else "guardians"}."
                        } else {
                            "Sent — but no guardians were reachable in your section(s) yet."
                        }
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
                }
            }
        }
    }
}
