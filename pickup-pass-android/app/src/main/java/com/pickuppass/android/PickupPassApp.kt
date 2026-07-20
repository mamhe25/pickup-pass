package com.pickuppass.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.pickuppass.android.notification.PICKUP_NOTIFICATION_CHANNEL_ID
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PickupPassApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            PICKUP_NOTIFICATION_CHANNEL_ID,
            "Pickup Confirmations",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies you when your child is picked up from school"
        }

        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
