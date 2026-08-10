package com.pickuppass.android.ui.teacher.students

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherStudentsScreen(
    viewModel: TeacherStudentsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onRegisterParent: (studentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.justCreatedStudentId) {
        uiState.justCreatedStudentId?.let { studentId ->
            showAddSheet = false
            viewModel.consumeJustCreatedStudentId()
            onRegisterParent(studentId) // jump straight into registering their parent
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandedTitle("Students", uiState.school) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onGoToExitLogs) {
                        Icon(Icons.Filled.History, contentDescription = "Dismissal History")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add student") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Only worth showing a search box once there's an actual roster
            // to search — an empty box over an empty/blocked state would
            // just be visual noise with nothing to do.
            val canSearch = !uiState.isLoading && uiState.error == null &&
                !uiState.hasNoAssignedSections && uiState.allStudents.isNotEmpty()
            if (canSearch) {
                OutlinedTextField(
                    value = uiState.searchTerm,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search by student name...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Gentle cross-fade between the roster's states so switching
                // (e.g. search narrowing to no-results, or first load resolving)
                // settles rather than snaps.
                val contentPhase = when {
                    uiState.isLoading -> "loading"
                    uiState.error != null -> "error"
                    uiState.hasNoAssignedSections -> "noSections"
                    uiState.allStudents.isEmpty() -> "emptyRoster"
                    uiState.groupedStudents.isEmpty() -> "noResults"
                    else -> "list"
                }
                Crossfade(targetState = contentPhase, animationSpec = tween(250), label = "rosterPhase") { phase ->
                    when (phase) {
                        "loading" -> FullScreenLoading()
                        "error" -> Box(Modifier.padding(Spacing.lg)) { ErrorBanner(uiState.error ?: "") }
                        "noSections" -> NoSectionsAssignedState()
                        "emptyRoster" -> EmptyRoster()
                        "noResults" -> NoSearchResultsState()
                        else -> LazyColumn(
                            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            uiState.groupedStudents.forEach { gradeGroup ->
                                item(key = "grade-${gradeGroup.grade}") {
                                    Text(
                                        "Grade ${gradeGroup.grade.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                                    )
                                }
                                gradeGroup.sections.forEach { sectionGroup ->
                                    item(key = "section-${gradeGroup.grade}-${sectionGroup.section}") {
                                        Text(
                                            "Section ${sectionGroup.section.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = Spacing.xs, start = Spacing.xs)
                                        )
                                    }
                                    items(sectionGroup.students, key = { it.id }) { student ->
                                        StudentRow(student = student, onRegisterParent = { onRegisterParent(student.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = addSheetState
        ) {
            AddStudentSheetContent(
                isSubmitting = uiState.isSubmitting,
                formError = uiState.formError,
                onSubmit = { lastName, firstName, mi, suffix, grade, section ->
                    viewModel.addStudent(lastName, firstName, mi, suffix, grade, section)
                }
            )
        }
    }
}

@Composable
private fun StudentRow(student: Student, onRegisterParent: () -> Unit) {
    val guardianCount = student.guardianUids.size

    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Grade ${student.grade.ifBlank { "-" }} · Section ${student.section.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("$guardianCount guardian${if (guardianCount == 1) "" else "s"}") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (guardianCount > 0)
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    )
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onRegisterParent, modifier = Modifier.fillMaxWidth()) {
                Text(if (guardianCount > 0) "Register Another Guardian" else "Register Parent")
            }
        }
    }
}

@Composable
private fun EmptyRoster() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No students yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap + to add your first student.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

/** Distinct from EmptyRoster — the roster isn't empty, the search just didn't match anything. */
@Composable
private fun NoSearchResultsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No students found", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try a different name or check your spelling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

/** Teacher-only: they're signed in fine, but no admin has assigned them a section yet. */
@Composable
private fun NoSectionsAssignedState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No sections assigned yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ask your school admin to assign you a grade and section before students will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

@Composable
private fun AddStudentSheetContent(
    isSubmitting: Boolean,
    formError: String?,
    onSubmit: (lastName: String, firstName: String, middleInitial: String, suffix: String, grade: String, section: String) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }

    // Scrollable so the form stays usable above the keyboard on short screens.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl)
    ) {
        Text(
            "Add a Student",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.md))
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First name") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        Row {
            OutlinedTextField(
                value = middleInitial,
                onValueChange = { middleInitial = it },
                label = { Text("M.I.") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = it },
                label = { Text("Suffix") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row {
            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it },
                label = { Text("Grade") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            OutlinedTextField(
                value = section,
                onValueChange = { section = it },
                label = { Text("Section") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            )
        }
        formError?.let {
            Spacer(Modifier.height(Spacing.sm))
            ErrorBanner(it)
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = "Add Student",
            loading = isSubmitting,
            onClick = { onSubmit(lastName.trim(), firstName.trim(), middleInitial.trim(), suffix.trim(), grade.trim(), section.trim()) }
        )
    }
}
