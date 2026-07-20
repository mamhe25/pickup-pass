package com.pickuppass.android.ui.schooladmin.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.ApiResult
import com.pickuppass.android.data.repository.SchoolAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InviteTeacherUiState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val successIsWarning: Boolean = false
)

@HiltViewModel
class InviteTeacherViewModel @Inject constructor(
    private val schoolAdminRepository: SchoolAdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteTeacherUiState())
    val uiState: StateFlow<InviteTeacherUiState> = _uiState

    fun invite(displayName: String, email: String) {
        if (displayName.isBlank() || email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter the teacher's name and email")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null, successIsWarning = false)

            when (val result = schoolAdminRepository.inviteTeacher(email, displayName)) {
                is ApiResult.Success -> {
                    val emailSent = result.data.emailSent
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        successMessage = if (emailSent) {
                            "Invite sent to $email."
                        } else {
                            "Account created for $email, but the invite email couldn't be sent — ask them to use \"Forgot password?\" on the sign-in page with this email."
                        },
                        successIsWarning = !emailSent
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = result.message)
                }
            }
        }
    }
}
