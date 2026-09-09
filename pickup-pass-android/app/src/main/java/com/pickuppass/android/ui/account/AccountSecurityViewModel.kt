package com.pickuppass.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AccountSecurityUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val currentEmail: String = "",
    val emailVerified: Boolean? = null,

    val emailBusy: Boolean = false,
    val emailError: String? = null,
    val emailSuccess: String? = null,

    val passwordBusy: Boolean = false,
    val passwordError: String? = null,
    val passwordSuccess: String? = null,

    val mfaRequired: Boolean = false,
    val mfaEnabled: Boolean = false,
    val mfaBusy: Boolean = false,
    val mfaError: String? = null,
    val mfaSuccess: String? = null,
    val totpSetupKey: String? = null,
    val totpSetupReady: Boolean = false,
    val verificationEmailSent: Boolean = false
)

@HiltViewModel
class AccountSecurityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: PickupPassApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSecurityUiState())
    val uiState: StateFlow<AccountSecurityUiState> = _uiState

    init {
        refreshIdentity(showBusy = false)
    }

    fun refreshIdentity(showBusy: Boolean = true) {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshing = showBusy,
                emailError = null,
                mfaError = null
            )

            authRepository.refreshCurrentUser()
                .onSuccess { email ->
                    val session = authRepository.currentSession(
                        forceRefresh = true
                    )
                    runCatching { api.sessionMe() }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        currentEmail = email,
                        emailVerified =
                            authRepository.isCurrentEmailVerified(),
                        mfaRequired = session?.role?.requiresMfa == true,
                        mfaEnabled =
                            authRepository.hasEnrolledTotpFactor(),
                        emailError = null
                    )
                }
                .onFailure { error ->
                    val fallback = authRepository.currentEmail()
                    val session = authRepository.currentSession(
                        forceRefresh = false
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        currentEmail = fallback,
                        emailVerified =
                            authRepository.isCurrentEmailVerified(),
                        mfaRequired = session?.role?.requiresMfa == true,
                        mfaEnabled =
                            authRepository.hasEnrolledTotpFactor(),
                        emailError = if (showBusy || fallback.isBlank()) {
                            error.toAccountMessage(AccountAction.Refresh)
                        } else {
                            null
                        }
                    )
                }
        }
    }

    fun requestEmailChange(
        currentPassword: String,
        newEmail: String
    ) {
        if (_uiState.value.emailBusy) return

        val validationError = AccountSecurityValidation.emailError(
            currentEmail = _uiState.value.currentEmail,
            newEmail = newEmail,
            currentPassword = currentPassword
        )

        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                emailError = validationError,
                emailSuccess = null
            )
            return
        }

        val normalizedEmail = newEmail.trim()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                emailBusy = true,
                emailError = null,
                emailSuccess = null
            )

            authRepository.requestEmailChange(
                currentPassword = currentPassword,
                newEmail = normalizedEmail
            )
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        emailBusy = false,
                        emailSuccess =
                            "Verification email sent to $normalizedEmail. Your current email stays active until you verify the new address."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        emailBusy = false,
                        emailError =
                            error.toAccountMessage(AccountAction.Email)
                    )
                }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String
    ) {
        if (_uiState.value.passwordBusy) return

        val validationError = AccountSecurityValidation.passwordError(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmation = confirmation
        )

        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                passwordError = validationError,
                passwordSuccess = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                passwordBusy = true,
                passwordError = null,
                passwordSuccess = null
            )

            authRepository.changePassword(
                currentPassword = currentPassword,
                newPassword = newPassword
            )
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        passwordBusy = false,
                        passwordSuccess =
                            "Password updated successfully. Use the new password the next time you sign in."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        passwordBusy = false,
                        passwordError =
                            error.toAccountMessage(AccountAction.Password)
                    )
                }
        }
    }

    fun sendMfaVerificationEmail() {
        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null,
                mfaSuccess = null
            )

            authRepository.sendEmailVerification()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        verificationEmailSent = true,
                        mfaSuccess =
                            "Verification email sent. Open the link, then return here and refresh your account."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaMessage()
                    )
                }
        }
    }

    fun beginMfaEnrollment(
        currentPassword: String
    ) {
        val state = _uiState.value
        if (state.mfaBusy || state.mfaEnabled) return

        if (state.emailVerified != true) {
            _uiState.value = state.copy(
                mfaError =
                    "Verify your sign-in email before enabling two-factor authentication.",
                mfaSuccess = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null,
                mfaSuccess = null
            )

            authRepository.beginTotpEnrollment(currentPassword)
                .onSuccess { setup ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        totpSetupKey = setup.sharedSecretKey,
                        totpSetupReady = true,
                        mfaError = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaMessage()
                    )
                }
        }
    }

    fun openAuthenticatorApp() {
        authRepository.openPendingTotpInOtpApp()
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    mfaError = error.toMfaMessage()
                )
            }
    }

    fun finishMfaEnrollment(
        verificationCode: String
    ) {
        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null,
                mfaSuccess = null
            )

            authRepository.finishTotpEnrollment(verificationCode)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaEnabled = true,
                        totpSetupKey = null,
                        totpSetupReady = false,
                        mfaSuccess =
                            "Two-factor authentication is enabled. Your next sign-in will require a code from your authenticator app."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaMessage()
                    )
                }
        }
    }

    fun cancelMfaEnrollment() {
        authRepository.cancelTotpEnrollment()
        _uiState.value = _uiState.value.copy(
            totpSetupKey = null,
            totpSetupReady = false,
            mfaError = null
        )
    }

    fun disableMfa(
        currentPassword: String
    ) {
        val state = _uiState.value
        if (state.mfaBusy || !state.mfaEnabled) return

        if (state.mfaRequired) {
            _uiState.value = state.copy(
                mfaError =
                    "Two-factor authentication is required for platform owners and school administrators.",
                mfaSuccess = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null,
                mfaSuccess = null
            )

            authRepository.disableTotp(currentPassword)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaEnabled = false,
                        totpSetupKey = null,
                        totpSetupReady = false,
                        mfaSuccess =
                            "Two-factor authentication is disabled for this account."
                    )
                }
                .onFailure { error ->
                    // Firebase can invalidate the local token after removing
                    // the final factor. If the factor is gone, report the
                    // operation as successful and let the normal session guard
                    // request a fresh sign-in when needed.
                    if (!authRepository.hasEnrolledTotpFactor()) {
                        _uiState.value = _uiState.value.copy(
                            mfaBusy = false,
                            mfaEnabled = false,
                            mfaSuccess =
                                "Two-factor authentication is disabled. You may be asked to sign in again.",
                            mfaError = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            mfaBusy = false,
                            mfaError = error.toMfaMessage()
                        )
                    }
                }
        }
    }

    fun clearEmailFeedback() {
        _uiState.value = _uiState.value.copy(
            emailError = null,
            emailSuccess = null
        )
    }

    fun clearPasswordFeedback() {
        _uiState.value = _uiState.value.copy(
            passwordError = null,
            passwordSuccess = null
        )
    }

    fun clearMfaFeedback() {
        _uiState.value = _uiState.value.copy(
            mfaError = null,
            mfaSuccess = null
        )
    }

    private enum class AccountAction {
        Refresh,
        Email,
        Password
    }

    private fun Throwable.toMfaMessage(): String =
        when (this) {
            is FirebaseNetworkException ->
                "No connection. Check your internet and try again."
            is FirebaseTooManyRequestsException ->
                "Too many attempts. Wait a few minutes and try again."
            is FirebaseAuthInvalidCredentialsException ->
                "Your current password or authenticator code is incorrect."
            is FirebaseAuthInvalidUserException ->
                "Your sign-in has expired. Sign in again."
            else ->
                message?.takeIf { it.isNotBlank() }
                    ?: "Two-factor authentication could not be updated. Please try again."
        }

    private fun Throwable.toAccountMessage(
        action: AccountAction
    ): String =
        when (this) {
            is FirebaseNetworkException ->
                "No connection. Check your internet and try again."
            is FirebaseTooManyRequestsException ->
                "Too many attempts. Wait a few minutes and try again."
            is FirebaseAuthInvalidCredentialsException ->
                if (action == AccountAction.Refresh) {
                    "Your sign-in is no longer valid. Sign in again."
                } else {
                    "Your current password is incorrect."
                }
            is FirebaseAuthUserCollisionException ->
                "That email address is already in use."
            is FirebaseAuthWeakPasswordException ->
                "Choose a stronger password that meets your account's password requirements."
            is FirebaseAuthInvalidUserException ->
                "This account is disabled or no longer exists. Contact your administrator."
            is FirebaseAuthException ->
                when (errorCode) {
                    "ERROR_INVALID_EMAIL" ->
                        "Enter a valid email address."
                    "ERROR_EMAIL_ALREADY_IN_USE" ->
                        "That email address is already in use."
                    "ERROR_WRONG_PASSWORD",
                    "ERROR_INVALID_CREDENTIAL" ->
                        "Your current password is incorrect."
                    "ERROR_REQUIRES_RECENT_LOGIN" ->
                        "For security, sign in again and retry this change."
                    "ERROR_USER_TOKEN_EXPIRED" ->
                        "Your sign-in has expired. Sign in again."
                    "ERROR_USER_DISABLED" ->
                        "This account is disabled. Contact your administrator."
                    "ERROR_OPERATION_NOT_ALLOWED" ->
                        "This account change is not available. Contact your administrator."
                    "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" ->
                        "Choose a stronger password that meets your account's password requirements."
                    else ->
                        genericAccountError(action)
                }
            else ->
                genericAccountError(action)
        }

    private fun genericAccountError(
        action: AccountAction
    ): String =
        when (action) {
            AccountAction.Refresh ->
                "Couldn't refresh your account details. Please try again."
            AccountAction.Email ->
                "Couldn't start the email change. Please try again."
            AccountAction.Password ->
                "Couldn't update your password. Please try again."
        }
}
