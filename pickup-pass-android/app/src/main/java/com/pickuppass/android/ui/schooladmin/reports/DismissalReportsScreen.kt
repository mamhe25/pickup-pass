package com.pickuppass.android.ui.schooladmin.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissalReportsScreen(
    viewModel: DismissalReportsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
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
            TopAppBar(
                title = {
                    Column {
                        Text("Dismissal Reports", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Operational analytics & export",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 860.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("Tenant-isolated reporting", fontWeight = FontWeight.Bold)
                            Text(
                                "Review completed pickups and export records for the selected date range and optional grade/section scope.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("Report filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                SuggestionChip(
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.minusDays(6).toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("Last 7 days") }
                                )
                                SuggestionChip(
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.withDayOfMonth(1).toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("This month") }
                                )
                                SuggestionChip(
                                    onClick = {
                                        val today = LocalDate.now()
                                        viewModel.setFrom(today.toString())
                                        viewModel.setTo(today.toString())
                                    },
                                    label = { Text("Today") }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = state.from,
                                    onValueChange = viewModel::setFrom,
                                    label = { Text("From") },
                                    supportingText = { Text("YYYY-MM-DD") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.to,
                                    onValueChange = viewModel::setTo,
                                    label = { Text("To") },
                                    supportingText = { Text("YYYY-MM-DD") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = state.grade,
                                    onValueChange = viewModel::setGrade,
                                    label = { Text("Grade") },
                                    placeholder = { Text("Optional") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.section,
                                    onValueChange = viewModel::setSection,
                                    label = { Text("Section") },
                                    placeholder = { Text("Optional") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(
                                    onClick = viewModel::exportCsv,
                                    enabled = !state.isExporting && !state.isLoading
                                ) {
                                    if (state.isExporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("Export CSV")
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                Button(
                                    onClick = viewModel::load,
                                    enabled = !state.isLoading && !state.isExporting
                                ) {
                                    if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("Run report")
                                }
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }

                state.summary?.let { report ->
                    item {
                        Column {
                            Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${report.from} to ${report.to} · ${report.timeZone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ReportCard("Releases", report.totalReleases.toString(), Modifier.weight(1f))
                            ReportCard("Students", report.uniqueStudentsReleased.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ReportCard("QR", report.qrReleases.toString(), Modifier.weight(1f))
                            ReportCard("Overrides", report.manualOverrides.toString(), Modifier.weight(1f))
                        }
                    }

                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("Daily totals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (report.dailyCounts.isEmpty()) {
                                    Text("No releases in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val max = report.dailyCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                                    report.dailyCounts.forEach { (date, count) ->
                                        Column {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(date)
                                                Text(count.toString(), fontWeight = FontWeight.Bold)
                                            }
                                            LinearProgressIndicator(
                                                progress = count.toFloat() / max.toFloat(),
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
                                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text("Grade / section distribution", fontWeight = FontWeight.Bold)
                                    report.gradeSectionCounts.entries
                                        .sortedByDescending { it.value }
                                        .take(12)
                                        .forEach { (label, count) ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(label)
                                                Text(count.toString(), fontWeight = FontWeight.SemiBold)
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
}

@Composable
private fun ReportCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
