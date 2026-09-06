package com.pickuppass.android.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpSecret
import com.pickuppass.android.telemetry.AppTelemetry
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed class UserRole {
    data object Parent : UserRole()
    data object Teacher : UserRole()
    data object SchoolAdmin : UserRole()
    data object MasterAdmin : UserRole()
    data object Unknown : UserRole()

    val requiresMfa: Boolean
        get() = this is SchoolAdmin || this is MasterAdmin

    companion object {
        fun from(claim: String?): UserRole = when (claim) {
            "parent" -> Parent
            "teacher" -> Teacher
            "school_admin" -> SchoolAdmin
            "master_admin" -> MasterAdmin
            else -> Unknown
        }
    }
}

data class SessionInfo(
    val uid: String,
    val schoolId: String?,
    val role: UserRole,
    val mfaSatisfied: Boolean = false
)

data class TotpEnrollmentInfo(
    val sharedSecretKey: String,
    val qrCodeUrl: String
)

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val telemetry: AppTelemetry
) {
    private companion object {
        const val AUTH_OPERATION_TIMEOUT_MS = 20_000L
        const val TOKEN_REFRESH_TIMEOUT_MS = 15_000L
        const val TOTP_DISPLAY_NAME = "PickupPass Authenticator"
        const val TOTP_ISSUER = "PickupPass"
    }

    @Volatile
    private var pendingTotpSecret: TotpSecret? = null

    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        checkNotNull(
            withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
            }
        ) { "Sign-in timed out" }
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        checkNotNull(
            withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
                firebaseAuth.sendPasswordResetEmail(email).await()
            }
        ) { "Password-reset request timed out" }
        Unit
    }

    fun currentEmail(): String = firebaseAuth.currentUser?.email.orEmpty()

    fun isCurrentEmailVerified(): Boolean =
        firebaseAuth.currentUser?.isEmailVerified == true

    fun hasEnrolledTotpFactor(): Boolean {
        return firebaseAuth.currentUser
            ?.multiFactor
            ?.enrolledFactors
            ?.any {
                it.factorId ==
                    TotpMultiFactorGenerator.FACTOR_ID
            } == true
    }

    fun enrolledTotpFactorId(): String? =
        firebaseAuth.currentUser
            ?.multiFactor
            ?.enrolledFactors
            ?.firstOrNull { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }
            ?.uid

    suspend fun refreshCurrentUser(): Result<String> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }

        val completed = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.reload().await()
            true
        } ?: false

        check(completed) { "Account refresh timed out" }

        firebaseAuth.currentUser?.email.orEmpty()
    }

    suspend fun refreshEmailVerification(): Result<Boolean> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }

        val completed = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.reload().await()
            true
        } ?: false

        check(completed) { "Account refresh timed out" }
        firebaseAuth.currentUser?.isEmailVerified == true
    }

    suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }

        if (user.isEmailVerified) {
            return@runCatching Unit
        }

        val completed = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.sendEmailVerification().await()
            true
        } ?: false

        check(completed) { "Verification email request timed out" }
    }

    suspend fun requestEmailChange(
        currentPassword: String,
        newEmail: String
    ): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val currentEmail = requireNotNull(user.email) {
            "This account does not have an email sign-in address"
        }

        reauthenticateWithPassword(user, currentEmail, currentPassword)

        val requested = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.verifyBeforeUpdateEmail(newEmail.trim()).await()
            true
        } ?: false

        check(requested) { "Email-change request timed out" }
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val currentEmail = requireNotNull(user.email) {
            "This account does not have an email sign-in address"
        }

        reauthenticateWithPassword(user, currentEmail, currentPassword)

        val updated = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.updatePassword(newPassword).await()
            true
        } ?: false

        check(updated) { "Password update timed out" }
    }

    /**
     * Starts TOTP enrollment after re-authenticating with the user's password.
     *
     * The generated secret is kept only in memory for the short enrollment
     * flow. It is never persisted, logged, sent to PickupPass' backend, or
     * stored in Firestore.
     */
    suspend fun beginTotpEnrollment(
        currentPassword: String
    ): Result<TotpEnrollmentInfo> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val email = requireNotNull(user.email) {
            "This account does not have an email sign-in address"
        }

        check(user.isEmailVerified) {
            "Verify your sign-in email before enabling two-factor authentication."
        }
        check(!hasEnrolledTotpFactor()) {
            "Two-factor authentication is already enabled."
        }

        reauthenticateWithPassword(user, email, currentPassword)

        val multiFactorSession = checkNotNull(
            withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
                user.multiFactor.session.await()
            }
        ) { "Two-factor setup timed out" }

        val secret = checkNotNull(
            withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
                TotpMultiFactorGenerator.generateSecret(multiFactorSession).await()
            }
        ) { "Two-factor setup timed out" }

        pendingTotpSecret = secret

        TotpEnrollmentInfo(
            sharedSecretKey = secret.sharedSecretKey,
            qrCodeUrl = secret.generateQrCodeUrl(
                email,
                TOTP_ISSUER
            )
        )
    }

    fun openPendingTotpInOtpApp(): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val secret = requireNotNull(pendingTotpSecret) {
            "Start two-factor setup first."
        }
        val email = user.email ?: "PickupPass account"
        val qrCodeUrl = secret.generateQrCodeUrl(email, TOTP_ISSUER)
        secret.openInOtpApp(qrCodeUrl)
    }

    suspend fun finishTotpEnrollment(
        verificationCode: String
    ): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val secret = requireNotNull(pendingTotpSecret) {
            "Start two-factor setup first."
        }
        val code = verificationCode.trim()

        require(code.matches(Regex("\\d{6}"))) {
            "Enter the 6-digit code from your authenticator app."
        }

        val assertion = TotpMultiFactorGenerator.getAssertionForEnrollment(
            secret,
            code
        )

        val completed = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.multiFactor.enroll(assertion, TOTP_DISPLAY_NAME).await()
            true
        } ?: false

        check(completed) { "Two-factor enrollment timed out" }
        pendingTotpSecret = null

        // Refresh the local user/token state immediately after enrollment.
        withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
            firebaseAuth.currentUser?.getIdToken(true)?.await()
        }
        Unit
    }

    suspend fun disableTotp(
        currentPassword: String
    ): Result<Unit> = runCatching {
        val user = requireNotNull(firebaseAuth.currentUser) {
            "Session expired"
        }
        val email = requireNotNull(user.email) {
            "This account does not have an email sign-in address"
        }
        val factorId = requireNotNull(enrolledTotpFactorId()) {
            "Two-factor authentication is not enabled."
        }

        reauthenticateWithPassword(user, email, currentPassword)

        val completed = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.multiFactor.unenroll(factorId).await()
            true
        } ?: false

        check(completed) { "Two-factor update timed out" }
        pendingTotpSecret = null
    }

    fun cancelTotpEnrollment() {
        pendingTotpSecret = null
    }

    fun signOut() {
        pendingTotpSecret = null
        telemetry.clearSignedInUser()
        firebaseAuth.signOut()
    }

    /**
     * Forces a refreshed ID token when requested so backend custom-claim
     * changes can become visible immediately.
     *
     * mfaSatisfied is read from Firebase's reserved token metadata rather than
     * any client-controlled field. The backend independently performs the same
     * check for mandatory admin roles.
     */
    suspend fun currentSession(forceRefresh: Boolean = false): SessionInfo? {
        val user = firebaseAuth.currentUser ?: return null

        return try {
            val result = withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
                user.getIdToken(forceRefresh).await()
            } ?: return null

            val claims = result.claims
            val roleClaim = claims["role"] as? String
            val schoolId = claims["schoolId"] as? String
            val firebaseClaim = claims["firebase"] as? Map<*, *>
            val secondFactor =
                firebaseClaim?.get("sign_in_second_factor")?.toString()

            telemetry.setSignedInUser(
                user.uid,
                roleClaim,
                schoolId
            )

            SessionInfo(
                uid = user.uid,
                schoolId = schoolId,
                role = UserRole.from(roleClaim),
                mfaSatisfied = !secondFactor.isNullOrBlank()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun currentUid(): String? = firebaseAuth.currentUser?.uid

    private suspend fun reauthenticateWithPassword(
        user: com.google.firebase.auth.FirebaseUser,
        email: String,
        password: String
    ) {
        require(password.isNotBlank()) {
            "Enter your current password."
        }

        val credential = EmailAuthProvider.getCredential(
            email,
            password
        )

        val reauthenticated = withTimeoutOrNull(AUTH_OPERATION_TIMEOUT_MS) {
            user.reauthenticate(credential).await()
            true
        } ?: false

        check(reauthenticated) { "Reauthentication timed out" }
    }
}
