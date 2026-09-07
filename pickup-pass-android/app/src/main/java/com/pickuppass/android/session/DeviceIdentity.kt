package com.pickuppass.android.session

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifies one authenticated app session on this installation.
 *
 * The identifier remains stable across normal app restarts so the backend can
 * list and revoke the same signed-in device. When that session is explicitly
 * revoked, [rotateDeviceId] creates the identity that a later fresh sign-in
 * will use. Rotation never happens merely because the app process restarts.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(
        "pickup_pass_device",
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    @Volatile
    private var cachedDeviceId: String? = null

    val deviceId: String
        get() = cachedDeviceId ?: synchronized(lock) {
            cachedDeviceId ?: loadOrCreateDeviceId().also {
                cachedDeviceId = it
            }
        }

    val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            return when {
                model.isBlank() -> "Android device"
                manufacturer.isBlank() ||
                    model.startsWith(manufacturer, ignoreCase = true) ->
                    model
                else -> "$manufacturer $model"
            }.take(120)
        }

    /**
     * Starts a new backend device-session identity after the previous one was
     * revoked. The write is synchronous because callers must not make the next
     * authenticated request with the old revoked identifier.
     */
    fun rotateDeviceId(): String = synchronized(lock) {
        val next = UUID.randomUUID().toString()
        check(
            prefs.edit()
                .putString(DEVICE_ID_KEY, next)
                .commit()
        ) {
            "Could not persist the new device session"
        }
        cachedDeviceId = next
        next
    }

    private fun loadOrCreateDeviceId(): String {
        prefs.getString(DEVICE_ID_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val created = UUID.randomUUID().toString()
        check(
            prefs.edit()
                .putString(DEVICE_ID_KEY, created)
                .commit()
        ) {
            "Could not persist the device session"
        }
        return created
    }

    private companion object {
        const val DEVICE_ID_KEY = "device_id"
    }
}
