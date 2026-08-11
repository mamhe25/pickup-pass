package com.pickuppass.android.ui.schooladmin.dataexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bytes = state.exportBytes
        if (uri != null && bytes != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        }
        viewModel.clearExport()
    }

    LaunchedEffect(state.exportFileName) {
        state.exportFileName?.let { saveLauncher.launch(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Backup & Export") },
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
        Column(
            Modifier.padding(padding).fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text("School data export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "This is an optional tenant-level portability backup. It downloads directly to the device you choose and PickupPass does not create a recurring cloud copy.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(if (state.enabled) "Self-service export enabled" else "Self-service export disabled", fontWeight = FontWeight.SemiBold)
                    Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Includes school operational records such as students, tenant users, academic structure, pickup locations, dismissal logs, and school audit history. Authentication secrets, device sessions, billing internals, and platform security telemetry are excluded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { viewModel.createExport() },
                enabled = state.enabled && !state.exporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(if (state.exporting) "Creating export…" else "Create & Save Export")
            }
            if (!state.enabled) {
                Text(
                    "The platform owner controls whether a school administrator may use this feature. This prevents a tenant admin from creating large exports without the operator's approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Text("What this does not do", fontWeight = FontWeight.SemiBold)
            Text(
                "It does not restore data automatically, change the production database, enable PITR, create Firestore schedules, or spend recurring cloud-storage budget. Platform-level backup and restore remain platform-owner responsibilities.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let { ErrorBanner(it) }
        }
    }
}
