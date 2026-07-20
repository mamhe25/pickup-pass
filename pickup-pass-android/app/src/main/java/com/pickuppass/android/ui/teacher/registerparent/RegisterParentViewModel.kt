package com.pickuppass.android.ui.teacher.registerparent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.StudentRepository
import com.pickuppass.android.data.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterParentUiState(
    val studentLabel: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val successIsWarning: Boolean = false
)

@HiltViewModel
class RegisterParentViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterParentUiState())
    val uiState: StateFlow<RegisterParentUiState> = _uiState

    fun loadStudent(studentId: String) {
        viewModelScope.launch {
            studentRepository.getStudent(studentId).onSuccess { student ->
                if (student != null) {
                    _uiState.value = _uiState.value.copy(
                        studentLabel = "${student.fullName} · Grade ${student.grade.ifBlank { "-" }}"
                    )
                }
            }
        }
    }

    fun register(studentId: String, parentName: String, parentEmail: String, relationship: String) {
        if (parentName.isBlank() || parentEmail.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter the parent's name and email")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null, successIsWarning = false)

            when (val result = teacherRepository.registerParent(studentId, parentName, parentEmail, relationship)) {
                is ApiResult.Success -> {
                    val message: String
                    val isWarning: Boolean
                    when {
                        result.data.status == "linked_existing" -> {
                            message = "Linked — $parentEmail already had an account."
                            isWarning = false
                        }
                        result.data.emailSent -> {
                            message = "Invite sent to $parentEmail."
                            isWarning = false
                        }
                        else -> {
                            message = "Account created for $parentEmail, but the invite email couldn't be sent — ask them to use \"Forgot password?\" on the sign-in page with this email."
                            isWarning = true
                        }
                    }
                    _uiState.value = _uiState.value.copy(isSubmitting = false, successMessage = message, successIsWarning = isWarning)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
                }
            }
        }
    }
}
