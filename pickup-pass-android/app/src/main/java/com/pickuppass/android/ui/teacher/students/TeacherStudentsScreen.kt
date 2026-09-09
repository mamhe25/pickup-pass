package com.pickuppass.android.ui.teacher.students

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.AcademicPlacementOption
import com.pickuppass.android.data.model.primaryGuardianUidCompat
import com.pickuppass.android.data.repository.UserRole
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherStudentsScreen(
    viewModel: TeacherStudentsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onOpenStudentLifecycle: () -> Unit = {},
    onRegisterParent: (studentId: String) -> Unit,
    onManageGuardians: (studentId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }

    val addSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(uiState.justCreatedStudentId) {
        uiState.justCreatedStudentId?.let { studentId ->
            showAddSheet = false
            viewModel.consumeJustCreatedStudentId()
            onRegisterParent(studentId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Students",
                subtitle =
                    uiState.school
                        ?.schoolName
                        ?.takeIf { it.isNotBlank() }
                        ?: if (uiState.role == UserRole.Teacher) {
                            "Assigned roster"
                        } else {
                            "School-wide roster"
                        },
                onBack = onBack,
                actions = {
                    if (
                        uiState.role ==
                            UserRole.SchoolAdmin
                    ) {
                        IconButton(
                            onClick =
                                onOpenStudentLifecycle
                        ) {
                            Icon(
                                Icons.Filled.ManageAccounts,
                                contentDescription =
                                    "Manage student records"
                            )
                        }
                    }
                    IconButton(onClick = onGoToExitLogs) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Dismissal history"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (
                !uiState.isLoading &&
                uiState.error == null &&
                !uiState.hasNoAssignedSections &&
                uiState.availablePlacements.isNotEmpty()
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.clearFormFeedback()
                        showAddSheet = true
                    }
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add student"
                    )
                }
            }
        }
    ) { padding ->
        PickupPassPullToRefresh(
            refreshing = uiState.isLoading,
            onRefresh = viewModel::load,
            enabled = !uiState.isLoading && !uiState.isSubmitting,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading ->
                    FullScreenLoading()

                uiState.error != null ->
                    RosterErrorState(
                        message = uiState.error ?: "Couldn't load students",
                        onRetry = viewModel::load
                    )

                uiState.hasNoAssignedSections ->
                    NoSectionsAssignedState()

                else ->
                    TeacherRosterContent(
                        uiState = uiState,
                        isTeacherAccount =
                            uiState.role == UserRole.Teacher,
                        onSearchChange = viewModel::onSearchChange,
                        onPlacementFilterChange = viewModel::onPlacementFilterChange,
                        onRegisterParent = onRegisterParent,
                        onManageGuardians = onManageGuardians,
                        onAddStudent = {
                            viewModel.clearFormFeedback()
                            showAddSheet = true
                        }
                    )
            }
        }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!uiState.isSubmitting) {
                    showAddSheet = false
                    viewModel.clearFormFeedback()
                }
            },
            sheetState = addSheetState
        ) {
            AddStudentSheetContent(
                sections = uiState.availablePlacements,
                isTeacherAccount = uiState.role == UserRole.Teacher,
                isSubmitting = uiState.isSubmitting,
                formError = uiState.formError,
                onCancel = {
                    if (!uiState.isSubmitting) {
                        showAddSheet = false
                        viewModel.clearFormFeedback()
                    }
                },
                onSubmit = viewModel::addStudent
            )
        }
    }
}

