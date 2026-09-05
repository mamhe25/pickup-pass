package com.pickuppass.android.ui.schooladmin.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.AcademicYear
import com.pickuppass.android.data.model.GradeSection
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicStructureScreen(
    viewModel: AcademicStructureViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateYear by remember { mutableStateOf(false) }
    var editingYear by remember { mutableStateOf<AcademicYear?>(null) }
    var yearStatusAction by remember { mutableStateOf<Pair<AcademicYear, Boolean>?>(null) }
    var deletingYear by remember { mutableStateOf<AcademicYear?>(null) }

    var showCreateSection by remember { mutableStateOf(false) }
    var editingSection by remember { mutableStateOf<GradeSection?>(null) }
    var sectionStatusAction by remember { mutableStateOf<Pair<GradeSection, Boolean>?>(null) }
    var deletingSection by remember { mutableStateOf<GradeSection?>(null) }

    LaunchedEffect(state.completedAction) {
        when (val action = state.completedAction) {
            "year-created" -> showCreateYear = false
            "section-created" -> showCreateSection = false
            else -> {
                if (action?.startsWith("year-updated:") == true) editingYear = null
                if (action?.startsWith("year-status:") == true) yearStatusAction = null
                if (action?.startsWith("year-deleted:") == true) deletingYear = null
                if (action?.startsWith("section-updated:") == true) editingSection = null
                if (action?.startsWith("section-status:") == true) sectionStatusAction = null
                if (action?.startsWith("section-deleted:") == true) deletingSection = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("School Year & Sections", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Academic structure",
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
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 820.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "intro") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("One source of truth", fontWeight = FontWeight.Bold)
                            Text(
                                "Academic years and grade sections are reused by student records, teacher assignments, promotion, and reporting. Records still referenced by students or staff are archived instead of hard-deleted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.error?.let { message ->
                    item(key = "error") { ErrorBanner(message) }
                }
                state.message?.let { message ->
                    item(key = "success") { SuccessBanner(message) }
                }

                item(key = "metrics") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        MetricCard(
                            "Academic years",
                            state.years.size.toString(),
                            Modifier.weight(1f)
                        )
                        MetricCard(
                            "Active sections",
                            state.sections.count { it.active }.toString(),
                            Modifier.weight(1f)
                        )
                    }
                }

                item(key = "years_header") {
                    SectionHeader(
                        title = "Academic years",
                        subtitle = "Edit dates, switch the current year, archive history, or remove unused setup records.",
                        actionLabel = "Add year",
                        enabled = !state.isSaving,
                        onAction = {
                            viewModel.clearFeedback()
                            showCreateYear = true
                        }
                    )
                }

                if (state.years.isEmpty()) {
                    item(key = "years_empty") {
                        EmptyCard(
                            "No academic year yet",
                            "Create the current school year before adding sections."
                        )
                    }
                } else {
                    items(state.years, key = { "year-${it.id}" }) { year ->
                        AcademicYearCard(
                            year = year,
                            busy = state.isSaving,
                            onEdit = {
                                viewModel.clearFeedback()
                                editingYear = year
                            },
                            onSetCurrent = {
                                viewModel.clearFeedback()
                                viewModel.setCurrentYear(year.id)
                            },
                            onSetActive = { active ->
                                viewModel.clearFeedback()
                                yearStatusAction = year to active
                            },
                            onDelete = {
                                viewModel.clearFeedback()
                                deletingYear = year
                            }
                        )
                    }
                }

                item(key = "sections_header") {
                    SectionHeader(
                        title = "Grade & sections",
                        subtitle = "Rename sections safely, archive retired sections, or remove configuration that has never been used.",
                        actionLabel = "Add section",
                        enabled = state.years.any { it.status.lowercase() != "archived" } && !state.isSaving,
                        onAction = {
                            viewModel.clearFeedback()
                            showCreateSection = true
                        }
                    )
                }

                if (state.sections.isEmpty()) {
                    item(key = "sections_empty") {
                        EmptyCard(
                            "No grade sections yet",
                            "Add the sections used by your school."
                        )
                    }
                } else {
                    items(
                        state.sections.sortedWith(
                            compareBy(
                                { it.academicYearName },
                                { it.gradeLevel },
                                { it.sectionName }
                            )
                        ),
                        key = { "section-${it.id}" }
                    ) { section ->
                        GradeSectionCard(
                            section = section,
                            currentYearId = state.currentYearId,
                            busy = state.isSaving,
                            onEdit = {
                                viewModel.clearFeedback()
                                editingSection = section
                            },
                            onSetActive = { active ->
                                viewModel.clearFeedback()
                                sectionStatusAction = section to active
                            },
                            onDelete = {
                                viewModel.clearFeedback()
                                deletingSection = section
                            }
                        )
                    }
                }

                item(key = "safety_note") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
                    ) {
                        Column(
                            Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Text("Data safety", fontWeight = FontWeight.Bold)
                            Text(
                                "PickupPass refuses destructive actions while students or teacher assignments still depend on the record. Archive operational history instead of deleting it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateYear) {
        AcademicYearEditorDialog(
            title = "Add academic year",
            initial = null,
            busy = state.isSaving,
            error = state.error,
            allowSetCurrent = true,
            defaultSetCurrent = state.years.isEmpty(),
            onDismiss = {
                if (!state.isSaving) {
                    showCreateYear = false
                    viewModel.clearFeedback()
                }
            },
            onSave = { name, start, end, current ->
                viewModel.createYear(name, start, end, current)
            }
        )
    }

    editingYear?.let { year ->
        AcademicYearEditorDialog(
            title = "Edit academic year",
            initial = year,
            busy = state.isSaving,
            error = state.error,
            allowSetCurrent = false,
            onDismiss = {
                if (!state.isSaving) {
                    editingYear = null
                    viewModel.clearFeedback()
                }
            },
            onSave = { name, start, end, _ ->
                viewModel.updateYear(year.id, name, start, end)
            }
        )
    }

    yearStatusAction?.let { (year, active) ->
        ConfirmationDialog(
            title = if (active) "Reactivate academic year?" else "Archive academic year?",
            message = if (active) {
                "${year.name} will become available for configuration again."
            } else {
                "Archive ${year.name}? The current year cannot be archived, and all grade sections in this year must already be archived."
            },
            confirmLabel = if (active) "Reactivate" else "Archive",
            destructive = !active,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    yearStatusAction = null
                    viewModel.clearFeedback()
                }
            },
            onConfirm = { viewModel.setYearActive(year.id, active) }
        )
    }

    deletingYear?.let { year ->
        ConfirmationDialog(
            title = "Delete academic year permanently?",
            message = "${year.name} will be permanently removed only when no grade section or student record currently references it. Historical dismissal snapshots are not deleted.",
            confirmLabel = "Delete unused year",
            destructive = true,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    deletingYear = null
                    viewModel.clearFeedback()
                }
            },
            onConfirm = { viewModel.deleteYear(year.id) }
        )
    }

    if (showCreateSection) {
        GradeSectionEditorDialog(
            title = "Add grade section",
            initial = null,
            years = state.years.filter { it.status.lowercase() != "archived" },
            currentYearId = state.currentYearId,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    showCreateSection = false
                    viewModel.clearFeedback()
                }
            },
            onSave = { yearId, grade, section ->
                viewModel.createSection(yearId, grade, section)
            }
        )
    }

    editingSection?.let { section ->
        GradeSectionEditorDialog(
            title = "Edit grade section",
            initial = section,
            years = state.years,
            currentYearId = state.currentYearId,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    editingSection = null
                    viewModel.clearFeedback()
                }
            },
            onSave = { _, grade, name ->
                viewModel.updateSection(section.id, grade, name)
            }
        )
    }

    sectionStatusAction?.let { (section, active) ->
        ConfirmationDialog(
            title = if (active) "Reactivate grade section?" else "Archive grade section?",
            message = if (active) {
                "Grade ${section.gradeLevel} → ${section.sectionName} will become selectable again. Its academic year must also be active."
            } else {
                "Archive Grade ${section.gradeLevel} → ${section.sectionName}? PickupPass will refuse if active students or current teacher assignments still depend on it."
            },
            confirmLabel = if (active) "Reactivate" else "Archive",
            destructive = !active,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    sectionStatusAction = null
                    viewModel.clearFeedback()
                }
            },
            onConfirm = { viewModel.setSectionActive(section.id, active) }
        )
    }

    deletingSection?.let { section ->
        ConfirmationDialog(
            title = "Delete grade section permanently?",
            message = "Grade ${section.gradeLevel} → ${section.sectionName} will be deleted only if no student record or current teacher assignment references it. Historical dismissal snapshots are not deleted.",
            confirmLabel = "Delete unused section",
            destructive = true,
            busy = state.isSaving,
            error = state.error,
            onDismiss = {
                if (!state.isSaving) {
                    deletingSection = null
                    viewModel.clearFeedback()
                }
            },
            onConfirm = { viewModel.deleteSection(section.id) }
        )
    }
}

