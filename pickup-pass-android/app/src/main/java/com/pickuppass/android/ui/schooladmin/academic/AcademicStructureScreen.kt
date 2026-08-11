package com.pickuppass.android.ui.schooladmin.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                title = { Text("School Year & Sections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    "Use one structured school year and grade/section list across student records and teacher assignments. Archived sections stay in historical records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.error?.let { item { ErrorBanner(it) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Academic Years", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { showYearDialog = true }) { Text("Add year") }
                }
            }
            if (state.years.isEmpty()) {
                item { EmptyCard("No academic year yet", "Create the current school year first.") }
            } else {
                items(state.years, key = { it.id }) { year ->
                    ElevatedCard {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(year.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (year.isCurrent) {
                                        Spacer(Modifier.width(Spacing.xs))
                                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                val dates = listOf(year.startDate, year.endDate).filter { it.isNotBlank() }.joinToString(" – ")
                                if (dates.isNotBlank()) Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!year.isCurrent) TextButton(onClick = { viewModel.setCurrentYear(year.id) }) { Text("Set current") }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Grade & Sections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        enabled = state.years.isNotEmpty(),
                        onClick = { showSectionDialog = true }
                    ) { Text("Add section") }
                }
            }
            if (state.sections.isEmpty()) {
                item { EmptyCard("No grade sections yet", "Add the sections that exist for the current school year.") }
            } else {
                items(state.sections, key = { it.id }) { gs ->
                    OutlinedCard {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Grade ${gs.gradeLevel} · ${gs.sectionName}", fontWeight = FontWeight.SemiBold)
                                Text(gs.academicYearName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = gs.active, onCheckedChange = { viewModel.setSectionActive(gs.id, it) })
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
            onDismissRequest = { showYearDialog = false },
            title = { Text("Add academic year") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. 2026–2027") }, singleLine = true)
                    OutlinedTextField(start, { start = it }, label = { Text("Start date (optional)") }, singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("End date (optional)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(current, { current = it })
                        Text("Set as current school year")
                    }
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && !state.isSaving, onClick = {
                    viewModel.createYear(name, start, end, current)
                    showYearDialog = false
                }) { Text("Create") }
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
            onDismissRequest = { showSectionDialog = false },
            title = { Text("Add grade/section") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = state.years.firstOrNull { it.id == yearId }?.name.orEmpty(),
                            onValueChange = {}, readOnly = true, label = { Text("Academic year") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.years.forEach { year ->
                                DropdownMenuItem(text = { Text(year.name) }, onClick = { yearId = year.id; expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(grade, { grade = it }, label = { Text("Grade level") }, singleLine = true)
                    OutlinedTextField(section, { section = it }, label = { Text("Section name") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = yearId.isNotBlank() && grade.isNotBlank() && section.isNotBlank() && !state.isSaving, onClick = {
                    viewModel.createSection(yearId, grade, section)
                    showSectionDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showSectionDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
