package com.pickuppass.android

import com.pickuppass.android.data.repository.PickupIdempotencyKeys
import com.pickuppass.android.navigation.Screen
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.session.SessionExpiryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalJourneySmokeTest {

    @Test
    fun parentAndTeacherCriticalRoutesRemainStable() {
        assertEquals("parent/students", Screen.ParentStudents.route)
        assertEquals("parent/pass/student-42", Screen.ParentPickupPass.createRoute("student-42"))
        assertEquals("teacher/scanner", Screen.TeacherScanner.route)
        assertEquals("teacher/exit-logs", Screen.TeacherExitLogs.route)
    }

    @Test
    fun approvalRetryUsesSameKeyForSameSignedQr() {
        val first = PickupIdempotencyKeys.approval("signed-qr-token")
        val retry = PickupIdempotencyKeys.approval("signed-qr-token")

        assertEquals(first, retry)
        assertTrue(first.length <= 128)
    }

    @Test
    fun differentPickupMutationProducesDifferentKey() {
        assertNotEquals(
            PickupIdempotencyKeys.approval("signed-qr-token-a"),
            PickupIdempotencyKeys.approval("signed-qr-token-b")
        )
        assertNotEquals(
            PickupIdempotencyKeys.manualOverride("student-1", "guardian-1", "Phone unavailable", "gate-1"),
            PickupIdempotencyKeys.manualOverride("student-1", "guardian-1", "Phone unavailable", "gate-2")
        )
    }

    @Test
    fun sessionExpiryReasonIsConsumedExactlyOnce() {
        val manager = SessionExpiryManager()
        manager.notifySessionEnded(SessionEndReason.ACCOUNT_DISABLED)

        assertEquals(SessionEndReason.ACCOUNT_DISABLED, manager.consumePendingReason())
        assertNull(manager.consumePendingReason())
    }
}
