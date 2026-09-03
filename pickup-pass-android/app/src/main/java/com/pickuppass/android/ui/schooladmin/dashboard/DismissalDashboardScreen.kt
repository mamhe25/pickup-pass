package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.DashboardRelease
import com.pickuppass.android.data.model.DashboardStudent
import com.pickuppass.android.data.model.GateActivityItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissalDashboardScreen(
    viewModel: DismissalDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dismissal Overview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "School operations today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh dismissal data"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && dashboard == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        if (dashboard == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                EmptyDashboardState(
                    message = state.error ?: "Dismissal data is not available yet.",
                    onRetry = viewModel::refresh,
                    refreshing = state.isRefreshing
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.md,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            state.error?.let { message ->
                item(key = "error") {
                    ErrorBanner(message)
                }
            }

            item(key = "context") {
                DashboardContextRow(
                    businessDate = dashboard.businessDate,
                    timeZone = dashboard.timeZone,
                    refreshing = state.isRefreshing
                )
            }

            item(key = "hero") {
                DismissalHero(
                    released = dashboard.releasedCount,
                    total = dashboard.totalStudents,
                    remaining = dashboard.remainingCount,
                    rate = dashboard.releaseRatePercent,
                    qr = dashboard.qrReleaseCount,
                    manual = dashboard.manualOverrideCount
                )
            }

            if (dashboard.manualOverrideCount > 0) {
                item(key = "manual-attention") {
                    ManualReleaseAttention(
                        count = dashboard.manualOverrideCount
                    )
                }
            }

            if (dashboard.gateActivity.isNotEmpty()) {
                item(key = "gate-heading") {
                    SectionHeader(
                        title = "Gate activity",
                        subtitle = "Today's releases by configured pickup gate",
                        trailing = "${dashboard.gateActivity.size} ${pluralize(dashboard.gateActivity.size, "gate", "gates")}"
                    )
                }

                items(
                    items = dashboard.gateActivity,
                    key = { "gate-${it.pickupGateId}" }
                ) { gate ->
                    GateActivityCard(gate)
                }
            }

            item(key = "activity-heading") {
                SectionHeader(
                    title = if (state.showRemaining) "Still on campus" else "Recent releases",
                    subtitle = if (state.showRemaining) {
                        "Students not yet recorded as released today"
                    } else {
                        "Latest verified dismissal records"
                    },
                    trailing = if (state.showRemaining) {
                        dashboard.remainingCount.toString()
                    } else {
                        dashboard.recentReleases.size.toString()
                    }
                )
            }

            item(key = "activity-toggle") {
                ActivityToggle(
                    showRemaining = state.showRemaining,
                    onReleased = viewModel::showReleased,
                    onRemaining = viewModel::showRemaining
                )
            }

            if (state.showRemaining) {
                if (dashboard.remainingStudents.isEmpty()) {
                    item(key = "remaining-empty") {
                        PositiveEmptyState(
                            icon = Icons.Filled.CheckCircle,
                            title = "Everyone is accounted for",
                            message = "All listed students have been released for today."
                        )
                    }
                } else {
                    items(
                        items = dashboard.remainingStudents,
                        key = { "remaining-${it.studentId}" }
                    ) { student ->
                        RemainingStudentCard(student)
                    }

                    if (dashboard.remainingTruncated) {
                        item(key = "remaining-truncated") {
                            SupportingNotice(
                                "Showing the first 250 students. Use the roster for the complete list."
                            )
                        }
                    }
                }
            } else {
                if (dashboard.recentReleases.isEmpty()) {
                    item(key = "releases-empty") {
                        PositiveEmptyState(
                            icon = Icons.Filled.Schedule,
                            title = "No releases yet",
                            message = "Verified dismissal records will appear here as students leave campus."
                        )
                    }
                } else {
                    items(
                        items = dashboard.recentReleases,
                        key = { "release-${it.exitLogId}" }
                    ) { release ->
                        ReleaseCard(
                            release = release,
                            timeZone = dashboard.timeZone
                        )
                    }
                }
            }

            item(key = "operational-note") {
                SupportingNotice(
                    "Operational view only — PickupPass records verified releases; it does not create a parent arrival queue or require check-in."
                )
            }
        }
    }
}

