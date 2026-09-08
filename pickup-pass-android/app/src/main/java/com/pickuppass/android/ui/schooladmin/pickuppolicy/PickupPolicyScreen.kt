package com.pickuppass.android.ui.schooladmin.pickuppolicy

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupPolicyScreen(
    viewModel: PickupPolicyViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmSave by remember {
        mutableStateOf(false)
    }

    val interactionEnabled =
        !state.isSaving &&
            !state.isRefreshing

    val timeWindowValid = remember(
        state.restrictedToTimeWindow,
        state.startTime,
        state.endTime
    ) {
        if (!state.restrictedToTimeWindow) {
            true
        } else {
            val start = parsePolicyTime(state.startTime)
            val end = parsePolicyTime(state.endTime)
            start != null &&
                end != null &&
                start.isBefore(end)
        }
    }

    fun showTimePicker(
        current: String,
        onSelected: (String) -> Unit
    ) {
        val fallback = LocalTime.of(14, 0)
        val initial =
            parsePolicyTime(current) ?: fallback

        TimePickerDialog(
            context,
            { _, hour, minute ->
                onSelected(
                    LocalTime.of(hour, minute)
                        .format(POLICY_TIME_FORMATTER)
                )
            },
            initial.hour,
            initial.minute,
            DateFormat.is24HourFormat(context)
        ).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Pickup policy",
                subtitle = "Release rules & fallback controls",
                onBack = onBack,
            )
        }
    ) { padding ->
        if (state.isLoading) {
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
            refreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            enabled =
                !state.isSaving &&
                    !state.isDirty,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 780.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    item(key = "hero") {
                        PremiumHeroCard(
                            eyebrow = "Dismissal governance",
                            title = "School-wide pickup rules",
                            message = "Define when valid QR passes may be approved and whether school administrators can use the audited manual-release fallback.",
                            icon = Icons.Filled.Security
                        )
                    }

                    item(key = "current-summary") {
                        PolicySummaryCard(
                            isDirty = state.isDirty,
                            restricted =
                                state.restrictedToTimeWindow,
                            startTime = state.startTime,
                            endTime = state.endTime,
                            timeZone = state.timeZone,
                            manualOverride =
                                state.allowManualOverride
                        )
                    }

                    if (state.isDirty) {
                        item(key = "unsaved") {
                            UnsavedPolicyCard(
                                onReset = viewModel::resetChanges
                            )
                        }
                    }

                    item(key = "availability-header") {
                        PremiumSectionHeader(
                            title = "QR pickup availability",
                            subtitle =
                                "Choose when a currently valid PickupPass QR may authorize release."
                        )
                    }

                    item(key = "unrestricted") {
                        PolicyOption(
                            selected =
                                !state.restrictedToTimeWindow,
                            enabled = interactionEnabled,
                            title = "Unrestricted",
                            description =
                                "Authorized guardians may use a valid QR whenever it is still active and unused.",
                            onClick = {
                                viewModel.setRestricted(false)
                            }
                        )
                    }

                    item(key = "time-window") {
                        PolicyOption(
                            selected =
                                state.restrictedToTimeWindow,
                            enabled = interactionEnabled,
                            title = "School pickup time window",
                            description =
                                "QR approval is allowed only between the configured start and end times.",
                            onClick = {
                                viewModel.setRestricted(true)
                            }
                        )
                    }

                    if (state.restrictedToTimeWindow) {
                        item(key = "window-editor") {
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
                                    PremiumSectionHeader(
                                        title = "Dismissal window",
                                        subtitle =
                                            "Times are enforced using " +
                                                state.timeZone +
                                                "."
                                    )

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                Spacing.sm
                                            )
                                    ) {
                                        TimePolicyButton(
                                            label = "Starts",
                                            value = state.startTime,
                                            enabled =
                                                interactionEnabled,
                                            modifier =
                                                Modifier.weight(1f),
                                            onClick = {
                                                showTimePicker(
                                                    state.startTime,
                                                    viewModel::setStartTime
                                                )
                                            }
                                        )

                                        TimePolicyButton(
                                            label = "Ends",
                                            value = state.endTime,
                                            enabled =
                                                interactionEnabled,
                                            modifier =
                                                Modifier.weight(1f),
                                            onClick = {
                                                showTimePicker(
                                                    state.endTime,
                                                    viewModel::setEndTime
                                                )
                                            }
                                        )
                                    }

                                    if (!timeWindowValid) {
                                        Surface(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            shape =
                                                MaterialTheme.shapes
                                                    .medium,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .errorContainer
                                        ) {
                                            Text(
                                                text =
                                                    "Pickup start time must be earlier than the end time.",
                                                modifier =
                                                    Modifier.padding(
                                                        Spacing.sm
                                                    ),
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .bodySmall,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onErrorContainer
                                            )
                                        }
                                    } else {
                                        Text(
                                            text =
                                                "QR approvals outside " +
                                                    state.startTime +
                                                    "–" +
                                                    state.endTime +
                                                    " will be blocked.",
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
                    }

                    item(key = "fallback-header") {
                        PremiumSectionHeader(
                            title = "Administrative fallback",
                            subtitle =
                                "Control whether school administrators may document an exception when normal QR pickup cannot be completed."
                        )
                    }

                    item(key = "manual-override") {
                        ManualOverrideCard(
                            enabled = interactionEnabled,
                            allowManualOverride =
                                state.allowManualOverride,
                            onChange =
                                viewModel::setManualOverride
                        )
                    }

                    if (!state.allowManualOverride) {
                        item(key = "manual-warning") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape =
                                    MaterialTheme.shapes.large,
                                color =
                                    MaterialTheme.colorScheme
                                        .tertiaryContainer
                            ) {
                                Row(
                                    modifier =
                                        Modifier.padding(Spacing.md),
                                    verticalAlignment =
                                        Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = null,
                                        tint =
                                            MaterialTheme.colorScheme
                                                .onTertiaryContainer,
                                        modifier =
                                            Modifier.size(18.dp)
                                    )
                                    Spacer(
                                        Modifier.width(Spacing.sm)
                                    )
                                    Column {
                                        Text(
                                            text =
                                                "Manual Release will be unavailable",
                                            fontWeight =
                                                FontWeight.Bold,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onTertiaryContainer
                                        )
                                        Text(
                                            text =
                                                "School admins will need to complete pickup through the normal QR flow. Existing dismissal history remains unchanged.",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "save") {
                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xs),
                            horizontalArrangement =
                                Arrangement.End,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    confirmSave = true
                                },
                                enabled =
                                    state.isDirty &&
                                        timeWindowValid &&
                                        !state.isSaving &&
                                        !state.isRefreshing,
                                modifier =
                                    Modifier
                                        .widthIn(
                                            min = 190.dp,
                                            max = 320.dp
                                        )
                                        .heightIn(min = 50.dp)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(
                                        Modifier.width(
                                            Spacing.xs
                                        )
                                    )
                                    Text("Saving…")
                                } else {
                                    Text("Review & save policy")
                                }
                            }
                        }

                        if (!state.isDirty) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text =
                                    "No unsaved policy changes.",
                                style =
                                    MaterialTheme.typography
                                        .labelSmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                modifier =
                                    Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmSave) {
        val confirmationMessage =
            buildPolicyConfirmationMessage(
                restricted =
                    state.restrictedToTimeWindow,
                startTime = state.startTime,
                endTime = state.endTime,
                timeZone = state.timeZone,
                manualOverride =
                    state.allowManualOverride
            )

        PremiumConfirmDialog(
            title = "Apply pickup policy?",
            message = confirmationMessage,
            confirmLabel = "Apply policy",
            destructive =
                !state.allowManualOverride,
            icon = Icons.Filled.Security,
            onDismiss = {
                if (!state.isSaving) {
                    confirmSave = false
                }
            },
            onConfirm = {
                confirmSave = false
                viewModel.save()
            }
        )
    }

    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                state.errorTitle
                    ?: "Policy action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.successMessage?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                state.successTitle
                    ?: "Pickup policy updated",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun PolicySummaryCard(
    isDirty: Boolean,
    restricted: Boolean,
    startTime: String,
    endTime: String,
    timeZone: String,
    manualOverride: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            scheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = if (isDirty) "POLICY PREVIEW" else "CURRENT POLICY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.primary
            )

            Text(
                text = if (restricted) {
                    "QR pickup allowed " +
                        startTime +
                        "–" +
                        endTime
                } else {
                    "QR pickup unrestricted by school hours"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (manualOverride) {
                        "Audited manual release is available to school admins."
                    } else {
                        "Manual release is disabled."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

            if (restricted) {
                Text(
                    text = "Time zone: " + timeZone,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnsavedPolicyCard(
    onReset: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Unsaved policy changes",
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSecondaryContainer
                )
                Text(
                    text =
                        "Pull-to-refresh is paused so your edits are not overwritten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            TextButton(
                onClick = onReset
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun PolicyOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
                    .copy(alpha = 0.26f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick
            )
            Spacer(Modifier.width(Spacing.xs))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimePolicyButton(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        )
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ManualOverrideCard(
    enabled: Boolean,
    allowManualOverride: Boolean,
    onChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color =
                    if (allowManualOverride) {
                        scheme.primaryContainer
                    } else {
                        scheme.surfaceVariant
                    },
                contentColor =
                    if (allowManualOverride) {
                        scheme.onPrimaryContainer
                    } else {
                        scheme.onSurfaceVariant
                    }
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Allow manual pickup override",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text =
                        if (allowManualOverride) {
                            "School admins may use the audited Manual Release flow when QR pickup cannot be completed."
                        } else {
                            "Manual Release is disabled. Pickup must use the normal QR authorization flow."
                        },
                    style =
                        MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Switch(
                checked = allowManualOverride,
                onCheckedChange = onChange,
                enabled = enabled
            )
        }
    }
}

private fun buildPolicyConfirmationMessage(
    restricted: Boolean,
    startTime: String,
    endTime: String,
    timeZone: String,
    manualOverride: Boolean
): String {
    val qrRule =
        if (restricted) {
            "QR approvals will be allowed only from " +
                startTime +
                " to " +
                endTime +
                " (" +
                timeZone +
                ")."
        } else {
            "Valid QR approvals will not be restricted by school hours."
        }

    val fallbackRule =
        if (manualOverride) {
            " Audited Manual Release remains available to school admins."
        } else {
            " Manual Release will be disabled for school admins."
        }

    return qrRule + fallbackRule
}

private fun parsePolicyTime(
    value: String
): LocalTime? =
    runCatching {
        LocalTime.parse(
            value,
            POLICY_TIME_FORMATTER
        )
    }.getOrNull()

private val POLICY_TIME_FORMATTER:
    DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")
