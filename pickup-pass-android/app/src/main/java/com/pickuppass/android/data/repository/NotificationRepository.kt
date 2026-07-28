package com.pickuppass.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.pickuppass.android.data.model.DeviceTokenRequest
import com.pickuppass.android.data.model.NotificationItem
import com.pickuppass.android.data.remote.PickupPassApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: PickupPassApi,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
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

    /**
     * The in-app notification inbox — a persisted record the backend
     * writes for every pickup notification (see PushNotificationService),
     * independent of whether the FCM push itself was ever delivered. This
     * is what "My Notifications" reads from, not the transient system
     * push, which has no history once dismissed.
     */
    suspend fun getMyNotifications(uid: String): Result<List<NotificationItem>> = runCatching {
        val snapshot = firestore.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()

        snapshot.documents.map { doc ->
            NotificationItem(
                id = doc.id,
                title = doc.getString("title") ?: "Notification",
                body = doc.getString("body") ?: "",
                type = doc.getString("type") ?: "",
                studentId = doc.getString("studentId"),
                read = doc.getBoolean("read") ?: false,
                createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time,
            )
        }
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        firestore.collection("notifications").document(notificationId)
            .update("read", true)
            .await()
        Unit
    }

    suspend fun markAllAsRead(notificationIds: List<String>): Result<Unit> = runCatching {
        if (notificationIds.isEmpty()) return@runCatching
        val batch = firestore.batch()
        notificationIds.forEach { id ->
            batch.update(firestore.collection("notifications").document(id), "read", true)
        }
        batch.commit().await()
        Unit
    }

    /**
     * Lightweight count-only fetch for the bell icon's badge — deliberately
     * separate from getMyNotifications() so the home screen doesn't need to
     * pull down the full inbox (title/body text for every notification)
     * just to show a number.
     */
    suspend fun getUnreadCount(uid: String): Result<Int> = runCatching {
        val snapshot = firestore.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("read", false)
            .get()
            .await()
        snapshot.size()
    }
}
