package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Amber100
import com.pickuppass.android.ui.theme.Amber700
import com.pickuppass.android.ui.theme.Blue50
import com.pickuppass.android.ui.theme.Blue600
import com.pickuppass.android.ui.theme.Spacing
import com.pickuppass.android.ui.theme.Success100
import com.pickuppass.android.ui.theme.Success600
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DismissalDashboardScreen(
    viewModel: DismissalDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "School operations",
                subtitle = "Live dismissal dashboard",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh dismissal data",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && dashboard == null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    FullScreenLoading()
                }
            }

            dashboard == null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    DashboardUnavailable(
                        message = state.error ?: "Dismissal data is not available yet.",
                        refreshing = state.isRefreshing,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .widthIn(max = 920.dp),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item(key = "context") {
                        DashboardContext(
                            businessDate = dashboard.businessDate,
                            timeZone = dashboard.timeZone,
                            refreshing = state.isRefreshing,
                        )
                    }

                    item(key = "hero") {
                        ReleaseProgressCard(
                            released = dashboard.releasedCount,
                            total = dashboard.totalStudents,
                            remaining = dashboard.remainingCount,
                            rate = dashboard.releaseRatePercent,
                        )
                    }

                    item(key = "metrics") {
                        DashboardMetrics(
                            released = dashboard.releasedCount,
                            remaining = dashboard.remainingCount,
                            qr = dashboard.qrReleaseCount,
                            manual = dashboard.manualOverrideCount,
                        )
                    }

                    if (dashboard.manualOverrideCount > 0) {
                        item(key = "manual_attention") {
                            ManualOverrideNotice(dashboard.manualOverrideCount)
                        }
                    }

                    if (dashboard.gateActivity.isNotEmpty()) {
                        item(key = "gate_header") {
                            SectionHeader(
                                title = "Gate activity",
                                subtitle = "Today's verified releases by pickup gate",
                                trailing = "${dashboard.gateActivity.size} ${pluralize(dashboard.gateActivity.size, "gate", "gates")}",
                            )
                        }

                        items(
                            items = dashboard.gateActivity,
                            key = { "gate-${it.pickupGateId}" },
                        ) { gate ->
                            GateCard(gate)
                        }
                    }

                    item(key = "activity_header") {
                        SectionHeader(
                            title = if (state.showRemaining) "Still on campus" else "Recent releases",
                            subtitle = if (state.showRemaining) {
                                "Students without a recorded release today"
                            } else {
                                "Latest verified dismissal records"
                            },
                            trailing = if (state.showRemaining) {
                                dashboard.remainingCount.toString()
                            } else {
                                dashboard.recentReleases.size.toString()
                            },
                        )
                    }

                    item(key = "activity_toggle") {
                        ActivityToggle(
                            showRemaining = state.showRemaining,
                            onReleased = viewModel::showReleased,
                            onRemaining = viewModel::showRemaining,
                        )
                    }

                    if (state.showRemaining) {
                        if (dashboard.remainingStudents.isEmpty()) {
                            item(key = "remaining_empty") {
                                PositiveEmptyState(
                                    icon = Icons.Filled.CheckCircle,
                                    title = "Everyone is accounted for",
                                    message = "All listed students have a recorded release for today.",
                                )
                            }
                        } else {
                            items(
                                items = dashboard.remainingStudents,
                                key = { "remaining-${it.studentId}" },
                            ) { student ->
                                RemainingStudentCard(student)
                            }

                            if (dashboard.remainingTruncated) {
                                item(key = "remaining_truncated") {
                                    SupportingNotice(
                                        "Showing the first 250 students. Open the roster for the complete list.",
                                    )
                                }
                            }
                        }
                    } else {
                        if (dashboard.recentReleases.isEmpty()) {
                            item(key = "releases_empty") {
                                PositiveEmptyState(
                                    icon = Icons.Filled.Schedule,
                                    title = "No releases yet",
                                    message = "Verified dismissal records will appear here as students leave campus.",
                                )
                            }
                        } else {
                            items(
                                items = dashboard.recentReleases,
                                key = { "release-${it.exitLogId}" },
                            ) { release ->
                                ReleaseCard(release, dashboard.timeZone)
                            }
                        }
                    }

                    item(key = "operational_note") {
                        SupportingNotice(
                            "PickupPass records verified releases. It does not create a parent arrival queue or require check-in.",
                        )
                    }
                }
            }
        }
    }

    // Screen-level feedback must never live inside a LazyColumn item. Keeping
    // it here guarantees refresh failures are visible regardless of scroll position.
    if (dashboard != null) {
        state.error?.let { ErrorBanner(it) }
    }
}

