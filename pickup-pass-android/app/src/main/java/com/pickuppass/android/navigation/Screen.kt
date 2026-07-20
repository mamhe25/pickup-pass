package com.pickuppass.android.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")

    data object ParentStudents : Screen("parent/students")
    data object ParentProfile : Screen("parent/profile")

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

    data object SchoolAdminBranding : Screen("school-admin/branding")

    data object SchoolAdminInviteTeacher : Screen("school-admin/invite-teacher")
}
