package com.pickuppass.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val resetEmailSent: Boolean = false
)

sealed class LoginResult {
    data object ParentHome : LoginResult()
    data object TeacherHome : LoginResult()
    data object SchoolAdminHome : LoginResult()
    data object UnrecognizedRole : LoginResult()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _loginResult = MutableStateFlow<LoginResult?>(null)
    val loginResult: StateFlow<LoginResult?> = _loginResult

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Enter your email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val result = authRepository.signIn(state.email.trim(), state.password)
            result.onSuccess {
                val session = authRepository.currentSession(forceRefresh = true)
                notificationRepository.registerCurrentDeviceTokenInBackground()
                val destination = when (session?.role) {
                    UserRole.Parent -> LoginResult.ParentHome
                    UserRole.Teacher -> LoginResult.TeacherHome
                    UserRole.SchoolAdmin -> LoginResult.SchoolAdminHome
                    else -> null
                }

                if (destination == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Signed in, but we couldn't verify your account. Check your connection and try again"
                    )
                } else {
                    _loginResult.value = destination
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (e.message == "Sign-in timed out") {
                        "Sign-in timed out. Check your connection and try again"
                    } else {
                        "Incorrect email or password"
                    }
                )
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter your email above first")
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess { _uiState.value = _uiState.value.copy(resetEmailSent = true, error = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "Couldn't send reset email") }
        }
    }

    fun consumeLoginResult() {
        _loginResult.value = null
    }
}
