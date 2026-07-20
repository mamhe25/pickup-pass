package com.pickuppass.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
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

data class SessionInfo(val uid: String, val schoolId: String?, val role: UserRole)

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    val isSignedIn: Boolean get() = firebaseAuth.currentUser != null

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Unit
    }

    fun signOut() = firebaseAuth.signOut()

    /**
     * Forces a refreshed ID token (forceRefresh = true) so custom claims
     * set by the backend moments earlier (e.g. right after account
     * creation) are picked up immediately rather than waiting up to an
     * hour for the cached token to expire naturally.
     *
     * Returns null on ANY failure, including network errors — this used to
     * let getIdToken()'s exception propagate unguarded, which crashed the
     * app on launch with no connectivity (this runs from SplashViewModel's
     * init block, so it fires on every single app open before the user
     * does anything, producing an open-close-crash loop offline). Callers
     * treat a null session as "not signed in," which isn't quite accurate
     * for a network failure specifically — see SplashViewModel, which
     * distinguishes "genuinely no session" from "isSignedIn locally but
     * couldn't verify due to no connectivity" so a connectivity blip
     * doesn't look like being signed out.
     */
    suspend fun currentSession(forceRefresh: Boolean = false): SessionInfo? {
        val user = firebaseAuth.currentUser ?: return null
        return try {
            val result = user.getIdToken(forceRefresh).await()
            val claims = result?.claims ?: emptyMap()
            val role = UserRole.from(claims["role"] as? String)
            val schoolId = claims["schoolId"] as? String
            SessionInfo(uid = user.uid, schoolId = schoolId, role = role)
        } catch (e: Exception) {
            null
        }
    }

    fun currentUid(): String? = firebaseAuth.currentUser?.uid
}
