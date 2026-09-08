package com.pickuppass.android.ui.schooladmin.staffgates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.StaffPickupGateAssignment
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPickupGatesScreen(
    viewModel: StaffPickupGatesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var editing by remember {
        mutableStateOf<StaffPickupGateAssignment?>(null)
    }
    var submittedUid by remember {
        mutableStateOf<String?>(null)
    }
    var query by rememberSaveable {
        mutableStateOf("")
    }

    val interactionEnabled =
        state.busyUid == null && !state.isRefreshing

    val filtered = remember(state.staff, query) {
        val normalized = query.trim().lowercase()

        if (normalized.isBlank()) {
            state.staff
        } else {
            state.staff.filter { staff ->
                staff.displayName.lowercase().contains(normalized) ||
                    staff.email.lowercase().contains(normalized) ||
                    staff.role.lowercase().contains(normalized)
            }
        }
    }

    val unrestrictedCount = remember(state.staff) {
        state.staff.count { it.allGates }
    }
    val restrictedCount = state.staff.size - unrestrictedCount
    val attentionCount = remember(state.staff) {
        state.staff.count {
            !it.allGates && it.assignedPickupGateIds.isEmpty()
        }
    }

    LaunchedEffect(
        state.busyUid,
        state.message,
        submittedUid
    ) {
        val submitted = submittedUid
        if (
            submitted != null &&
            state.busyUid == null &&
            !state.message.isNullOrBlank()
        ) {
            editing = null
            submittedUid = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Staff pickup gates",
                subtitle = "Scanner location access",
                onBack = onBack,
            )
        }
    ) { padding ->
        if (state.isLoading) {
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
            refreshing = state.isRefreshing,
            onRefresh = viewModel::load,
            enabled = state.busyUid == null,
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
                        bottom = Spacing.xl
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    item(key = "hero") {
                        PremiumHeroCard(
                            eyebrow = "Dismissal authorization",
                            title = "Gate-scoped staff access",
                            message = "Control which active pickup locations each teacher or school administrator can use during student release.",
                            icon = Icons.Filled.Security
                        )
                    }

                    item(key = "metrics") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Metric(
                                label = "All gates",
                                value = unrestrictedCount,
                                modifier = Modifier.weight(1f)
                            )
                            Metric(
                                label = "Restricted",
                                value = restrictedCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (attentionCount > 0) {
                        item(key = "attention") {
                            AssignmentAttentionCard(
                                count = attentionCount
                            )
                        }
                    }

                    if (state.gates.isEmpty()) {
                        item(key = "no-gates") {
                            NoActiveGatesCard()
                        }
                    }

                    item(key = "staff-header") {
                        PremiumSectionHeader(
                            title = "Staff access",
                            subtitle =
                                filtered.size.toString() +
                                    " of " +
                                    state.staff.size +
                                    " staff account" +
                                    if (state.staff.size == 1) "" else "s"
                        )
                    }

                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = if (query.isNotBlank()) {
                                {
                                    IconButton(
                                        onClick = {
                                            query = ""
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription =
                                                "Clear staff search"
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            label = {
                                Text("Search staff")
                            },
                            placeholder = {
                                Text("Name, email or role")
                            },
                            singleLine = true,
                            enabled = interactionEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            StaffEmptyState(
                                hasStaff = state.staff.isNotEmpty(),
                                hasSearch = query.isNotBlank(),
                                onClearSearch = {
                                    query = ""
                                }
                            )
                        }
                    } else {
                        items(
                            items = filtered,
                            key = { it.uid }
                        ) { staff ->
                            StaffGateCard(
                                staff = staff,
                                gates = state.gates,
                                busy = state.busyUid == staff.uid,
                                enabled = interactionEnabled,
                                onEdit = {
                                    submittedUid = null
                                    editing = staff
                                }
                            )
                        }
                    }

                    item(key = "security-note") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color =
                                MaterialTheme.colorScheme
                                    .surfaceContainerLow
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    contentDescription = null,
                                    tint =
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = "Restricted staff can approve releases only at their assigned active gates. If all assigned gates become inactive, release authorization is blocked until an administrator updates the assignment.",
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { staff ->
        StaffGateAssignmentDialog(
            staff = staff,
            gates = state.gates,
            busy = state.busyUid == staff.uid,
            onDismiss = {
                if (state.busyUid == null) {
                    editing = null
                    submittedUid = null
                }
            },
            onSave = { gateIds ->
                submittedUid = staff.uid
                viewModel.save(
                    staff = staff,
                    gateIds = gateIds
                )
            }
        )
    }

    // Keep feedback last so it remains topmost above the editor on failure.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title = "Gate assignment not saved",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.message?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title = state.messageTitle ?: "Gate access updated",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun StaffGateCard(
    staff: StaffPickupGateAssignment,
    gates: List<PickupGateItem>,
    busy: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val activeAssigned = remember(
        staff.assignedPickupGateIds,
        gates
    ) {
        gates.filter {
            it.id in staff.assignedPickupGateIds
        }
    }

    val restrictionNeedsAttention =
        !staff.allGates && activeAssigned.isEmpty()

    val accent = when {
        !staff.isActive -> scheme.onSurfaceVariant
        restrictionNeedsAttention -> scheme.error
        staff.allGates -> scheme.primary
        else -> scheme.tertiary
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = scheme.surface
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
                    modifier = Modifier.size(46.dp),
                    shape = MaterialTheme.shapes.large,
                    color = accent.copy(alpha = 0.10f),
                    contentColor = accent,
                    border = BorderStroke(
                        1.dp,
                        accent.copy(alpha = 0.18f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = staff.displayName
                                .trim()
                                .take(1)
                                .uppercase()
                                .ifBlank { "S" },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.sm))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = staff.displayName
                            .ifBlank { staff.email }
                            .ifBlank { "Staff member" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (
                                staff.role == "school_admin"
                            ) {
                                "School Admin"
                            } else {
                                "Teacher"
                            },
                            style =
                                MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )

                        if (!staff.isActive) {
                            Text(
                                text = "· Inactive account",
                                style =
                                    MaterialTheme.typography.labelSmall,
                                color = scheme.error
                            )
                        }
                    }

                    if (staff.email.isNotBlank()) {
                        Text(
                            text = staff.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.sm))

                AccessBadge(
                    allGates = staff.allGates,
                    activeAssignedCount = activeAssigned.size,
                    needsAttention = restrictionNeedsAttention
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (restrictionNeedsAttention) {
                    scheme.errorContainer
                } else {
                    scheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (restrictionNeedsAttention) {
                            scheme.onErrorContainer
                        } else {
                            scheme.primary
                        }
                    )
                    Spacer(Modifier.width(Spacing.xs))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = when {
                                staff.allGates ->
                                    "All active pickup gates"

                                activeAssigned.isNotEmpty() ->
                                    activeAssigned
                                        .joinToString {
                                            it.displayName
                                        }

                                else ->
                                    "No active assigned pickup gate"
                            },
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = if (restrictionNeedsAttention) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (restrictionNeedsAttention) {
                                scheme.onErrorContainer
                            } else {
                                scheme.onSurface
                            }
                        )

                        if (
                            staff.unavailableAssignedGateCount > 0
                        ) {
                            Text(
                                text =
                                    staff.unavailableAssignedGateCount
                                        .toString() +
                                        " configured assignment" +
                                        if (
                                            staff.unavailableAssignedGateCount ==
                                            1
                                        ) {
                                            " is unavailable."
                                        } else {
                                            "s are unavailable."
                                        },
                                style =
                                    MaterialTheme.typography.labelSmall,
                                color = if (
                                    restrictionNeedsAttention
                                ) {
                                    scheme.onErrorContainer
                                } else {
                                    scheme.onSurfaceVariant
                                }
                            )
                        }

                        if (restrictionNeedsAttention) {
                            Text(
                                text = "Release authorization is blocked until an active gate is assigned or All active pickup gates is selected.",
                                style =
                                    MaterialTheme.typography.labelSmall,
                                color = scheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Saving access…",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    OutlinedButton(
                        onClick = onEdit,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Edit gate access")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessBadge(
    allGates: Boolean,
    activeAssignedCount: Int,
    needsAttention: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    val container = when {
        needsAttention -> scheme.errorContainer
        allGates -> scheme.primaryContainer
        else -> scheme.tertiaryContainer
    }
    val content = when {
        needsAttention -> scheme.onErrorContainer
        allGates -> scheme.onPrimaryContainer
        else -> scheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Text(
            text = when {
                needsAttention -> "ACTION NEEDED"
                allGates -> "ALL GATES"
                else ->
                    activeAssignedCount.toString() +
                        " GATE" +
                        if (activeAssignedCount == 1) "" else "S"
            },
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun AssignmentAttentionCard(
    count: Int
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = scheme.onErrorContainer
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text =
                        count.toString() +
                            " staff assignment" +
                            if (count == 1) {
                                " needs attention"
                            } else {
                                "s need attention"
                            },
                    fontWeight = FontWeight.Bold,
                    color = scheme.onErrorContainer
                )
                Text(
                    text = "These accounts are restricted but currently have no active assigned gate. Pickup approval is blocked for them until you update their access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun NoActiveGatesCard() {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = scheme.onTertiaryContainer
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = "No active pickup gates",
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer
                )
                Text(
                    text = "Create or reactivate a pickup gate in Campuses & pickup gates before assigning specific locations. You can still change a staff member to All active pickup gates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun StaffEmptyState(
    hasStaff: Boolean,
    hasSearch: Boolean,
    onClearSearch: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = if (hasStaff) {
                    Icons.Filled.Search
                } else {
                    Icons.Filled.Security
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )

            Text(
                text = if (hasStaff) {
                    "No matching staff"
                } else {
                    "No staff accounts"
                },
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (hasStaff) {
                    "Try a different name, email address, or role."
                } else {
                    "Teacher and school administrator accounts will appear here when available."
                },
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (hasSearch) {
                TextButton(onClick = onClearSearch) {
                    Text("Clear search")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffGateAssignmentDialog(
    staff: StaffPickupGateAssignment,
    gates: List<PickupGateItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember(staff.uid) {
        mutableStateOf(
            staff.assignedPickupGateIds
                .filter { assignedId ->
                    gates.any { it.id == assignedId }
                }
                .toSet()
        )
    }
    var allGates by remember(staff.uid) {
        mutableStateOf(staff.allGates)
    }
    var gateQuery by rememberSaveable(staff.uid) {
        mutableStateOf("")
    }

    val visibleGates = remember(
        gates,
        gateQuery
    ) {
        val normalized = gateQuery.trim().lowercase()

        if (normalized.isBlank()) {
            gates
        } else {
            gates.filter { gate ->
                gate.name.lowercase().contains(normalized) ||
                    gate.campusName.lowercase().contains(normalized) ||
                    gate.description.lowercase().contains(normalized)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                text = "Assign pickup gates",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = staff.displayName
                        .ifBlank { staff.email }
                        .ifBlank { "Staff member" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Choose where this account may approve student pickup. Selecting All active pickup gates automatically includes future active gates.",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (
                    !staff.allGates &&
                    staff.unavailableAssignedGateCount > 0
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color =
                            MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text =
                                staff.unavailableAssignedGateCount
                                    .toString() +
                                    " previous gate assignment" +
                                    if (
                                        staff.unavailableAssignedGateCount ==
                                        1
                                    ) {
                                        " is no longer active. Choose a current gate or switch to All active pickup gates."
                                    } else {
                                        "s are no longer active. Choose current gates or switch to All active pickup gates."
                                    },
                            modifier = Modifier.padding(Spacing.sm),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onErrorContainer
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color =
                        if (allGates) {
                            MaterialTheme.colorScheme
                                .primaryContainer
                        } else {
                            MaterialTheme.colorScheme
                                .surfaceVariant
                        },
                    border = BorderStroke(
                        1.dp,
                        if (allGates) {
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.24f)
                        } else {
                            MaterialTheme.colorScheme
                                .outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xs
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allGates,
                            enabled = !busy,
                            onCheckedChange = { checked ->
                                allGates = checked
                                if (checked) {
                                    selected = emptySet()
                                }
                            }
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "All active pickup gates",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Unrestricted location access",
                                style =
                                    MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                            )
                        }
                    }
                }

                if (!allGates) {
                    OutlinedTextField(
                        value = gateQuery,
                        onValueChange = {
                            gateQuery = it
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = if (
                            gateQuery.isNotBlank()
                        ) {
                            {
                                IconButton(
                                    onClick = {
                                        gateQuery = ""
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription =
                                            "Clear gate search"
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        label = {
                            Text("Find pickup gate")
                        },
                        placeholder = {
                            Text("Gate, campus or description")
                        },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (gates.isEmpty()) {
                        Text(
                            text = "No active pickup gates are available.",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    } else if (visibleGates.isEmpty()) {
                        Text(
                            text = "No pickup gates match this search.",
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    } else {
                        visibleGates.forEach { gate ->
                            GateSelectionRow(
                                gate = gate,
                                checked = gate.id in selected,
                                enabled = !busy,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + gate.id
                                    } else {
                                        selected - gate.id
                                    }
                                }
                            )
                        }
                    }

                    if (selected.isEmpty()) {
                        Text(
                            text = "Select at least one active gate, or choose All active pickup gates.",
                            color =
                                MaterialTheme.colorScheme.error,
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text =
                                selected.size.toString() +
                                    " active gate" +
                                    if (selected.size == 1) {
                                        " selected"
                                    } else {
                                        "s selected"
                                    },
                            style =
                                MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    (allGates || selected.isNotEmpty()) &&
                        !busy,
                onClick = {
                    onSave(
                        if (allGates) {
                            emptyList()
                        } else {
                            selected.toList()
                        }
                    )
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    if (busy) {
                        "Saving…"
                    } else {
                        "Save assignment"
                    }
                )
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
private fun GateSelectionRow(
    gate: PickupGateItem,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) {
            MaterialTheme.colorScheme
                .secondaryContainer
                .copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (checked) {
                MaterialTheme.colorScheme.secondary
                    .copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.sm,
                    vertical = Spacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )

            Spacer(Modifier.width(Spacing.xs))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = gate.name,
                    fontWeight = FontWeight.Medium
                )

                val detail = listOf(
                    gate.campusName,
                    gate.description
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")

                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
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
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
