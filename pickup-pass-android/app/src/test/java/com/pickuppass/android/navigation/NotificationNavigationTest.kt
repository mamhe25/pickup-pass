package com.pickuppass.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNavigationTest {

    @Test
    fun schoolAdminLaunchApprovalOpensLaunchReadiness() {
        assertEquals(
            Screen.SchoolAdminLaunchReadiness.route,
            notificationRouteFor(
                role = NotificationRoles.SCHOOL_ADMIN,
                type = "launch_approved"
            )
        )
    }

    @Test
    fun schoolAdminLaunchReopenOpensLaunchReadiness() {
        assertEquals(
            Screen.SchoolAdminLaunchReadiness.route,
            notificationRouteFor(
                role = NotificationRoles.SCHOOL_ADMIN,
                type = "launch_reopened"
            )
        )
    }

    @Test
    fun platformLaunchRequestOpensRequestedSchoolReview() {
        assertEquals(
            "master-admin/launch-readiness/school-123",
            notificationRouteFor(
                role = NotificationRoles.MASTER_ADMIN,
                type = "launch_review_requested",
                schoolId = "school-123"
            )
        )
    }

    @Test
    fun parentPickupUpdateOpensStudentHome() {
        assertEquals(
            Screen.ParentStudents.route,
            notificationRouteFor(
                role = NotificationRoles.PARENT,
                type = "pickup_confirmation",
                studentId = "student-123"
            )
        )
    }

    @Test
    fun teacherPickupUpdateOpensDismissalHistory() {
        assertEquals(
            Screen.TeacherExitLogs.route,
            notificationRouteFor(
                role = NotificationRoles.TEACHER,
                type = "pickup_confirmation",
                studentId = "student-123"
            )
        )
    }
}