@Composable
private fun AcademicYearCard(
    year: AcademicYear,
    busy: Boolean,
    onEdit: () -> Unit,
    onSetCurrent: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(year.id) { mutableStateOf(false) }
    val archived = year.status.equals("archived", ignoreCase = true)

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (year.isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    if (year.isCurrent) Icons.Filled.CheckCircle else Icons.Filled.School,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                    tint = if (year.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        year.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (year.isCurrent) {
                        Spacer(Modifier.width(Spacing.xs))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Current") }
                        )
                    } else if (archived) {
                        Spacer(Modifier.width(Spacing.xs))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Archived") }
                        )
                    }
                }

                val dates = listOf(year.startDate, year.endDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" – ")
                Text(
                    dates.ifBlank { "Dates not specified" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Academic year actions")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )

                    if (!year.isCurrent) {
                        DropdownMenuItem(
                            text = { Text("Set as current") },
                            leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                            onClick = {
                                menuExpanded = false
                                onSetCurrent()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (archived) "Reactivate" else "Archive") },
                            leadingIcon = {
                                Icon(
                                    if (archived) Icons.Filled.Restore else Icons.Filled.Archive,
                                    null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onSetActive(archived)
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                                                        onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeSectionCard(
    section: GradeSection,
    currentYearId: String?,
    busy: Boolean,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(section.id) { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Grade ${section.gradeLevel} → ${section.sectionName}",
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        section.academicYearName.ifBlank { "Academic year" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (section.academicYearId == currentYearId) {
                        Text(
                            " · Current year",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    if (section.active) "Active" else "Archived",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (section.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Grade section actions")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (section.active) "Archive" else "Reactivate") },
                        leadingIcon = {
                            Icon(
                                if (section.active) Icons.Filled.Archive else Icons.Filled.Restore,
                                null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSetActive(!section.active)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicYearEditorDialog(
    title: String,
    initial: AcademicYear?,
    busy: Boolean,
    error: String?,
    allowSetCurrent: Boolean,
    defaultSetCurrent: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, start: String, end: String, current: Boolean) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var start by remember(initial?.id) { mutableStateOf(initial?.startDate.orEmpty()) }
    var end by remember(initial?.id) { mutableStateOf(initial?.endDate.orEmpty()) }
    var current by remember(initial?.id, defaultSetCurrent) {
        mutableStateOf(initial?.isCurrent ?: defaultSetCurrent)
    }

    val dateError = remember(start, end) {
        validateDates(start, end)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (initial == null) Icons.Filled.Add else Icons.Filled.Edit, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Academic year name") },
                    placeholder = { Text("2026–2027") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.take(10) },
                    label = { Text("Start date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    supportingText = { Text("Optional · YYYY-MM-DD") },
                    singleLine = true,
                    isError = dateError != null,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it.take(10) },
                    label = { Text("End date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    supportingText = { Text(dateError ?: "Optional · YYYY-MM-DD") },
                    singleLine = true,
                    isError = dateError != null,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                if (allowSetCurrent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = current,
                            onCheckedChange = { current = it },
                            enabled = !busy
                        )
                        Text("Set as current school year")
                    }
                }

                if (initial != null) {
                    Text(
                        "Renaming a year also updates its linked student and section labels. Historical dismissal snapshots remain unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && dateError == null && !busy,
                onClick = { onSave(name, start, end, current) }
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(if (initial == null) "Create year" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeSectionEditorDialog(
    title: String,
    initial: GradeSection?,
    years: List<AcademicYear>,
    currentYearId: String?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (yearId: String, grade: String, section: String) -> Unit
) {
    var yearId by remember(initial?.id) {
        mutableStateOf(
            initial?.academicYearId
                ?: currentYearId
                ?: years.firstOrNull()?.id.orEmpty()
        )
    }
    var grade by remember(initial?.id) { mutableStateOf(initial?.gradeLevel.orEmpty()) }
    var section by remember(initial?.id) { mutableStateOf(initial?.sectionName.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    val selectedYear = years.firstOrNull { it.id == yearId }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (initial == null) Icons.Filled.Add else Icons.Filled.Edit, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (initial == null) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!busy) expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedYear?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Academic year") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            year.name +
                                                if (year.id == currentYearId) " · Current" else ""
                                        )
                                    },
                                    onClick = {
                                        yearId = year.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedYear?.name ?: initial.academicYearName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Academic year") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade level") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Section name") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                if (initial != null) {
                    Text(
                        "If this section is already used, PickupPass safely updates linked student records and current teacher assignments. Historical dismissal snapshots are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = yearId.isNotBlank() &&
                    grade.isNotBlank() &&
                    section.isNotBlank() &&
                    !busy,
                onClick = { onSave(yearId, grade, section) }
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(if (initial == null) "Add section" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(message)
                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = onAction, enabled = enabled) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(actionLabel)
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    body: String
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.School,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun validateDates(start: String, end: String): String? {
    val startParsed = try {
        start.trim().takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (_: Exception) {
        return "Start date must use YYYY-MM-DD"
    }

    val endParsed = try {
        end.trim().takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (_: Exception) {
        return "End date must use YYYY-MM-DD"
    }

    return if (
        startParsed != null &&
        endParsed != null &&
        startParsed.isAfter(endParsed)
    ) {
        "Start date cannot be after end date"
    } else {
        null
    }
}
