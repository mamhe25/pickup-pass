package com.pickuppass.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pickuppass.android.ui.login.LoginScreen
import com.pickuppass.android.ui.masteradmin.MasterAdminScreen
import com.pickuppass.android.ui.parent.guardians.ManageGuardiansScreen
import com.pickuppass.android.ui.parent.notifications.NotificationsScreen
import com.pickuppass.android.ui.parent.pass.PickupPassScreen
import com.pickuppass.android.ui.parent.profile.ProfileScreen
import com.pickuppass.android.ui.parent.devices.MyDevicesScreen
import com.pickuppass.android.ui.parent.students.StudentsScreen
import com.pickuppass.android.ui.schooladmin.branding.SchoolBrandingScreen
import com.pickuppass.android.ui.schooladmin.bulkimport.BulkStudentImportScreen
import com.pickuppass.android.ui.schooladmin.studentlifecycle.StudentLifecycleScreen
import com.pickuppass.android.ui.schooladmin.broadcast.SchoolBroadcastScreen
import com.pickuppass.android.ui.schooladmin.sections.ManageSectionsScreen
import com.pickuppass.android.ui.schooladmin.manualpickup.ManualPickupScreen
import com.pickuppass.android.ui.schooladmin.staffmanagement.StaffManagementScreen
import com.pickuppass.android.ui.schooladmin.audit.AuditLogScreen
import com.pickuppass.android.ui.schooladmin.dashboard.DismissalDashboardScreen
import com.pickuppass.android.ui.schooladmin.reports.DismissalReportsScreen
import com.pickuppass.android.ui.schooladmin.guardianverification.GuardianVerificationScreen
import com.pickuppass.android.ui.schooladmin.pickuppolicy.PickupPolicyScreen
import com.pickuppass.android.ui.schooladmin.academic.AcademicStructureScreen
import com.pickuppass.android.ui.schooladmin.campusgates.CampusGateScreen
import com.pickuppass.android.ui.schooladmin.staff.InviteTeacherScreen
import com.pickuppass.android.ui.splash.SplashDestination
import com.pickuppass.android.ui.splash.SplashScreen
import com.pickuppass.android.ui.teacher.broadcast.TeacherBroadcastScreen
import com.pickuppass.android.ui.teacher.exitlogs.ExitLogsScreen
import com.pickuppass.android.ui.teacher.registerparent.RegisterParentScreen
import com.pickuppass.android.ui.teacher.scanner.ScannerScreen
import com.pickuppass.android.ui.teacher.students.TeacherStudentsScreen
import com.pickuppass.android.session.SessionGuardViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.pickuppass.android.ui.schooladmin.staffgates.StaffPickupGatesScreen

