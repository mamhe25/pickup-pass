package com.pickuppass.android.ui.schooladmin.dataexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolDataExportScreen(
    viewModel: SchoolDataExportViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmExport by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bytes = state.exportBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
        viewModel.clearExport()
    }

    LaunchedEffect(state.exportFileName) {
        state.exportFileName?.let { saveLauncher.launch(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Data Backup & Export", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Tenant data portability",
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
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (state.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (state.enabled) Icons.Filled.FolderZip else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (state.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                if (state.enabled) "Self-service export enabled" else "Self-service export disabled",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                state.message.ifBlank {
                                    if (state.enabled) "Your school may create an on-demand portability export."
                                    else "Platform-owner approval is required for school-admin exports."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.error?.let { ErrorBanner(it) }

                Text("What is included", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        IncludedRow("Students and guardian relationships")
                        IncludedRow("School users and academic structure")
                        IncludedRow("Campuses and pickup gates")
                        IncludedRow("Dismissal logs and operational audit history")
                    }
                }

                Text("Excluded for security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ExcludedRow("Authentication secrets and credentials")
                        ExcludedRow("Device sessions and security tokens")
                        ExcludedRow("Billing internals and platform security telemetry")
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "This is a direct-download portability export. It does not create recurring cloud backups, restore production data, enable PITR, or change Firestore protection settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!state.enabled) {
                    Text(
                        "The platform owner controls this capability to prevent large tenant exports without operator approval.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { confirmExport = true },
                        enabled = state.enabled && !state.exporting,
                        modifier = Modifier.widthIn(min = 220.dp, max = 360.dp).height(52.dp)
                    ) {
                        if (state.exporting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Creating export…")
                        } else {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Create school export")
                        }
                    }
                }
            }
        }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { if (!state.exporting) confirmExport = false },
            icon = { Icon(Icons.Filled.FolderZip, null) },
            title = { Text("Create school data export?") },
            text = {
                Text(
                    "PickupPass will prepare a ZIP containing eligible school operational data, then ask where to save it on this device. Handle the exported file as confidential school data."
                )
            },
            confirmButton = {
                Button(
                    enabled = state.enabled && !state.exporting,
                    onClick = {
                        confirmExport = false
                        viewModel.createExport()
                    }
                ) { Text("Create export") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun IncludedRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExcludedRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
