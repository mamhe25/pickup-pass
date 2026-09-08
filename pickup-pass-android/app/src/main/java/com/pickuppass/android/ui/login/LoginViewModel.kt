package com.pickuppass.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.repository.AuthRepository
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.data.repository.SessionInfo
import com.pickuppass.android.data.repository.UserRole
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionEndReason: SessionEndReason? = null,
    val resetEmailSent: Boolean = false,

    // Existing MFA sign-in challenge.
    val mfaChallengeRequired: Boolean = false,
    val mfaCode: String = "",
    val mfaBusy: Boolean = false,
    val mfaError: String? = null,

    // Mandatory first-time enrollment for protected roles.
    val requiredMfaEnrollment: Boolean = false,
    val emailVerified: Boolean = false,
    val verificationEmailSent: Boolean = false,
    val totpSetupKey: String? = null,
    val totpSetupReady: Boolean = false,
    val setupCompleteMessage: String? = null
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
        LoginUiState(
            sessionEndReason =
                sessionExpiryManager.consumePendingReason()
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _loginResult = MutableStateFlow<LoginResult?>(null)
    val loginResult: StateFlow<LoginResult?> = _loginResult

    private var pendingMfaResolver: MultiFactorResolver? = null

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(
            email = value,
            error = null,
            setupCompleteMessage = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            error = null,
            setupCompleteMessage = null
        )
    }

    fun onMfaCodeChange(value: String) {
        val digits = value.filter(Char::isDigit).take(6)
        _uiState.value = _uiState.value.copy(
            mfaCode = digits,
            mfaError = null
        )
    }

    fun signIn() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(
                error = "Enter your email and password"
            )
            return
        }

        if (state.isLoading || state.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true,
                error = null,
                sessionEndReason = null,
                mfaError = null,
                setupCompleteMessage = null
            )

            authRepository.signIn(
                state.email.trim(),
                state.password
            )
                .onSuccess {
                    completeAuthenticatedSession()
                }
                .onFailure { error ->
                    if (error is FirebaseAuthMultiFactorException) {
                        beginExistingMfaChallenge(error)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.toLoginMessage()
                        )
                    }
                }
        }
    }

    private fun beginExistingMfaChallenge(
        exception: FirebaseAuthMultiFactorException
    ) {
        val resolver = exception.resolver
        val totpHint = resolver.hints.firstOrNull {
            it.factorId == TotpMultiFactorGenerator.FACTOR_ID
        }

        if (totpHint == null) {
            pendingMfaResolver = null
            authRepository.signOut()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "This account uses an unsupported two-factor method. Contact your administrator."
            )
            return
        }

        pendingMfaResolver = resolver
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            mfaChallengeRequired = true,
            mfaCode = "",
            mfaBusy = false,
            mfaError = null
        )
    }

    fun verifyMfaChallenge() {
        val resolver = pendingMfaResolver ?: return
        val code = _uiState.value.mfaCode.trim()

        if (!code.matches(Regex("\\d{6}"))) {
            _uiState.value = _uiState.value.copy(
                mfaError = "Enter the 6-digit code from your authenticator app."
            )
            return
        }

        val hint = resolver.hints.firstOrNull {
            it.factorId == TotpMultiFactorGenerator.FACTOR_ID
        } ?: run {
            cancelMfaChallenge()
            return
        }

        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null
            )

            try {
                val assertion =
                    TotpMultiFactorGenerator.getAssertionForSignIn(
                        hint.uid,
                        code
                    )
                resolver.resolveSignIn(assertion).await()
                pendingMfaResolver = null
                _uiState.value = _uiState.value.copy(
                    mfaChallengeRequired = false,
                    mfaCode = "",
                    mfaBusy = false,
                    mfaError = null,
                    isLoading = true
                )
                completeAuthenticatedSession()
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    mfaBusy = false,
                    mfaError = when (error) {
                        is FirebaseTooManyRequestsException ->
                            "Too many verification attempts. Wait a few minutes and try again."
                        is FirebaseNetworkException ->
                            "No connection. Check your internet and try again."
                        else ->
                            "That code is invalid or expired. Enter the current code from your authenticator app."
                    }
                )
            }
        }
    }

    fun cancelMfaChallenge() {
        pendingMfaResolver = null
        authRepository.signOut()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            mfaChallengeRequired = false,
            mfaCode = "",
            mfaBusy = false,
            mfaError = null,
            password = ""
        )
    }

    /**
     * Called after either password-only sign-in or a successful MFA challenge.
     *
     * Platform owners and school admins are never allowed to proceed to the
     * backend unless Firebase's ID token proves a second factor was used.
     * First-time protected-role users are held on the sign-in surface until
     * they enroll TOTP.
     */
    private suspend fun completeAuthenticatedSession() {
        val session = authRepository.currentSession(forceRefresh = true)
        if (session == null) {
            authRepository.signOut()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "We couldn't verify your account. Check your connection and try again."
            )
            return
        }

        if (session.role.requiresMfa && !session.mfaSatisfied) {
            if (authRepository.hasEnrolledTotpFactor()) {
                // A protected role with an enrolled factor but no second-factor
                // claim must complete a fresh MFA sign-in.
                authRepository.signOut()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    password = "",
                    error = "Two-factor verification is required. Sign in again and enter your authenticator code."
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                requiredMfaEnrollment = true,
                emailVerified = authRepository.isCurrentEmailVerified(),
                verificationEmailSent = false,
                totpSetupKey = null,
                totpSetupReady = false,
                mfaCode = "",
                mfaError = null
            )
            return
        }

        finishAuthorizedLogin(session)
    }

    private suspend fun finishAuthorizedLogin(
        session: SessionInfo
    ) {
        try {
            val serverSession = api.sessionMe()
            if (!serverSession.isSuccessful) {
                authRepository.signOut()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = when (serverSession.code()) {
                        401 ->
                            "This session is no longer authorized. Sign in again or contact your school administrator."
                        428 ->
                            "Two-factor authentication is required before this account can continue."
                        else ->
                            "PickupPass could not verify your account right now. Please try again."
                    }
                )
                return
            }
        } catch (_: IOException) {
            authRepository.signOut()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "PickupPass can't reach the server. Check your connection and try again."
            )
            return
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
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                password = "",
                mfaCode = ""
            )
        }
    }

    fun sendRequiredVerificationEmail() {
        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null
            )

            authRepository.sendEmailVerification()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        verificationEmailSent = true,
                        mfaError = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaSetupMessage()
                    )
                }
        }
    }

    fun refreshRequiredEmailVerification() {
        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null
            )

            authRepository.refreshEmailVerification()
                .onSuccess { verified ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        emailVerified = verified,
                        mfaError = if (verified) {
                            null
                        } else {
                            "Your email is not verified yet. Open the verification link, then try again."
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaSetupMessage()
                    )
                }
        }
    }

    fun beginRequiredMfaEnrollment() {
        val state = _uiState.value
        if (state.mfaBusy) return
        if (!state.emailVerified) {
            _uiState.value = state.copy(
                mfaError = "Verify your email before setting up two-factor authentication."
            )
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(
                mfaError = "Enter your password again to continue two-factor setup."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null
            )

            authRepository.beginTotpEnrollment(state.password)
                .onSuccess { setup ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        totpSetupKey = setup.sharedSecretKey,
                        totpSetupReady = true,
                        password = "",
                        mfaError = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaSetupMessage()
                    )
                }
        }
    }

    fun openAuthenticatorApp() {
        authRepository.openPendingTotpInOtpApp()
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    mfaError = error.toMfaSetupMessage()
                )
            }
    }

    fun finishRequiredMfaEnrollment() {
        val code = _uiState.value.mfaCode
        if (_uiState.value.mfaBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mfaBusy = true,
                mfaError = null
            )

            authRepository.finishTotpEnrollment(code)
                .onSuccess {
                    // A mandatory role must perform a new sign-in so the new ID
                    // token proves the second factor was actually presented.
                    authRepository.signOut()
                    _uiState.value = LoginUiState(
                        email = _uiState.value.email,
                        setupCompleteMessage =
                            "Two-factor authentication is enabled. Sign in again, then enter the code from your authenticator app."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        mfaBusy = false,
                        mfaError = error.toMfaSetupMessage()
                    )
                }
        }
    }

    fun cancelRequiredMfaEnrollment() {
        authRepository.cancelTotpEnrollment()
        authRepository.signOut()
        _uiState.value = LoginUiState(
            email = _uiState.value.email
        )
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Enter your email above first"
            )
            return
        }

        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        resetEmailSent = true,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = when (error) {
                            is FirebaseNetworkException ->
                                "No connection. Try again when you're online."
                            is FirebaseTooManyRequestsException ->
                                "Too many requests. Wait a few minutes and try again."
                            else ->
                                "Couldn't send the reset email. Please try again."
                        }
                    )
                }
        }
    }

    fun consumeLoginResult() {
        _loginResult.value = null
    }

    private fun Throwable.toLoginMessage(): String = when (this) {
        is FirebaseNetworkException ->
            "No connection. Check your internet and try again."
        is FirebaseTooManyRequestsException ->
            "Too many sign-in attempts. Wait a few minutes and try again."
        is FirebaseAuthInvalidUserException ->
            "This account is disabled or no longer exists. Contact your school administrator."
        is FirebaseAuthInvalidCredentialsException ->
            "Incorrect email or password."
        else ->
            if (message == "Sign-in timed out") {
                "Sign-in timed out. Check your connection and try again."
            } else {
                "Sign-in failed. Please try again."
            }
    }

    private fun Throwable.toMfaSetupMessage(): String = when (this) {
        is FirebaseNetworkException ->
            "No connection. Check your internet and try again."
        is FirebaseTooManyRequestsException ->
            "Too many attempts. Wait a few minutes and try again."
        is FirebaseAuthInvalidCredentialsException ->
            "Your password or authenticator code is incorrect."
        else ->
            message?.takeIf { it.isNotBlank() }
                ?: "Two-factor authentication could not be updated. Please try again."
    }

    }
