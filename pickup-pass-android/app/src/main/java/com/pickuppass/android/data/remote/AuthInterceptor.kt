package com.pickuppass.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Attaches "Authorization: Bearer <Firebase ID token>" to every request.
 * getIdToken() only hits the network if the cached token is within ~5 min
 * of expiry, so this doesn't add a network round trip on most calls.
 *
 * Runs on OkHttp's dispatcher thread, so blocking with Tasks.await() here
 * (rather than making the whole interceptor chain suspend) is the standard,
 * safe pattern for OkHttp interceptors.
 */
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val user = firebaseAuth.currentUser
        val requestBuilder = chain.request().newBuilder()

        if (user != null) {
            val token = try {
                Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS)?.token
            } catch (e: Exception) {
                null
            }
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
