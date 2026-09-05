package com.pickuppass.android.ui.schooladmin.studentlifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.StudentLifecycleItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

private val statuses = listOf("active", "inactive", "transferred", "graduated", "archived")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLifecycleScreen(
    viewModel: StudentLifecycleViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var statusStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var showPromotion by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Student Lifecycle", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Status, retention & promotion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showPromotion = true },
                        enabled = !state.isWorking
                    ) {
                        Icon(Icons.Filled.School, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Promote")
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
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("Preserve student history", fontWeight = FontWeight.Bold)
                            Text(
                                "Use lifecycle statuses instead of deleting records. Only Active students can use PickupPass QR pickup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SmallMetric("Total", state.students.size, Modifier.weight(1f))
                        SmallMetric("Active", state.counts["active"] ?: 0, Modifier.weight(1f))
                        SmallMetric("Archived", state.counts["archived"] ?: 0, Modifier.weight(1f))
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = viewModel::setSearch,
                        label = { Text("Search students") },
                        placeholder = { Text("Name or student number") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        FilterChip(
                            selected = state.filter == "all",
                            onClick = { viewModel.setFilter("all") },
                            label = { Text("All (${state.students.size})") }
                        )
                        statuses.forEach { status ->
                            FilterChip(
                                selected = state.filter == status,
                                onClick = { viewModel.setFilter(status) },
                                label = {
                                    Text("${status.pretty()} (${state.counts[status] ?: 0})")
                                }
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.success?.let { item { SuccessBanner(it) } }

                if (!state.isLoading && state.visibleStudents.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.ManageAccounts, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.sm))
                                Text("No matching students", fontWeight = FontWeight.Bold)
                                Text(
                                    "Try another search or lifecycle filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(state.visibleStudents, key = { it.studentId }) { student ->
                    StudentLifecycleCard(
                        student = student,
                        enabled = !state.isWorking,
                        onClick = { statusStudent = student }
                    )
                }
            }
        }
    }

    statusStudent?.let { student ->
        StatusDialog(
            student = student,
            busy = state.isWorking,
            onDismiss = { statusStudent = null },
            onSave = { status, reason ->
                viewModel.updateStatus(student.studentId, status, reason)
                statusStudent = null
            }
        )
    }

    if (showPromotion) {
        PromotionDialog(
            state = state,
            onDismiss = { if (!state.isWorking) showPromotion = false },
            onSelectYear = viewModel::selectTargetAcademicYear,
            onPreview = viewModel::previewPromotion,
            onExecute = viewModel::executePromotion
        )
    }
}

@Composable
private fun StudentLifecycleCard(
    student: StudentLifecycleItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    student.fullName.trim().take(1).uppercase().ifBlank { "S" },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    student.fullName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (student.studentNumber.isNotBlank()) append("#${student.studentNumber} · ")
                        append("Grade ${student.grade} → ${student.section}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (student.academicYearName.isNotBlank()) {
                    Text(student.academicYearName, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                AssistChip(
                    onClick = onClick,
                    enabled = enabled,
                    label = { Text(student.status.pretty()) }
                )
                Text("Manage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDialog(
    student: StudentLifecycleItem,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var selected by remember(student.studentId) { mutableStateOf(student.status) }
    var reason by remember(student.studentId) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change student status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(student.fullName, fontWeight = FontWeight.Bold)
                Text(
                    "Current: ${student.status.pretty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selected.pretty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("New status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        statuses.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.pretty()) },
                                onClick = { selected = status; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / administrative note") },
                    supportingText = { Text("Recommended for transferred, inactive, graduated, or archived records.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (selected != "active" && selected != student.status) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Unused pickup QR passes will be invalidated immediately.",
                            modifier = Modifier.padding(Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selected, reason) },
                enabled = !busy && selected != student.status
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Confirm status")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromotionDialog(
    state: StudentLifecycleUiState,
    onDismiss: () -> Unit,
    onSelectYear: (String) -> Unit,
    onPreview: () -> Unit,
    onExecute: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val targetYear = state.academicYears.firstOrNull { it.id == state.targetAcademicYearId }
    val preview = state.promotionPreview

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.School, null) },
        title = { Text("End-of-year promotion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Preview is mandatory. PickupPass will not write promotion changes while unresolved section mappings remain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = targetYear?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target school year") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.academicYears
                            .filter { it.id != state.currentAcademicYearId && it.status == "active" }
                            .forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year.name) },
                                    onClick = { onSelectYear(year.id); expanded = false }
                                )
                            }
                    }
                }

                preview?.let {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SmallMetric("Ready", it.readyCount, Modifier.weight(1f))
                        SmallMetric("Needs mapping", it.unresolvedCount, Modifier.weight(1f))
                    }
                    if (it.unresolvedCount > 0) {
                        Text(
                            "Create matching active sections before promotion. No data will be changed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        it.unresolved.take(5).forEach { row ->
                            Text("• ${row.fullName}: ${row.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null && preview.unresolvedCount == 0 && preview.dryRun) {
                Button(onClick = onExecute, enabled = !state.isWorking && preview.readyCount > 0) {
                    Text("Confirm promotion")
                }
            } else {
                Button(
                    onClick = onPreview,
                    enabled = !state.isWorking && state.targetAcademicYearId.isNotBlank()
                ) {
                    Text(if (state.isWorking) "Working…" else "Preview changes")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isWorking) { Text("Close") } }
    )
}

@Composable
private fun SmallMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.pretty(): String =
    replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