@Composable
private fun DashboardContextRow(
    businessDate: String,
    timeZone: String,
    refreshing: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TODAY'S DISMISSAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$businessDate · $timeZone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = CircleShape,
            color = if (refreshing) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = if (refreshing) "Refreshing" else "Current",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (refreshing) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}

@Composable
private fun DismissalHero(
    released: Int,
    total: Int,
    remaining: Int,
    rate: Double,
    qr: Int,
    manual: Int
) {
    val safeRate = rate.coerceIn(0.0, 100.0)
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.82f)
                    )
                )
            )
            .padding(Spacing.lg)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RELEASE PROGRESS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.76f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = released.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = " of $total released",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White
                ) {
                    Text(
                        text = "${safeRate.toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            LinearProgressIndicator(
                progress = { (safeRate / 100.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.20f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Schedule,
                    value = remaining.toString(),
                    label = "Remaining"
                )
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    value = qr.toString(),
                    label = "QR"
                )
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.PeopleAlt,
                    value = manual.toString(),
                    label = "Manual"
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.11f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Color.White.copy(alpha = 0.82f)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ManualReleaseAttention(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Review manual releases",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "$count ${pluralize(count, "manual release", "manual releases")} recorded today. Review only if this is unexpected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f)
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = trailing,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GateActivityCard(gate: GateActivityItem) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = gate.pickupGateName
                            .trim()
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "G",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gate.pickupGateName.ifBlank { "Pickup Gate" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (gate.campusName.isNotBlank()) {
                    Text(
                        text = gate.campusName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMethodBadge(
                        label = "QR ${gate.qrReleaseCount}",
                        emphasized = false
                    )
                    if (gate.manualOverrideCount > 0) {
                        CompactMethodBadge(
                            label = "Manual ${gate.manualOverrideCount}",
                            emphasized = true
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Released",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = gate.releaseCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CompactMethodBadge(
    label: String,
    emphasized: Boolean
) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ActivityToggle(
    showRemaining: Boolean,
    onReleased: () -> Unit,
    onRemaining: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActivityToggleButton(
                modifier = Modifier.weight(1f),
                selected = !showRemaining,
                label = "Recent releases",
                onClick = onReleased
            )
            ActivityToggleButton(
                modifier = Modifier.weight(1f),
                selected = showRemaining,
                label = "Still on campus",
                onClick = onRemaining
            )
        }
    }
}

@Composable
private fun ActivityToggleButton(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RemainingStudentCard(student: DashboardStudent) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(
                name = student.studentName,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = studentAcademicLabel(student.grade, student.section),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Text(
                        text = "On campus",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: DashboardRelease,
    timeZone: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(
                    name = release.studentName,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.studentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = studentAcademicLabel(release.grade, release.section),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTimestamp(release.timestamp, timeZone),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    CompactMethodBadge(
                        label = if (release.method == "manual_override") "Manual" else "QR verified",
                        emphasized = release.method == "manual_override"
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "Guardian · ${release.guardianName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Approved by ${release.staffName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (release.pickupGateName.isNotBlank()) {
                Text(
                    text = releaseLocationLabel(release),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InitialAvatar(
    name: String,
    containerColor: Color,
    contentColor: Color
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PositiveEmptyState(
    icon: ImageVector,
    title: String,
    message: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SupportingNotice(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyDashboardState(
    message: String,
    onRetry: () -> Unit,
    refreshing: Boolean
) {
    ElevatedCard(
        modifier = Modifier.widthIn(max = 460.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Dismissal status unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = onRetry,
                enabled = !refreshing
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (refreshing) "Refreshing…" else "Try again")
            }
        }
    }
}

private fun studentAcademicLabel(grade: String, section: String): String {
    val gradeLabel = grade.ifBlank { "—" }
    val sectionLabel = section.ifBlank { "—" }
    return "Grade $gradeLabel · Section $sectionLabel"
}

private fun releaseLocationLabel(release: DashboardRelease): String {
    return if (release.campusName.isBlank()) {
        "Released at ${release.pickupGateName}"
    } else {
        "Released at ${release.campusName} · ${release.pickupGateName}"
    }
}

private fun pluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) singular else plural
}

private fun formatTimestamp(value: String?, timeZone: String): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.of(timeZone))
            .format(instant)
    } catch (_: Exception) {
        value.take(16).replace('T', ' ')
    }
}
