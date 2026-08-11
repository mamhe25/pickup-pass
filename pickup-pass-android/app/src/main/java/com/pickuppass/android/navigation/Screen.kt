package com.pickuppass.android.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")

    data object ParentStudents : Screen("parent/students")
    data object ParentProfile : Screen("parent/profile")
    data object ParentNotifications : Screen("parent/notifications")

    data object ParentPickupPass : Screen("parent/pass/{studentId}") {
        fun createRoute(studentId: String) = "parent/pass/$studentId"
    }

    data object ParentManageGuardians : Screen("parent/guardians/{studentId}") {
        fun createRoute(studentId: String) = "parent/guardians/$studentId"
    }

    data object TeacherScanner : Screen("teacher/scanner")

    data object TeacherStudents : Screen("teacher/students")

    data object TeacherRegisterParent : Screen("teacher/register-parent/{studentId}") {
        fun createRoute(studentId: String) = "teacher/register-parent/$studentId"
    }

    data object TeacherExitLogs : Screen("teacher/exit-logs")

    data object TeacherNotifications : Screen("teacher/notifications")

    data object TeacherBroadcast : Screen("teacher/broadcast")

    data object SchoolAdminBranding : Screen("school-admin/branding")

    data object SchoolAdminInviteTeacher : Screen("school-admin/invite-teacher")

    data object SchoolAdminBroadcast : Screen("school-admin/broadcast")

    data object SchoolAdminManageSections : Screen("school-admin/manage-sections")

    data object SchoolAdminManualPickup : Screen("school-admin/manual-pickup")

    data object SchoolAdminStaffManagement : Screen("school-admin/staff-management")

    data object SchoolAdminAuditLog : Screen("school-admin/audit-log")

    data object SchoolAdminDismissalDashboard : Screen("school-admin/dismissal-dashboard")

    data object SchoolAdminPickupPolicy : Screen("school-admin/pickup-policy")

    data object SchoolAdminAcademicStructure : Screen("school-admin/academic-structure")

    data object SchoolAdminBulkStudentImport : Screen("school-admin/bulk-student-import")

    data object SchoolAdminStudentLifecycle : Screen("school-admin/student-lifecycle")
}
