package com.pickuppass.android.ui.parent.students

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel = hiltViewModel(),
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onGetPass: (studentId: String) -> Unit,
    onManageGuardians: (studentId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Notification permission only exists as a runtime prompt on API 33+;
    // on older versions POST_NOTIFICATIONS is granted automatically at install.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notificationPermission.status.isGranted) {
                notificationPermission.launchPermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandedTitle("My Students", uiState.school) },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationCount > 0) {
                                    Badge {
                                        Text(if (uiState.unreadNotificationCount > 9) "9+" else uiState.unreadNotificationCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "My Profile")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> FullScreenLoading()
                uiState.error != null -> Box(Modifier.padding(Spacing.lg)) { ErrorBanner(uiState.error!!) }
                uiState.students.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(uiState.students, key = { it.id }) { student ->
                        StudentCard(
                            student = student,
                            onGetPass = { onGetPass(student.id) },
                            onManageGuardians = { onManageGuardians(student.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: Student,
    onGetPass: () -> Unit,
    onManageGuardians: () -> Unit
) {
    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Spacing.md)) {
            Text(student.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Grade ${student.grade} · Section ${student.section}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilledTonalButton(onClick = onGetPass, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Get Pass")
                }
                OutlinedButton(onClick = onManageGuardians, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardians")
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            "No students are linked to your account yet.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
        )
        Text(
            "Contact your school office to be added as a pickup contact.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
