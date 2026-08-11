package com.pickuppass.android.ui.schooladmin.readiness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.LaunchReadinessCheck
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchReadinessScreen(
    viewModel: LaunchReadinessViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAction: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readiness = state.readiness

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launch Readiness") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }, enabled = !state.saving) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh readiness")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (readiness == null) {
                state.error?.let { ErrorBanner(it) }
                return@Column
            }

            ReadinessSummary(readiness.effectiveStatus, readiness.blockerCount, readiness.warningCount, readiness.passedCount)

            Text(
                "PickupPass checks the configuration it can verify automatically. The four final operational checks are confirmed manually because they must be tested at the actual school and dismissal device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (readiness.readyForLaunch) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("Ready for Launch", fontWeight = FontWeight.Bold)
                        Text("The platform owner approved this school and all required checks currently pass.")
                    }
                }
            } else if (readiness.launchApproved) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("Approved, but configuration now needs attention", fontWeight = FontWeight.Bold)
                        Text("Resolve the required blockers before relying on the previous launch approval.")
                    }
                }
            } else if (readiness.reviewStatus == "review_requested") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Platform review requested. You can still refresh and improve the configuration while waiting.",
                        Modifier.padding(Spacing.md)
                    )
                }
            }

            Text("Automatic checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            readiness.checks.filter { it.action != "manual" }.forEach { check ->
                ReadinessCheckCard(check = check, onOpenAction = onOpenAction)
            }

            HorizontalDivider()
            Text("On-site launch checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ManualCheckRow(
                "scannerDeviceTested", "Scanner device tested",
                "Test sign-in, camera permission, QR recognition, network connection, and approval on the actual dismissal device.",
                readiness.manualChecks["scannerDeviceTested"] == true, state.saving, viewModel::setManualCheck
            )
            ManualCheckRow(
                "guardianQrTested", "Guardian QR flow tested end-to-end",
                "Generate a real test QR and confirm staff can verify and approve it successfully.",
                readiness.manualChecks["guardianQrTested"] == true, state.saving, viewModel::setManualCheck
            )
            ManualCheckRow(
                "dismissalStaffBriefed", "Dismissal staff briefed",
                "Staff understand normal QR release, duplicate-pass behavior, escalation, and gate selection when applicable.",
                readiness.manualChecks["dismissalStaffBriefed"] == true, state.saving, viewModel::setManualCheck
            )
            ManualCheckRow(
                "emergencyProcedureReviewed", "Emergency/fallback procedure reviewed",
                "The school has a documented fallback for device/network problems and knows when authorized manual pickup may be used.",
                readiness.manualChecks["emergencyProcedureReviewed"] == true, state.saving, viewModel::setManualCheck
            )

            state.error?.let { ErrorBanner(it) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Button(
                onClick = { viewModel.requestReview() },
                enabled = readiness.readyForReview && readiness.reviewStatus != "review_requested" && !readiness.launchApproved && !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        state.saving -> "Saving…"
                        readiness.launchApproved -> "Launch Approved"
                        readiness.reviewStatus == "review_requested" -> "Review Requested"
                        readiness.readyForReview -> "Request Platform Launch Review"
                        else -> "Complete Required Checks"
                    }
                )
            }
            Text(
                "Final launch approval belongs to the PickupPass platform owner. School admins can complete their own configuration but cannot approve the tenant for production themselves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadinessSummary(status: String, blockers: Long, warnings: Long, passed: Long) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(statusLabel(status), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$passed passed · $warnings warning(s) · $blockers required blocker(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReadinessCheckCard(check: LaunchReadinessCheck, onOpenAction: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
            val color = checkColor(check.status)
            Icon(
                when (check.status) {
                    "pass" -> Icons.Filled.CheckCircle
                    "warning" -> Icons.Filled.WarningAmber
                    else -> Icons.Filled.ErrorOutline
                },
                contentDescription = null,
                tint = color
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(check.label, fontWeight = FontWeight.SemiBold)
                Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (check.status != "pass" && check.action.isNotBlank() && check.action !in setOf("manual", "platform_owner", "billing")) {
                    TextButton(onClick = { onOpenAction(check.action) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Open setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualCheckRow(
    key: String,
    title: String,
    detail: String,
    checked: Boolean,
    saving: Boolean,
    onChange: (String, Boolean) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onChange(key, it) }, enabled = !saving)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun checkColor(status: String): Color = when (status) {
    "pass" -> MaterialTheme.colorScheme.primary
    "warning" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun statusLabel(status: String): String = when (status) {
    "approved" -> "Launch Approved"
    "approved_needs_attention" -> "Approved · Attention Required"
    "review_requested" -> "Awaiting Platform Review"
    "review_requested_needs_attention" -> "Review Requested · New Blockers Found"
    else -> "Setup in Progress"
}
