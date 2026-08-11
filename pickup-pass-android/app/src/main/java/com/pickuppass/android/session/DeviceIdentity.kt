package com.pickuppass.android.session

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentity @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pickup_pass_device", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            return when {
                model.isBlank() -> "Android device"
                manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
                else -> "$manufacturer $model"
            }.take(120)
        }
}
