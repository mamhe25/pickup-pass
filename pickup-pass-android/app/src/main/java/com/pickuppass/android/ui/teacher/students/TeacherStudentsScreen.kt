package com.pickuppass.android.ui.teacher.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherStudentsScreen(
    viewModel: TeacherStudentsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onRegisterParent: (studentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.justCreatedStudentId) {
        uiState.justCreatedStudentId?.let { studentId ->
            showAddDialog = false
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add student")
            }
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> FullScreenLoading()
                    uiState.error != null -> Box(Modifier.padding(24.dp)) { ErrorBanner(uiState.error!!) }
                    uiState.hasNoAssignedSections -> NoSectionsAssignedState()
                    uiState.allStudents.isEmpty() -> EmptyRoster()
                    uiState.groupedStudents.isEmpty() -> NoSearchResultsState()
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.groupedStudents.forEach { gradeGroup ->
                            item(key = "grade-${gradeGroup.grade}") {
                                Text(
                                    "Grade ${gradeGroup.grade.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                                )
                            }
                            gradeGroup.sections.forEach { sectionGroup ->
                                item(key = "section-${gradeGroup.grade}-${sectionGroup.section}") {
                                    Text(
                                        "Section ${sectionGroup.section.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
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

    if (showAddDialog) {
        AddStudentDialog(
            isSubmitting = uiState.isSubmitting,
            formError = uiState.formError,
            onDismiss = { showAddDialog = false },
            onSubmit = { lastName, firstName, mi, suffix, grade, section ->
                viewModel.addStudent(lastName, firstName, mi, suffix, grade, section)
            }
        )
    }
}

@Composable
private fun StudentRow(student: Student, onRegisterParent: () -> Unit) {
    val guardianCount = student.guardianUids.size

    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
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
            Spacer(Modifier.height(10.dp))
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No students yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap + to add your first student.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Distinct from EmptyRoster — the roster isn't empty, the search just didn't match anything. */
@Composable
private fun NoSearchResultsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No students found", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try a different name or check your spelling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Teacher-only: they're signed in fine, but no admin has assigned them a section yet. */
@Composable
private fun NoSectionsAssignedState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No sections assigned yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ask your school admin to assign you a grade and section before students will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AddStudentDialog(
    isSubmitting: Boolean,
    formError: String?,
    onDismiss: () -> Unit,
    onSubmit: (lastName: String, firstName: String, middleInitial: String, suffix: String, grade: String, section: String) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a Student") },
        text = {
            Column {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = middleInitial,
                        onValueChange = { middleInitial = it },
                        label = { Text("M.I.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text("Suffix") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = grade,
                        onValueChange = { grade = it },
                        label = { Text("Grade") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                formError?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(it)
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Add",
                loading = isSubmitting,
                onClick = { onSubmit(lastName.trim(), firstName.trim(), middleInitial.trim(), suffix.trim(), grade.trim(), section.trim()) },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
