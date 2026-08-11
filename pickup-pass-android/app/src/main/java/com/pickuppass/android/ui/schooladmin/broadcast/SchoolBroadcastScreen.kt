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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.BroadcastHistoryItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
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
                        scheduledDateTime = ZonedDateTime.of(
                            year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault()
                        )
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
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Announcements") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshHistory) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh history")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                Text(
                    if (uiState.schedulingEnabled) "Send immediately or schedule an announcement. Recipients receive it as a push notification and in their notification inbox." else "Send an announcement now. Scheduled announcements are not enabled for this school plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; viewModel.clearMessage() },
                    label = { Text("Title") },
                    supportingText = { Text("${title.length}/120") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; viewModel.clearMessage() },
                    label = { Text("Message") },
                    supportingText = { Text("${body.length}/2000") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("SEND TO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = Spacing.xs)) {
                        AudienceRow("Teachers", uiState.includeTeachers) { viewModel.setIncludeTeachers(it) }
                        AudienceRow("Guardians", uiState.includeParents) { viewModel.setIncludeParents(it) }
                    }
                }
            }

            item {
                Text("DELIVERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !scheduleMode, onClick = { scheduleMode = false; viewModel.clearMessage() })
                    Text("Send now", Modifier.clickable { scheduleMode = false; viewModel.clearMessage() })
                    if (uiState.schedulingEnabled) {
                        Spacer(Modifier.width(Spacing.md))
                        RadioButton(selected = scheduleMode, onClick = { scheduleMode = true; viewModel.clearMessage() })
                        Text("Schedule", Modifier.clickable { scheduleMode = true; viewModel.clearMessage() })
                    }
                }
            }

            if (scheduleMode) {
                item {
                    OutlinedButton(onClick = ::chooseScheduleTime, modifier = Modifier.fillMaxWidth()) {
                        Text(scheduledDateTime?.let(::formatLocalDateTime) ?: "Choose date and time")
                    }
                }
            }

            item {
                AnimatedVisibility(visible = uiState.error != null, enter = fadeIn() + expandVertically()) {
                    uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
                }
                AnimatedVisibility(visible = uiState.successMessage != null, enter = fadeIn() + expandVertically()) {
                    uiState.successMessage?.let { SuccessBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
                }
                PrimaryButton(
                    text = if (scheduleMode) "Schedule Announcement" else "Send Announcement",
                    loading = uiState.isSubmitting,
                    onClick = {
                        if (scheduleMode) {
                            viewModel.schedule(title, body, scheduledDateTime?.toInstant()?.toString())
                        } else {
                            viewModel.send(title, body)
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(Spacing.md))
                Text("RECENT ANNOUNCEMENTS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scheduled announcements can be cancelled until the server starts sending them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.isLoadingHistory) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
            }

            if (!uiState.isLoadingHistory && uiState.history.isEmpty()) {
                item {
                    Text("No announcements yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun AudienceRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BroadcastHistoryCard(
    item: BroadcastHistoryItem,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, enabled = false, label = { Text(item.status.ifBlank { "unknown" }.uppercase()) })
            }
            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Audience: ${item.audience.joinToString { if (it == "parent") "Guardians" else "Teachers" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (item.status) {
                "scheduled" -> item.scheduledAt?.let {
                    Text("Scheduled: ${formatInstant(it)}", style = MaterialTheme.typography.bodySmall)
                }
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
