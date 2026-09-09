package com.pickuppass.android.ui.schooladmin.campusgates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.CampusItem
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.ui.common.CollectionAddFab
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusGateScreen(
    viewModel: CampusGateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val interactionEnabled = !state.saving && !state.refreshing

    var showAddChooser by remember { mutableStateOf(false) }
    var addCampus by remember { mutableStateOf(false) }
    var addGate by remember { mutableStateOf(false) }
    var campusToggle by remember {
        mutableStateOf<Pair<CampusItem, Boolean>?>(null)
    }
    var gateToggle by remember {
        mutableStateOf<Pair<PickupGateItem, Boolean>?>(null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Campuses & pickup gates",
                subtitle = "Physical dismissal locations",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (!state.loading) {
                CollectionAddFab(
                    onClick = { showAddChooser = true },
                    contentDescription = "Add campus or pickup gate"
                )
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = state.refreshing,
            onRefresh = viewModel::load,
            enabled = interactionEnabled,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 820.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.md,
                        end = Spacing.md,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item(key = "hero") {
                        PremiumHeroCard(
                            eyebrow = "Dismissal infrastructure",
                            title = "Release locations",
                            message = "Control the campuses and pickup gates that staff can use during secure student dismissal.",
                            icon = Icons.Filled.Place
                        )
                    }

                    if (!state.multiCampusEnabled) {
                        item(key = "plan") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Single-campus plan",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "One campus can be active at a time. You can still configure multiple pickup gates for that campus or create school-wide gates.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item(key = "metrics") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Metric(
                                label = "Active campuses",
                                value = state.campuses.count { it.active },
                                modifier = Modifier.weight(1f)
                            )
                            Metric(
                                label = "Active gates",
                                value = state.gates.count { it.active },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "campus-header") {
                        LocationSectionHeader(
                            title = "Campuses",
                            subtitle = "School sites that contain pickup locations."
                        )
                    }

                    if (state.campuses.isEmpty()) {
                        item(key = "campus-empty") {
                            LocationEmptyState(
                                icon = Icons.Filled.Place,
                                title = "No campuses configured",
                                body = "Add the school's main campus to organize physical pickup locations."
                            )
                        }
                    } else {
                        items(
                            items = state.campuses,
                            key = { "campus-" + it.id }
                        ) { campus ->
                            val campusGates = state.gates.filter {
                                it.campusId == campus.id
                            }

                            CampusCard(
                                campus = campus,
                                totalGates = campusGates.size,
                                activeGates = campusGates.count { it.active },
                                enabled = interactionEnabled,
                                onToggle = {
                                    campusToggle = campus to it
                                }
                            )
                        }
                    }

                    item(key = "gate-header") {
                        LocationSectionHeader(
                            title = "Pickup gates",
                            subtitle = "Release points available to dismissal staff."
                        )
                    }

                    if (state.gates.isEmpty()) {
                        item(key = "gate-empty") {
                            LocationEmptyState(
                                icon = Icons.Filled.LocationOn,
                                title = "No pickup gates configured",
                                body = "Add a release point such as Main Entrance, Gate 1, Carline, or North Exit."
                            )
                        }
                    } else {
                        items(
                            items = state.gates,
                            key = { "gate-" + it.id }
                        ) { gate ->
                            val campusActive = gate.campusId.isBlank() ||
                                state.campuses
                                    .firstOrNull { it.id == gate.campusId }
                                    ?.active == true

                            GateCard(
                                gate = gate,
                                campusActive = campusActive,
                                enabled = interactionEnabled,
                                onToggle = {
                                    gateToggle = gate to it
                                }
                            )
                        }
                    }

                    item(key = "operations-note") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = "Only active pickup gates appear in dismissal workflows. Deactivating a campus also deactivates every gate assigned to it; reactivating the campus does not automatically reactivate those gates.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddChooser) {
        ModalBottomSheet(
            onDismissRequest = { showAddChooser = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.xl
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    "Add release location",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Choose what you want to add. Existing campuses and gates stay visible behind this create flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    onClick = {
                        if (
                            interactionEnabled &&
                            (state.multiCampusEnabled ||
                                state.campuses.none { it.active })
                        ) {
                            showAddChooser = false
                            addCampus = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactionEnabled &&
                        (state.multiCampusEnabled ||
                            state.campuses.none { it.active })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Campus",
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                if (
                                    state.multiCampusEnabled ||
                                    state.campuses.none { it.active }
                                ) {
                                    "Create a school site that can contain pickup gates."
                                } else {
                                    "Your current plan allows one active campus."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedCard(
                    onClick = {
                        if (interactionEnabled) {
                            showAddChooser = false
                            addGate = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = interactionEnabled
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Pickup gate",
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Create a release point used by scanner and manual dismissal workflows.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (addCampus) {
        AddCampusDialog(
            busy = state.saving,
            onDismiss = {
                if (!state.saving) addCampus = false
            },
            onCreate = { name, address ->
                addCampus = false
                viewModel.createCampus(name, address)
            }
        )
    }

    if (addGate) {
        AddGateDialog(
            campuses = state.campuses.filter { it.active },
            busy = state.saving,
            onDismiss = {
                if (!state.saving) addGate = false
            },
            onCreate = { campusId, name, description ->
                addGate = false
                viewModel.createGate(
                    campusId = campusId,
                    name = name,
                    description = description
                )
            }
        )
    }

    campusToggle?.let { (campus, active) ->
        PremiumConfirmDialog(
            title = if (active) {
                "Activate campus?"
            } else {
                "Deactivate campus?"
            },
            message = if (active) {
                campus.name + " will become available for current operations. Gates that were deactivated with the campus must be reactivated individually."
            } else {
                "Deactivating " + campus.name + " removes it from dismissal operations and also deactivates every pickup gate assigned to it."
            },
            confirmLabel = if (active) "Activate" else "Deactivate",
            destructive = !active,
            icon = Icons.Filled.Place,
            onDismiss = {
                if (!state.saving) campusToggle = null
            },
            onConfirm = {
                campusToggle = null
                viewModel.setCampus(
                    id = campus.id,
                    name = campus.name,
                    active = active
                )
            }
        )
    }

    gateToggle?.let { (gate, active) ->
        PremiumConfirmDialog(
            title = if (active) {
                "Activate pickup gate?"
            } else {
                "Deactivate pickup gate?"
            },
            message = if (active) {
                gate.displayName + " will become selectable in dismissal flows."
            } else {
                gate.displayName + " will no longer be selectable by dismissal staff."
            },
            confirmLabel = if (active) "Activate" else "Deactivate",
            destructive = !active,
            icon = Icons.Filled.LocationOn,
            onDismiss = {
                if (!state.saving) gateToggle = null
            },
            onConfirm = {
                gateToggle = null
                viewModel.setGate(
                    id = gate.id,
                    displayName = gate.displayName,
                    active = active
                )
            }
        )
    }

    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title = "Location action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.message?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title = state.messageTitle ?: "Location updated",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun CampusCard(
    campus: CampusItem,
    totalGates: Int,
    activeGates: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (campus.active) {
        scheme.primary
    } else {
        scheme.onSurfaceVariant
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (campus.active) {
                scheme.primary.copy(alpha = 0.18f)
            } else {
                scheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = 0.10f),
                    contentColor = accent
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = campus.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = campus.address.ifBlank {
                            "No address provided"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    StatusLabel(active = campus.active)
                    Switch(
                        checked = campus.active,
                        onCheckedChange = onToggle,
                        enabled = enabled
                    )
                }
            }

            HorizontalDivider(color = scheme.outlineVariant)

            val gateLabel = if (totalGates == 1) "gate" else "gates"
            Text(
                text = when {
                    totalGates == 0 ->
                        "No pickup gates assigned to this campus."
                    campus.active ->
                        activeGates.toString() + " of " + totalGates + " assigned " + gateLabel + " currently active."
                    else ->
                        totalGates.toString() + " assigned " + gateLabel + " unavailable while this campus is inactive."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GateCard(
    gate: PickupGateItem,
    campusActive: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (gate.active) {
        scheme.primary
    } else {
        scheme.onSurfaceVariant
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (gate.active) {
                scheme.primary.copy(alpha = 0.18f)
            } else {
                scheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.10f),
                contentColor = accent
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = gate.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val context = listOf(
                    gate.campusName,
                    gate.description
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")

                Text(
                    text = context.ifBlank {
                        "School-wide pickup gate"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )

                if (!campusActive && gate.campusId.isNotBlank()) {
                    Text(
                        text = "Reactivate the campus before this gate can be activated.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.tertiary
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                StatusLabel(active = gate.active)
                Switch(
                    checked = gate.active,
                    onCheckedChange = onToggle,
                    enabled = enabled && (gate.active || campusActive)
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(active: Boolean) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) {
            scheme.primaryContainer
        } else {
            scheme.surfaceVariant
        }
    ) {
        Text(
            text = if (active) "ACTIVE" else "INACTIVE",
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) {
                scheme.onPrimaryContainer
            } else {
                scheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun LocationSectionHeader(
    title: String,
    subtitle: String
) {
    PremiumSectionHeader(
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun LocationEmptyState(
    icon: ImageVector,
    title: String,
    body: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddCampusDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                text = "Add campus",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Create a school site that can contain one or more pickup gates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Campus name") },
                    placeholder = { Text("e.g. Main Campus") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("Optional") },
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    onCreate(name.trim(), address.trim())
                }
            ) {
                Text("Create campus")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGateDialog(
    campuses: List<CampusItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var campusId by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                text = "Add pickup gate",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Create a release point for scanner and manual dismissal workflows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        if (!busy) expanded = !expanded
                    }
                ) {
                    OutlinedTextField(
                        value = campuses
                            .firstOrNull { it.id == campusId }
                            ?.name
                            ?: "No campus / school-wide",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !busy,
                        label = { Text("Campus") },
                        supportingText = {
                            Text("School-wide gates are independent of a campus.")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("No campus / school-wide")
                            },
                            onClick = {
                                campusId = ""
                                expanded = false
                            }
                        )

                        campuses.forEach { campus ->
                            DropdownMenuItem(
                                text = {
                                    Text(campus.name)
                                },
                                onClick = {
                                    campusId = campus.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Gate name") },
                    placeholder = { Text("e.g. Main Entrance") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Optional operational note") },
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    onCreate(
                        campusId,
                        name.trim(),
                        description.trim()
                    )
                }
            ) {
                Text("Create gate")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun Metric(
    label: String,
    value: Int,
    modifier: Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
