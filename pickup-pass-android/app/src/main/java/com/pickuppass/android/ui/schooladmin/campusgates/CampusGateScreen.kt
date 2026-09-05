package com.pickuppass.android.ui.schooladmin.campusgates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.CampusItem
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusGateScreen(
    viewModel: CampusGateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addCampus by remember { mutableStateOf(false) }
    var addGate by remember { mutableStateOf(false) }
    var campusToggle by remember { mutableStateOf<Pair<CampusItem, Boolean>?>(null) }
    var gateToggle by remember { mutableStateOf<Pair<PickupGateItem, Boolean>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Campuses & Pickup Gates", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Physical dismissal locations",
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
        if (state.loading) {
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
                        Column(Modifier.padding(Spacing.md)) {
                            Text("Release location controls", fontWeight = FontWeight.Bold)
                            Text(
                                "Pickup gates appear in scanner and manual-release flows. Deactivating a campus also makes its gates unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!state.multiCampusEnabled) {
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    "Current plan: one active campus. Multiple pickup gates are still supported.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.message?.let { item { SuccessBanner(it) } }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Metric("Active campuses", state.campuses.count { it.active }, Modifier.weight(1f))
                        Metric("Active gates", state.gates.count { it.active }, Modifier.weight(1f))
                    }
                }

                item {
                    SectionHeader(
                        title = "Campuses",
                        subtitle = "School sites that contain pickup locations.",
                        action = "Add campus",
                        enabled = !state.saving && (state.multiCampusEnabled || state.campuses.none { it.active }),
                        onClick = { addCampus = true }
                    )
                }

                if (state.campuses.isEmpty()) {
                    item { InfoCard("No campuses configured", "Add the school's main campus, or keep release points school-wide.") }
                } else {
                    items(state.campuses, key = { it.id }) { campus ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Icon(Icons.Filled.Place, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(campus.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        campus.address.ifBlank { "No address provided" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (campus.active) "Active" else "Inactive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (campus.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = campus.active,
                                        onCheckedChange = { campusToggle = campus to it },
                                        enabled = !state.saving
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Pickup gates",
                        subtitle = "Release points available to dismissal staff.",
                        action = "Add gate",
                        enabled = !state.saving,
                        onClick = { addGate = true }
                    )
                }

                if (state.gates.isEmpty()) {
                    item { InfoCard("No pickup gates configured", "Examples: Main Entrance, Gate 1, Carline, or North Exit.") }
                } else {
                    items(state.gates, key = { it.id }) { gate ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Icon(Icons.Filled.LocationOn, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(gate.name, fontWeight = FontWeight.Bold)
                                    val sub = listOf(gate.campusName, gate.description).filter { it.isNotBlank() }.joinToString(" · ")
                                    Text(
                                        sub.ifBlank { "School-wide pickup gate" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (gate.active) "Active" else "Inactive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (gate.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = gate.active,
                                        onCheckedChange = { gateToggle = gate to it },
                                        enabled = !state.saving
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addCampus) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!state.saving) addCampus = false },
            icon = { Icon(Icons.Filled.Place, null) },
            title = { Text("Add campus") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Campus name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it }, label = { Text("Address (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !state.saving,
                    onClick = { viewModel.createCampus(name, address); addCampus = false }
                ) { Text("Create campus") }
            },
            dismissButton = { TextButton(onClick = { addCampus = false }) { Text("Cancel") } }
        )
    }

    if (addGate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var campusId by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.saving) addGate = false },
            icon = { Icon(Icons.Filled.LocationOn, null) },
            title = { Text("Add pickup gate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = state.campuses.firstOrNull { it.id == campusId }?.name ?: "No campus / school-wide",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Campus") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("No campus / school-wide") },
                                onClick = { campusId = ""; expanded = false }
                            )
                            state.campuses.filter { it.active }.forEach { campus ->
                                DropdownMenuItem(
                                    text = { Text(campus.name) },
                                    onClick = { campusId = campus.id; expanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Gate name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !state.saving,
                    onClick = { viewModel.createGate(campusId, name, desc); addGate = false }
                ) { Text("Create gate") }
            },
            dismissButton = { TextButton(onClick = { addGate = false }) { Text("Cancel") } }
        )
    }

    campusToggle?.let { (campus, active) ->
        AlertDialog(
            onDismissRequest = { campusToggle = null },
            title = { Text(if (active) "Activate campus?" else "Deactivate campus?") },
            text = {
                Text(
                    if (active) "${campus.name} will become available for current operations."
                    else "Deactivating ${campus.name} also makes its pickup gates unavailable for dismissal."
                )
            },
            confirmButton = {
                Button(onClick = { campusToggle = null; viewModel.setCampus(campus.id, active) }) {
                    Text(if (active) "Activate" else "Deactivate")
                }
            },
            dismissButton = { TextButton(onClick = { campusToggle = null }) { Text("Cancel") } }
        )
    }

    gateToggle?.let { (gate, active) ->
        AlertDialog(
            onDismissRequest = { gateToggle = null },
            title = { Text(if (active) "Activate pickup gate?" else "Deactivate pickup gate?") },
            text = {
                Text(
                    if (active) "${gate.displayName} will be available in dismissal flows."
                    else "${gate.displayName} will no longer be selectable by staff."
                )
            },
            confirmButton = {
                Button(onClick = { gateToggle = null; viewModel.setGate(gate.id, active) }) {
                    Text(if (active) "Activate" else "Deactivate")
                }
            },
            dismissButton = { TextButton(onClick = { gateToggle = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(action)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
