package com.pickuppass.android.ui.teacher.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    viewModel: TeacherHomeViewModel = hiltViewModel(),
    onStartScanner: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBroadcast: () -> Unit,
    onOpenOperations: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Teacher Home",
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            uiState.school?.schoolName
                                ?.takeIf { it.isNotBlank() }
                                ?: "PickupPass",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotifications > 0) {
                                    Badge {
                                        Text(
                                            if (uiState.unreadNotifications > 99) {
                                                "99+"
                                            } else {
                                                uiState.unreadNotifications.toString()
                                            }
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

                    IconButton(onClick = onOpenProfile) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "My profile"
                        )
                    }
                }
            )
        }
    ) { padding ->
        PickupPassPullToRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            enabled = !uiState.isLoading
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    TeacherWelcome(
                        displayName = uiState.displayName,
                        schoolName = uiState.school?.schoolName.orEmpty(),
                        isLoading = uiState.isLoading
                    )
                }

                item {
                    ScannerHero(
                        enabled = !uiState.isLoading,
                        onStartScanner = onStartScanner
                    )
                }

                uiState.error?.let { message ->
                    item {
                        ErrorBanner(message)
                    }
                }

                if (uiState.hasNoAssignedSections) {
                    item {
                        NoSectionsNotice()
                    }
                }

                item {
                    TeacherMetrics(
                        studentCount = uiState.studentCount,
                        sectionCount = uiState.sectionSummaries.size,
                        isLoading = uiState.isLoading
                    )
                }

                if (uiState.sectionSummaries.isNotEmpty()) {
                    item {
                        Text(
                            "My classes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    items(
                        items = uiState.sectionSummaries.take(4),
                        key = { "${it.grade}|${it.section}" }
                    ) { section ->
                        SectionRow(section)
                    }

                    if (uiState.sectionSummaries.size > 4) {
                        item {
                            TextButton(
                                onClick = onOpenStudents,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "View all ${uiState.sectionSummaries.size} assigned sections"
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Teacher tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                item {
                    QuickActionRow(
                        first = TeacherAction(
                            title = "My students",
                            supportingText = "Roster and guardians",
                            icon = Icons.Filled.Groups,
                            onClick = onOpenStudents
                        ),
                        second = TeacherAction(
                            title = "Dismissal history",
                            supportingText = "Review releases",
                            icon = Icons.Filled.History,
                            onClick = onOpenHistory
                        )
                    )
                }

                item {
                    QuickActionRow(
                        first = TeacherAction(
                            title = "Announcement",
                            supportingText = "Message your sections",
                            icon = Icons.Filled.Campaign,
                            onClick = onOpenBroadcast
                        ),
                        second = TeacherAction(
                            title = "Pickup operations",
                            supportingText = "Readiness and policy",
                            icon = Icons.Filled.Settings,
                            onClick = onOpenOperations
                        )
                    )
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "Student access and section announcements are scoped to the classes assigned to your teacher account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherWelcome(
    displayName: String,
    schoolName: String,
    isLoading: Boolean
) {
    Column {
        Text(
            when {
                isLoading -> "Preparing your workspace…"
                displayName.isNotBlank() -> "Welcome, ${displayName.trim().substringBefore(" ")}"
                else -> "Welcome back"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(3.dp))

        Text(
            if (schoolName.isBlank()) {
                "Your dismissal tools and assigned classes are ready here."
            } else {
                "Manage your assigned classes and run dismissal for $schoolName."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScannerHero(
    enabled: Boolean,
    onStartScanner: () -> Unit
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
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                "DISMISSAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "Start secure pickup scanning",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Verify the PickupPass QR, confirm the authorized guardian, then record the student release.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.lg))

            Button(
                onClick = onStartScanner,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 50.dp),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Start scanner",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun TeacherMetrics(
    studentCount: Int,
    sectionCount: Int,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        MetricCard(
            label = "Students",
            value = if (isLoading) "—" else studentCount.toString(),
            supportingText = "Assigned roster",
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            label = "Sections",
            value = if (isLoading) "—" else sectionCount.toString(),
            supportingText = "Assigned classes",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    supportingText: String,
    modifier: Modifier
) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionRow(
    summary: TeacherHomeSectionSummary
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    summary.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "${summary.studentCount} student${if (summary.studentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class TeacherAction(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionRow(
    first: TeacherAction,
    second: TeacherAction
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        TeacherActionCard(
            action = first,
            modifier = Modifier.weight(1f)
        )
        TeacherActionCard(
            action = second,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TeacherActionCard(
    action: TeacherAction,
    modifier: Modifier
) {
    FilledTonalButton(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 106.dp),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.height(9.dp))
            Text(
                action.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                action.supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NoSectionsNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    "No classes assigned",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Ask your school administrator to assign at least one grade and section. The dismissal scanner remains available for authorized staff.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
