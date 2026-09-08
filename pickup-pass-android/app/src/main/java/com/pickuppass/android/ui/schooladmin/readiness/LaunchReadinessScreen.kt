package com.pickuppass.android.ui.schooladmin.readiness

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Security
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
import com.pickuppass.android.data.model.LaunchReadinessResponse
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchReadinessScreen(
    viewModel: LaunchReadinessViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAction: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readiness = state.readiness

    var confirmReview by remember {
        mutableStateOf(false)
    }

    var manualRollback by remember {
        mutableStateOf<ManualLaunchCheck?>(null)
    }

    Scaffold(
        containerColor =
            MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Launch readiness",
                subtitle =
                    "Production setup & review",
                onBack = {
                    if (!state.saving) {
                        onBack()
                    }
                },
            )
        }
    ) { padding ->
        if (
            state.loading &&
            readiness == null
        ) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            enabled = !state.saving,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (readiness == null) {
                    LaunchReadinessUnavailableCard(
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .align(Alignment.TopCenter)
                            .padding(Spacing.md)
                    )
                } else {
                    val total =
                        (
                            readiness.passedCount +
                                readiness.warningCount +
                                readiness.blockerCount
                            ).coerceAtLeast(1)

                    val progress =
                        readiness.passedCount
                            .toFloat() /
                            total.toFloat()

                    val automaticChecks =
                        readiness.checks.filter {
                            it.action != "manual"
                        }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 840.dp)
                            .align(Alignment.TopCenter),
                        contentPadding =
                            PaddingValues(
                                start = Spacing.md,
                                top = Spacing.md,
                                end = Spacing.md,
                                bottom = Spacing.xl
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                Spacing.md
                            )
                    ) {
                        item(key = "hero") {
                            PremiumHeroCard(
                                eyebrow =
                                    "Production readiness",
                                title =
                                    statusLabel(
                                        readiness
                                            .effectiveStatus
                                    ),
                                message =
                                    readinessHeroMessage(
                                        readiness
                                    ),
                                icon =
                                    Icons.Filled.Launch
                            )
                        }

                        item(key = "overview") {
                            ReadinessOverviewCard(
                                readiness =
                                    readiness,
                                progress =
                                    progress
                            )
                        }

                        item(key = "status-banner") {
                            CurrentReviewStatusCard(
                                readiness =
                                    readiness
                            )
                        }

                        if (
                            readiness.reviewRequestedAt !=
                            null ||
                            readiness.approvedAt != null ||
                            readiness.lastAssessedAt != null
                        ) {
                            item(key = "review-history") {
                                ReviewHistoryCard(
                                    readiness =
                                        readiness
                                )
                            }
                        }

                        item(key = "automatic-header") {
                            PremiumSectionHeader(
                                title =
                                    "Automatic checks",
                                subtitle =
                                    "Required blockers must be resolved before review. Recommendations do not block submission."
                            )
                        }

                        automaticChecks.forEach {
                                check ->
                            item(
                                key =
                                    "automatic-" +
                                        check.key
                            ) {
                                ReadinessCheckCard(
                                    check = check,
                                    enabled =
                                        !state.saving &&
                                            !state.refreshing,
                                    onOpenAction =
                                        onOpenAction
                                )
                            }
                        }

                        item(key = "manual-header") {
                            PremiumSectionHeader(
                                title =
                                    "On-site launch checks",
                                subtitle =
                                    "Confirm these only after testing them with real dismissal devices and school staff."
                            )
                        }

                        MANUAL_LAUNCH_CHECKS.forEach {
                                check ->
                            item(
                                key =
                                    "manual-" +
                                        check.key
                            ) {
                                val checked =
                                    readiness
                                        .manualChecks[
                                            check.key
                                        ] == true

                                val busy =
                                    state.savingAction ==
                                        "manual:" +
                                        check.key

                                ManualCheckCard(
                                    check = check,
                                    checked = checked,
                                    enabled =
                                        !state.saving &&
                                            !state.refreshing,
                                    busy = busy,
                                    onChange = {
                                            next ->
                                        if (
                                            checked &&
                                            !next &&
                                            (
                                                readiness
                                                    .reviewStatus ==
                                                    "review_requested" ||
                                                    readiness
                                                        .launchApproved
                                                )
                                        ) {
                                            manualRollback =
                                                check
                                        } else {
                                            viewModel
                                                .setManualCheck(
                                                    check.key,
                                                    next
                                                )
                                        }
                                    }
                                )
                            }
                        }

                        item(key = "governance") {
                            LaunchGovernanceCard()
                        }

                        item(key = "review-action") {
                            HorizontalDivider()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = Spacing.xs
                                    ),
                                horizontalArrangement =
                                    Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        confirmReview =
                                            true
                                    },
                                    enabled =
                                        readiness
                                            .readyForReview &&
                                            readiness
                                                .reviewStatus !=
                                                "review_requested" &&
                                            !readiness
                                                .launchApproved &&
                                            !state.saving &&
                                            !state.refreshing,
                                    modifier = Modifier
                                        .widthIn(
                                            min = 220.dp,
                                            max = 380.dp
                                        )
                                        .heightIn(
                                            min = 52.dp
                                        )
                                ) {
                                    if (
                                        state.savingAction ==
                                        "request-review"
                                    ) {
                                        CircularProgressIndicator(
                                            modifier =
                                                Modifier.size(
                                                    18.dp
                                                ),
                                            strokeWidth =
                                                2.dp
                                        )
                                        Spacer(
                                            Modifier.width(
                                                Spacing.xs
                                            )
                                        )
                                    }

                                    Text(
                                        reviewActionLabel(
                                            readiness
                                        )
                                    )
                                }
                            }

                            if (
                                !readiness.readyForReview
                            ) {
                                Spacer(
                                    Modifier.height(
                                        Spacing.xs
                                    )
                                )

                                Text(
                                    text =
                                        readiness.blockerCount
                                            .toString() +
                                            " required blocker" +
                                            if (
                                                readiness
                                                    .blockerCount ==
                                                1L
                                            ) {
                                                " must be resolved before platform review."
                                            } else {
                                                "s must be resolved before platform review."
                                            },
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (
        confirmReview &&
        readiness != null
    ) {
        PremiumConfirmDialog(
            title =
                "Request platform launch review?",
            message =
                buildReviewConfirmationMessage(
                    readiness
                ),
            confirmLabel = "Request review",
            destructive = false,
            icon =
                Icons.AutoMirrored.Filled
                    .FactCheck,
            onDismiss = {
                if (!state.saving) {
                    confirmReview = false
                }
            },
            onConfirm = {
                confirmReview = false
                viewModel.requestReview()
            }
        )
    }

    manualRollback?.let { check ->
        PremiumConfirmDialog(
            title =
                "Mark launch check incomplete?",
            message =
                check.title +
                    " is currently confirmed. Marking it incomplete will create a required launch blocker. A pending or previous platform review remains recorded, but the school will show as needing attention until the check passes again.",
            confirmLabel = "Mark incomplete",
            destructive = true,
            icon = Icons.Filled.WarningAmber,
            onDismiss = {
                if (!state.saving) {
                    manualRollback = null
                }
            },
            onConfirm = {
                manualRollback = null
                viewModel.setManualCheck(
                    check.key,
                    false
                )
            }
        )
    }

    // Terminal feedback stays last so it remains above confirmation dialogs.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                state.errorTitle
                    ?: "Readiness action not completed",
            onDismiss =
                viewModel::clearFeedback
        )
    }

    state.message?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                state.messageTitle
                    ?: "Launch readiness updated",
            onDismiss =
                viewModel::clearFeedback
        )
    }
}

