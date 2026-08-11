package com.pickuppass.android.ui.schooladmin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            state.error?.let { message ->
                item { ErrorBanner(message) }
            }

            if (dashboard == null) {
                item {
                    Text(
                        "No dashboard data is available yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.PeopleAlt,
                        label = "Students",
                        value = dashboard.totalStudents.toString()
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.CheckCircle,
                        label = "Released",
                        value = dashboard.releasedCount.toString()
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Schedule,
                        label = "Remaining",
                        value = dashboard.remainingCount.toString()
                    )
                }
            }

            item {
                LinearProgressIndicator(
                    progress = (dashboard.releaseRatePercent / 100.0).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${dashboard.releaseRatePercent}% released today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("QR ${dashboard.qrReleaseCount}") },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Manual ${dashboard.manualOverrideCount}") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (dashboard.gateActivity.isNotEmpty()) {
                item {
                    Text(
                        "Pickup Gate Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    Text(
                        "Live release counts by configured gate. A single-gate school requires no staff gate assignment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        label = { Text("Recent Releases") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.showRemaining,
                        onClick = viewModel::showRemaining,
                        label = { Text("Still On Campus") },
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
                                "Showing the first 250 students. Use the student roster for the full list.",
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
                    "This screen is status-only. It does not create a queue or require parents to check in before presenting a valid QR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md)
                )
            }
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gate.pickupGateName.ifBlank { "Pickup Gate" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
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
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(student.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(release.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
