package com.pickuppass.android.ui.schooladmin.broadcast

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.BroadcastHistoryItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing
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
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var scheduleMode by remember { mutableStateOf(false) }
    var scheduledDateTime by remember { mutableStateOf<ZonedDateTime?>(null) }
    var confirmDelivery by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.schedulingEnabled) {
        if (!uiState.schedulingEnabled) scheduleMode = false
    }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage == "Announcement scheduled." || uiState.successMessage?.startsWith("Sent to") == true) {
            title = ""
            body = ""
            scheduledDateTime = null
        }
    }

    fun chooseScheduleTime() {
        val initial = scheduledDateTime ?: ZonedDateTime.now().plusMinutes(10)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        scheduledDateTime = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault())
                        viewModel.clearMessage()
                    },
                    initial.hour,
                    initial.minute,
                    false
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
    }

    val audienceCount = (if (uiState.includeTeachers) 1 else 0) + (if (uiState.includeParents) 1 else 0)
    val formReady = title.isNotBlank() &&
        body.isNotBlank() &&
        title.length <= 120 &&
        body.length <= 2000 &&
        audienceCount > 0 &&
        (!scheduleMode || scheduledDateTime != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Announcements", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "School-wide communication",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshHistory, enabled = !uiState.isLoadingHistory) {
                        Icon(Icons.Filled.Refresh, "Refresh history")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 820.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Campaign, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text("Reach your school community", fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.schedulingEnabled)
                                        "Send immediately or schedule a push notification and inbox announcement."
                                    else
                                        "Send an immediate push notification and inbox announcement. Scheduling is unavailable on this plan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("Compose announcement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(120); viewModel.clearMessage() },
                                label = { Text("Title") },
                                supportingText = { Text("${title.length}/120") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it.take(2000); viewModel.clearMessage() },
                                label = { Text("Message") },
                                supportingText = { Text("${body.length}/2000") },
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Audience", fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    AudienceRow("Teachers", uiState.includeTeachers) { viewModel.setIncludeTeachers(it) }
                                    HorizontalDivider()
                                    AudienceRow("Guardians", uiState.includeParents) { viewModel.setIncludeParents(it) }
                                }
                            }

                            Text("Delivery", fontWeight = FontWeight.SemiBold)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                DeliveryOption(
                                    title = "Send now",
                                    subtitle = "Immediate delivery",
                                    selected = !scheduleMode,
                                    modifier = Modifier.weight(1f),
                                    onClick = { scheduleMode = false; viewModel.clearMessage() }
                                )
                                if (uiState.schedulingEnabled) {
                                    DeliveryOption(
                                        title = "Schedule",
                                        subtitle = scheduledDateTime?.let(::formatLocalDateTime) ?: "Choose later",
                                        selected = scheduleMode,
                                        modifier = Modifier.weight(1f),
                                        onClick = { scheduleMode = true; viewModel.clearMessage() }
                                    )
                                }
                            }

                            if (scheduleMode) {
                                OutlinedButton(onClick = ::chooseScheduleTime, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(scheduledDateTime?.let(::formatLocalDateTime) ?: "Choose date and time")
                                }
                            }

                            AnimatedVisibility(uiState.error != null, enter = fadeIn() + expandVertically()) {
                                uiState.error?.let { ErrorBanner(it) }
                            }
                            AnimatedVisibility(uiState.successMessage != null, enter = fadeIn() + expandVertically()) {
                                uiState.successMessage?.let { SuccessBanner(it) }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = { confirmDelivery = true },
                                    enabled = formReady && !uiState.isSubmitting,
                                    modifier = Modifier.widthIn(min = 190.dp, max = 340.dp).height(52.dp)
                                ) {
                                    if (uiState.isSubmitting) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(Spacing.xs))
                                    }
                                    Text(if (scheduleMode) "Review schedule" else "Review & send")
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Recent announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Scheduled announcements can be cancelled until sending begins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.isLoadingHistory) {
                        Spacer(Modifier.height(Spacing.sm))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                if (!uiState.isLoadingHistory && uiState.history.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Text(
                                "No announcements yet.",
                                modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(uiState.history, key = { it.id }) { item ->
                    BroadcastHistoryCard(
                        item = item,
                        isCancelling = uiState.cancellingId == item.id,
                        onCancel = { viewModel.cancel(item.id) }
                    )
                }
            }
        }
    }

    if (confirmDelivery) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isSubmitting) confirmDelivery = false },
            icon = { Icon(Icons.Filled.Campaign, null) },
            title = { Text(if (scheduleMode) "Schedule announcement?" else "Send announcement now?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(body, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    HorizontalDivider()
                    Text(
                        "Audience: ${buildList { if (uiState.includeTeachers) add("Teachers"); if (uiState.includeParents) add("Guardians") }.joinToString()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (scheduleMode) "Delivery: ${scheduledDateTime?.let(::formatLocalDateTime)}"
                        else "Delivery: Immediately",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !uiState.isSubmitting,
                    onClick = {
                        confirmDelivery = false
                        if (scheduleMode) {
                            viewModel.schedule(title, body, scheduledDateTime?.toInstant()?.toString())
                        } else {
                            viewModel.send(title, body)
                        }
                    }
                ) { Text(if (scheduleMode) "Schedule" else "Send now") }
            },
            dismissButton = { TextButton(onClick = { confirmDelivery = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AudienceRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeliveryOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .25f)
        else MaterialTheme.colorScheme.surface,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BroadcastHistoryCard(
    item: BroadcastHistoryItem,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AssistChip(onClick = {}, enabled = false, label = { Text(item.status.ifBlank { "unknown" }.uppercase()) })
            }
            Text(item.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "Audience: ${item.audience.joinToString { if (it == "parent") "Guardians" else "Teachers" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (item.status) {
                "scheduled" -> item.scheduledAt?.let { Text("Scheduled: ${formatInstant(it)}", style = MaterialTheme.typography.bodySmall) }
                "sent" -> Text(
                    "Sent${item.sentAt?.let { " ${formatInstant(it)}" } ?: ""} · ${item.recipientCount} recipient${if (item.recipientCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
                "failed" -> Text(
                    "Failed${item.errorMessage?.let { ": $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (item.status == "scheduled") {
                TextButton(onClick = onCancel, enabled = !isCancelling, contentPadding = PaddingValues(0.dp)) {
                    if (isCancelling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Cancel scheduled announcement")
                }
            }
        }
    }
}

private fun formatLocalDateTime(value: ZonedDateTime): String =
    value.format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))

private fun formatInstant(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
} catch (_: Exception) {
    value
}
