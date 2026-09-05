package com.pickuppass.android.ui.schooladmin.readiness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.pickuppass.android.ui.common.SuccessBanner
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
    var confirmReview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Launch Readiness", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Production setup checklist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !state.saving && !state.loading) {
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

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 820.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (readiness == null) {
                    state.error?.let { ErrorBanner(it) }
                    return@Column
                }

                ReadinessHero(readiness.effectiveStatus, readiness.blockerCount, readiness.warningCount, readiness.passedCount)

                val total = (readiness.passedCount + readiness.warningCount + readiness.blockerCount).coerceAtLeast(1)
                val progress = readiness.passedCount.toFloat() / total.toFloat()

                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Configuration progress", fontWeight = FontWeight.SemiBold)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }

                when {
                    readiness.readyForLaunch -> {
                        StatusBanner(
                            title = "Ready for launch",
                            body = "Platform approval is complete and all required checks currently pass.",
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    readiness.launchApproved -> {
                        StatusBanner(
                            title = "Approved, but attention is required",
                            body = "Resolve current blockers before relying on the previous launch approval.",
                            color = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                    readiness.reviewStatus == "review_requested" -> {
                        StatusBanner(
                            title = "Platform review requested",
                            body = "You can continue improving school configuration while the platform owner reviews the tenant.",
                            color = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }

                Text(
                    "PickupPass checks configuration automatically where possible. Final on-site checks remain manual because they must be verified on the real dismissal device and with school staff.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionHeading("Automatic checks", "Open the linked setup screen when PickupPass detects a blocker.")
                readiness.checks.filter { it.action != "manual" }.forEach { check ->
                    ReadinessCheckCard(check = check, onOpenAction = onOpenAction)
                }

                HorizontalDivider()

                SectionHeading("On-site launch checks", "Confirm these only after testing them at the school.")

                ManualCheckRow(
                    "scannerDeviceTested",
                    "Scanner device tested",
                    "Test sign-in, camera permission, QR recognition, network connection, gate selection, and approval on the actual dismissal device.",
                    readiness.manualChecks["scannerDeviceTested"] == true,
                    state.saving,
                    viewModel::setManualCheck
                )
                ManualCheckRow(
                    "guardianQrTested",
                    "Guardian QR flow tested end-to-end",
                    "Generate a real test QR and confirm staff can verify and approve it successfully.",
                    readiness.manualChecks["guardianQrTested"] == true,
                    state.saving,
                    viewModel::setManualCheck
                )
                ManualCheckRow(
                    "dismissalStaffBriefed",
                    "Dismissal staff briefed",
                    "Staff understand QR release, duplicate-pass behavior, guardian identity checks, escalation, and gate selection.",
                    readiness.manualChecks["dismissalStaffBriefed"] == true,
                    state.saving,
                    viewModel::setManualCheck
                )
                ManualCheckRow(
                    "emergencyProcedureReviewed",
                    "Emergency / fallback procedure reviewed",
                    "The school has a documented response for device or network problems and knows when audited Manual Release may be used.",
                    readiness.manualChecks["emergencyProcedureReviewed"] == true,
                    state.saving,
                    viewModel::setManualCheck
                )

                state.error?.let { ErrorBanner(it) }
                state.message?.let { SuccessBanner(it) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { confirmReview = true },
                        enabled = readiness.readyForReview &&
                            readiness.reviewStatus != "review_requested" &&
                            !readiness.launchApproved &&
                            !state.saving,
                        modifier = Modifier.widthIn(min = 220.dp, max = 380.dp).height(52.dp)
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Spacing.xs))
                        }
                        Text(
                            when {
                                readiness.launchApproved -> "Launch approved"
                                readiness.reviewStatus == "review_requested" -> "Review requested"
                                readiness.readyForReview -> "Request platform review"
                                else -> "Complete required checks"
                            }
                        )
                    }
                }

                Text(
                    "Final production approval belongs to the PickupPass platform owner. School admins can complete configuration and request review, but cannot approve their own tenant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmReview) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) confirmReview = false },
            icon = { Icon(Icons.Filled.FactCheck, null) },
            title = { Text("Request platform launch review?") },
            text = {
                Text(
                    "Submit the current school configuration for platform-owner review. Continue only if the on-site checks are accurate and required blockers have been resolved."
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.saving,
                    onClick = {
                        confirmReview = false
                        viewModel.requestReview()
                    }
                ) { Text("Request review") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReview = false }, enabled = !state.saving) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReadinessHero(status: String, blockers: Long, warnings: Long, passed: Long) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(statusLabel(status), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusMetric("Passed", passed, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                StatusMetric("Warnings", warnings, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f))
                StatusMetric("Blockers", blockers, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: Long, color: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusBanner(title: String, body: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadinessCheckCard(
    check: LaunchReadinessCheck,
    onOpenAction: (String) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
            val color = checkColor(check.status)
            Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = .12f)) {
                Icon(
                    when (check.status) {
                        "pass" -> Icons.Filled.CheckCircle
                        "warning" -> Icons.Filled.WarningAmber
                        else -> Icons.Filled.ErrorOutline
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(check.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(check.status.replace('_', ' ').uppercase()) }
                    )
                }
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (
                    check.status != "pass" &&
                    check.action.isNotBlank() &&
                    check.action !in setOf("manual", "platform_owner", "billing")
                ) {
                    TextButton(
                        onClick = { onOpenAction(check.action) },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Open setup") }
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
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onChange(key, it) },
                enabled = !saving
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
