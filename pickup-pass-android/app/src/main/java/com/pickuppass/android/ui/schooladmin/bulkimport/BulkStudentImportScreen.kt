package com.pickuppass.android.ui.schooladmin.bulkimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
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
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkStudentImportScreen(
    viewModel: BulkStudentImportViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmImport by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.selectFile(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bulk Import Students", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Validate before writing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("Safe roster import", fontWeight = FontWeight.Bold)
                            Text(
                                "PickupPass performs a dry-run validation first. Students are created only after you review and confirm a clean result.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { ImportSteps(hasFile = state.filename.isNotBlank(), ready = state.preview?.readyToImport == true) }

                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("Roster format", fontWeight = FontWeight.Bold)
                            Text("Required: firstName, lastName, grade, section", style = MaterialTheme.typography.bodySmall)
                            Text("Optional: studentNumber / LRN, middleInitial, suffix", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "CSV, XLS, or XLSX · maximum 5,000 rows · maximum 10 MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.filename.ifBlank { "No roster selected" },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.filename.isBlank()) "Choose a CSV or Excel roster to begin."
                                    else "The selected file is validated on the server before import.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    picker.launch(
                                        arrayOf(
                                            "text/csv",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        )
                                    )
                                },
                                enabled = !state.isWorking
                            ) {
                                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text(if (state.filename.isBlank()) "Choose" else "Replace")
                            }
                        }
                    }
                }

                if (state.isWorking) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(Spacing.sm))
                                Text("Processing roster securely…")
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.success?.let { item { SuccessBanner(it) } }

                state.preview?.let { preview ->
                    item {
                        Column {
                            Text("Validation result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (preview.readyToImport) "Roster is ready for import."
                                else "Resolve validation issues before importing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MetricCard("Rows", preview.totalRows.toString(), Modifier.weight(1f))
                            MetricCard("Ready", preview.validRows.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MetricCard("Invalid", preview.invalidRows.toString(), Modifier.weight(1f))
                            MetricCard("Duplicates", preview.duplicateRows.toString(), Modifier.weight(1f))
                        }
                    }

                    if (preview.errors.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    "Fix these rows and upload again. No student records have been created.",
                                    modifier = Modifier.padding(Spacing.md),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(preview.errors.take(30)) { error ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(Spacing.sm)) {
                                    Text("Row ${error.row} · ${error.field}", fontWeight = FontWeight.Bold)
                                    Text(error.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (preview.errors.size > 30) {
                            item { Text("Showing the first 30 validation errors.", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    if (preview.sample.isNotEmpty()) {
                        item {
                            Text("Student preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(preview.sample) { row ->
                            ListItem(
                                headlineContent = { Text(row.fullName, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            if (row.studentNumber.isNotBlank()) append("#${row.studentNumber} · ")
                                            append("Grade ${row.grade} → ${row.section}")
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (preview.readyToImport && preview.importedRows == 0) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = { confirmImport = true },
                                    enabled = !state.isWorking,
                                    modifier = Modifier.widthIn(min = 210.dp, max = 360.dp).height(52.dp)
                                ) {
                                    Text("Review & import ${preview.validRows}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmImport) {
        val preview = state.preview
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) confirmImport = false },
            icon = { Icon(Icons.Filled.CheckCircle, null) },
            title = { Text("Confirm student import") },
            text = {
                Text(
                    "Create ${preview?.validRows ?: 0} validated student record(s)? This writes the roster to the current school tenant. Duplicate and invalid rows are not imported."
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.isWorking && preview?.readyToImport == true,
                    onClick = {
                        confirmImport = false
                        viewModel.importConfirmed()
                    }
                ) { Text("Import students") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }, enabled = !state.isWorking) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ImportSteps(hasFile: Boolean, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StepChip("1", "Choose", true, Modifier.weight(1f))
        StepChip("2", "Validate", hasFile, Modifier.weight(1f))
        StepChip("3", "Import", ready, Modifier.weight(1f))
    }
}

@Composable
private fun StepChip(number: String, label: String, active: Boolean, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