@Composable
private fun ReadinessOverviewCard(
    readiness: LaunchReadinessResponse,
    progress: Float
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.md
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text =
                            "Configuration progress",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            (
                                progress * 100
                                ).toInt()
                                .toString() +
                                "% checks passing",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                ReadinessStatusBadge(
                    readiness =
                        readiness
                )
            }

            LinearProgressIndicator(
                progress = {
                    progress
                        .coerceIn(
                            0f,
                            1f
                        )
                },
                modifier =
                    Modifier.fillMaxWidth()
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm
                    )
            ) {
                StatusMetric(
                    label = "Passed",
                    value =
                        readiness.passedCount,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer,
                    modifier =
                        Modifier.weight(1f)
                )

                StatusMetric(
                    label = "Warnings",
                    value =
                        readiness.warningCount,
                    color =
                        MaterialTheme
                            .colorScheme
                            .tertiaryContainer,
                    modifier =
                        Modifier.weight(1f)
                )

                StatusMetric(
                    label = "Blockers",
                    value =
                        readiness.blockerCount,
                    color =
                        MaterialTheme
                            .colorScheme
                            .errorContainer,
                    modifier =
                        Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm
                    )
            ) {
                OperationalMetric(
                    label = "Students",
                    value =
                        readiness.activeStudents,
                    modifier =
                        Modifier.weight(1f)
                )

                OperationalMetric(
                    label = "Teachers",
                    value =
                        readiness.activeTeachers,
                    modifier =
                        Modifier.weight(1f)
                )

                OperationalMetric(
                    label = "Sections",
                    value =
                        readiness.activeSections,
                    modifier =
                        Modifier.weight(1f)
                )

                OperationalMetric(
                    label = "Gates",
                    value =
                        readiness.activeGates,
                    modifier =
                        Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReadinessStatusBadge(
    readiness: LaunchReadinessResponse
) {
    val scheme =
        MaterialTheme.colorScheme

    val container =
        when {
            readiness.readyForLaunch ->
                scheme.primaryContainer

            readiness.blockerCount > 0 ->
                scheme.errorContainer

            readiness.reviewStatus ==
                "review_requested" ->
                scheme.secondaryContainer

            else ->
                scheme.tertiaryContainer
        }

    val content =
        when {
            readiness.readyForLaunch ->
                scheme.onPrimaryContainer

            readiness.blockerCount > 0 ->
                scheme.onErrorContainer

            readiness.reviewStatus ==
                "review_requested" ->
                scheme.onSecondaryContainer

            else ->
                scheme.onTertiaryContainer
        }

    Surface(
        shape =
            MaterialTheme.shapes.small,
        color = container
    ) {
        Text(
            text =
                when {
                    readiness.readyForLaunch ->
                        "READY"

                    readiness.blockerCount >
                        0 ->
                        "BLOCKED"

                    readiness.reviewStatus ==
                        "review_requested" ->
                        "IN REVIEW"

                    else ->
                        "CONFIGURING"
                },
            modifier =
                Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 5.dp
                ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            fontWeight =
                FontWeight.ExtraBold,
            color = content
        )
    }
}

@Composable
private fun CurrentReviewStatusCard(
    readiness: LaunchReadinessResponse
) {
    val scheme =
        MaterialTheme.colorScheme

    val config =
        when {
            readiness.readyForLaunch ->
                Triple(
                    "Launch approved & ready",
                    "Platform approval is complete and all required checks currently pass.",
                    scheme.primaryContainer
                )

            readiness.launchApproved ->
                Triple(
                    "Approved · attention required",
                    "A previous platform approval remains recorded, but one or more required checks no longer pass. Resolve the blockers before relying on the launch approval.",
                    scheme.errorContainer
                )

            readiness.reviewStatus ==
                "review_requested" &&
                readiness.readyForReview ->
                Triple(
                    "Platform review requested",
                    "The school is currently ready for review. You may continue improving non-blocking recommendations while the platform owner reviews the tenant.",
                    scheme.secondaryContainer
                )

            readiness.reviewStatus ==
                "review_requested" ->
                Triple(
                    "Review requested · new blockers found",
                    "The review request remains recorded, but the current configuration now has required blockers. Resolve them before production launch.",
                    scheme.errorContainer
                )

            readiness.readyForReview ->
                Triple(
                    "Ready to request review",
                    "All required launch checks pass. Warnings are recommendations and do not block platform review.",
                    scheme.primaryContainer
                )

            else ->
                Triple(
                    "Setup still in progress",
                    "Resolve all required blockers below before requesting platform review.",
                    scheme.tertiaryContainer
                )
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        color = config.third
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.xs
                )
        ) {
            Text(
                text = config.first,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = config.second,
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }
    }
}

@Composable
private fun ReviewHistoryCard(
    readiness: LaunchReadinessResponse
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Text(
                text = "REVIEW HISTORY",
                style =
                    MaterialTheme.typography
                        .labelSmall,
                fontWeight =
                    FontWeight.ExtraBold,
                color =
                    MaterialTheme.colorScheme
                        .primary
            )

            readiness.reviewRequestedAt
                ?.let {
                    HistoryRow(
                        label =
                            "Review requested",
                        value =
                            formatReadinessTime(
                                it
                            )
                    )
                }

            readiness.approvedAt
                ?.let {
                    HistoryRow(
                        label =
                            "Approved",
                        value =
                            formatReadinessTime(
                                it
                            )
                    )
                }

            readiness.lastAssessedAt
                ?.let {
                    HistoryRow(
                        label =
                            "Last assessed",
                        value =
                            formatReadinessTime(
                                it
                            )
                    )
                }

            if (
                readiness.approvalNote
                    .isNotBlank()
            ) {
                HorizontalDivider()

                Text(
                    text =
                        "Platform review note",
                    style =
                        MaterialTheme.typography
                            .labelMedium,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        readiness.approvalNote,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography
                    .bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.width(Spacing.sm)
        )

        Text(
            text = value,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReadinessCheckCard(
    check: LaunchReadinessCheck,
    enabled: Boolean,
    onOpenAction: (String) -> Unit
) {
    val color =
        checkColor(check.status)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            color.copy(alpha = 0.20f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment =
                Alignment.Top
        ) {
            Surface(
                shape =
                    MaterialTheme.shapes
                        .medium,
                color =
                    color.copy(
                        alpha = 0.12f
                    )
            ) {
                Icon(
                    imageVector =
                        when (check.status) {
                            "pass" ->
                                Icons.Filled
                                    .CheckCircle

                            "warning" ->
                                Icons.Filled
                                    .WarningAmber

                            else ->
                                Icons.Filled
                                    .ErrorOutline
                        },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp)
                )
            }

            Spacer(
                Modifier.width(Spacing.sm)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.xs
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.Top
                ) {
                    Text(
                        text = check.label,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.weight(1f)
                    )

                    Spacer(
                        Modifier.width(
                            Spacing.xs
                        )
                    )

                    CheckStatusChip(
                        check = check
                    )
                }

                Text(
                    text = check.detail,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                if (
                    check.status != "pass"
                ) {
                    when (check.action) {
                        "platform_owner" -> {
                            Text(
                                text =
                                    "Platform owner action required.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }

                        "manual" -> Unit

                        else -> {
                            if (
                                check.action
                                    .isNotBlank()
                            ) {
                                TextButton(
                                    onClick = {
                                        onOpenAction(
                                            check.action
                                        )
                                    },
                                    enabled = enabled,
                                    contentPadding =
                                        PaddingValues(
                                            0.dp
                                        )
                                ) {
                                    Text(
                                        actionLabel(
                                            check.action
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckStatusChip(
    check: LaunchReadinessCheck
) {
    val scheme =
        MaterialTheme.colorScheme

    val container =
        when (check.status) {
            "pass" ->
                scheme.primaryContainer

            "warning" ->
                scheme.tertiaryContainer

            else ->
                scheme.errorContainer
        }

    val content =
        when (check.status) {
            "pass" ->
                scheme.onPrimaryContainer

            "warning" ->
                scheme.onTertiaryContainer

            else ->
                scheme.onErrorContainer
        }

    Surface(
        shape =
            MaterialTheme.shapes.small,
        color = container
    ) {
        Text(
            text =
                (
                    if (check.required) {
                        "REQUIRED · "
                    } else {
                        "RECOMMENDED · "
                    }
                    ) +
                    check.status
                        .replace('_', ' ')
                        .uppercase(),
            modifier =
                Modifier.padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
            style =
                MaterialTheme.typography
                    .labelSmall,
            fontWeight =
                FontWeight.Bold,
            color = content
        )
    }
}

@Composable
private fun ManualCheckCard(
    check: ManualLaunchCheck,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onChange: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment =
                Alignment.Top
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp),
                contentAlignment =
                    Alignment.Center
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Checkbox(
                        checked = checked,
                        onCheckedChange =
                            onChange,
                        enabled = enabled
                    )
                }
            }

            Spacer(
                Modifier.width(Spacing.sm)
            )

            Column(
                modifier =
                    Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.xs
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = check.title,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.weight(1f)
                    )

                    Surface(
                        shape =
                            MaterialTheme.shapes
                                .small,
                        color =
                            if (checked) {
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .errorContainer
                            }
                    ) {
                        Text(
                            text =
                                if (checked) {
                                    "CONFIRMED"
                                } else {
                                    "REQUIRED"
                                },
                            modifier =
                                Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 4.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (checked) {
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onErrorContainer
                                }
                        )
                    }
                }

                Text(
                    text = check.detail,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Text(
                    text =
                        "Confirm only after this has been verified on-site.",
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LaunchGovernanceCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme
                .outlineVariant
        )
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme
                            .primary,
                    modifier =
                        Modifier.size(20.dp)
                )

                Spacer(
                    Modifier.width(Spacing.sm)
                )

                Text(
                    text = "Launch governance",
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            GovernancePoint(
                "School admins complete tenant configuration and on-site verification."
            )

            GovernancePoint(
                "Required blockers must be zero before a platform review can be requested."
            )

            GovernancePoint(
                "Warnings are recommendations and do not block review or launch."
            )

            GovernancePoint(
                "Only the PickupPass platform owner can grant final production approval."
            )

            GovernancePoint(
                "A later configuration change can create a blocker even after review or approval; PickupPass reports that as attention required without deleting the historical review decision."
            )
        }
    }
}

@Composable
private fun GovernancePoint(
    text: String
) {
    Row(
        verticalAlignment =
            Alignment.Top
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .primary,
            modifier =
                Modifier.size(18.dp)
        )

        Spacer(
            Modifier.width(Spacing.sm)
        )

        Text(
            text = text,
            style =
                MaterialTheme.typography
                    .bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun StatusMetric(
    label: String,
    value: Long,
    color: Color,
    modifier: Modifier
) {
    Surface(
        shape =
            MaterialTheme.shapes.medium,
        color = color,
        modifier = modifier
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.sm)
        ) {
            Text(
                text = value.toString(),
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun OperationalMetric(
    label: String,
    value: Long,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style =
                MaterialTheme.typography
                    .titleMedium,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text = label,
            style =
                MaterialTheme.typography
                    .labelSmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun LaunchReadinessUnavailableCard(
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme
                        .error,
                modifier =
                    Modifier.size(30.dp)
            )

            Text(
                text =
                    "Readiness data unavailable",
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    "Pull down to retry the launch assessment.",
                style =
                    MaterialTheme.typography
                        .bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun checkColor(
    status: String
): Color =
    when (status) {
        "pass" ->
            MaterialTheme.colorScheme
                .primary

        "warning" ->
            MaterialTheme.colorScheme
                .tertiary

        else ->
            MaterialTheme.colorScheme
                .error
    }

private fun statusLabel(
    status: String
): String =
    when (status) {
        "approved" ->
            "Launch Approved"

        "approved_needs_attention" ->
            "Approved · Attention Required"

        "review_requested" ->
            "Awaiting Platform Review"

        "review_requested_needs_attention" ->
            "Review Requested · New Blockers"

        else ->
            "Setup in Progress"
    }

private fun readinessHeroMessage(
    readiness: LaunchReadinessResponse
): String =
    when {
        readiness.readyForLaunch ->
            "The school has platform approval and all required production checks currently pass."

        readiness.launchApproved ->
            "Platform approval remains recorded, but current configuration has required blockers that must be resolved before production use."

        readiness.reviewStatus ==
            "review_requested" &&
            readiness.readyForReview ->
            "The school is ready and awaiting platform-owner review. Continue monitoring readiness until final approval."

        readiness.reviewStatus ==
            "review_requested" ->
            "A review request is recorded, but new required blockers were detected after submission."

        readiness.readyForReview ->
            "All required checks pass. The school can now request platform-owner review."

        else ->
            "Complete the required configuration and on-site checks below before requesting production review."
    }

private fun reviewActionLabel(
    readiness: LaunchReadinessResponse
): String =
    when {
        readiness.launchApproved ->
            "Launch approved"

        readiness.reviewStatus ==
            "review_requested" ->
            "Review requested"

        readiness.readyForReview ->
            "Request platform review"

        else ->
            "Complete required checks"
    }

private fun buildReviewConfirmationMessage(
    readiness: LaunchReadinessResponse
): String {
    val warningText =
        if (readiness.warningCount > 0) {
            " " +
                readiness.warningCount +
                " recommendation" +
                if (
                    readiness.warningCount ==
                    1L
                ) {
                    " remains, but it does not block review."
                } else {
                    "s remain, but they do not block review."
                }
        } else {
            ""
        }

    return "Submit the current school configuration and confirmed on-site checks to the PickupPass platform owner for production review." +
        warningText +
        " Final approval can only be granted by the platform owner, and later configuration changes can introduce new blockers."
}

private fun actionLabel(
    action: String
): String =
    when (action) {
        "academic" ->
            "Open Academic Structure"

        "students" ->
            "Open Student Roster"

        "guardians" ->
            "Open Student Guardians"

        "staff" ->
            "Open Staff Management"

        "pickup_policy" ->
            "Open Pickup Policy"

        "campus_gates" ->
            "Open Campus Gates"

        "branding" ->
            "Open School Branding"

        "billing" ->
            "Open Billing & Subscription"

        else ->
            "Open setup"
    }

private fun formatReadinessTime(
    value: String
): String =
    runCatching {
        Instant.parse(value)
            .atZone(
                ZoneId.systemDefault()
            )
            .format(
                READINESS_TIME_FORMAT
            )
    }.getOrElse {
        value
    }

private data class ManualLaunchCheck(
    val key: String,
    val title: String,
    val detail: String
)

private val MANUAL_LAUNCH_CHECKS =
    listOf(
        ManualLaunchCheck(
            key =
                "scannerDeviceTested",
            title =
                "Scanner device tested",
            detail =
                "Test sign-in, camera permission, QR recognition, network connection, gate selection, and approval on the actual dismissal device."
        ),
        ManualLaunchCheck(
            key =
                "guardianQrTested",
            title =
                "Guardian QR flow tested end-to-end",
            detail =
                "Generate a controlled guardian QR and confirm staff can verify and approve it successfully."
        ),
        ManualLaunchCheck(
            key =
                "dismissalStaffBriefed",
            title =
                "Dismissal staff briefed",
            detail =
                "Staff understand QR release, duplicate-pass behavior, guardian identity checks, escalation, and gate selection."
        ),
        ManualLaunchCheck(
            key =
                "emergencyProcedureReviewed",
            title =
                "Emergency / fallback procedure reviewed",
            detail =
                "The school has a documented response for device or network problems and knows when audited Manual Release may be used."
        )
    )

private val READINESS_TIME_FORMAT:
    DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "MMM d, yyyy · h:mm a"
    )
