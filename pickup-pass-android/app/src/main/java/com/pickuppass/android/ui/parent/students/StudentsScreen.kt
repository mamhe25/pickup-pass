package com.pickuppass.android.ui.parent.students

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.pickuppass.android.ui.common.SmartImage
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
    var notificationNudgeDismissed by rememberSaveable { mutableStateOf(false) }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    val notificationsGranted =
        notificationPermission == null || notificationPermission.status.isGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BrandedTitle(
                        title = "My Students",
                        school = uiState.school
                    )
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationCount > 0) {
                                    Badge {
                                        Text(
                                            if (uiState.unreadNotificationCount > 9) "9+"
                                            else uiState.unreadNotificationCount.toString()
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                    }

                    ParentProfileButton(
                        displayName = uiState.parentDisplayName,
                        onClick = onOpenProfile
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> FullScreenLoading()

                uiState.error != null -> ParentErrorState(
                    message = uiState.error ?: "Couldn't load your students",
                    onRetry = viewModel::load
                )

                uiState.students.isEmpty() -> EmptyState(
                    schoolName = uiState.school?.schoolName.orEmpty()
                )

                else -> StudentsContent(
                    students = uiState.students,
                    parentDisplayName = uiState.parentDisplayName,
                    showNotificationNudge =
                        !notificationsGranted && !notificationNudgeDismissed,
                    onEnableNotifications = {
                        notificationPermission?.launchPermissionRequest()
                    },
                    onDismissNotificationNudge = {
                        notificationNudgeDismissed = true
                    },
                    onGetPass = onGetPass,
                    onManageGuardians = onManageGuardians
                )
            }
        }
    }
}

@Composable
private fun StudentsContent(
    students: List<Student>,
    parentDisplayName: String,
    showNotificationNudge: Boolean,
    onEnableNotifications: () -> Unit,
    onDismissNotificationNudge: () -> Unit,
    onGetPass: (String) -> Unit,
    onManageGuardians: (String) -> Unit
) {
    val activeCount = students.count {
        it.status.equals("active", ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 780.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.md,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "overview") {
                ParentOverviewHero(
                    parentDisplayName = parentDisplayName,
                    studentCount = students.size,
                    activeCount = activeCount
                )
            }

            if (showNotificationNudge) {
                item(key = "notification_permission") {
                    NotificationPermissionCard(
                        onEnable = onEnableNotifications,
                        onDismiss = onDismissNotificationNudge
                    )
                }
            }

            item(key = "students_header") {
                Column {
                    Text(
                        "Linked students",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Choose the student being collected. Pickup passes are generated individually and still require school verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = students,
                key = { "student-${it.id}" }
            ) { student ->
                StudentCard(
                    student = student,
                    onGetPass = { onGetPass(student.id) },
                    onManageGuardians = { onManageGuardians(student.id) }
                )
            }

            item(key = "security_note") {
                SecurityNotice()
            }
        }
    }
}

@Composable
private fun ParentOverviewHero(
    parentDisplayName: String,
    studentCount: Int,
    activeCount: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            Text(
                "FAMILY PICKUP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (parentDisplayName.isBlank()) {
                    "Safe pickup starts here."
                } else {
                    "Welcome, ${parentDisplayName.firstName()}."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Generate a secure pass for the right student and keep authorized pickup contacts up to date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )
            Spacer(Modifier.height(Spacing.lg))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroMetric(
                    value = studentCount.toString(),
                    label = if (studentCount == 1) "Linked student" else "Linked students",
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    value = activeCount.toString(),
                    label = "Pass eligible",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Stay updated on dismissal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Allow notifications for school announcements and pickup updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilledTonalButton(onClick = onEnable) {
                    Text("Enable notifications")
                }
                TextButton(onClick = onDismiss) {
                    Text("Not now")
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
    val isActive = student.status.equals("active", ignoreCase = true)
    val guardianCount = student.guardianUids
        .filter { it.isNotBlank() }
        .distinct()
        .size

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudentAvatar(student)
                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            student.fullName.ifBlank { "Unnamed student" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        StudentStatusChip(
                            status = student.status,
                            active = isActive
                        )
                    }

                    Spacer(Modifier.height(Spacing.xs))

                    Text(
                        "Grade ${student.grade.ifBlank { "—" }}  ·  ${
                            student.section.ifBlank { "Section —" }
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (student.studentNumber.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Student no. ${student.studentNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (student.academicYearName.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            student.academicYearName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    when (guardianCount) {
                        0 -> "No linked pickup contacts"
                        1 -> "1 linked pickup contact"
                        else -> "$guardianCount linked pickup contacts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isActive) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "Pickup passes are unavailable while this student is ${
                            student.status.ifBlank { "inactive" }.lowercase()
                        }.",
                        modifier = Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Button(
                    onClick = onGetPass,
                    enabled = isActive,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Pickup pass", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onManageGuardians,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Guardians", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StudentAvatar(student: Student) {
    val initials = student.fullName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initials,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (!student.photoUrl.isNullOrBlank()) {
            SmartImage(
                model = student.photoUrl,
                contentDescription = "${student.fullName} profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun StudentStatusChip(
    status: String,
    active: Boolean
) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            if (active) {
                "Active"
            } else {
                status.ifBlank { "Inactive" }.replaceFirstChar { it.uppercase() }
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SecurityNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    "School verification is always required",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Generating a pass does not release a student. Authorized school staff must verify and approve every pickup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParentProfileButton(
    displayName: String,
    onClick: () -> Unit
) {
    val initial = displayName.trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()

    IconButton(onClick = onClick) {
        if (initial == null) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = "My profile"
            )
        } else {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ErrorBanner(message)
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun EmptyState(schoolName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            "No linked students yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            if (schoolName.isBlank()) {
                "Contact your school office to be added as an authorized pickup contact for a student."
            } else {
                "Contact $schoolName to be added as an authorized pickup contact for a student."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.md))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                "For security, parents cannot link themselves to a student from the app.",
                modifier = Modifier.padding(Spacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun String.firstName(): String =
    trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        .orEmpty()
        .ifBlank { this }
