package com.pickuppass.android.ui.schooladmin.manualpickup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.model.primaryGuardianUidCompat
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

private const val MAX_STUDENT_RESULTS = 8

private data class StudentSearchResult(
    val visible: List<Student> = emptyList(),
    val total: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPickupScreen(
    viewModel: ManualPickupViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var studentQuery by rememberSaveable { mutableStateOf("") }
    var gateMenuExpanded by remember { mutableStateOf(false) }
    var reviewAttempted by rememberSaveable { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }

    val studentSearch = remember(state.students, studentQuery, state.selectedStudent) {
        if (state.selectedStudent != null || studentQuery.isBlank()) {
            StudentSearchResult()
        } else {
            val query = studentQuery.trim().lowercase()
            val matches = state.students
                .asSequence()
                .filter { student -> studentMatchesQuery(student, query) }
                .sortedWith(
                    compareBy<Student> { it.fullName.lowercase() }
                        .thenBy { it.studentNumber.lowercase() }
                )
                .toList()

            StudentSearchResult(
                visible = matches.take(MAX_STUDENT_RESULTS),
                total = matches.size
            )
        }
    }

    LaunchedEffect(state.success) {
        if (state.success != null) {
            showConfirmation = false
            reviewAttempted = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Manual Release",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Administrative pickup override",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.isSubmitting
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && state.students.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.md,
                        top = Spacing.sm,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                if (state.success != null) {
                    ManualReleaseSuccess(
                        state = state,
                        onReleaseAnother = {
                            studentQuery = ""
                            viewModel.startAnotherRelease()
                        },
                        onDone = onBack
                    )
                    return@Column
                }

                OverrideNoticeCard()

                state.error?.let { message ->
                    ErrorBanner(message)
                }

                if (state.students.isEmpty() && !state.isLoading && state.error != null) {
                    TextButton(onClick = viewModel::load) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Retry loading")
                    }
                    return@Column
                }

                if (state.students.isEmpty() && !state.isLoading) {
                    EmptyStudentsCard(onRetry = viewModel::load)
                    return@Column
                }

                StepHeader(
                    number = 1,
                    title = "Select student",
                    subtitle = "Search by name, student number, grade, or section.",
                    complete = state.selectedStudent != null
                )

                state.selectedStudent?.let { student ->
                    SelectedStudentCard(
                        student = student,
                        enabled = !state.isSubmitting,
                        onChange = {
                            studentQuery = ""
                            reviewAttempted = false
                            viewModel.clearStudentSelection()
                        }
                    )
                } ?: run {
                    StudentSearchField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it.take(80) }
                    )

                    when {
                        studentQuery.isBlank() -> SearchPromptCard()
                        studentSearch.visible.isEmpty() -> NoStudentMatchesCard(studentQuery)
                        else -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                studentSearch.visible.forEach { student ->
                                    StudentResultCard(
                                        student = student,
                                        onClick = {
                                            studentQuery = ""
                                            reviewAttempted = false
                                            viewModel.selectStudent(student)
                                        }
                                    )
                                }
                            }

                            if (studentSearch.total > MAX_STUDENT_RESULTS) {
                                Text(
                                    text = "Showing the first $MAX_STUDENT_RESULTS matches. Refine your search to narrow the list.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (reviewAttempted && state.selectedStudent == null) {
                    InlineValidation("Select the student being released.")
                }

                state.selectedStudent?.let { student ->
                    HorizontalDivider()

                    StepHeader(
                        number = 2,
                        title = "Authorized guardian",
                        subtitle = "Confirm the person collecting this student.",
                        complete = state.selectedGuardian != null
                    )

                    when {
                        state.isLoadingGuardians -> GuardianLoadingCard()
                        state.guardians.isEmpty() -> NoGuardiansCard()
                        else -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                state.guardians.forEach { guardian ->
                                    GuardianSelectionCard(
                                        guardian = guardian,
                                        student = student,
                                        selected = guardian.uid == state.selectedGuardian?.uid,
                                        enabled = !state.isSubmitting,
                                        onSelect = {
                                            viewModel.selectGuardian(guardian)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (reviewAttempted && state.selectedGuardian == null && !state.isLoadingGuardians) {
                        InlineValidation("Select the authorized guardian present at pickup.")
                    }
                }

                if (state.selectedGuardian != null) {
                    HorizontalDivider()

                    StepHeader(
                        number = 3,
                        title = "Release details",
                        subtitle = "Record where and why the manual override is required.",
                        complete = releaseDetailsComplete(state)
                    )

                    PickupGateField(
                        state = state,
                        expanded = gateMenuExpanded,
                        onExpandedChange = { gateMenuExpanded = it },
                        onSelect = {
                            gateMenuExpanded = false
                            viewModel.selectPickupGate(it)
                        },
                        onRetry = viewModel::load
                    )

                    if (
                        reviewAttempted &&
                        state.pickupGatesLoaded &&
                        state.pickupGates.isNotEmpty() &&
                        state.selectedPickupGate == null
                    ) {
                        InlineValidation("Select the pickup gate used for this release.")
                    }

                    val reasonInvalid = reviewAttempted && state.reason.trim().length < 5
                    OutlinedTextField(
                        value = state.reason,
                        onValueChange = viewModel::setReason,
                        label = { Text("Reason for manual release *") },
                        placeholder = {
                            Text("Example: guardian phone unavailable; identity checked by school admin")
                        },
                        supportingText = {
                            Text(
                                if (reasonInvalid) {
                                    "Enter a clear reason of at least 5 characters."
                                } else {
                                    "Required · ${state.reason.length}/500 characters"
                                }
                            )
                        },
                        isError = reasonInvalid,
                        minLines = 3,
                        maxLines = 6,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    AuditNoticeCard()

                    Button(
                        onClick = {
                            reviewAttempted = true
                            val canReview =
                                state.selectedStudent != null &&
                                    state.selectedGuardian != null &&
                                    state.pickupGatesLoaded &&
                                    (state.pickupGates.isEmpty() || state.selectedPickupGate != null) &&
                                    state.reason.trim().length >= 5

                            if (canReview) {
                                showConfirmation = true
                            }
                        },
                        enabled = !state.isSubmitting && state.pickupGatesLoaded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Review release")
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        ReviewReleaseDialog(
            state = state,
            onDismiss = {
                if (!state.isSubmitting) {
                    showConfirmation = false
                }
            },
            onConfirm = viewModel::submit
        )
    }
}

@Composable
private fun OverrideNoticeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Administrative override",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Use this only when the normal QR pickup flow cannot be completed. Verify the guardian's identity before releasing the student.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepHeader(
    number: Int,
    title: String,
    subtitle: String,
    complete: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = if (complete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (complete) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Search students") },
        placeholder = { Text("Name, student #, grade, or section") },
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SearchPromptCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Start typing to find a student. Results are limited for faster selection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoStudentMatchesCard(query: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "No students found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No active student matches “${query.trim()}”. Try a name, student number, grade, or section.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentResultCard(
    student: Student,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatar(student)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = student.fullName.ifBlank { "Unnamed student" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = studentIdentifier(student),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = studentClassLabel(student),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedStudentCard(
    student: Student,
    enabled: Boolean,
    onChange: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatar(student)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Selected student",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = student.fullName.ifBlank { "Unnamed student" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${studentIdentifier(student)} · ${studentClassLabel(student)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onChange,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = Spacing.sm)
            ) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun StudentAvatar(student: Student) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (!student.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = student.photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun GuardianLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "Loading authorized guardians…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoGuardiansCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "No authorized guardian available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "This student cannot be manually released until an active authorized guardian is linked to the student.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuardianSelectionCard(
    guardian: UserProfile,
    student: Student,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val entry = student.guardians[guardian.uid]
    val relationship = entry?.relationship?.trim().orEmpty()
    val isPrimary = student.primaryGuardianUidCompat() == guardian.uid

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuardianAvatar(guardian)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = guardian.displayName.ifBlank { guardian.email.ifBlank { "Authorized guardian" } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildGuardianLabel(relationship, isPrimary),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (guardian.email.isNotBlank()) {
                    Text(
                        text = guardian.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun GuardianAvatar(guardian: UserProfile) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (!guardian.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = guardian.photoUrl,
                contentDescription = "Guardian photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickupGateField(
    state: ManualPickupUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (PickupGateItem) -> Unit,
    onRetry: () -> Unit
) {
    when {
        !state.pickupGatesLoaded -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.50f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pickup gates unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.pickupGateError ?: "Could not load pickup gate information.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Retry")
                    }
                }
            }
        }

        state.pickupGates.isEmpty() -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Pickup gate",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No active pickup gates are configured. This release will be recorded without a gate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        state.pickupGates.size == 1 -> {
            val gate = state.pickupGates.first()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pickup gate",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = gate.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        else -> {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                OutlinedTextField(
                    value = state.selectedPickupGate?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pickup gate *") },
                    placeholder = { Text("Select release location") },
                    leadingIcon = {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    state.pickupGates.forEach { gate ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = gate.displayName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (gate.description.isNotBlank()) {
                                        Text(
                                            text = gate.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = { onSelect(gate) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditNoticeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Audited action",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "This bypasses the normal QR pass flow. The student, guardian, gate, reason, staff member, and release event are recorded by the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun InlineValidation(message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ReviewReleaseDialog(
    state: ManualPickupUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val student = state.selectedStudent ?: return
    val guardian = state.selectedGuardian ?: return
    val relationship = student.guardians[guardian.uid]?.relationship.orEmpty().trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        title = {
            Text(
                text = "Review manual release",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ReviewRow("Student", student.fullName.ifBlank { "Unnamed student" })
                ReviewRow(
                    "Student details",
                    "${studentIdentifier(student)} · ${studentClassLabel(student)}"
                )
                ReviewRow(
                    "Guardian",
                    guardian.displayName.ifBlank { guardian.email.ifBlank { "Authorized guardian" } }
                )
                if (relationship.isNotBlank()) {
                    ReviewRow("Relationship", relationship)
                }
                ReviewRow(
                    "Pickup gate",
                    state.selectedPickupGate?.displayName ?: "No gate configured"
                )
                ReviewRow("Reason", state.reason.trim())

                state.error?.let { message ->
                    ErrorBanner(message)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Confirm the guardian's identity before continuing. This action bypasses QR verification and creates an audited dismissal record.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Releasing…")
                } else {
                    Text("Confirm manual release")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ManualReleaseSuccess(
    state: ManualPickupUiState,
    onReleaseAnother: () -> Unit,
    onDone: () -> Unit
) {
    val student = state.selectedStudent
    val guardian = state.selectedGuardian

    Spacer(Modifier.height(Spacing.sm))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Student released",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = state.success ?: "Release approved and recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            if (student != null) {
                SuccessDetail(
                    label = "Student",
                    value = student.fullName.ifBlank { "Unnamed student" }
                )
            }
            if (guardian != null) {
                SuccessDetail(
                    label = "Guardian",
                    value = guardian.displayName.ifBlank { guardian.email.ifBlank { "Authorized guardian" } }
                )
            }
            SuccessDetail(
                label = "Pickup gate",
                value = state.selectedPickupGate?.displayName ?: "No gate configured"
            )
            SuccessDetail(
                label = "Release method",
                value = "Manual override"
            )
        }
    }

    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Done")
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onReleaseAnother) {
            Text("Release another student")
        }
    }
}

@Composable
private fun SuccessDetail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStudentsCard(onRetry: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "No students available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No active students are currently available for manual release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Retry")
            }
        }
    }
}

private fun studentMatchesQuery(student: Student, query: String): Boolean =
    listOf(
        student.fullName,
        student.studentNumber,
        student.grade,
        student.section,
        "grade ${student.grade}",
        "${student.grade} ${student.section}"
    ).any { it.lowercase().contains(query) }

private fun studentIdentifier(student: Student): String =
    if (student.studentNumber.isNotBlank()) {
        "#${student.studentNumber}"
    } else {
        "Student record"
    }

private fun studentClassLabel(student: Student): String {
    val grade = student.grade.trim()
    val section = student.section.trim()
    return when {
        grade.isNotBlank() && section.isNotBlank() -> "Grade $grade → $section"
        grade.isNotBlank() -> "Grade $grade"
        section.isNotBlank() -> section
        else -> "Class not assigned"
    }
}

private fun buildGuardianLabel(
    relationship: String,
    isPrimary: Boolean
): String {
    return when {
        relationship.isNotBlank() && isPrimary -> "$relationship · Primary guardian"
        relationship.isNotBlank() -> relationship
        isPrimary -> "Primary guardian"
        else -> "Authorized guardian"
    }
}

private fun releaseDetailsComplete(state: ManualPickupUiState): Boolean =
    state.pickupGatesLoaded &&
        (state.pickupGates.isEmpty() || state.selectedPickupGate != null) &&
        state.reason.trim().length >= 5
