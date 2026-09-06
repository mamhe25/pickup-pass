package com.pickuppass.android.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSecurityValidationTest {

    @Test
    fun email_rejectsSameAddressIgnoringCase() {
        assertEquals(
            "Enter an email address different from your current one.",
            AccountSecurityValidation.emailError(
                currentEmail = "teacher@example.com",
                newEmail = "TEACHER@example.com",
                currentPassword = "current-password"
            )
        )
    }

    @Test
    fun email_requiresCurrentPassword() {
        assertEquals(
            "Enter your current password to confirm this change.",
            AccountSecurityValidation.emailError(
                currentEmail = "teacher@example.com",
                newEmail = "new@example.com",
                currentPassword = ""
            )
        )
    }

    @Test
    fun email_acceptsValidDifferentAddress() {
        assertNull(
            AccountSecurityValidation.emailError(
                currentEmail = "teacher@example.com",
                newEmail = "new@example.com",
                currentPassword = "current-password"
            )
        )
    }

    @Test
    fun password_requiresMinimumLength() {
        assertEquals(
            "Use at least 8 characters for your new password.",
            AccountSecurityValidation.passwordError(
                currentPassword = "old-password",
                newPassword = "short",
                confirmation = "short"
            )
        )
    }

    @Test
    fun password_rejectsMismatch() {
        assertEquals(
            "The new passwords do not match.",
            AccountSecurityValidation.passwordError(
                currentPassword = "old-password",
                newPassword = "new-password",
                confirmation = "different-password"
            )
        )
    }

    @Test
    fun password_acceptsValidChange() {
        assertNull(
            AccountSecurityValidation.passwordError(
                currentPassword = "old-password",
                newPassword = "new-password",
                confirmation = "new-password"
            )
        )
    }
}
