package com.pickuppass.android.data.repository

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Stable retry keys for safety-critical pickup mutations. */
object PickupIdempotencyKeys {
    fun approval(qrToken: String): String = stable("pickup.approve\n$qrToken")

    fun manualOverride(studentId: String, guardianUid: String, reason: String, pickupGateId: String?): String =
        stable("pickup.manual_override\n$studentId\n$guardianUid\n${reason.trim()}\n${pickupGateId.orEmpty()}")

    private fun stable(source: String): String =
        UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8)).toString()
}
