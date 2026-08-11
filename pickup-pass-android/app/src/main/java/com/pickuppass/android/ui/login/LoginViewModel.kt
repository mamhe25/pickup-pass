package com.pickuppass.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseTooManyRequestsException
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.UserRole
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
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
    data object MasterAdminHome : LoginResult()
    data object UnrecognizedRole : LoginResult()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val api: PickupPassApi,
    sessionExpiryManager: SessionExpiryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(error = sessionExpiryManager.consumePendingReason()?.toLoginMessage())
    )
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
            authRepository.signIn(state.email.trim(), state.password)
                .onSuccess {
                    val session = authRepository.currentSession(forceRefresh = true)
                    if (session == null) {
                        authRepository.signOut()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "We couldn't verify your account. Check your connection and try again."
                        )
                        return@onSuccess
                    }

                    try {
                        val serverSession = api.sessionMe()
                        if (!serverSession.isSuccessful) {
                            authRepository.signOut()
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = if (serverSession.code() == 401) {
                                    "This session is no longer authorized. Sign in again or contact your school administrator."
                                } else {
                                    "PickupPass could not verify your account right now. Please try again."
                                }
                            )
                            return@onSuccess
                        }
                    } catch (_: IOException) {
                        authRepository.signOut()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "PickupPass can't reach the server. Check your connection and try again."
                        )
                        return@onSuccess
                    }

                    notificationRepository.registerCurrentDeviceTokenInBackground()
                    val destination = when (session.role) {
                        UserRole.Parent -> LoginResult.ParentHome
                        UserRole.Teacher -> LoginResult.TeacherHome
                        UserRole.SchoolAdmin -> LoginResult.SchoolAdminHome
                        UserRole.MasterAdmin -> LoginResult.MasterAdminHome
                        else -> null
                    }
                    if (destination == null) {
                        authRepository.signOut()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Your account doesn't have an app role assigned. Contact your school administrator."
                        )
                    } else {
                        _loginResult.value = destination
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.toLoginMessage())
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
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = when (e) {
                            is FirebaseNetworkException -> "No connection. Try again when you're online."
                            is FirebaseTooManyRequestsException -> "Too many requests. Wait a few minutes and try again."
                            else -> "Couldn't send the reset email. Please try again."
                        }
                    )
                }
        }
    }

    fun consumeLoginResult() { _loginResult.value = null }

    private fun Throwable.toLoginMessage(): String = when (this) {
        is FirebaseNetworkException -> "No connection. Check your internet and try again."
        is FirebaseTooManyRequestsException -> "Too many sign-in attempts. Wait a few minutes and try again."
        is FirebaseAuthInvalidUserException -> "This account is disabled or no longer exists. Contact your school administrator."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        else -> if (message == "Sign-in timed out") {
            "Sign-in timed out. Check your connection and try again."
        } else {
            "Sign-in failed. Please try again."
        }
    }

    private fun SessionEndReason.toLoginMessage(): String = when (this) {
        SessionEndReason.EXPIRED_OR_REVOKED -> "Your session expired or was revoked. Please sign in again."
        SessionEndReason.ACCOUNT_DISABLED -> "Your account has been disabled. Contact your school administrator."
        SessionEndReason.UNAUTHORIZED -> "Your account no longer has access to this app."
    }
}
