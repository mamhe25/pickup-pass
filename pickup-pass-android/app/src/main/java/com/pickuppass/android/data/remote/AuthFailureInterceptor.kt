package com.pickuppass.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import com.pickuppass.android.telemetry.AppTelemetry
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts backend 401 responses into one application-wide signed-out state.
 * Firebase client SDK can keep a locally cached user even after the backend
 * has revoked refresh tokens. The backend verifies revocation on each call;
 * this interceptor makes the Android UI react immediately when that happens.
 */
@Singleton
class AuthFailureInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val telemetry: AppTelemetry,
    private val sessionExpiryManager: SessionExpiryManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401 && firebaseAuth.currentUser != null) {
            telemetry.clearSignedInUser()
            firebaseAuth.signOut()
            sessionExpiryManager.notifySessionEnded(SessionEndReason.EXPIRED_OR_REVOKED)
        }
        return response
    }
}
