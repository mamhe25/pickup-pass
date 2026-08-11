package com.pickuppass.android.ui.schooladmin.pickuppolicy

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupPolicyScreen(
    viewModel: PickupPolicyViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickup Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "QR pickup behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Unrestricted keeps the original PickupPass flow: an authorized parent can present a currently valid QR without joining a queue or checking in first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !state.restrictedToTimeWindow,
                            onClick = { viewModel.setRestricted(false) }
                        )
                        Column(Modifier.padding(start = Spacing.xs)) {
                            Text("Unrestricted", fontWeight = FontWeight.Medium)
                            Text(
                                "Any valid QR can be scanned until that QR expires or is used.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.restrictedToTimeWindow,
                            onClick = { viewModel.setRestricted(true) }
                        )
                        Column(Modifier.padding(start = Spacing.xs)) {
                            Text("School pickup time window", fontWeight = FontWeight.Medium)
                            Text(
                                "Valid QR codes are accepted only during the configured hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.restrictedToTimeWindow) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = state.startTime,
                                onValueChange = viewModel::setStartTime,
                                label = { Text("Start") },
                                placeholder = { Text("14:00") },
                                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.endTime,
                                onValueChange = viewModel::setEndTime,
                                label = { Text("End") },
                                placeholder = { Text("18:00") },
                                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Times use 24-hour HH:mm format in ${state.timeZone}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow manual pickup override", fontWeight = FontWeight.Medium)
                        Text(
                            "School admins can document an exception if a phone or scanner cannot be used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.allowManualOverride, onCheckedChange = viewModel::setManualOverride)
                }
            }

            state.error?.let { ErrorBanner(it) }
            state.successMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (state.isSaving) "Saving..." else "Save Pickup Policy")
            }
        }
    }
}
