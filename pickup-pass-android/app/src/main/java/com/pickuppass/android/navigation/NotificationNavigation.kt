package com.pickuppass.android.navigation

/**
 * Minimal metadata needed to route a notification without trusting display
 * text. Backend/API authorization remains authoritative for every destination.
 */
data class NotificationNavigationRequest(
    val type: String,
    val recipientRole: String = "",
    val schoolId: String? = null,
    val studentId: String? = null
)

object NotificationRoles {
    const val PARENT = "parent"
    const val TEACHER = "teacher"
    const val SCHOOL_ADMIN = "school_admin"
    const val MASTER_ADMIN = "master_admin"
}

fun notificationRouteFor(
    role: String,
    type: String,
    schoolId: String? = null,
    studentId: String? = null
): String? {
    val normalizedRole = role.trim().lowercase()
    val normalizedType = type.trim().lowercase()

    return when (normalizedRole) {
        NotificationRoles.SCHOOL_ADMIN ->
            schoolAdminNotificationRoute(normalizedType)

        NotificationRoles.MASTER_ADMIN ->
            masterAdminNotificationRoute(
                normalizedType,
                schoolId
            )

        NotificationRoles.TEACHER ->
            teacherNotificationRoute(normalizedType)

        NotificationRoles.PARENT ->
            parentNotificationRoute(
                normalizedType,
                studentId
            )

        else ->
            inferNotificationRoute(
                normalizedType,
                schoolId,
                studentId
            )
    }
}

private fun schoolAdminNotificationRoute(
    type: String
): String =
    when {
        type == "launch_approved" ||
            type == "launch_reopened" ||
            "launch" in type ->
            Screen.SchoolAdminLaunchReadiness.route

        "billing" in type ||
            "payment" in type ||
            "invoice" in type ->
            Screen.SchoolAdminBilling.route

        "guardian" in type ||
            "verification" in type ->
            Screen.SchoolAdminGuardianVerification.route

        "pickup" in type ||
            "release" in type ||
            "dismiss" in type ->
            Screen.SchoolAdminDismissalDashboard.route

        else ->
            Screen.SchoolAdminNotifications.route
    }

private fun masterAdminNotificationRoute(
    type: String,
    schoolId: String?
): String =
    when {
        (
            type == "launch_review_requested" ||
                "launch_review" in type
        ) &&
            !schoolId.isNullOrBlank() ->
            Screen.MasterAdminLaunchReadiness
                .createRoute(schoolId)

        else ->
            Screen.MasterAdminHome.route
    }

private fun teacherNotificationRoute(
    type: String
): String =
    when {
        "pickup" in type ||
            "release" in type ||
            "dismiss" in type ->
            Screen.TeacherExitLogs.route

        else ->
            Screen.TeacherNotifications.route
    }

private fun parentNotificationRoute(
    type: String,
    studentId: String?
): String =
    when {
        type == "pickup_confirmation" ||
            "release" in type ||
            "pickup" in type ->
            Screen.ParentStudents.route

        (
            "guardian" in type ||
                "verification" in type
        ) &&
            !studentId.isNullOrBlank() ->
            Screen.ParentManageGuardians
                .createRoute(studentId)

        else ->
            Screen.ParentNotifications.route
    }

private fun inferNotificationRoute(
    type: String,
    schoolId: String?,
    studentId: String?
): String? =
    when {
        type == "launch_approved" ||
            type == "launch_reopened" ->
            Screen.SchoolAdminLaunchReadiness.route

        (
            type == "launch_review_requested" ||
                "launch_review" in type
        ) &&
            !schoolId.isNullOrBlank() ->
            Screen.MasterAdminLaunchReadiness
                .createRoute(schoolId)

        type == "pickup_confirmation" ||
            "pickup" in type ||
            "release" in type ->
            Screen.ParentStudents.route

        (
            "guardian" in type ||
                "verification" in type
        ) &&
            !studentId.isNullOrBlank() ->
            Screen.ParentManageGuardians
                .createRoute(studentId)

        else ->
            null
    }
