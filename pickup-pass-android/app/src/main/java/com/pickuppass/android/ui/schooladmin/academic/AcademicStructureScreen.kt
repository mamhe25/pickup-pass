package com.pickuppass.android.ui.schooladmin.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicStructureScreen(
    viewModel: AcademicStructureViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showYearDialog by remember { mutableStateOf(false) }
    var showSectionDialog by remember { mutableStateOf(false) }

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
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text("One source of truth", fontWeight = FontWeight.Bold)
                            Text(
                                "Academic years and grade sections are reused by student records, teacher assignments, promotion, and reporting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.message?.let { item { SuccessBanner(it) } }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        MetricCard("Academic years", state.years.size.toString(), Modifier.weight(1f))
                        MetricCard("Active sections", state.sections.count { it.active }.toString(), Modifier.weight(1f))
                    }
                }

                item {
                    SectionHeader(
                        title = "Academic years",
                        subtitle = "Set the active school year used for current operations.",
                        actionLabel = "Add year",
                        enabled = !state.isSaving,
                        onAction = { showYearDialog = true }
                    )
                }

                if (state.years.isEmpty()) {
                    item { EmptyCard("No academic year yet", "Create the current school year before adding sections.") }
                } else {
                    items(state.years, key = { it.id }) { year ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (year.isCurrent) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Icon(
                                        if (year.isCurrent) Icons.Filled.CheckCircle else Icons.Filled.School,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp).size(22.dp),
                                        tint = if (year.isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(year.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (year.isCurrent) {
                                            Spacer(Modifier.width(Spacing.xs))
                                            AssistChip(onClick = {}, enabled = false, label = { Text("Current") })
                                        }
                                    }
                                    val dates = listOf(year.startDate, year.endDate).filter { it.isNotBlank() }.joinToString(" – ")
                                    Text(
                                        dates.ifBlank { "Dates not specified" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!year.isCurrent) {
                                    TextButton(
                                        enabled = !state.isSaving,
                                        onClick = { viewModel.setCurrentYear(year.id) }
                                    ) { Text("Set current") }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Grade & sections",
                        subtitle = "Keep only current operational sections active.",
                        actionLabel = "Add section",
                        enabled = state.years.isNotEmpty() && !state.isSaving,
                        onAction = { showSectionDialog = true }
                    )
                }

                if (state.sections.isEmpty()) {
                    item { EmptyCard("No grade sections yet", "Add the sections used by your school.") }
                } else {
                    items(
                        state.sections.sortedWith(compareBy({ it.academicYearName }, { it.gradeLevel }, { it.sectionName })),
                        key = { it.id }
                    ) { gs ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Grade ${gs.gradeLevel} → ${gs.sectionName}", fontWeight = FontWeight.Bold)
                                    Text(
                                        gs.academicYearName.ifBlank { "Academic year" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (gs.active) "Active" else "Archived",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (gs.active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = gs.active,
                                        onCheckedChange = { viewModel.setSectionActive(gs.id, it) },
                                        enabled = !state.isSaving
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showYearDialog) {
        var name by remember { mutableStateOf("") }
        var start by remember { mutableStateOf("") }
        var end by remember { mutableStateOf("") }
        var current by remember { mutableStateOf(state.years.isEmpty()) }
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) showYearDialog = false },
            icon = { Icon(Icons.Filled.School, null) },
            title = { Text("Add academic year") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Create the school year first, then attach grade sections to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. 2026–2027") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it }, label = { Text("Start date (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(end, { end = it }, label = { Text("End date (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(current, { current = it })
                        Text("Set as current school year")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !state.isSaving,
                    onClick = {
                        viewModel.createYear(name, start, end, current)
                        showYearDialog = false
                    }
                ) { Text("Create year") }
            },
            dismissButton = { TextButton(onClick = { showYearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSectionDialog) {
        var yearId by remember { mutableStateOf(state.currentYearId ?: state.years.firstOrNull()?.id.orEmpty()) }
        var grade by remember { mutableStateOf("") }
        var section by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) showSectionDialog = false },
            icon = { Icon(Icons.Filled.Add, null) },
            title = { Text("Add grade section") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = state.years.firstOrNull { it.id == yearId }?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Academic year") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year.name) },
                                    onClick = { yearId = year.id; expanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(grade, { grade = it }, label = { Text("Grade level") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(section, { section = it }, label = { Text("Section name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = yearId.isNotBlank() && grade.isNotBlank() && section.isNotBlank() && !state.isSaving,
                    onClick = {
                        viewModel.createSection(yearId, grade, section)
                        showSectionDialog = false
                    }
                ) { Text("Add section") }
            },
            dismissButton = { TextButton(onClick = { showSectionDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onAction, enabled = enabled) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(actionLabel)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
