package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
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
        topBar = {
            TopAppBar(
                title = { Text("Live Dismissal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && dashboard == null) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            state.error?.let { message -> item { ErrorBanner(message) } }

            if (dashboard == null) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text(
                            "No live dismissal data is available yet.",
                            modifier = Modifier.padding(Spacing.lg),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Text(
                    "${dashboard.businessDate} · ${dashboard.timeZone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                DismissalHero(
                    released = dashboard.releasedCount,
                    total = dashboard.totalStudents,
                    rate = dashboard.releaseRatePercent,
                    qr = dashboard.qrReleaseCount,
                    manual = dashboard.manualOverrideCount
                )
            }

            if (dashboard.manualOverrideCount > 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text(
                                    "Manual releases recorded",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "${dashboard.manualOverrideCount} manual release(s) were recorded today. Review them if unexpected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SummaryCard(
                        Modifier.weight(1f),
                        Icons.Filled.PeopleAlt,
                        "Students",
                        dashboard.totalStudents.toString()
                    )
                    SummaryCard(
                        Modifier.weight(1f),
                        Icons.Filled.CheckCircle,
                        "Released",
                        dashboard.releasedCount.toString()
                    )
                    SummaryCard(
                        Modifier.weight(1f),
                        Icons.Filled.Schedule,
                        "Remaining",
                        dashboard.remainingCount.toString()
                    )
                }
            }

            if (dashboard.gateActivity.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "Gate activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Live release counts by configured gate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(dashboard.gateActivity, key = { it.pickupGateId }) { gate ->
                    GateActivityCard(gate)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = !state.showRemaining,
                        onClick = viewModel::showReleased,
                        label = { Text("Recent releases") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.showRemaining,
                        onClick = viewModel::showRemaining,
                        label = { Text("Still on campus") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.showRemaining) {
                if (dashboard.remainingStudents.isEmpty()) {
                    item { EmptyMessage("All listed students have been released for today.") }
                } else {
                    items(dashboard.remainingStudents, key = { it.studentId }) { student ->
                        RemainingStudentCard(student)
                    }
                    if (dashboard.remainingTruncated) {
                        item {
                            Text(
                                "Showing the first 250 students. Use the roster for the full list.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (dashboard.recentReleases.isEmpty()) {
                    item { EmptyMessage("No students have been released yet today.") }
                } else {
                    items(dashboard.recentReleases, key = { it.exitLogId }) { release ->
                        ReleaseCard(release, dashboard.timeZone)
                    }
                }
            }

            item {
                Text(
                    "This is an operational status screen. It does not create a parent queue or require check-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            }
        }
    }
}

@Composable
private fun DismissalHero(
    released: Int,
    total: Int,
    rate: Double,
    qr: Int,
    manual: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 7.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "RELEASED TODAY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        released.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        " of $total",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Text(
                    "${rate.toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { (rate / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "QR $qr · Manual $manual",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GateActivityCard(gate: GateActivityItem) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    gate.pickupGateName.ifBlank { "Pickup Gate" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (gate.campusName.isNotBlank()) {
                    Text(
                        gate.campusName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "QR ${gate.qrReleaseCount} · Manual ${gate.manualOverrideCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                gate.releaseCount.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RemainingStudentCard(student: DashboardStudent) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Text(student.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Grade ${student.grade.ifBlank { "-" }} · Section ${student.section.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReleaseCard(release: DashboardRelease, timeZone: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(release.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    formatTimestamp(release.timestamp, timeZone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Grade ${release.grade.ifBlank { "-" }} · Section ${release.section.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Text("Picked up by ${release.guardianName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Approved by ${release.staffName} · ${if (release.method == "manual_override") "Manual override" else "QR scan"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (release.pickupGateName.isNotBlank()) {
                Text(
                    "Released at ${if (release.campusName.isBlank()) release.pickupGateName else "${release.campusName} · ${release.pickupGateName}"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
