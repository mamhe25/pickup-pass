package com.pickuppass.android.ui.schooladmin.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissalReportsScreen(
    viewModel: DismissalReportsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            try {
                val saved = context.contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                    true
                } ?: false

                if (saved) {
                    viewModel.setSuccess(
                        title = "Report exported",
                        message = "The dismissal CSV was saved successfully to the selected location."
                    )
                } else {
                    viewModel.setError("Could not open the selected location to save the CSV file.")
                }
            } catch (_: Exception) {
                viewModel.setError("Could not save the CSV file.")
            }
        }
        pendingBytes = null
        viewModel.consumeExport()
    }

    LaunchedEffect(state.exportPayload) {
        state.exportPayload?.let {
            pendingBytes = it.bytes
            saveCsv.launch(it.fileName)
        }
    }

    Scaffold(
        topBar = {
            PremiumTopAppBar(
                title = "Dismissal reports",
                subtitle = "Operational analytics & export",
                onBack = onBack,
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 860.dp)
                    .align(Alignment.TopCenter)
                    .imePadding(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    PremiumHeroCard(
                        eyebrow = "School operations",
                        title = "Dismissal intelligence",
                        message = "Review completed student releases, spot daily activity patterns, and export tenant-isolated records for school operations."
                    )
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            PremiumSectionHeader(
                                title = "Report filters",
                                subtitle = "Choose a date range, then optionally narrow results by grade or section."
                            )

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                SuggestionChip(
                                    enabled = !state.isLoading && !state.isExporting,
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.minusDays(6).toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("Last 7 days") }
                                )
                                SuggestionChip(
                                    enabled = !state.isLoading && !state.isExporting,
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.withDayOfMonth(1).toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("This month") }
                                )
                                SuggestionChip(
                                    enabled = !state.isLoading && !state.isExporting,
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("Today") }
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                DateFilterButton(
                                    label = "From",
                                    value = state.from,
                                    enabled = !state.isLoading && !state.isExporting,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showFromPicker = true }
                                )
                                DateFilterButton(
                                    label = "To",
                                    value = state.to,
                                    enabled = !state.isLoading && !state.isExporting,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showToPicker = true }
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = state.grade,
                                    onValueChange = viewModel::setGrade,
                                    label = { Text("Grade filter") },
                                    placeholder = { Text("All grades") },
                                    supportingText = { Text("Optional") },
                                    singleLine = true,
                                    enabled = !state.isLoading && !state.isExporting,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.section,
                                    onValueChange = viewModel::setSection,
                                    label = { Text("Section filter") },
                                    placeholder = { Text("All sections") },
                                    supportingText = { Text("Optional") },
                                    singleLine = true,
                                    enabled = !state.isLoading && !state.isExporting,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (state.filtersDirty && state.summary != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "Filters changed. Run the report to refresh the displayed results before exporting.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.sm),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            HorizontalDivider()

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::exportCsv,
                                    enabled = !state.isExporting &&
                                        !state.isLoading &&
                                        state.summary != null &&
                                        !state.filtersDirty
                                ) {
                                    if (state.isExporting) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(if (state.isExporting) "Preparing…" else "Export CSV")
                                }

                                Spacer(Modifier.width(Spacing.sm))

                                Button(
                                    onClick = viewModel::load,
                                    enabled = !state.isLoading && !state.isExporting
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(if (state.isLoading) "Running…" else "Run report")
                                }
                            }
                        }
                    }
                }

                if (state.isLoading && state.summary == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                state.summary?.let { report ->
                    item {
                        PremiumSectionHeader(
                            title = "Report summary",
                            subtitle = report.from + " to " + report.to + " · " + report.timeZone
                        )
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            ReportCard(
                                "Releases",
                                report.totalReleases.toString(),
                                Modifier.weight(1f)
                            )
                            ReportCard(
                                "Students",
                                report.uniqueStudentsReleased.toString(),
                                Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            ReportCard(
                                "QR verified",
                                report.qrReleases.toString(),
                                Modifier.weight(1f)
                            )
                            ReportCard(
                                "Manual overrides",
                                report.manualOverrides.toString(),
                                Modifier.weight(1f)
                            )
                        }
                    }

                    if (report.prelaunchTestReleasesExcluded > 0) {
                        item(key = "prelaunch_test_exclusion") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text =
                                        report.prelaunchTestReleasesExcluded.toString() +
                                            " pre-launch test " +
                                            if (report.prelaunchTestReleasesExcluded == 1) {
                                                "release was"
                                            } else {
                                                "releases were"
                                            } +
                                            " excluded from this production report and CSV export. " +
                                            "Test records remain available in Dismissal History for audit review.",
                                    modifier = Modifier.padding(Spacing.md),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                PremiumSectionHeader(
                                    title = "Daily release activity",
                                    subtitle = "Relative release volume across the selected range."
                                )

                                if (report.dailyCounts.isEmpty()) {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "No completed releases were recorded for this range.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.md),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val max = report.dailyCounts.values
                                        .maxOrNull()
                                        ?.coerceAtLeast(1)
                                        ?: 1

                                    report.dailyCounts.forEach { (date, count) ->
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    date,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    count.toString(),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { count.toFloat() / max.toFloat() },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (report.gradeSectionCounts.isNotEmpty()) {
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    PremiumSectionHeader(
                                        title = "Grade & section distribution",
                                        subtitle = "Highest-volume groups in the selected report."
                                    )

                                    report.gradeSectionCounts.entries
                                        .sortedByDescending { it.value }
                                        .take(12)
                                        .forEachIndexed { index, (label, count) ->
                                            if (index > 0) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = Spacing.xs),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    label,
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(Modifier.width(Spacing.sm))
                                                Text(
                                                    count.toString(),
                                                    fontWeight = FontWeight.SemiBold
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
    }

    if (showFromPicker) {
        ReportDatePicker(
            selectedDate = state.from,
            onDismiss = { showFromPicker = false },
            onSelected = viewModel::setFrom
        )
    }

    if (showToPicker) {
        ReportDatePicker(
            selectedDate = state.to,
            onDismiss = { showToPicker = false },
            onSelected = viewModel::setTo
        )
    }

    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title = "Report action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.success?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title = state.successTitle ?: "Action completed",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun DateFilterButton(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePicker(
    selectedDate: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val initialSelectedDateMillis = remember(selectedDate) {
        runCatching {
            LocalDate.parse(selectedDate)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant
                            .ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        onSelected(selected)
                    }
                    onDismiss()
                },
                enabled = pickerState.selectedDateMillis != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun ReportCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
