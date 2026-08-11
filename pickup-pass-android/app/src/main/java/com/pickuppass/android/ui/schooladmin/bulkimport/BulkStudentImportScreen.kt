package com.pickuppass.android.ui.schooladmin.bulkimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkStudentImportScreen(
    viewModel: BulkStudentImportViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.selectFile(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk Import Students") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    "Upload a CSV or Excel roster. PickupPass validates the entire file before any student is created.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Required columns", fontWeight = FontWeight.SemiBold)
                        Text("firstName, lastName, grade, section", style = MaterialTheme.typography.bodySmall)
                        Text("Optional: studentNumber / LRN, middleInitial, suffix", style = MaterialTheme.typography.bodySmall)
                        Text("Maximum: 5,000 rows and 10 MB per import", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        picker.launch(arrayOf(
                            "text/csv",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        ))
                    },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(if (state.filename.isBlank()) "Choose roster file" else "Choose another file")
                }
            }

            if (state.filename.isNotBlank()) {
                item { Text("Selected: ${state.filename}", style = MaterialTheme.typography.bodySmall) }
            }

            if (state.isWorking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Validating roster…")
                    }
                }
            }

            state.error?.let { message -> item { ErrorBanner(message) } }

            state.success?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(message)
                        }
                    }
                }
            }

            state.preview?.let { preview ->
                item {
                    Text("Validation result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        Text(
                            "Fix these rows, then upload the file again. No students have been imported.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    items(preview.errors.take(30)) { error ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(Spacing.sm)) {
                                Text("Row ${error.row} · ${error.field}", fontWeight = FontWeight.SemiBold)
                                Text(error.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (preview.errors.size > 30) {
                        item { Text("Showing the first 30 validation errors.", style = MaterialTheme.typography.bodySmall) }
                    }
                }

                if (preview.sample.isNotEmpty()) {
                    item { Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(preview.sample) { row ->
                        ListItem(
                            headlineContent = { Text(row.fullName) },
                            supportingContent = {
                                Text("${if (row.studentNumber.isNotBlank()) row.studentNumber + " · " else ""}Grade ${row.grade} · ${row.section}")
                            }
                        )
                    }
                }

                if (preview.readyToImport && preview.importedRows == 0) {
                    item {
                        Button(
                            onClick = viewModel::importConfirmed,
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text("Import ${preview.validRows} validated students")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
