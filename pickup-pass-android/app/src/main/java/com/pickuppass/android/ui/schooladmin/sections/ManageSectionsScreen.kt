package com.pickuppass.android.ui.schooladmin.sections

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
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
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSectionsScreen(
    viewModel: ManageSectionsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<Pair<TeacherWithSections, Int>?>(null) }

    val filtered = remember(uiState.teachers, query) {
        val q = query.trim()
        if (q.isBlank()) uiState.teachers
        else uiState.teachers.filter {
            it.displayName.orEmpty().contains(q, true) ||
                it.email.orEmpty().contains(q, true) ||
                it.assignedSections.any { s -> s.grade.contains(q, true) || s.section.contains(q, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Teacher Sections", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Teaching scope & roster access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val phase = when {
            uiState.isLoading -> "loading"
            uiState.error != null && uiState.teachers.isEmpty() -> "error"
            uiState.teachers.isEmpty() -> "empty"
            else -> "list"
        }

        Crossfade(targetState = phase, animationSpec = tween(220), label = "sectionsPhase") { state ->
            when (state) {
                "loading" -> Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
                "error" -> Box(Modifier.padding(padding).padding(Spacing.lg)) { ErrorBanner(uiState.error ?: "") }
                "empty" -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Groups, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.sm))
                        Text("No teachers yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Invite a teacher before assigning sections.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
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
                                    Text("Section-scoped access", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Assign only configured sections from the current school year. Teacher broadcast and roster scope follow these assignments.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        uiState.error?.let { item { ErrorBanner(it) } }

                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Metric("Teachers", uiState.teachers.size, Modifier.weight(1f))
                                Metric("Unassigned", uiState.teachers.count { it.assignedSections.isEmpty() }, Modifier.weight(1f))
                                Metric("Sections", uiState.availableSections.size, Modifier.weight(1f))
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Filled.Search, null) },
                                label = { Text("Search teachers") },
                                placeholder = { Text("Name, email, grade or section") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.availableSections.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(
                                        "No active grade sections are configured for the current school year. Create them first in School Year & Sections.",
                                        modifier = Modifier.padding(Spacing.md),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        items(filtered, key = { it.uid }) { teacher ->
                            TeacherSectionsCard(
                                teacher = teacher,
                                availableSections = uiState.availableSections,
                                saveStatus = uiState.saveStatusByUid[teacher.uid],
                                onAddSection = { grade, section -> viewModel.addSection(teacher.uid, grade, section) },
                                onRequestRemove = { index -> pendingRemoval = teacher to index }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { (teacher, index) ->
        val section = teacher.assignedSections.getOrNull(index)
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove section assignment?") },
            text = {
                Text(
                    "Remove ${section?.let { "Grade ${it.grade} → ${it.section}" } ?: "this section"} from ${teacher.displayName ?: teacher.email ?: "this teacher"}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRemoval = null
                        viewModel.removeSection(teacher.uid, index)
                    }
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TeacherSectionsCard(
    teacher: TeacherWithSections,
    availableSections: List<GradeSection>,
    saveStatus: String?,
    onAddSection: (grade: String, section: String) -> Unit,
    onRequestRemove: (index: Int) -> Unit
) {
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf<GradeSection?>(null) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        teacher.displayName.orEmpty().trim().take(1).uppercase().ifBlank { "T" },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        teacher.displayName ?: teacher.email ?: "Teacher",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    teacher.email?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AssistChip(onClick = {}, enabled = false, label = { Text("${teacher.assignedSections.size} section${if (teacher.assignedSections.size == 1) "" else "s"}") })
            }

            Spacer(Modifier.height(Spacing.sm))

            if (teacher.assignedSections.isEmpty()) {
                Text(
                    "No sections assigned. This teacher has no section-scoped roster assignment yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                teacher.assignedSections.forEachIndexed { index, chip ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Grade ${chip.grade} → ${chip.section}", Modifier.weight(1f))
                            IconButton(onClick = { onRequestRemove(index) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove section", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            if (availableSections.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { sectionMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedSection?.displayName ?: "Choose configured section")
                    }
                    DropdownMenu(
                        expanded = sectionMenuExpanded,
                        onDismissRequest = { sectionMenuExpanded = false }
                    ) {
                        availableSections
                            .filterNot { option ->
                                teacher.assignedSections.any {
                                    it.grade.equals(option.gradeLevel, true) &&
                                        it.section.equals(option.sectionName, true)
                                }
                            }
                            .forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        selectedSection = option
                                        sectionMenuExpanded = false
                                    }
                                )
                            }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                FilledTonalButton(
                    enabled = selectedSection != null,
                    onClick = {
                        selectedSection?.let { onAddSection(it.gradeLevel, it.sectionName) }
                        selectedSection = null
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Assign section") }
            }

            saveStatus?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
