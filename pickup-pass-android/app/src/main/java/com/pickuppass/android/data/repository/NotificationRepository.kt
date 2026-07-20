package com.pickuppass.android.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.pickuppass.android.data.model.DeviceTokenRequest
import com.pickuppass.android.data.remote.PickupPassApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: PickupPassApi,
    private val authRepository: AuthRepository
) {
    /**
     * Fetches the current FCM token and registers it with the backend for
     * the signed-in user. Safe to call repeatedly (e.g. on every app start)
     * — the backend stores tokens in a Set-like array via arrayUnion, so
     * re-registering the same token is a no-op server-side.
     */
    suspend fun registerCurrentDeviceToken(): Result<Unit> = runCatching {
        if (!authRepository.isSignedIn) return@runCatching
        val token = FirebaseMessaging.getInstance().token.await()
        val response = api.registerDeviceToken(DeviceTokenRequest(token))
        if (!response.isSuccessful) {
            error("Token registration failed: ${response.code()}")
        }
    }

    /** Called on sign-out so a shared/borrowed device stops receiving another user's notifications. */
    suspend fun unregisterCurrentDeviceToken(): Result<Unit> = runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        api.unregisterDeviceToken(DeviceTokenRequest(token))
        Unit
    }
}