@Composable
private fun TeacherRosterContent(
    uiState: TeacherStudentsUiState,
    isTeacherAccount: Boolean,
    onSearchChange: (String) -> Unit,
    onPlacementFilterChange: (AcademicPlacementOption?) -> Unit,
    onRegisterParent: (String) -> Unit,
    onManageGuardians: (String) -> Unit,
    onAddStudent: () -> Unit
) {
    val totalStudents = uiState.allStudents.size
    val guardianReady = uiState.allStudents.count(::hasPrimaryGuardian)
    val needsPrimary = totalStudents - guardianReady

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 860.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.md,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(key = "hero") {
                RosterHero(
                    totalStudents = totalStudents,
                    sectionCount = uiState.availablePlacements.size,
                    needsPrimaryGuardian = needsPrimary,
                    isTeacherAccount = isTeacherAccount
                )
            }

            uiState.placementError?.let { message ->
                item(key = "placement_warning") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Section setup needs attention",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item(key = "search") {
                SearchCard(
                    searchTerm = uiState.searchTerm,
                    onSearchChange = onSearchChange
                )
            }

            if (uiState.availablePlacements.size > 1) {
                item(key = "section_filter") {
                    SectionFilter(
                        sections = uiState.availablePlacements,
                        selected = uiState.selectedPlacementFilter,
                        allSectionsLabel =
                            if (isTeacherAccount) {
                                "All assigned sections"
                            } else {
                                "All school sections"
                            },
                        onSelect = onPlacementFilterChange
                    )
                }
            }

            item(key = "roster_heading") {
                SectionHeading(
                    title = "Roster",
                    detail = when {
                        uiState.searchTerm.isNotBlank() ->
                            "${uiState.filteredStudents.size} matching student${
                                if (uiState.filteredStudents.size == 1) "" else "s"
                            }"

                        uiState.selectedPlacementFilter != null ->
                            "${uiState.filteredStudents.size} student${
                                if (uiState.filteredStudents.size == 1) "" else "s"
                            } in ${uiState.selectedPlacementFilter.displayLabel()}"

                        else ->
                            "$totalStudents active student${
                                if (totalStudents == 1) "" else "s"
                            }"
                    }
                )
            }

            when {
                uiState.allStudents.isEmpty() -> {
                    item(key = "empty_roster") {
                        EmptyRoster(onAdd = onAddStudent)
                    }
                }

                uiState.filteredStudents.isEmpty() -> {
                    item(key = "empty_filter") {
                        NoSearchResultsState(
                            searchTerm = uiState.searchTerm,
                            section = uiState.selectedPlacementFilter,
                            onClear = {
                                onSearchChange("")
                                onPlacementFilterChange(null)
                            }
                        )
                    }
                }

                else -> {
                    var lastGroup: String? = null
                    uiState.filteredStudents.forEach { student ->
                        val group = "${student.grade}||${student.section}"

                        if (group != lastGroup) {
                            item(key = "header-$group") {
                                RosterGroupHeader(
                                    grade = student.grade,
                                    section = student.section,
                                    studentCount = uiState.filteredStudents.count {
                                        it.grade == student.grade &&
                                            it.section == student.section
                                    }
                                )
                            }
                            lastGroup = group
                        }

                        item(key = "student-${student.id}") {
                            StudentRosterCard(
                                student = student,
                                onRegisterParent = {
                                    onRegisterParent(student.id)
                                },
                                onManageGuardians = {
                                    onManageGuardians(student.id)
                                }
                            )
                        }
                    }
                }
            }

            item(key = "security_note") {
                RosterSecurityNote()
            }
        }
    }
}

