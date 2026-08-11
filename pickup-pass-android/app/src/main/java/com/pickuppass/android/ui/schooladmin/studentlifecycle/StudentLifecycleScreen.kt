package com.pickuppass.android.ui.schooladmin.studentlifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.StudentLifecycleItem
import com.pickuppass.android.ui.common.ErrorBanner
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
                title = { Text("Student Lifecycle") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { showPromotion = true }) { Text("Promote") }
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
                    "Keep historical records instead of deleting students. Only Active students can generate or use pickup QR passes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    label = { Text("Search student or LRN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                            label = { Text("${status.replaceFirstChar { it.uppercase() }} (${state.counts[status] ?: 0})") }
                        )
                    }
                }
            }

            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            state.error?.let { item { ErrorBanner(it) } }
            state.success?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(message, modifier = Modifier.padding(Spacing.md))
                    }
                }
            }

            if (!state.isLoading && state.visibleStudents.isEmpty()) {
                item { Text("No students match this filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(state.visibleStudents, key = { it.studentId }) { student ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { statusStudent = student }
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(student.fullName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${student.grade} · ${student.section}" + if (student.studentNumber.isNotBlank()) " · ${student.studentNumber}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (student.academicYearName.isNotBlank()) {
                                Text(student.academicYearName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        AssistChip(
                            onClick = { statusStudent = student },
                            label = { Text(student.status.replaceFirstChar { it.uppercase() }) }
                        )
                    }
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
            onDismiss = { showPromotion = false },
            onSelectYear = viewModel::selectTargetAcademicYear,
            onPreview = viewModel::previewPromotion,
            onExecute = viewModel::executePromotion
        )
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
        onDismissRequest = onDismiss,
        title = { Text("Change student status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(student.fullName, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selected.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        statuses.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.replaceFirstChar { it.uppercase() }) },
                                onClick = { selected = status; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (selected != "active") {
                    Text(
                        "Changing to $selected immediately invalidates unused pickup QR passes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected, reason) }, enabled = !busy && selected != student.status) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.School, null) },
        title = { Text("End-of-year promotion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("PickupPass previews all changes before writing anything. Students are matched to the next grade with the same section name.")
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
                        state.academicYears.filter { it.id != state.currentAcademicYearId && it.status == "active" }.forEach { year ->
                            DropdownMenuItem(text = { Text(year.name) }, onClick = { onSelectYear(year.id); expanded = false })
                        }
                    }
                }
                state.promotionPreview?.let { preview ->
                    HorizontalDivider()
                    Text("Ready: ${preview.readyCount}", fontWeight = FontWeight.SemiBold)
                    Text("Needs mapping: ${preview.unresolvedCount}")
                    if (preview.unresolvedCount > 0) {
                        Text(
                            "Create matching active sections in the target year before promotion. No data will be changed while unresolved students remain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        preview.unresolved.take(5).forEach {
                            Text("• ${it.fullName}: ${it.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.promotionPreview?.unresolvedCount == 0 && state.promotionPreview != null && state.promotionPreview.dryRun) {
                Button(onClick = onExecute, enabled = !state.isWorking && state.promotionPreview.readyCount > 0) { Text("Confirm promotion") }
            } else {
                Button(onClick = onPreview, enabled = !state.isWorking && state.targetAcademicYearId.isNotBlank()) { Text("Preview") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
