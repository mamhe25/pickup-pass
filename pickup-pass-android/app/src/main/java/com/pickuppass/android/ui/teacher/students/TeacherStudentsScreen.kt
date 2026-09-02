package com.pickuppass.android.ui.teacher.students

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
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
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SmartImage
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

    var showAddSheet by remember {
        mutableStateOf(false)
    }

    var expandedGradeKey by remember {
        mutableStateOf<String?>(null)
    }

    var expandedSectionKey by remember {
        mutableStateOf<String?>(null)
    }

    val addSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    LaunchedEffect(
        uiState.justCreatedStudentId
    ) {
        uiState.justCreatedStudentId?.let {
                studentId ->
            showAddSheet = false
            viewModel.consumeJustCreatedStudentId()
            onRegisterParent(studentId)
        }
    }

    val totalStudents =
        uiState.allStudents.size

    val studentsWithGuardian =
        uiState.allStudents.count {
            it.guardianUids.isNotEmpty()
        }

    val needsGuardian =
        totalStudents - studentsWithGuardian

    val totalSections =
        uiState.groupedStudents.sumOf {
            it.sections.size
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BrandedTitle(
                        "Students",
                        uiState.school
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onGoToExitLogs
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription =
                                "Dismissal History"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (
                !uiState.isLoading &&
                uiState.error == null &&
                !uiState.hasNoAssignedSections
            ) {
                SmallFloatingActionButton(
                    onClick = {
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (
                !uiState.isLoading &&
                uiState.error == null &&
                !uiState.hasNoAssignedSections
            ) {
                CompactRosterSummary(
                    totalStudents = totalStudents,
                    sectionCount = totalSections,
                    needsGuardian = needsGuardian
                )
            }

            val canSearch =
                !uiState.isLoading &&
                    uiState.error == null &&
                    !uiState.hasNoAssignedSections &&
                    uiState.allStudents.isNotEmpty()

            if (canSearch) {
                CompactSearchBar(
                    searchTerm =
                        uiState.searchTerm,
                    sectionCount =
                        totalSections,
                    onSearchChange =
                        viewModel::onSearchChange
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val contentPhase =
                    when {
                        uiState.isLoading ->
                            "loading"

                        uiState.error != null ->
                            "error"

                        uiState.hasNoAssignedSections ->
                            "noSections"

                        uiState.allStudents.isEmpty() ->
                            "emptyRoster"

                        uiState.groupedStudents.isEmpty() ->
                            "noResults"

                        else ->
                            "list"
                    }

                Crossfade(
                    targetState = contentPhase,
                    animationSpec = tween(180),
                    label = "rosterPhase"
                ) { phase ->
                    when (phase) {
                        "loading" ->
                            FullScreenLoading()

                        "error" ->
                            RosterErrorState(
                                message =
                                    uiState.error
                                        ?: "Couldn't load students",
                                onRetry =
                                    viewModel::load
                            )

                        "noSections" ->
                            NoSectionsAssignedState()

                        "emptyRoster" ->
                            EmptyRoster(
                                onAdd = {
                                    showAddSheet = true
                                }
                            )

                        "noResults" ->
                            NoSearchResultsState(
                                searchTerm =
                                    uiState.searchTerm,
                                onClear = {
                                    viewModel
                                        .onSearchChange("")
                                }
                            )

                        else ->
                            LazyColumn(
                                contentPadding =
                                    PaddingValues(
                                        start =
                                            Spacing.md,
                                        end =
                                            Spacing.md,
                                        top = 2.dp,
                                        bottom = 88.dp
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                uiState.groupedStudents
                                    .forEachIndexed {
                                            gradeIndex,
                                            gradeGroup ->

                                        val gradeKey =
                                            gradeGroup.grade

                                        val forceExpanded =
                                            uiState.searchTerm
                                                .isNotBlank()

                                        val gradeExpanded =
                                            if (forceExpanded) {
                                                true
                                            } else {
                                                expandedGradeKey
                                                    ?.let {
                                                        it == gradeKey
                                                    }
                                                    ?: (gradeIndex == 0)
                                            }

                                        item(
                                            key =
                                                "grade-$gradeKey"
                                        ) {
                                            GradeAccordion(
                                                grade =
                                                    gradeGroup.grade,
                                                sections =
                                                    gradeGroup.sections.map {
                                                        SectionDisplay(
                                                            section =
                                                                it.section,
                                                            students =
                                                                it.students
                                                        )
                                                    },
                                                expanded =
                                                    gradeExpanded,
                                                searchActive =
                                                    forceExpanded,
                                                expandedSectionKey =
                                                    expandedSectionKey,
                                                onGradeToggle = {
                                                    if (
                                                        !forceExpanded
                                                    ) {
                                                        if (
                                                            gradeExpanded
                                                        ) {
                                                            expandedGradeKey =
                                                                "__collapsed__"
                                                            expandedSectionKey =
                                                                null
                                                        } else {
                                                            expandedGradeKey =
                                                                gradeKey

                                                            expandedSectionKey =
                                                                gradeGroup
                                                                    .sections
                                                                    .firstOrNull()
                                                                    ?.let {
                                                                        "${gradeGroup.grade}|${it.section}"
                                                                    }
                                                        }
                                                    }
                                                },
                                                onSectionToggle = {
                                                        sectionKey,
                                                        isExpanded ->
                                                    if (
                                                        !forceExpanded
                                                    ) {
                                                        expandedSectionKey =
                                                            if (
                                                                isExpanded
                                                            ) {
                                                                null
                                                            } else {
                                                                sectionKey
                                                            }
                                                    }
                                                },
                                                onRegisterParent =
                                                    onRegisterParent
                                            )
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
                if (!uiState.isSubmitting) {
                    showAddSheet = false
                }
            },
            sheetState = addSheetState
        ) {
            AddStudentSheetContent(
                isSubmitting =
                    uiState.isSubmitting,
                formError =
                    uiState.formError,
                onCancel = {
                    showAddSheet = false
                },
                onSubmit = {
                        lastName,
                        firstName,
                        mi,
                        suffix,
                        grade,
                        section ->
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
private fun CompactRosterSummary(
    totalStudents: Int,
    sectionCount: Int,
    needsGuardian: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.md,
                vertical = 4.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {
        RosterSummaryPill(
            value = totalStudents.toString(),
            label = "Students",
            modifier = Modifier.weight(1f)
        )

        RosterSummaryPill(
            value = sectionCount.toString(),
            label = "Sections",
            modifier = Modifier.weight(1f)
        )

        RosterSummaryPill(
            value = needsGuardian.toString(),
            label = "Needs guardian",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RosterSummaryPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = label,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactSearchBar(
    searchTerm: String,
    sectionCount: Int,
    onSearchChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.md,
                vertical = 5.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchChange,
            placeholder = {
                Text("Search student")
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
        )

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text =
                    "$sectionCount section${if (sectionCount == 1) "" else "s"}",
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 7.dp
                ),
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private data class SectionDisplay(
    val section: String,
    val students: List<Student>
)

@Composable
private fun GradeAccordion(
    grade: String,
    sections: List<SectionDisplay>,
    expanded: Boolean,
    searchActive: Boolean,
    expandedSectionKey: String?,
    onGradeToggle: () -> Unit,
    onSectionToggle: (
        sectionKey: String,
        isExpanded: Boolean
    ) -> Unit,
    onRegisterParent: (String) -> Unit
) {
    val studentCount =
        sections.sumOf {
            it.students.size
        }

    val readyCount =
        sections.sumOf { section ->
            section.students.count {
                it.guardianUids.isNotEmpty()
            }
        }

    val needsCount =
        studentCount - readyCount

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = 1.dp
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !searchActive,
                    onClick = onGradeToggle
                ),
            color =
                MaterialTheme.colorScheme.primary,
            contentColor =
                MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme.onPrimary
                            .copy(alpha = 0.14f)
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = gradeBadge(grade),
                            style =
                                MaterialTheme.typography.labelLarge,
                            fontWeight =
                                FontWeight.ExtraBold,
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "Grade ${grade.ifBlank { "—" }}",
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(1.dp))

                    Text(
                        text = buildString {
                            append(
                                "${sections.size} section${if (sections.size == 1) "" else "s"}"
                            )
                            append(
                                " · $studentCount student${if (studentCount == 1) "" else "s"}"
                            )

                            if (needsCount > 0) {
                                append(
                                    " · $needsCount need guardian"
                                )
                            } else if (studentCount > 0) {
                                append(
                                    " · all ready"
                                )
                            }
                        },
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme.onPrimary
                                .copy(alpha = 0.76f)
                    )
                }

                if (!searchActive) {
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                        contentDescription =
                            if (expanded) {
                                "Collapse grade"
                            } else {
                                "Expand grade"
                            }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                sections.forEachIndexed {
                        sectionIndex,
                        sectionGroup ->

                    val sectionKey =
                        "$grade|${sectionGroup.section}"

                    val sectionExpanded =
                        if (searchActive) {
                            true
                        } else {
                            expandedSectionKey
                                ?.let {
                                    it == sectionKey
                                }
                                ?: (sectionIndex == 0)
                        }

                    SectionAccordion(
                        section =
                            sectionGroup.section,
                        students =
                            sectionGroup.students,
                        expanded =
                            sectionExpanded,
                        searchActive =
                            searchActive,
                        onToggle = {
                            onSectionToggle(
                                sectionKey,
                                sectionExpanded
                            )
                        },
                        onRegisterParent =
                            onRegisterParent
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionAccordion(
    section: String,
    students: List<Student>,
    expanded: Boolean,
    searchActive: Boolean,
    onToggle: () -> Unit,
    onRegisterParent: (String) -> Unit
) {
    val readyCount =
        students.count {
            it.guardianUids.isNotEmpty()
        }

    val needsCount =
        students.size - readyCount

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !searchActive,
                        onClick = onToggle
                    )
                    .padding(
                        horizontal = 11.dp,
                        vertical = 8.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text =
                                sectionInitial(section),
                            style =
                                MaterialTheme.typography.labelMedium,
                            fontWeight =
                                FontWeight.ExtraBold,
                            color =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(9.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "Section ${section.ifBlank { "—" }}",
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        fontWeight =
                            FontWeight.ExtraBold,
                        color =
                            MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(1.dp))

                    Text(
                        text = buildString {
                            append(
                                "${students.size} student${if (students.size == 1) "" else "s"}"
                            )

                            append(
                                " · $readyCount ready"
                            )

                            if (needsCount > 0) {
                                append(
                                    " · $needsCount need guardian"
                                )
                            }
                        },
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            if (needsCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                    )
                }

                if (!searchActive) {
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                        contentDescription =
                            if (expanded) {
                                "Collapse section"
                            } else {
                                "Expand section"
                            },
                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color =
                        MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        students.forEachIndexed {
                                index,
                                student ->

                            CompactStudentRow(
                                student = student,
                                onRegisterParent = {
                                    onRegisterParent(
                                        student.id
                                    )
                                }
                            )

                            if (
                                index != students.lastIndex
                            ) {
                                HorizontalDivider(
                                    modifier =
                                        Modifier.padding(
                                            start = 58.dp
                                        ),
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant
                                            .copy(
                                                alpha = 0.65f
                                            )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStudentRow(
    student: Student,
    onRegisterParent: () -> Unit
) {
    val guardianCount =
        student.guardianUids.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 11.dp,
                top = 7.dp,
                end = 8.dp,
                bottom = 7.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        StudentAvatar(
            student = student
        )

        Spacer(Modifier.width(9.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text =
                    student.fullName.ifBlank {
                        "Unnamed student"
                    },
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.bodyMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(Modifier.height(1.dp))

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                GuardianStatusDot(
                    ready =
                        guardianCount > 0
                )

                Spacer(Modifier.width(5.dp))

                Text(
                    text =
                        if (guardianCount > 0) {
                            "$guardianCount guardian${if (guardianCount == 1) "" else "s"}"
                        } else {
                            "Needs guardian"
                        },
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        if (guardianCount > 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                )

                if (
                    student.studentNumber
                        .isNotBlank()
                ) {
                    Text(
                        text =
                            " · ${student.studentNumber}",
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TextButton(
            onClick = onRegisterParent,
            contentPadding =
                PaddingValues(
                    horizontal = 8.dp
                ),
            modifier =
                Modifier.heightIn(min = 44.dp)
        ) {
            Text(
                text =
                    if (guardianCount > 0) {
                        "Add guardian"
                    } else {
                        "Register"
                    },
                style =
                    MaterialTheme.typography.labelMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StudentAvatar(
    student: Student
) {
    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        studentInitials(
                            student.fullName
                        ),
                    style =
                        MaterialTheme.typography.labelLarge,
                    fontWeight =
                        FontWeight.ExtraBold,
                    color =
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (
            !student.photoUrl
                .isNullOrBlank()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            ) {
                SmartImage(
                    model =
                        student.photoUrl,
                    contentDescription =
                        "${student.fullName} photo",
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun GuardianStatusDot(
    ready: Boolean
) {
    Surface(
        modifier = Modifier.size(7.dp),
        shape = CircleShape,
        color =
            if (ready) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.error
            }
    ) {}
}

@Composable
private fun EmptyRoster(
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier =
                        Modifier.size(27.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            "No students yet",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            "Add the first student to begin building this dismissal roster.",
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign =
                TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = onAdd,
            modifier =
                Modifier.heightIn(min = 44.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null
            )

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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            "No students found",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            if (searchTerm.isBlank()) {
                "Try another search."
            } else {
                "No roster matches “$searchTerm”."
            },
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign =
                TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedButton(
            onClick = onClear,
            modifier =
                Modifier.heightIn(min = 44.dp)
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.size(27.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            "No sections assigned yet",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            "Ask your school admin to assign your grade and section. Your roster remains scoped to those assignments.",
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign =
                TextAlign.Center
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        ErrorBanner(message)

        Spacer(Modifier.height(Spacing.md))

        OutlinedButton(
            onClick = onRetry,
            modifier =
                Modifier.heightIn(min = 44.dp)
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
    var lastName by remember {
        mutableStateOf("")
    }

    var firstName by remember {
        mutableStateOf("")
    }

    var middleInitial by remember {
        mutableStateOf("")
    }

    var suffix by remember {
        mutableStateOf("")
    }

    var grade by remember {
        mutableStateOf("")
    }

    var section by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(
                rememberScrollState()
            )
            .imePadding()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl
            )
    ) {
        Text(
            "Add student",
            style =
                MaterialTheme.typography.headlineSmall,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            "Create the roster record first. PickupPass will continue directly to parent / guardian registration after a successful save.",
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = lastName,
            onValueChange = {
                lastName = it
            },
            label = {
                Text("Last name")
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = firstName,
            onValueChange = {
                firstName = it
            },
            label = {
                Text("First name")
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            OutlinedTextField(
                value = middleInitial,
                onValueChange = {
                    middleInitial = it
                },
                label = {
                    Text("M.I.")
                },
                singleLine = true,
                modifier =
                    Modifier.weight(1f)
            )

            OutlinedTextField(
                value = suffix,
                onValueChange = {
                    suffix = it
                },
                label = {
                    Text("Suffix")
                },
                singleLine = true,
                modifier =
                    Modifier.weight(2f)
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            OutlinedTextField(
                value = grade,
                onValueChange = {
                    grade = it
                },
                label = {
                    Text("Grade")
                },
                singleLine = true,
                modifier =
                    Modifier.weight(1f)
            )

            OutlinedTextField(
                value = section,
                onValueChange = {
                    section = it
                },
                label = {
                    Text("Section")
                },
                singleLine = true,
                modifier =
                    Modifier.weight(1f)
            )
        }

        formError?.let { message ->
            Spacer(Modifier.height(Spacing.sm))
            ErrorBanner(message)
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.End,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting,
                modifier =
                    Modifier.heightIn(min = 44.dp)
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
                modifier =
                    Modifier.heightIn(min = 46.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color =
                            MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(Modifier.width(Spacing.sm))
                }

                Text(
                    if (isSubmitting) {
                        "Adding…"
                    } else {
                        "Add student"
                    }
                )
            }
        }
    }
}

private fun studentInitials(
    fullName: String
): String {
    val parts =
        fullName
            .replace(",", " ")
            .trim()
            .split(Regex("\\s+"))
            .filter {
                it.isNotBlank()
            }

    if (parts.isEmpty()) {
        return "S"
    }

    val first =
        parts.first()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()

    val second =
        parts
            .drop(1)
            .firstOrNull()
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()

    return (first + second)
        .ifBlank { "S" }
}

private fun gradeBadge(
    grade: String
): String {
    val compact =
        grade
            .trim()
            .replace(
                Regex("(?i)^grade\\s*"),
                ""
            )
            .take(3)

    return compact.ifBlank { "G" }
}

private fun sectionInitial(
    section: String
): String =
    section
        .trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        .orEmpty()
        .ifBlank { "S" }