@Composable
private fun DashboardContext(
    businessDate: String,
    timeZone: String,
    refreshing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TODAY'S DISMISSAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$businessDate · $timeZone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = if (refreshing) "Refreshing" else "Live",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ReleaseProgressCard(
    released: Int,
    total: Int,
    remaining: Int,
    rate: Double,
) {
    val safeRate = rate.coerceIn(0.0, 100.0)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Release progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$released of $total students released",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${safeRate.toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            LinearProgressIndicator(
                progress = { (safeRate / 100.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$remaining remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (remaining == 0) "Dismissal complete" else "Dismissal in progress",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining == 0) Success600 else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DashboardMetrics(
    released: Int,
    remaining: Int,
    qr: Int,
    manual: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CheckCircle,
                label = "Released",
                value = released.toString(),
                accent = Success600,
                container = Success100,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Schedule,
                label = "Remaining",
                value = remaining.toString(),
                accent = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.QrCode2,
                label = "QR verified",
                value = qr.toString(),
                accent = Blue600,
                container = Blue50,
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.PeopleAlt,
                label = "Manual",
                value = manual.toString(),
                accent = Amber700,
                container = Amber100,
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    container: Color,
) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.small,
                color = container,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accent,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualOverrideNotice(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Amber100.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Amber700.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Amber700.copy(alpha = 0.10f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = Amber700,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count manual ${pluralize(count, "release", "releases")}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Amber700,
                )
                Text(
                    text = "Manual overrides are audited. Review them if the count is unexpected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = trailing,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GateCard(gate: GateActivityItem) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = gate.pickupGateName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gate.pickupGateName.ifBlank { "Pickup gate" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (gate.campusName.isNotBlank()) {
                    Text(
                        text = gate.campusName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MethodBadge("QR ${gate.qrReleaseCount}", false)
                    if (gate.manualOverrideCount > 0) {
                        MethodBadge("Manual ${gate.manualOverrideCount}", true)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Released",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = gate.releaseCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MethodBadge(label: String, manual: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (manual) Amber100 else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (manual) Amber700 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityToggle(
    showRemaining: Boolean,
    onReleased: () -> Unit,
    onRemaining: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToggleButton(
                modifier = Modifier.weight(1f),
                selected = !showRemaining,
                label = "Recent releases",
                onClick = onReleased,
            )
            ToggleButton(
                modifier = Modifier.weight(1f),
                selected = showRemaining,
                label = "Still on campus",
                onClick = onRemaining,
            )
        }
    }
}

@Composable
private fun ToggleButton(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemainingStudentCard(student: DashboardStudent) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialAvatar(
                name = student.studentName,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = academicLabel(student.grade, student.section),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Surface(
                shape = CircleShape,
                color = Amber100,
            ) {
                Text(
                    text = "On campus",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber700,
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: DashboardRelease,
    timeZone: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(
                    name = release.studentName,
                    container = Success100,
                    content = Success600,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.studentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = academicLabel(release.grade, release.section),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTimestamp(release.timestamp, timeZone),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    MethodBadge(
                        label = if (release.method == "manual_override") "Manual" else "QR verified",
                        manual = release.method == "manual_override",
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "Guardian · ${release.guardianName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Approved by ${release.staffName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (release.pickupGateName.isNotBlank()) {
                Text(
                    text = releaseLocationLabel(release),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InitialAvatar(
    name: String,
    container: Color,
    content: Color,
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

@Composable
private fun PositiveEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = Success100,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Success600,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupportingNotice(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardUnavailable(
    message: String,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.widthIn(max = 460.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Dismissal status unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = onRetry,
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (refreshing) "Refreshing…" else "Try again")
            }
        }
    }
}

private fun academicLabel(grade: String, section: String): String {
    return "Grade ${grade.ifBlank { "—" }} · Section ${section.ifBlank { "—" }}"
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
        DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.of(timeZone))
            .format(Instant.parse(value))
    } catch (_: Exception) {
        value.take(16).replace('T', ' ')
    }
}
