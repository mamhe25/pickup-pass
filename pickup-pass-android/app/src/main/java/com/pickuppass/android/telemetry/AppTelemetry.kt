package com.pickuppass.android.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small, privacy-conscious wrapper around Crashlytics.
 * Never attach student names, guardian names, QR tokens, emails, or message bodies.
 */
@Singleton
class AppTelemetry @Inject constructor() {
    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    fun setSignedInUser(uid: String, role: String?, schoolId: String?) {
        crashlytics.setUserId(uid)
        role?.takeIf { it.isNotBlank() }?.let { crashlytics.setCustomKey("role", it) }
        schoolId?.takeIf { it.isNotBlank() }?.let { crashlytics.setCustomKey("school_id", it) }
    }

    fun clearSignedInUser() {
        crashlytics.setUserId("")
        crashlytics.setCustomKey("role", "signed_out")
        crashlytics.setCustomKey("school_id", "")
    }

    fun log(message: String) {
        crashlytics.log(message.take(500))
    }

    fun recordNonFatal(throwable: Throwable, area: String) {
        crashlytics.setCustomKey("error_area", area.take(80))
        crashlytics.recordException(throwable)
    }

    fun recordHttpFailure(path: String, statusCode: Int, requestId: String?) {
        crashlytics.setCustomKey("http_path", sanitizePath(path))
        crashlytics.setCustomKey("http_status", statusCode)
        if (!requestId.isNullOrBlank()) {
            crashlytics.setCustomKey("request_id", requestId.take(100))
        }
        crashlytics.recordException(
            IllegalStateException("PickupPass API returned HTTP $statusCode for ${sanitizePath(path)}")
        )
    }

    fun recordNetworkFailure(path: String, throwable: Throwable, requestId: String?) {
        crashlytics.setCustomKey("http_path", sanitizePath(path))
        if (!requestId.isNullOrBlank()) {
            crashlytics.setCustomKey("request_id", requestId.take(100))
        }
        crashlytics.recordException(throwable)
    }

    private fun sanitizePath(path: String): String {
        // Keep only the route path. Do not log query strings, which may contain identifiers.
        return path.substringBefore('?').take(160)
    }
}
