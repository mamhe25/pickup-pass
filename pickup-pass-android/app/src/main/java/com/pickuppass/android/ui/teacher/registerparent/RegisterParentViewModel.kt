package com.pickuppass.android.ui.teacher.registerparent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.model.primaryGuardianUidCompat
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
    val successIsWarning: Boolean = false,
    val hasPrimaryGuardian: Boolean = false
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
                        studentLabel = "${student.fullName} · Grade ${student.grade.ifBlank { "-" }}",
                        hasPrimaryGuardian = student.primaryGuardianUidCompat() != null,
                        error = null
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.value =
            _uiState.value.copy(
                error = null,
                successMessage = null,
                successIsWarning = false
            )
    }

    fun register(
        studentId: String,
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        parentEmail: String,
        relationship: String
    ) {
        if (_uiState.value.isSubmitting) return
        if (lastName.isBlank() || firstName.isBlank() || parentEmail.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter the parent's last name, first name, and email")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null, successIsWarning = false)

            when (val result = teacherRepository.registerParent(studentId, lastName, firstName, middleInitial, suffix, parentEmail, relationship)) {
                is ApiResult.Success -> {
                    val message: String
                    val isWarning: Boolean
                    when {
                        result.data.status == "linked_existing" -> {
                            message = "Primary guardian linked — $parentEmail already had an account. QR pickup remains locked until the guardian has an accepted verification photo in My Profile."
                            isWarning = false
                        }
                        result.data.emailSent -> {
                            message = "Primary guardian registered. Invite sent to $parentEmail. The guardian must complete account setup and upload an accepted verification photo before QR pickup passes can be generated."
                            isWarning = false
                        }
                        else -> {
                            message = "Primary guardian registered for $parentEmail, but the invite email couldn't be sent — ask them to use \"Forgot password?\" on the sign-in page. After signing in, they must upload an accepted verification photo before QR pickup passes can be generated."
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