@Composable
fun PickupPassNavHost(navController: NavHostController = rememberNavController()) {
    val sessionGuard: SessionGuardViewModel = hiltViewModel()

    LaunchedEffect(sessionGuard, navController) {
        sessionGuard.sessionExpiryManager.events.collect {
            navController.navigateToLoginClearingBackStack()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigate = { destination ->
                    val target = when (destination) {
                        SplashDestination.ParentHome -> Screen.ParentStudents.route
                        SplashDestination.TeacherHome -> Screen.TeacherScanner.route
                        SplashDestination.SchoolAdminHome -> Screen.SchoolAdminBranding.route
                        SplashDestination.MasterAdminHome -> Screen.MasterAdminHome.route
                        else -> Screen.Login.route
                    }
                    navController.navigate(target) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onParentHome = {
                    navController.navigate(Screen.ParentStudents.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onTeacherHome = {
                    navController.navigate(Screen.TeacherScanner.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onSchoolAdminHome = {
                    navController.navigate(Screen.SchoolAdminBranding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onMasterAdminHome = {
                    navController.navigate(Screen.MasterAdminHome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ---- Master admin SaaS flow ----

        composable(Screen.MasterAdminHome.route) {
            MasterAdminScreen(onSignedOut = { navController.navigateToLoginClearingBackStack() })
        }

        // ---- Parent flow ----

        composable(Screen.ParentStudents.route) {
            StudentsScreen(
                onOpenProfile = { navController.navigate(Screen.ParentProfile.route) },
                onOpenNotifications = { navController.navigate(Screen.ParentNotifications.route) },
                onGetPass = { studentId ->
                    navController.navigate(Screen.ParentPickupPass.createRoute(studentId))
                },
                onManageGuardians = { studentId ->
                    navController.navigate(Screen.ParentManageGuardians.createRoute(studentId))
                }
            )
        }

        composable(Screen.ParentProfile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Screen.ParentMyDevices.route) },
                onSignedOut = { navController.navigateToLoginClearingBackStack() }
            )
        }

        composable(Screen.ParentNotifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.ParentMyDevices.route) {
            MyDevicesScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ParentPickupPass.route,
            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId").orEmpty()
            PickupPassScreen(studentId = studentId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ParentManageGuardians.route,
            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId").orEmpty()
            ManageGuardiansScreen(studentId = studentId, onBack = { navController.popBackStack() })
        }

        // ---- Teacher / guard flow ----

        composable(Screen.TeacherScanner.route) {
            ScannerScreen(
                onGoToStudents = { navController.navigate(Screen.TeacherStudents.route) },
                onGoToExitLogs = { navController.navigate(Screen.TeacherExitLogs.route) },
                onGoToNotifications = { navController.navigate(Screen.TeacherNotifications.route) },
                onGoToBroadcast = { navController.navigate(Screen.TeacherBroadcast.route) },
                onSignOut = { navController.navigateToLoginClearingBackStack() }
            )
        }

        composable(Screen.TeacherStudents.route) {
            TeacherStudentsScreen(
                onBack = { navController.popBackStack() },
                onGoToExitLogs = { navController.navigate(Screen.TeacherExitLogs.route) },
                onRegisterParent = { studentId ->
                    navController.navigate(Screen.TeacherRegisterParent.createRoute(studentId))
                }
            )
        }

        composable(
            route = Screen.TeacherRegisterParent.route,
            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getString("studentId").orEmpty()
            RegisterParentScreen(studentId = studentId, onBack = { navController.popBackStack() })
        }

        composable(Screen.TeacherExitLogs.route) {
            ExitLogsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.TeacherNotifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.TeacherBroadcast.route) {
            TeacherBroadcastScreen(onBack = { navController.popBackStack() })
        }

        // ---- School admin flow ----

        composable(Screen.SchoolAdminBranding.route) {
            SchoolBrandingScreen(
                onGoToScanner = { navController.navigate(Screen.TeacherScanner.route) },
                onGoToStudents = { navController.navigate(Screen.TeacherStudents.route) },
                onGoToExitLogs = { navController.navigate(Screen.TeacherExitLogs.route) },
                onGoToInviteTeacher = { navController.navigate(Screen.SchoolAdminInviteTeacher.route) },
                onGoToManageSections = { navController.navigate(Screen.SchoolAdminManageSections.route) },
                onGoToStaffManagement = { navController.navigate(Screen.SchoolAdminStaffManagement.route) },
                onGoToManualPickup = { navController.navigate(Screen.SchoolAdminManualPickup.route) },
                onGoToAuditLog = { navController.navigate(Screen.SchoolAdminAuditLog.route) },
                onGoToDismissalDashboard = { navController.navigate(Screen.SchoolAdminDismissalDashboard.route) },
                onGoToPickupPolicy = { navController.navigate(Screen.SchoolAdminPickupPolicy.route) },
                onGoToAcademicStructure = { navController.navigate(Screen.SchoolAdminAcademicStructure.route) },
                onGoToBulkStudentImport = { navController.navigate(Screen.SchoolAdminBulkStudentImport.route) },
                onGoToStudentLifecycle = { navController.navigate(Screen.SchoolAdminStudentLifecycle.route) },
                onGoToDismissalReports = { navController.navigate(Screen.SchoolAdminDismissalReports.route) },
                onGoToGuardianVerification = { navController.navigate(Screen.SchoolAdminGuardianVerification.route) },
                onGoToCampusGates = { navController.navigate(Screen.SchoolAdminCampusGates.route) },
                onGoToStaffPickupGates = { navController.navigate(Screen.SchoolAdminStaffPickupGates.route) },
                onGoToBroadcast = { navController.navigate(Screen.SchoolAdminBroadcast.route) },
                onSignedOut = { navController.navigateToLoginClearingBackStack() }
            )
        }

        composable(Screen.SchoolAdminInviteTeacher.route) {
            InviteTeacherScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminManageSections.route) {
            ManageSectionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminBroadcast.route) {
            SchoolBroadcastScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminManualPickup.route) {
            ManualPickupScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminStaffManagement.route) {
            StaffManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminAuditLog.route) {
            AuditLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminDismissalDashboard.route) {
            DismissalDashboardScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminPickupPolicy.route) {
            PickupPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminAcademicStructure.route) {
            AcademicStructureScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminBulkStudentImport.route) {
            BulkStudentImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminStudentLifecycle.route) {
            StudentLifecycleScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminDismissalReports.route) {
            DismissalReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminGuardianVerification.route) {
            GuardianVerificationScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminCampusGates.route) {
            CampusGateScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.SchoolAdminStaffPickupGates.route) {
            StaffPickupGatesScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateToLoginClearingBackStack() {
    navigate(Screen.Login.route) {
        popUpTo(0) { inclusive = true }
    }
}