@Composable
private fun RosterHero(
    totalStudents: Int,
    sectionCount: Int,
    needsPrimaryGuardian: Int,
    isTeacherAccount: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "DISMISSAL ROSTER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Know who is ready for pickup.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                if (isTeacherAccount) {
                    "Student records stay scoped to your assigned sections. Guardian readiness is visible before dismissal starts."
                } else {
                    "This school-wide active roster covers every current section. Archived and historical records are managed separately in Student Lifecycle."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroMetric(
                    value = totalStudents.toString(),
                    label = "Students",
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    value = sectionCount.toString(),
                    label = "Sections",
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    value = needsPrimaryGuardian.toString(),
                    label = "Need guardian",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        )
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchCard(
    searchTerm: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchTerm,
        onValueChange = onSearchChange,
        placeholder = { Text("Search by student name") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null
            )
        },
        singleLine = true,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionFilter(
    sections: List<AcademicPlacementOption>,
    selected: AcademicPlacementOption?,
    allSectionsLabel: String,
    onSelect: (AcademicPlacementOption?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value =
                selected?.displayLabel()
                    ?: allSectionsLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Section filter") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(allSectionsLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )

            sections.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.displayLabel()) },
                    onClick = {
                        onSelect(section)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    detail: String
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RosterGroupHeader(
    grade: String,
    section: String,
    studentCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        gradeBadge(grade),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(Modifier.weight(1f)) {
                Text(
                    "Grade ${grade.ifBlank { "—" }} · ${section.ifBlank { "Unassigned section" }}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$studentCount student${if (studentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StudentRosterCard(
    student: Student,
    onRegisterParent: () -> Unit,
    onManageGuardians: () -> Unit
) {
    val hasPrimary = hasPrimaryGuardian(student)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudentAvatar(student)

                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        student.fullName.ifBlank { "Unnamed student" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        "Grade ${student.grade.ifBlank { "—" }} · ${
                            student.section.ifBlank { "Section —" }
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (student.studentNumber.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Student no. ${student.studentNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                GuardianReadinessBadge(hasPrimary = hasPrimary)
            }

            Spacer(Modifier.height(Spacing.md))

            if (hasPrimary) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "Primary guardian registered. Review backup or temporary pickup access when needed.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "Primary guardian required before backup or one-day pickup access can be added.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!hasPrimary) {
                    FilledTonalButton(
                        onClick = onRegisterParent,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Register primary")
                    }
                } else {
                    OutlinedButton(
                        onClick = onManageGuardians,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Manage guardians")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentAvatar(student: Student) {
    val initials = studentInitials(student.fullName)

    Box(
        modifier = Modifier.size(58.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (!student.photoUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            ) {
                SmartImage(
                    model = student.photoUrl,
                    contentDescription = "${student.fullName} profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GuardianReadinessBadge(hasPrimary: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (hasPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Text(
            if (hasPrimary) "Guardian ready" else "Needs guardian",
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (hasPrimary) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        )
    }
}

@Composable
private fun EmptyRoster(onAdd: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                "No students yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Add the first student to this roster, then register their primary guardian.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.md))

            Button(
                onClick = onAdd,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Add student")
            }
        }
    }
}

@Composable
private fun NoSearchResultsState(
    searchTerm: String,
    section: AcademicPlacementOption?,
    onClear: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                "No students found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                when {
                    searchTerm.isNotBlank() && section != null ->
                        "No student matches “$searchTerm” in ${section.displayLabel()}."

                    searchTerm.isNotBlank() ->
                        "No student matches “$searchTerm”."

                    section != null ->
                        "No active students are currently in ${section.displayLabel()}."

                    else ->
                        "Try another filter."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.md))

            OutlinedButton(onClick = onClear) {
                Text("Clear filters")
            }
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
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Group,
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
            "Ask your school admin to assign your grade and section. Your roster remains strictly scoped to those assignments.",
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
private fun RosterSecurityNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Roster access does not authorize release. Every pickup must still pass the normal QR and guardian verification workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStudentSheetContent(
    sections: List<AcademicPlacementOption>,
    isTeacherAccount: Boolean,
    isSubmitting: Boolean,
    formError: String?,
    onCancel: () -> Unit,
    onSubmit: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        placement: AcademicPlacementOption
    ) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var placement by remember(sections) {
        mutableStateOf(sections.singleOrNull())
    }
    var placementExpanded by remember { mutableStateOf(false) }

    val currentYearName = sections
        .firstOrNull()
        ?.academicYearName
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    "Register student",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Create the roster record, then continue to primary guardian registration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "School-controlled placement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        when {
                            currentYearName.isBlank() && isTeacherAccount ->
                                "Only grade and section combinations configured by the school administrator and assigned to your teacher account are available."

                            currentYearName.isBlank() ->
                                "Only grade and section combinations configured in School Year & Sections are available."

                            isTeacherAccount ->
                                "Only active classes from $currentYearName that are assigned to your teacher account are available."

                            else ->
                                "Only active classes from $currentYearName are available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                    )
                }
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Grade & section",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    when {
                        sections.isEmpty() ->
                            "No valid class is available for new student registration."

                        sections.size == 1 && isTeacherAccount ->
                            "Your only current assigned class is selected automatically."

                        sections.size == 1 ->
                            "The only current class is selected automatically."

                        isTeacherAccount ->
                            "Choose from the current classes assigned to your teacher account."

                        else ->
                            "Choose the student's current grade and section from the school setup."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (sections.size == 1 && placement != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    placement?.displayLabel().orEmpty(),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (currentYearName.isNotBlank()) {
                                    Text(
                                        currentYearName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = placementExpanded,
                        onExpandedChange = {
                            if (!isSubmitting && sections.isNotEmpty()) {
                                placementExpanded = it
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = placement?.displayLabel().orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isSubmitting && sections.isNotEmpty(),
                            label = { Text("Assigned class") },
                            placeholder = { Text("Select grade & section") },
                            supportingText = {
                                Text(
                                    if (sections.isEmpty()) {
                                        "Ask the school administrator to configure and assign a current class."
                                    } else {
                                        sections.size.toString() +
                                            " current class" +
                                            (if (sections.size == 1) "" else "es") +
                                            " available"
                                    }
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = placementExpanded
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = placementExpanded,
                            onDismissRequest = { placementExpanded = false }
                        ) {
                            sections.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                option.displayLabel(),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (option.academicYearName.isNotBlank()) {
                                                Text(
                                                    option.academicYearName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        placement = option
                                        placementExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Student identity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    "Use the student's official school record name. PickupPass stores the display name in last-name-first format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it.take(80) },
                    label = { Text("Last name") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it.take(80) },
                    label = { Text("First name") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = middleInitial,
                        onValueChange = { middleInitial = it.take(2) },
                        label = { Text("M.I.") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it.take(20) },
                        label = { Text("Suffix") },
                        placeholder = { Text("Jr., III") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }

        formError?.let { message ->
            ErrorBanner(message)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.width(Spacing.sm))

            Button(
                onClick = {
                    placement?.let {
                        onSubmit(
                            lastName.trim(),
                            firstName.trim(),
                            middleInitial.trim(),
                            suffix.trim(),
                            it
                        )
                    }
                },
                enabled = !isSubmitting &&
                    lastName.isNotBlank() &&
                    firstName.isNotBlank() &&
                    placement != null,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }

                Text(
                    if (isSubmitting) "Registering…" else "Register student",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private fun hasPrimaryGuardian(student: Student): Boolean =
    student.primaryGuardianUidCompat() != null

private fun AcademicPlacementOption.displayLabel(): String = displayName

private fun studentInitials(fullName: String): String {
    val parts = fullName
        .replace(",", " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (parts.isEmpty()) return "S"

    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }
}

private fun gradeBadge(grade: String): String =
    grade
        .trim()
        .replace(Regex("(?i)^grade\\s*"), "")
        .take(3)
        .ifBlank { "G" }
