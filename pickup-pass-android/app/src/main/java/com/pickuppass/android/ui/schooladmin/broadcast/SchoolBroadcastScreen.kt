package com.pickuppass.android.ui.schooladmin.broadcast

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.BroadcastHistoryItem
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolBroadcastScreen(
    viewModel: SchoolBroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var scheduleMode by rememberSaveable { mutableStateOf(false) }
    var scheduledDateTime by remember { mutableStateOf<ZonedDateTime?>(null) }
    var confirmDelivery by remember { mutableStateOf(false) }
    var pendingCancel by remember {
        mutableStateOf<BroadcastHistoryItem?>(null)
    }

    LaunchedEffect(uiState.schedulingEnabled) {
        if (!uiState.schedulingEnabled) {
            scheduleMode = false
            scheduledDateTime = null
        }
    }

    LaunchedEffect(uiState.composerResetToken) {
        if (uiState.composerResetToken > 0) {
            title = ""
            body = ""
            scheduledDateTime = null
            scheduleMode = false
            confirmDelivery = false
        }
    }

    fun chooseScheduleTime() {
        val now = ZonedDateTime.now()
        val initial = scheduledDateTime ?: now.plusMinutes(10)

        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        scheduledDateTime = ZonedDateTime.of(
                            year,
                            month + 1,
                            day,
                            hour,
                            minute,
                            0,
                            0,
                            ZoneId.systemDefault()
                        )
                        viewModel.clearFeedback()
                    },
                    initial.hour,
                    initial.minute,
                    false
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            datePicker.maxDate =
                System.currentTimeMillis() +
                    Duration.ofDays(90).toMillis()
        }.show()
    }

    val selectedAudience = buildList {
        if (uiState.includeTeachers) add("Teachers")
        if (uiState.includeParents) add("Guardians")
    }

    val scheduleValidation = remember(
        scheduleMode,
        scheduledDateTime
    ) {
        if (!scheduleMode) {
            null
        } else {
            val scheduled = scheduledDateTime?.toInstant()
            val now = Instant.now()

            when {
                scheduled == null ->
                    "Choose a delivery date and time."

                scheduled.isBefore(
                    now.plus(Duration.ofSeconds(30))
                ) ->
                    "Choose a time at least 30 seconds in the future."

                scheduled.isAfter(
                    now.plus(Duration.ofDays(90))
                ) ->
                    "Choose a time within the next 90 days."

                else -> null
            }
        }
    }

    val formReady =
        title.isNotBlank() &&
            body.isNotBlank() &&
            title.trim().length <= 120 &&
            body.trim().length <= 2000 &&
            selectedAudience.isNotEmpty() &&
            scheduleValidation == null

    val interactionEnabled =
        !uiState.isSubmitting &&
            uiState.cancellingId == null &&
            !uiState.isRefreshingHistory

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Announcements",
                subtitle = "School-wide communication",
                onBack = onBack,
            )
        }
    ) { padding ->
        PickupPassPullToRefresh(
            refreshing = uiState.isRefreshingHistory,
            onRefresh = viewModel::refreshHistory,
            enabled =
                !uiState.isSubmitting &&
                    uiState.cancellingId == null,
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
                        .widthIn(max = 820.dp)
                        .align(Alignment.TopCenter)
                        .imePadding(),
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
                            eyebrow = "School communication",
                            title = "Reach your school community",
                            message = if (uiState.schedulingEnabled) {
                                "Send immediate announcements or schedule push and inbox delivery for teachers, guardians, or both."
                            } else {
                                "Send immediate push and inbox announcements to teachers, guardians, or both. Scheduled delivery is not enabled on this plan."
                            },
                            icon = Icons.Filled.Campaign
                        )
                    }

                    item(key = "composer") {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement =
                                    Arrangement.spacedBy(Spacing.md)
                            ) {
                                PremiumSectionHeader(
                                    title = "Compose announcement",
                                    subtitle =
                                        "Write a clear message, choose recipients, then review before delivery."
                                )

                                OutlinedTextField(
                                    value = title,
                                    onValueChange = {
                                        title = it.take(120)
                                        viewModel.clearFeedback()
                                    },
                                    label = {
                                        Text("Title")
                                    },
                                    placeholder = {
                                        Text("e.g. Early dismissal reminder")
                                    },
                                    supportingText = {
                                        Text(
                                            title.length.toString() +
                                                "/120"
                                        )
                                    },
                                    singleLine = true,
                                    enabled = interactionEnabled,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = body,
                                    onValueChange = {
                                        body = it.take(2000)
                                        viewModel.clearFeedback()
                                    },
                                    label = {
                                        Text("Message")
                                    },
                                    placeholder = {
                                        Text(
                                            "Share the information families and staff need to know."
                                        )
                                    },
                                    supportingText = {
                                        Text(
                                            body.length.toString() +
                                                "/2000"
                                        )
                                    },
                                    minLines = 5,
                                    maxLines = 10,
                                    enabled = interactionEnabled,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(
                                    verticalArrangement =
                                        Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        text = "Audience",
                                        style =
                                            MaterialTheme.typography
                                                .titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text =
                                            "Select at least one recipient group.",
                                        style =
                                            MaterialTheme.typography
                                                .bodySmall,
                                        color =
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme
                                            .outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        AudienceRow(
                                            label = "Teachers",
                                            description =
                                                "Teaching and school staff accounts",
                                            checked =
                                                uiState.includeTeachers,
                                            enabled = interactionEnabled,
                                            onChange =
                                                viewModel::setIncludeTeachers
                                        )

                                        HorizontalDivider()

                                        AudienceRow(
                                            label = "Guardians",
                                            description =
                                                "Parent and guardian accounts",
                                            checked =
                                                uiState.includeParents,
                                            enabled = interactionEnabled,
                                            onChange =
                                                viewModel::setIncludeParents
                                        )
                                    }
                                }

                                if (selectedAudience.isEmpty()) {
                                    Text(
                                        text =
                                            "Select Teachers, Guardians, or both.",
                                        style =
                                            MaterialTheme.typography
                                                .bodySmall,
                                        color =
                                            MaterialTheme.colorScheme
                                                .error
                                    )
                                }

                                Column(
                                    verticalArrangement =
                                        Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        text = "Delivery",
                                        style =
                                            MaterialTheme.typography
                                                .titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (
                                            uiState.schedulingEnabled
                                        ) {
                                            "Send now or choose a future time up to 90 days ahead."
                                        } else {
                                            "Immediate delivery is available on the current plan."
                                        },
                                        style =
                                            MaterialTheme.typography
                                                .bodySmall,
                                        color =
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    DeliveryOption(
                                        title = "Send now",
                                        subtitle = "Immediate delivery",
                                        selected = !scheduleMode,
                                        enabled = interactionEnabled,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            scheduleMode = false
                                            scheduledDateTime = null
                                            viewModel.clearFeedback()
                                        }
                                    )

                                    if (uiState.schedulingEnabled) {
                                        DeliveryOption(
                                            title = "Schedule",
                                            subtitle =
                                                scheduledDateTime
                                                    ?.let(
                                                        ::formatLocalDateTime
                                                    )
                                                    ?: "Choose later",
                                            selected = scheduleMode,
                                            enabled = interactionEnabled,
                                            modifier =
                                                Modifier.weight(1f),
                                            onClick = {
                                                scheduleMode = true
                                                viewModel.clearFeedback()
                                            }
                                        )
                                    }
                                }

                                if (scheduleMode) {
                                    OutlinedButton(
                                        onClick = ::chooseScheduleTime,
                                        enabled = interactionEnabled,
                                        modifier = Modifier.heightIn(
                                            min = 48.dp
                                        )
                                    ) {
                                        Icon(
                                            Icons.Filled.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(
                                            Modifier.width(Spacing.xs)
                                        )
                                        Text(
                                            scheduledDateTime
                                                ?.let(
                                                    ::formatLocalDateTime
                                                )
                                                ?: "Choose date and time"
                                        )
                                    }

                                    Text(
                                        text =
                                            scheduleValidation
                                                ?: "Scheduled delivery must be at least 30 seconds from now and within 90 days.",
                                        style =
                                            MaterialTheme.typography
                                                .bodySmall,
                                        color = if (
                                            scheduleValidation != null &&
                                            scheduledDateTime != null
                                        ) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                        }
                                    )
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            confirmDelivery = true
                                        },
                                        enabled =
                                            formReady &&
                                                !uiState.isSubmitting &&
                                                uiState.cancellingId == null,
                                        modifier = Modifier
                                            .widthIn(
                                                min = 180.dp,
                                                max = 320.dp
                                            )
                                            .heightIn(min = 50.dp)
                                    ) {
                                        if (uiState.isSubmitting) {
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
                                        }

                                        Text(
                                            if (scheduleMode) {
                                                "Review schedule"
                                            } else {
                                                "Review & send"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "history-header") {
                        PremiumSectionHeader(
                            title = "Recent announcements",
                            subtitle =
                                "Scheduled announcements can be cancelled until delivery begins."
                        )

                        if (uiState.isLoadingHistory) {
                            Spacer(Modifier.height(Spacing.sm))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (
                        !uiState.isLoadingHistory &&
                        uiState.history.isEmpty()
                    ) {
                        item(key = "history-empty") {
                            BroadcastEmptyState()
                        }
                    } else {
                        items(
                            items = uiState.history,
                            key = { it.id }
                        ) { historyItem ->
                            BroadcastHistoryCard(
                                item = historyItem,
                                isCancelling =
                                    uiState.cancellingId ==
                                        historyItem.id,
                                onCancel = {
                                    pendingCancel = historyItem
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelivery) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSubmitting) {
                    confirmDelivery = false
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    if (scheduleMode) {
                        "Schedule announcement?"
                    } else {
                        "Send announcement now?"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = title.trim(),
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = body.trim(),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    HorizontalDivider()

                    ReviewDetail(
                        label = "Audience",
                        value = selectedAudience.joinToString()
                    )

                    ReviewDetail(
                        label = "Delivery",
                        value = if (scheduleMode) {
                            scheduledDateTime
                                ?.let(::formatLocalDateTime)
                                ?: "Not selected"
                        } else {
                            "Immediately"
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled =
                        !uiState.isSubmitting &&
                            formReady,
                    onClick = {
                        if (scheduleMode) {
                            viewModel.schedule(
                                title = title,
                                body = body,
                                scheduledAtUtc =
                                    scheduledDateTime
                                        ?.toInstant()
                                        ?.toString()
                            )
                        } else {
                            viewModel.send(
                                title = title,
                                body = body
                            )
                        }
                    }
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(
                            Modifier.width(Spacing.xs)
                        )
                    }
                    Text(
                        if (scheduleMode) {
                            "Schedule"
                        } else {
                            "Send now"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDelivery = false
                    },
                    enabled = !uiState.isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingCancel?.let { item ->
        PremiumConfirmDialog(
            title = "Cancel scheduled announcement?",
            message =
                "This announcement will be removed from the delivery queue. It cannot be restored after cancellation.",
            confirmLabel = "Cancel announcement",
            destructive = true,
            icon = Icons.Filled.Campaign,
            onDismiss = {
                if (uiState.cancellingId == null) {
                    pendingCancel = null
                }
            },
            onConfirm = {
                pendingCancel = null
                viewModel.cancel(item)
            }
        )
    }

    // Keep terminal feedback last so it stays above confirmation dialogs.
    uiState.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                uiState.errorTitle
                    ?: "Announcement action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    uiState.successMessage?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                uiState.successTitle
                    ?: "Announcement updated",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun AudienceRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled
            ) {
                onChange(!checked)
            }
            .padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            )
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange
        )

        Spacer(Modifier.width(Spacing.xs))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeliveryOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
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
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
                .copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = modifier.clickable(
            enabled = enabled,
            onClick = onClick
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReviewDetail(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BroadcastHistoryCard(
    item: BroadcastHistoryItem,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title,
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.audience.joinToString {
                            if (it == "parent") {
                                "Guardians"
                            } else {
                                "Teachers"
                            }
                        },
                        style =
                            MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(Spacing.sm))
                BroadcastStatusBadge(item.status)
            }

            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = scheme.outlineVariant)

            when (item.status.lowercase()) {
                "scheduled" -> {
                    item.scheduledAt?.let {
                        Text(
                            text =
                                "Scheduled for " +
                                    formatInstant(it),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                "sent" -> {
                    val timestamp = item.sentAt?.let {
                        " · " + formatInstant(it)
                    } ?: ""

                    Text(
                        text =
                            "Sent" +
                                timestamp +
                                " · " +
                                item.recipientCount +
                                " recipient" +
                                (
                                    if (item.recipientCount == 1) {
                                        ""
                                    } else {
                                        "s"
                                    }
                                    ),
                        style =
                            MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                "failed" -> {
                    Text(
                        text =
                            "Delivery failed" +
                                (
                                    item.errorMessage?.let {
                                        ": " + it
                                    } ?: ""
                                    ),
                        style =
                            MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                }

                "cancelled" -> {
                    Text(
                        text =
                            "This scheduled announcement was cancelled.",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (item.status.equals("scheduled", ignoreCase = true)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "Cancelling…",
                            style =
                                MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(
                            onClick = onCancel
                        ) {
                            Text("Cancel scheduled announcement")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastStatusBadge(
    status: String
) {
    val scheme = MaterialTheme.colorScheme
    val normalized = status.lowercase()

    val container = when (normalized) {
        "sent" -> scheme.primaryContainer
        "scheduled" -> scheme.tertiaryContainer
        "failed" -> scheme.errorContainer
        else -> scheme.surfaceVariant
    }

    val content = when (normalized) {
        "sent" -> scheme.onPrimaryContainer
        "scheduled" -> scheme.onTertiaryContainer
        "failed" -> scheme.onErrorContainer
        else -> scheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Text(
            text = status
                .ifBlank { "unknown" }
                .uppercase(),
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun BroadcastEmptyState() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = "No announcements yet",
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Sent and scheduled school announcements will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatLocalDateTime(
    value: ZonedDateTime
): String =
    value.format(
        DateTimeFormatter.ofPattern(
            "MMM d, yyyy · h:mm a"
        )
    )

private fun formatInstant(
    value: String
): String = try {
    Instant.parse(value)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                "MMM d, yyyy · h:mm a"
            )
        )
} catch (_: Exception) {
    value
}
