package com.pickuppass.android.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pickuppass.android.MainActivity
import com.pickuppass.android.R
import com.pickuppass.android.data.repository.NotificationRepository
import com.pickuppass.android.session.DeviceIdentity
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import com.pickuppass.android.telemetry.AppTelemetry
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val PICKUP_NOTIFICATION_CHANNEL_ID = "pickup_notifications"

@AndroidEntryPoint
class PickupPassMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var firebaseAuth: FirebaseAuth
    @Inject lateinit var deviceIdentity: DeviceIdentity
    @Inject lateinit var sessionExpiryManager: SessionExpiryManager
    @Inject lateinit var telemetry: AppTelemetry

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val notificationIdCounter = AtomicInteger(1000)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            notificationRepository.registerCurrentDeviceToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (message.data["type"] == SESSION_REVOKED_PUSH_TYPE) {
            handleSessionRevocation(message.data)
            return
        }

        val title = message.notification?.title ?: "Pickup Pass"
        val body = message.notification?.body ?: "Your child was just picked up."
        showNotification(title, body)
    }

    private fun handleSessionRevocation(
        data: Map<String, String>
    ) {
        if (firebaseAuth.currentUser == null) return

        val currentDeviceId = deviceIdentity.deviceId
        val targetDeviceId = data["targetDeviceId"].orEmpty()
        val excludedDeviceId = data["excludedDeviceId"].orEmpty()

        if (
            targetDeviceId.isNotBlank() &&
            targetDeviceId != currentDeviceId
        ) {
            return
        }

        if (
            excludedDeviceId.isNotBlank() &&
            excludedDeviceId == currentDeviceId
        ) {
            return
        }

        telemetry.clearSignedInUser()
        firebaseAuth.signOut()

        runCatching {
            deviceIdentity.rotateDeviceId()
        }

        sessionExpiryManager.notifySessionEnded(
            SessionEndReason.EXPIRED_OR_REVOKED
        )
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(
                this,
                PICKUP_NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this)
            .notify(
                notificationIdCounter.incrementAndGet(),
                notification
            )
    }

    private companion object {
        const val SESSION_REVOKED_PUSH_TYPE =
            "device_session_revoked"
    }
}
