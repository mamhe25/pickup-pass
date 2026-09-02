package com.pickuppass.android.data.repository

import com.google.firebase.auth.FirebaseAuth
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
    val role: UserRole
)

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val telemetry: AppTelemetry
) {
    private companion object {
        const val AUTH_OPERATION_TIMEOUT_MS = 20_000L
        const val TOKEN_REFRESH_TIMEOUT_MS = 15_000L
    }

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

    fun signOut() {
        telemetry.clearSignedInUser()
        firebaseAuth.signOut()
    }

    /**
     * Forces a refreshed ID token when requested so backend custom-claim
     * changes can become visible immediately.
     *
     * Any connectivity/token-refresh failure returns null rather than crashing
     * startup. SplashViewModel can distinguish that from a truly signed-out
     * local Firebase session via isSignedIn.
     */
    suspend fun currentSession(forceRefresh: Boolean = false): SessionInfo? {
        val user = firebaseAuth.currentUser ?: return null

        return try {
            val result = withTimeoutOrNull(TOKEN_REFRESH_TIMEOUT_MS) {
                user.getIdToken(forceRefresh).await()
            } ?: return null

            // GetTokenResult is non-null after the timeout/null guard above.
            val claims = result.claims
            val roleClaim = claims["role"] as? String
            val schoolId = claims["schoolId"] as? String

            telemetry.setSignedInUser(
                user.uid,
                roleClaim,
                schoolId
            )

            SessionInfo(
                uid = user.uid,
                schoolId = schoolId,
                role = UserRole.from(roleClaim)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun currentUid(): String? = firebaseAuth.currentUser?.uid
}
