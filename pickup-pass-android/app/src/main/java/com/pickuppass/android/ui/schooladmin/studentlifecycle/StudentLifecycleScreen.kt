package com.pickuppass.android.ui.schooladmin.studentlifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Restore
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
import com.pickuppass.android.data.model.GradeSection
import com.pickuppass.android.data.model.StudentLifecycleItem
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

private val statuses = listOf("active", "inactive", "transferred", "graduated", "archived")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLifecycleScreen(
    viewModel: StudentLifecycleViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var statusStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var editStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var placementStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var archiveStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var restoreStudent by remember { mutableStateOf<StudentLifecycleItem?>(null) }
    var showPromotion by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PremiumTopAppBar(
                title = "Student lifecycle",
                subtitle = "Status, retention & promotion",
                onBack = onBack,
                actions = {
                    FilledTonalButton(
                        onClick = { showPromotion = true },
                        enabled = !state.isWorking
                    ) {
                        Icon(Icons.Filled.School, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Promote")
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 820.dp).align(Alignment.TopCenter)
                    .imePadding(),
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
                                "Reassign students to current grade/sections, or archive obsolete records without losing guardian and dismissal history.",
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
                        onClick = { actionStudent = student }
                    )
                }
            }
        }
    }

    actionStudent?.let { student ->
        StudentActionsSheet(
            student = student,
            onDismiss = { actionStudent = null },
            onEditDetails = {
                actionStudent = null
                editStudent = student
            },
            onReassign = {
                actionStudent = null
                placementStudent = student
            },
            onChangeStatus = {
                actionStudent = null
                statusStudent = student
            },
            onRestore = {
                actionStudent = null
                restoreStudent = student
            },
            onArchive = {
                actionStudent = null
                archiveStudent = student
            }
        )
    }

    editStudent?.let { student ->
        EditStudentDetailsDialog(
            student = student,
            busy = state.isWorking,
            onDismiss = { editStudent = null },
            onSave = {
                    lastName,
                    firstName,
                    middleInitial,
                    suffix,
                    studentNumber ->
                viewModel.updateStudentDetails(
                    studentId = student.studentId,
                    lastName = lastName,
                    firstName = firstName,
                    middleInitial = middleInitial,
                    suffix = suffix,
                    studentNumber = studentNumber
                )
                editStudent = null
            }
        )
    }

    placementStudent?.let { student ->
        PlacementDialog(
            student = student,
            sections = state.gradeSections,
            busy = state.isWorking,
            onDismiss = { placementStudent = null },
            onSave = { gradeSectionId ->
                viewModel.reassignStudent(
                    student.studentId,
                    gradeSectionId
                )
                placementStudent = null
            }
        )
    }

    restoreStudent?.let { student ->
        PremiumConfirmDialog(
            title = "Restore student?",
            message =
                student.fullName +
                    " will return to active school and pickup rosters. " +
                    "Confirm the student's current grade and section after restoring.",
            confirmLabel = "Restore active",
            onDismiss = { restoreStudent = null },
            onConfirm = {
                restoreStudent = null
                viewModel.restoreStudent(student.studentId)
            }
        )
    }

    archiveStudent?.let { student ->
        PremiumConfirmDialog(
            title = "Archive student?",
            message =
                student.fullName +
                    " will be removed from active pickup and teacher roster views. " +
                    "Guardian links and dismissal history will be preserved.",
            confirmLabel = "Archive",
            destructive = true,
            onDismiss = { archiveStudent = null },
            onConfirm = {
                archiveStudent = null
                viewModel.archiveStudent(student.studentId)
            }
        )
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

    LaunchedEffect(state.successTitle) {
        if (state.successTitle == "Promotion completed") {
            showPromotion = false
        }
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

    // Keep terminal action feedback last so the canonical feedback modal is
    // always topmost, including promotion preview/execute failures that occur
    // while the promotion dialog is still open.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title = "Action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.success?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title = state.successTitle ?: "Success",
            onDismiss = viewModel::clearFeedback
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
private fun StudentActionsSheet(
    student: StudentLifecycleItem,
    onDismiss: () -> Unit,
    onEditDetails: () -> Unit,
    onReassign: () -> Unit,
    onChangeStatus: () -> Unit,
    onRestore: () -> Unit,
    onArchive: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "MANAGE STUDENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                student.fullName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Grade " + student.grade +
                    " · " + student.section +
                    " · " + student.status.pretty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            OutlinedCard(
                onClick = onEditDetails,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            "Edit student details",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Update name and student number. This is available even for archived records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedCard(
                onClick = onReassign,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        "Reassign grade & section",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Move this student into a current configured section so assigned teachers and roster filters match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedCard(
                onClick = onChangeStatus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        "Change lifecycle status",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Mark active, inactive, transferred, graduated, or archived.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (student.status.equals("archived", ignoreCase = true)) {
                FilledTonalButton(
                    onClick = onRestore,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "Restore to active",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(
                    onClick = onArchive,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        "Archive student",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EditStudentDetailsDialog(
    student: StudentLifecycleItem,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        studentNumber: String
    ) -> Unit
) {
    var lastName by remember(student.studentId) {
        mutableStateOf(student.lastName)
    }
    var firstName by remember(student.studentId) {
        mutableStateOf(student.firstName)
    }
    var middleInitial by remember(student.studentId) {
        mutableStateOf(student.middleInitial)
    }
    var suffix by remember(student.studentId) {
        mutableStateOf(student.suffix)
    }
    var studentNumber by remember(student.studentId) {
        mutableStateOf(student.studentNumber)
    }

    val legacyName =
        student.lastName.isBlank() ||
            student.firstName.isBlank()

    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        icon = {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null
            )
        },
        title = {
            Text("Edit student details")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    student.fullName,
                    fontWeight = FontWeight.Bold
                )

                if (
                    student.status.equals(
                        "archived",
                        ignoreCase = true
                    )
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color =
                            MaterialTheme.colorScheme
                                .tertiaryContainer
                    ) {
                        Text(
                            "Archived record: these edits update identity information only. The student stays archived until you explicitly restore the record.",
                            modifier =
                                Modifier.padding(Spacing.sm),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onTertiaryContainer
                        )
                    }
                }

                if (legacyName) {
                    Text(
                        "This older record does not have structured first/last-name fields yet. Enter them once to modernize the record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = lastName,
                    onValueChange = {
                        lastName = it.take(100)
                    },
                    label = { Text("Last name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = {
                        firstName = it.take(100)
                    },
                    label = { Text("First name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = middleInitial,
                        onValueChange = {
                            middleInitial = it.take(10)
                        },
                        label = { Text("Middle") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = {
                            suffix = it.take(30)
                        },
                        label = { Text("Suffix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = studentNumber,
                    onValueChange = {
                        studentNumber = it.take(80)
                    },
                    label = {
                        Text("Student number")
                    },
                    supportingText = {
                        Text(
                            "Optional, but must be unique within the school when provided."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        lastName,
                        firstName,
                        middleInitial,
                        suffix,
                        studentNumber
                    )
                },
                enabled =
                    !busy &&
                        lastName.isNotBlank() &&
                        firstName.isNotBlank()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save details")
                }
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
private fun PlacementDialog(
    student: StudentLifecycleItem,
    sections: List<GradeSection>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var selectedId by remember(student.studentId) {
        mutableStateOf(
            student.gradeSectionId
                .takeIf { current ->
                    sections.any { it.id == current }
                }
                .orEmpty()
        )
    }
    var expanded by remember { mutableStateOf(false) }
    val selected =
        sections.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Reassign grade & section") },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    student.fullName,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Current record: Grade " +
                        student.grade +
                        " · " +
                        student.section,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (
                    student.gradeSectionId.isBlank() ||
                    sections.none {
                        it.id == student.gradeSectionId
                    }
                ) {
                    Surface(
                        color =
                            MaterialTheme.colorScheme
                                .tertiaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "This student uses an older placement. Choose one of the active sections from the current school year.",
                            modifier =
                                Modifier.padding(Spacing.sm),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onTertiaryContainer
                        )
                    }
                }

                if (sections.isEmpty()) {
                    Text(
                        "No active sections are configured for the current academic year.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            if (!busy) expanded = !expanded
                        }
                    ) {
                        OutlinedTextField(
                            value =
                                selected?.displayName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("New grade & section") },
                            placeholder = {
                                Text("Choose current section")
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults
                                    .TrailingIcon(expanded)
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
                            sections.forEach { section ->
                                DropdownMenuItem(
                                    text = {
                                        Text(section.displayName)
                                    },
                                    onClick = {
                                        selectedId = section.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    "Saving updates the student's grade, section, structured section ID, and current academic year together.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedId) },
                enabled =
                    !busy &&
                        selectedId.isNotBlank() &&
                        selectedId != student.gradeSectionId
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save placement")
                }
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
