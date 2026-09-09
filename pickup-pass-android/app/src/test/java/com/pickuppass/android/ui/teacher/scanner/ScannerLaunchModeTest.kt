package com.pickuppass.android.ui.teacher.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerLaunchModeTest {

    @Test
    fun approvedSchoolDoesNotShowPrelaunchNotice() {
        assertFalse(
            shouldShowPrelaunchScannerNotice(
                "approved"
            )
        )
    }

    @Test
    fun reviewRequestedSchoolShowsPrelaunchNotice() {
        assertTrue(
            shouldShowPrelaunchScannerNotice(
                "review_requested"
            )
        )
    }

    @Test
    fun draftSchoolShowsPrelaunchNotice() {
        assertTrue(
            shouldShowPrelaunchScannerNotice(
                "draft"
            )
        )
    }

    @Test
    fun unknownSchoolStateDoesNotClaimPrelaunchMode() {
        assertFalse(
            shouldShowPrelaunchScannerNotice(
                null
            )
        )
    }
}
