package com.pickuppass.android.ui.schooladmin.staffgates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.StaffPickupGateAssignment
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPickupGatesScreen(
    viewModel: StaffPickupGatesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StaffPickupGateAssignment?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(state.staff, query) {
        val q = query.trim()
        if (q.isBlank()) state.staff
        else state.staff.filter {
            it.displayName.contains(q, true) ||
                it.email.contains(q, true) ||
                it.role.contains(q, true)
        }
    }
    val allGateCount = state.staff.count { it.assignedPickupGateIds.isEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Staff Pickup Gates", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Scanner location access",
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
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

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
                            Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text("Gate-scoped scanner access", fontWeight = FontWeight.Bold)
                                Text(
                                    "Restrict staff to specific dismissal gates, or keep All active pickup gates for unrestricted scanner use.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.message?.let { item { SuccessBanner(it) } }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Metric("Staff", state.staff.size, Modifier.weight(1f))
                        Metric("All gates", allGateCount, Modifier.weight(1f))
                        Metric("Restricted", state.staff.size - allGateCount, Modifier.weight(1f))
                    }
                }

                if (state.gates.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "No active pickup gates are configured. Add a gate before restricting staff access.",
                                modifier = Modifier.padding(Spacing.md),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Search staff") },
                        placeholder = { Text("Name, email or role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(filtered, key = { it.uid }) { staff ->
                    StaffGateCard(
                        staff = staff,
                        state = state,
                        busy = state.busyUid == staff.uid,
                        enabled = state.gates.isNotEmpty() && state.busyUid == null,
                        onEdit = { editing = staff }
                    )
                }
            }
        }
    }

    editing?.let { staff ->
        var selected by remember(staff.uid) { mutableStateOf(staff.assignedPickupGateIds.toSet()) }
        var allGates by remember(staff.uid) { mutableStateOf(staff.assignedPickupGateIds.isEmpty()) }

        AlertDialog(
            onDismissRequest = { if (state.busyUid == null) editing = null },
            icon = { Icon(Icons.Filled.LocationOn, null) },
            title = { Text("Assign pickup gates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(staff.displayName.ifBlank { staff.email }, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose the gates this account may use when approving student pickup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allGates,
                                onCheckedChange = {
                                    allGates = it
                                    if (it) selected = emptySet()
                                }
                            )
                            Text("All active pickup gates", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    state.gates.forEach { gate ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = !allGates && gate.id in selected,
                                enabled = !allGates,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + gate.id else selected - gate.id
                                }
                            )
                            Column {
                                Text(gate.name, fontWeight = FontWeight.Medium)
                                if (gate.campusName.isNotBlank()) {
                                    Text(
                                        gate.campusName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (!allGates && selected.isEmpty()) {
                        Text(
                            "Select at least one gate, or choose All active pickup gates.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (allGates || selected.isNotEmpty()) && state.busyUid == null,
                    onClick = {
                        viewModel.save(staff.uid, if (allGates) emptyList() else selected.toList())
                        editing = null
                    }
                ) { Text("Save assignment") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StaffGateCard(
    staff: StaffPickupGateAssignment,
    state: StaffPickupGatesUiState,
    busy: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        staff.displayName.trim().take(1).uppercase().ifBlank { "S" },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        staff.displayName.ifBlank { staff.email.ifBlank { "Staff member" } },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (staff.role == "school_admin") "School Admin" else "Teacher",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (staff.email.isNotBlank()) {
                        Text(
                            staff.email,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(if (staff.assignedPickupGateIds.isEmpty()) "All gates" else "${staff.assignedPickupGateIds.size} gate${if (staff.assignedPickupGateIds.size == 1) "" else "s"}")
                    }
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        if (staff.assignedPickupGateIds.isEmpty()) "All active pickup gates"
                        else state.gates.filter { it.id in staff.assignedPickupGateIds }.joinToString { it.displayName }.ifBlank { "Assigned gates unavailable" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEdit, enabled = enabled) {
                    Text("Edit gate access")
                }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
