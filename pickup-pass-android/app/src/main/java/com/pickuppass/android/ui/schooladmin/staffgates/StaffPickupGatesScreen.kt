package com.pickuppass.android.ui.schooladmin.staffgates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.StaffPickupGateAssignment
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPickupGatesScreen(
    viewModel: StaffPickupGatesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StaffPickupGateAssignment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Pickup Gates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator(Modifier.padding(Spacing.xl)) }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    Text(
                        "Limit staff to specific dismissal gates. Leave a staff member on All active gates to preserve the normal unrestricted scanner behavior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.gates.isEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text("No active pickup gates are configured yet.", color = MaterialTheme.colorScheme.error)
                    }
                    state.error?.let { Spacer(Modifier.height(Spacing.sm)); ErrorBanner(it) }
                    state.message?.let { Spacer(Modifier.height(Spacing.sm)); Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                items(state.staff, key = { it.uid }) { staff ->
                    ElevatedCard {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(staff.displayName.ifBlank { staff.email.ifBlank { "Staff member" } }, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (staff.role == "school_admin") "School Admin" else "Teacher",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (staff.email.isNotBlank()) Text(staff.email, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(Spacing.sm))
                            val label = if (staff.assignedPickupGateIds.isEmpty()) {
                                "All active pickup gates"
                            } else {
                                state.gates.filter { it.id in staff.assignedPickupGateIds }
                                    .joinToString { it.displayName }
                                    .ifBlank { "Assigned gates unavailable" }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.weight(1f)) {
                                    Icon(Icons.Filled.LocationOn, null)
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(Spacing.sm))
                            OutlinedButton(
                                onClick = { editing = staff },
                                enabled = state.gates.isNotEmpty() && state.busyUid == null,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Assign Pickup Gates") }
                            if (state.busyUid == staff.uid) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.sm))
                        }
                    }
                }
            }
        }
    }

    editing?.let { staff ->
        var selected by remember(staff.uid) { mutableStateOf(staff.assignedPickupGateIds.toSet()) }
        var allGates by remember(staff.uid) { mutableStateOf(staff.assignedPickupGateIds.isEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Assign pickup gates") },
            text = {
                Column {
                    Text(staff.displayName.ifBlank { staff.email })
                    Spacer(Modifier.height(Spacing.sm))
                    Row {
                        Checkbox(checked = allGates, onCheckedChange = {
                            allGates = it
                            if (it) selected = emptySet()
                        })
                        Text("All active pickup gates", modifier = Modifier.padding(top = 12.dp))
                    }
                    state.gates.forEach { gate ->
                        Row {
                            Checkbox(
                                checked = !allGates && gate.id in selected,
                                enabled = !allGates,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + gate.id else selected - gate.id
                                }
                            )
                            Text(gate.displayName, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    if (!allGates && selected.isEmpty()) {
                        Text("Select at least one gate, or choose All active pickup gates.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = allGates || selected.isNotEmpty(),
                    onClick = {
                        viewModel.save(staff.uid, if (allGates) emptyList() else selected.toList())
                        editing = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}
