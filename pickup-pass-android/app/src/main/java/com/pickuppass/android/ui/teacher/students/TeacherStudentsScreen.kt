package com.pickuppass.android.ui.teacher.students

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
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
            onRegisterParent(studentId)
        }
    }

    val totalStudents = uiState.allStudents.size
    val studentsWithGuardian = uiState.allStudents.count { it.guardianUids.isNotEmpty() }
    val needsGuardian = totalStudents - studentsWithGuardian

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandedTitle("Students", uiState.school) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onGoToExitLogs) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Dismissal History"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.error == null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add student") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!uiState.isLoading && uiState.error == null) {
                RosterHero(
                    totalStudents = totalStudents,
                    studentsWithGuardian = studentsWithGuardian,
                    needsGuardian = needsGuardian,
                    hasNoAssignedSections = uiState.hasNoAssignedSections
                )
            }

            val canSearch = !uiState.isLoading &&
                uiState.error == null &&
                !uiState.hasNoAssignedSections &&
                uiState.allStudents.isNotEmpty()

            if (canSearch) {
                OutlinedTextField(
                    value = uiState.searchTerm,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search by student name") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val contentPhase = when {
                    uiState.isLoading -> "loading"
                    uiState.error != null -> "error"
                    uiState.hasNoAssignedSections -> "noSections"
                    uiState.allStudents.isEmpty() -> "emptyRoster"
                    uiState.groupedStudents.isEmpty() -> "noResults"
                    else -> "list"
                }

                Crossfade(
                    targetState = contentPhase,
                    animationSpec = tween(220),
                    label = "rosterPhase"
                ) { phase ->
                    when (phase) {
                        "loading" -> FullScreenLoading()
                        "error" -> RosterErrorState(
                            message = uiState.error ?: "Couldn't load students",
                            onRetry = viewModel::load
                        )
                        "noSections" -> NoSectionsAssignedState()
                        "emptyRoster" -> EmptyRoster(onAdd = { showAddSheet = true })
                        "noResults" -> NoSearchResultsState(
                            searchTerm = uiState.searchTerm,
                            onClear = { viewModel.onSearchChange("") }
                        )
                        else -> LazyColumn(
                            contentPadding = PaddingValues(
                                start = Spacing.md,
                                end = Spacing.md,
                                top = Spacing.xs,
                                bottom = 108.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            uiState.groupedStudents.forEach { gradeGroup ->
                                item(key = "grade-${gradeGroup.grade}") {
                                    GradeHeader(
                                        grade = gradeGroup.grade,
                                        count = gradeGroup.sections.sumOf { it.students.size }
                                    )
                                }

                                gradeGroup.sections.forEach { sectionGroup ->
                                    item(key = "section-${gradeGroup.grade}-${sectionGroup.section}") {
                                        SectionHeader(
                                            section = sectionGroup.section,
                                            count = sectionGroup.students.size
                                        )
                                    }

                                    items(sectionGroup.students, key = { it.id }) { student ->
                                        StudentCard(
                                            student = student,
                                            onRegisterParent = { onRegisterParent(student.id) }
                                        )
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
            onDismissRequest = {
                if (!uiState.isSubmitting) showAddSheet = false
            },
            sheetState = addSheetState
        ) {
            AddStudentSheetContent(
                isSubmitting = uiState.isSubmitting,
                formError = uiState.formError,
                onCancel = { showAddSheet = false },
                onSubmit = { lastName, firstName, mi, suffix, grade, section ->
                    viewModel.addStudent(
                        lastName,
                        firstName,
                        mi,
                        suffix,
                        grade,
                        section
                    )
                }
            )
        }
    }
}

@Composable
private fun RosterHero(
    totalStudents: Int,
    studentsWithGuardian: Int,
    needsGuardian: Int,
    hasNoAssignedSections: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 5.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "DISMISSAL ROSTER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f)
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = if (hasNoAssignedSections) {
                    "Your assigned roster will appear here"
                } else {
                    "$totalStudents student${if (totalStudents == 1) "" else "s"} in this roster"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Review guardian readiness, find students quickly, and continue into pickup-contact registration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                RosterMetric(
                    value = totalStudents.toString(),
                    label = "Students",
                    modifier = Modifier.weight(1f)
                )
                RosterMetric(
                    value = studentsWithGuardian.toString(),
                    label = "Ready",
                    modifier = Modifier.weight(1f)
                )
                RosterMetric(
                    value = needsGuardian.toString(),
                    label = "Needs guardian",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RosterMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GradeHeader(grade: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Grade ${grade.ifBlank { "—" }}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "$count student${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(section: String, count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "Section ${section.ifBlank { "—" }} · $count",
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StudentCard(
    student: Student,
    onRegisterParent: () -> Unit
) {
    val guardianCount = student.guardianUids.size

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = studentInitials(student.fullName),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.fullName.ifBlank { "Unnamed student" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Grade ${student.grade.ifBlank { "—" }} · Section ${student.section.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GuardianReadinessBadge(guardianCount)
            }

            Spacer(Modifier.height(Spacing.md))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (guardianCount > 0) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (guardianCount > 0) {
                            Icons.Filled.People
                        } else {
                            Icons.Filled.WarningAmber
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (guardianCount > 0) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Text(
                        text = if (guardianCount > 0) {
                            "$guardianCount authorized guardian${if (guardianCount == 1) "" else "s"} on file"
                        } else {
                            "No authorized guardian on file yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (guardianCount > 0) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRegisterParent,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(
                        if (guardianCount > 0) {
                            "Add guardian"
                        } else {
                            "Register parent"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuardianReadinessBadge(guardianCount: Int) {
    Surface(
        shape = CircleShape,
        color = if (guardianCount > 0) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Text(
            text = if (guardianCount > 0) "Ready" else "Action needed",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (guardianCount > 0) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        )
    }
}

@Composable
private fun EmptyRoster(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            "No students yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Add the first student to begin building this dismissal roster.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = onAdd,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Add student")
        }
    }
}

@Composable
private fun NoSearchResultsState(
    searchTerm: String,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "No students found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (searchTerm.isBlank()) {
                "Try another search."
            } else {
                "No roster matches “$searchTerm”."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text("Clear search")
        }
    }
}

@Composable
private fun NoSectionsAssignedState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "No sections assigned yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Ask your school admin to assign your grade and section. Your roster remains scoped to those assignments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RosterErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ErrorBanner(message)
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun AddStudentSheetContent(
    isSubmitting: Boolean,
    formError: String?,
    onCancel: () -> Unit,
    onSubmit: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        grade: String,
        section: String
    ) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl
            )
    ) {
        Text(
            "Add student",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Create the roster record first. PickupPass will continue directly to parent / guardian registration after a successful save.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = middleInitial,
                onValueChange = { middleInitial = it },
                label = { Text("M.I.") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = it },
                label = { Text("Suffix") },
                singleLine = true,
                modifier = Modifier.weight(2f)
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it },
                label = { Text("Grade") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = section,
                onValueChange = { section = it },
                label = { Text("Section") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        formError?.let { message ->
            Spacer(Modifier.height(Spacing.sm))
            ErrorBanner(message)
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.width(Spacing.sm))

            Button(
                onClick = {
                    onSubmit(
                        lastName.trim(),
                        firstName.trim(),
                        middleInitial.trim(),
                        suffix.trim(),
                        grade.trim(),
                        section.trim()
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier.heightIn(min = 46.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (isSubmitting) "Adding…" else "Add student")
            }
        }
    }
}

private fun studentInitials(fullName: String): String {
    val parts = fullName
        .replace(",", " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (parts.isEmpty()) return "S"

    val first = parts.first().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    val second = parts.drop(1).firstOrNull()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()

    return (first + second).ifBlank { "S" }
}
