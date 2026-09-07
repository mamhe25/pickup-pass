package com.pickuppass.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.pickuppass.android.session.DeviceIdentity
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import com.pickuppass.android.telemetry.AppTelemetry
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Converts backend 401 responses into one application-wide signed-out state.
 *
 * A delayed response from an older login must never sign out a newer Firebase
 * session. We therefore compare the token that sent the rejected request with
 * the token that is current when the response arrives.
 *
 * Device-session revocation is different from an account ban: the revoked
 * session remains blocked, but a user who performs a fresh Firebase sign-in is
 * allowed to establish a new device session on the same installation.
 */
@Singleton
class AuthFailureInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val telemetry: AppTelemetry,
    private val sessionExpiryManager: SessionExpiryManager,
    private val deviceIdentity: DeviceIdentity
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401) return response

        val currentUser = firebaseAuth.currentUser ?: return response
        if (!belongsToCurrentFirebaseSession(request, currentUser)) {
            // The 401 belongs to a request from an older login. The user has
            // already authenticated again, so this response must not kill the
            // replacement session.
            return response
        }

        val deviceSessionRevoked = responseIndicatesDeviceRevocation(response)

        telemetry.clearSignedInUser()
        firebaseAuth.signOut()

        if (deviceSessionRevoked) {
            // Persist the replacement identity only after the old Firebase
            // session is gone. A later explicit sign-in will use this ID.
            runCatching { deviceIdentity.rotateDeviceId() }
        }

        sessionExpiryManager.notifySessionEnded(
            SessionEndReason.EXPIRED_OR_REVOKED
        )
        return response
    }

    private fun belongsToCurrentFirebaseSession(
        request: Request,
        currentUser: FirebaseUser
    ): Boolean {
        val requestToken = request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val currentToken = try {
            Tasks.await(
                currentUser.getIdToken(false),
                CURRENT_TOKEN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )?.token
        } catch (_: Exception) {
            null
        }

        return currentToken != null && requestToken == currentToken
    }

    private fun responseIndicatesDeviceRevocation(
        response: Response
    ): Boolean = runCatching {
        response.peekBody(MAX_ERROR_BODY_BYTES)
            .string()
            .contains(DEVICE_SESSION_REVOKED_CODE)
    }.getOrDefault(false)

    private companion object {
        const val CURRENT_TOKEN_TIMEOUT_SECONDS = 5L
        const val MAX_ERROR_BODY_BYTES = 8_192L
        const val DEVICE_SESSION_REVOKED_CODE =
            "DEVICE_SESSION_REVOKED"
    }
}
