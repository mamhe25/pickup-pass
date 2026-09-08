package com.pickuppass.android.ui.schooladmin.manualpickup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.Student
import com.pickuppass.android.data.model.UserProfile
import com.pickuppass.android.data.model.primaryGuardianUidCompat
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Spacing
import com.pickuppass.android.ui.theme.Success500
import com.pickuppass.android.ui.theme.Success600

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

    val studentSearch = remember(
        state.students,
        studentQuery,
        state.selectedStudent
    ) {
        if (
            state.selectedStudent != null ||
            studentQuery.isBlank()
        ) {
            StudentSearchResult()
        } else {
            val query = studentQuery.trim().lowercase()
            val matches =
                state.students
                    .asSequence()
                    .filter { student ->
                        studentMatchesQuery(student, query)
                    }
                    .sortedWith(
                        compareBy<Student> {
                            it.fullName.lowercase()
                        }.thenBy {
                            it.studentNumber.lowercase()
                        }
                    )
                    .toList()

            StudentSearchResult(
                visible = matches.take(MAX_STUDENT_RESULTS),
                total = matches.size
            )
        }
    }

    val releaseDetailsReady =
        releaseDetailsComplete(state)

    val activeStep =
        when {
            state.selectedStudent == null -> 1
            state.selectedGuardian == null -> 2
            !releaseDetailsReady -> 3
            else -> 4
        }

    LaunchedEffect(state.success) {
        if (state.success != null) {
            showConfirmation = false
            reviewAttempted = false
        }
    }

    // A failed submit must not leave the confirmation dialog covering the
    // standardized centered error feedback.
    LaunchedEffect(state.error, state.isSubmitting) {
        if (
            !state.error.isNullOrBlank() &&
            !state.isSubmitting
        ) {
            showConfirmation = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Manual Release",
                subtitle = "Audited administrative pickup override",
                onBack = {
                    if (!state.isSubmitting) {
                        onBack()
                    }
                }
            )
        }
    ) { padding ->
        if (
            state.isLoading &&
            state.students.isEmpty()
        ) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing =
                state.isLoading &&
                    state.students.isNotEmpty() &&
                    state.success == null,
            onRefresh = viewModel::load,
            enabled =
                !state.isSubmitting &&
                    state.success == null,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(
                            start = Spacing.md,
                            top = Spacing.md,
                            end = Spacing.md,
                            bottom = Spacing.xl
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    if (state.success != null) {
                        ManualReleaseSuccess(
                            state = state,
                            onReleaseAnother = {
                                studentQuery = ""
                                gateMenuExpanded = false
                                reviewAttempted = false
                                viewModel.startAnotherRelease()
                            },
                            onDone = onBack
                        )
                        return@Column
                    }

                    if (state.testMode) {
                        ManualPrelaunchTestNotice()
                    }

                    OverrideNoticeCard()

                    WorkflowProgress(
                        activeStep = activeStep,
                        studentComplete =
                            state.selectedStudent != null,
                        guardianComplete =
                            state.selectedGuardian != null,
                        detailsComplete =
                            releaseDetailsReady
                    )

                    if (
                        state.students.isEmpty() &&
                        !state.isLoading
                    ) {
                        EmptyStudentsCard(
                            hasError = state.error != null,
                            onRetry = viewModel::load
                        )
                        return@Column
                    }

                    WorkflowSection(
                        number = 1,
                        title = "Student",
                        subtitle =
                            "Search the roster and confirm who is being released.",
                        complete =
                            state.selectedStudent != null,
                        active = activeStep == 1
                    ) {
                        state.selectedStudent?.let { student ->
                            SelectedStudentCard(
                                student = student,
                                enabled = !state.isSubmitting,
                                onChange = {
                                    studentQuery = ""
                                    gateMenuExpanded = false
                                    reviewAttempted = false
                                    viewModel.clearStudentSelection()
                                }
                            )
                        } ?: run {
                            StudentSearchField(
                                value = studentQuery,
                                onValueChange = {
                                    studentQuery = it.take(80)
                                }
                            )

                            when {
                                studentQuery.isBlank() ->
                                    SearchPromptCard()

                                studentSearch.visible.isEmpty() ->
                                    NoStudentMatchesCard(
                                        studentQuery
                                    )

                                else -> {
                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(
                                                Spacing.sm
                                            )
                                    ) {
                                        studentSearch.visible
                                            .forEach { student ->
                                                StudentResultCard(
                                                    student = student,
                                                    onClick = {
                                                        studentQuery = ""
                                                        reviewAttempted =
                                                            false
                                                        viewModel
                                                            .selectStudent(
                                                                student
                                                            )
                                                    }
                                                )
                                            }
                                    }

                                    if (
                                        studentSearch.total >
                                        MAX_STUDENT_RESULTS
                                    ) {
                                        Text(
                                            text =
                                                "Showing the first $MAX_STUDENT_RESULTS of ${studentSearch.total} matches. Refine your search to narrow the list.",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (
                            reviewAttempted &&
                            state.selectedStudent == null
                        ) {
                            InlineValidation(
                                "Select the student being released."
                            )
                        }
                    }

                    state.selectedStudent?.let { student ->
                        WorkflowSection(
                            number = 2,
                            title = "Guardian",
                            subtitle =
                                "Confirm the authorized person physically present for pickup.",
                            complete =
                                state.selectedGuardian != null,
                            active = activeStep == 2
                        ) {
                            when {
                                state.isLoadingGuardians ->
                                    GuardianLoadingCard()

                                state.guardians.isEmpty() ->
                                    NoGuardiansCard()

                                else -> {
                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(
                                                Spacing.sm
                                            )
                                    ) {
                                        state.guardians.forEach {
                                                guardian ->
                                            GuardianSelectionCard(
                                                guardian = guardian,
                                                student = student,
                                                selected =
                                                    guardian.uid ==
                                                        state
                                                            .selectedGuardian
                                                            ?.uid,
                                                enabled =
                                                    !state.isSubmitting,
                                                onSelect = {
                                                    viewModel
                                                        .selectGuardian(
                                                            guardian
                                                        )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (
                                reviewAttempted &&
                                state.selectedGuardian == null &&
                                !state.isLoadingGuardians
                            ) {
                                InlineValidation(
                                    "Select the authorized guardian present at pickup."
                                )
                            }
                        }
                    }

                    if (state.selectedGuardian != null) {
                        WorkflowSection(
                            number = 3,
                            title = "Release details",
                            subtitle =
                                "Record the release location and the reason QR verification is being bypassed.",
                            complete =
                                releaseDetailsReady,
                            active = activeStep == 3
                        ) {
                            PickupGateField(
                                state = state,
                                expanded = gateMenuExpanded,
                                onExpandedChange = {
                                    gateMenuExpanded = it
                                },
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
                                InlineValidation(
                                    "Select the pickup gate used for this release."
                                )
                            }

                            val reasonInvalid =
                                reviewAttempted &&
                                    state.reason
                                        .trim()
                                        .length < 5

                            OutlinedTextField(
                                value = state.reason,
                                onValueChange =
                                    viewModel::setReason,
                                label = {
                                    Text(
                                        "Reason for manual release *"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "Example: guardian phone unavailable; identity checked by school admin"
                                    )
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
                                shape =
                                    MaterialTheme.shapes.large,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            AuditNoticeCard()
                        }

                        WorkflowSection(
                            number = 4,
                            title = "Review & release",
                            subtitle =
                                "Check the selected student, guardian, gate and audit reason before submitting.",
                            complete = false,
                            active = activeStep == 4
                        ) {
                            ReviewSummaryCard(state)

                            Button(
                                onClick = {
                                    reviewAttempted = true

                                    val canReview =
                                        state.selectedStudent != null &&
                                            state.selectedGuardian != null &&
                                            state.pickupGatesLoaded &&
                                            (
                                                state.pickupGates
                                                    .isEmpty() ||
                                                    state
                                                        .selectedPickupGate !=
                                                    null
                                                ) &&
                                            state.reason
                                                .trim()
                                                .length >= 5

                                    if (canReview) {
                                        showConfirmation = true
                                    }
                                },
                                enabled =
                                    !state.isSubmitting &&
                                        state.pickupGatesLoaded,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape =
                                    MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    contentDescription = null,
                                    modifier =
                                        Modifier.size(18.dp)
                                )
                                Spacer(
                                    Modifier.width(Spacing.sm)
                                )
                                Text(
                                    "Review manual release",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Terminal server/action feedback is deliberately outside the scroll
    // container so the centered feedback dialog is always composed.
    state.error?.let { message ->
        ErrorBanner(message)
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
private fun ManualPrelaunchTestNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm),
            verticalAlignment =
                Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme
                        .onTertiaryContainer
            )
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "PRE-LAUNCH TEST MODE",
                    style =
                        MaterialTheme.typography
                            .labelLarge,
                    fontWeight =
                        FontWeight.ExtraBold,
                    color =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                )
                Text(
                    text =
                        "This manual release uses the real identity and audit workflow, but it will be stored as test activity. It will not count toward production dismissal totals or consume a one-time temporary guardian authorization.",
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun OverrideNoticeCard() {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.tertiaryContainer.copy(
            alpha = 0.52f
        ),
        border = BorderStroke(
            1.dp,
            scheme.tertiary.copy(alpha = 0.24f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = MaterialTheme.shapes.large,
                color =
                    scheme.tertiary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = scheme.onTertiaryContainer,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Administrative override",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer
                )
                Text(
                    text =
                        "Use this only when the normal QR pickup flow cannot be completed. Confirm the guardian's identity before releasing the student.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        scheme.onTertiaryContainer.copy(
                            alpha = 0.82f
                        )
                )
            }
        }
    }
}

@Composable
private fun WorkflowProgress(
    activeStep: Int,
    studentComplete: Boolean,
    guardianComplete: Boolean,
    detailsComplete: Boolean
) {
    val steps = listOf(
        Triple(1, "Student", studentComplete),
        Triple(2, "Guardian", guardianComplete),
        Triple(3, "Details", detailsComplete),
        Triple(4, "Review", false)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.md
            ),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val number = step.first
                val label = step.second
                val complete = step.third
                val active = activeStep == number

                ProgressStep(
                    modifier = Modifier.weight(1f),
                    number = number,
                    label = label,
                    complete = complete,
                    active = active
                )

                if (index != steps.lastIndex) {
                    Surface(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp),
                        shape = CircleShape,
                        color =
                            MaterialTheme.colorScheme
                                .outlineVariant
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ProgressStep(
    modifier: Modifier,
    number: Int,
    label: String,
    complete: Boolean,
    active: Boolean
) {
    val success = successAccent()
    val scheme = MaterialTheme.colorScheme

    val accent =
        when {
            complete -> success
            active -> scheme.primary
            else -> scheme.onSurfaceVariant
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color =
                when {
                    complete ->
                        success.copy(alpha = 0.12f)
                    active ->
                        scheme.primaryContainer
                    else ->
                        scheme.surfaceContainerLow
                },
            border = BorderStroke(
                1.dp,
                accent.copy(
                    alpha =
                        if (complete || active) {
                            0.30f
                        } else {
                            0.12f
                        }
                )
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (complete) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = success,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    Text(
                        number.toString(),
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight =
                if (active || complete) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun WorkflowSection(
    number: Int,
    title: String,
    subtitle: String,
    complete: Boolean,
    active: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val success = successAccent()
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                complete ->
                    success.copy(alpha = 0.20f)
                active ->
                    scheme.primary.copy(alpha = 0.22f)
                else ->
                    scheme.outlineVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation =
                if (active) 2.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = MaterialTheme.shapes.medium,
                    color =
                        when {
                            complete ->
                                success.copy(alpha = 0.10f)
                            active ->
                                scheme.primaryContainer
                            else ->
                                scheme.surfaceContainerLow
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        if (complete) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = success,
                                modifier =
                                    Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                number.toString(),
                                style =
                                    MaterialTheme.typography
                                        .labelLarge,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (active) {
                                        scheme.primary
                                    } else {
                                        scheme
                                            .onSurfaceVariant
                                    }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            scheme.onSurfaceVariant
                    )
                }
            }

            content()
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
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        label = { Text("Search student") },
        placeholder = {
            Text(
                "Name, student number, grade or section"
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(
                    onClick = {
                        onValueChange("")
                    }
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

@Composable
private fun SearchPromptCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text =
                    "Start typing to find a student. Results are limited for faster, safer selection.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoStudentMatchesCard(
    query: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "No students found",
                style =
                    MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text =
                    "No active student matches “${query.trim()}”. Try a name, student number, grade or section.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentResultCard(
    student: Student,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonPhoto(
                photoUrl = student.photoUrl,
                contentDescription = "Student photo",
                fallbackIcon = Icons.Filled.Person,
                size = 50.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text =
                        student.fullName.ifBlank {
                            "Unnamed student"
                        },
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = studentIdentifier(student),
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
                Text(
                    text = studentClassLabel(student),
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
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
    val success = successAccent()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = success.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            success.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonPhoto(
                photoUrl = student.photoUrl,
                contentDescription = "Selected student photo",
                fallbackIcon = Icons.Filled.Person,
                size = 52.dp,
                accent = success
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Selected student",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = success,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        student.fullName.ifBlank {
                            "Unnamed student"
                        },
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text =
                        "${studentIdentifier(student)} · ${studentClassLabel(student)}",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            TextButton(
                onClick = onChange,
                enabled = enabled
            ) {
                Text("Change")
            }
        }
    }
}

@Composable
private fun GuardianLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Text(
                text =
                    "Loading authorized guardians…",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoGuardiansCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error
                .copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text =
                        "No authorized guardian available",
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text =
                        "This student cannot be manually released until an active authorized guardian is linked to the student.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
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
    val relationship =
        entry?.relationship?.trim().orEmpty()
    val isPrimary =
        student.primaryGuardianUidCompat() ==
            guardian.uid

    val accent =
        if (selected) {
            successAccent()
        } else {
            MaterialTheme.colorScheme.primary
        }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onSelect
            ),
        shape = MaterialTheme.shapes.large,
        color =
            if (selected) {
                accent.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color =
                if (selected) {
                    accent.copy(alpha = 0.52f)
                } else {
                    MaterialTheme.colorScheme
                        .outlineVariant
                }
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonPhoto(
                photoUrl = guardian.photoUrl,
                contentDescription = "Guardian photo",
                fallbackIcon = Icons.Filled.Badge,
                size = 56.dp,
                accent = accent
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text =
                        guardian.displayName.ifBlank {
                            guardian.email.ifBlank {
                                "Authorized guardian"
                            }
                        },
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text =
                        buildGuardianLabel(
                            relationship,
                            isPrimary
                        ),
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        if (selected) {
                            accent
                        } else {
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                        }
                )
                if (guardian.email.isNotBlank()) {
                    Text(
                        text = guardian.email,
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
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
private fun PersonPhoto(
    photoUrl: String?,
    contentDescription: String,
    fallbackIcon:
        androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.16f)
        )
    ) {
        if (!photoUrl.isNullOrBlank()) {
            SmartImage(
                model = photoUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = accent
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
                color =
                    MaterialTheme.colorScheme
                        .errorContainer.copy(
                            alpha = 0.50f
                        ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme
                        .error.copy(alpha = 0.16f)
                )
            ) {
                Row(
                    modifier =
                        Modifier.padding(Spacing.md),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.sm
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.error
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text =
                                "Pickup gates unavailable",
                            style =
                                MaterialTheme.typography
                                    .titleSmall,
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            text =
                                state.pickupGateError
                                    ?: "Could not load pickup gate information.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Spacer(
                            Modifier.width(Spacing.xs)
                        )
                        Text("Retry")
                    }
                }
            }
        }

        state.pickupGates.isEmpty() -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color =
                    MaterialTheme.colorScheme
                        .surfaceContainerLow
            ) {
                Row(
                    modifier =
                        Modifier.padding(Spacing.md),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.sm
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Pickup gate",
                            style =
                                MaterialTheme.typography
                                    .labelMedium,
                            fontWeight =
                                FontWeight.Bold
                        )
                        Text(
                            text =
                                "No active pickup gates are configured. This release will be recorded without a gate.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }
        }

        state.pickupGates.size == 1 -> {
            val gate = state.pickupGates.first()
            val success = successAccent()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = success.copy(alpha = 0.06f),
                border = BorderStroke(
                    1.dp,
                    success.copy(alpha = 0.16f)
                )
            ) {
                Row(
                    modifier =
                        Modifier.padding(Spacing.md),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.sm
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = success
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Pickup gate",
                            style =
                                MaterialTheme.typography
                                    .labelMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                        Text(
                            text = gate.displayName,
                            style =
                                MaterialTheme.typography
                                    .titleSmall,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = success
                    )
                }
            }
        }

        else -> {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange =
                    onExpandedChange
            ) {
                OutlinedTextField(
                    value =
                        state.selectedPickupGate
                            ?.displayName
                            ?: "",
                    onValueChange = {},
                    readOnly = true,
                    shape =
                        MaterialTheme.shapes.large,
                    label = {
                        Text("Pickup gate *")
                    },
                    placeholder = {
                        Text("Select release location")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults
                            .TrailingIcon(
                                expanded = expanded
                            )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        onExpandedChange(false)
                    }
                ) {
                    state.pickupGates.forEach { gate ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text =
                                            gate.displayName,
                                        fontWeight =
                                            FontWeight
                                                .SemiBold
                                    )
                                    if (
                                        gate.description
                                            .isNotBlank()
                                    ) {
                                        Text(
                                            text =
                                                gate.description,
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelect(gate)
                            }
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
        color =
            MaterialTheme.colorScheme
                .tertiaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary
                .copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme
                        .onTertiaryContainer
            )

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Audited action",
                    style =
                        MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                )
                Text(
                    text =
                        "This bypasses the normal QR pass flow. The student, guardian, gate, reason, staff member and release event are recorded by the server.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onTertiaryContainer
                            .copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    state: ManualPickupUiState
) {
    val student = state.selectedStudent
    val guardian = state.selectedGuardian

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Release summary",
                style =
                    MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.primary
            )

            ReviewSummaryRow(
                label = "Student",
                value =
                    student?.fullName
                        ?.ifBlank {
                            "Select a student"
                        }
                        ?: "Select a student"
            )
            ReviewSummaryRow(
                label = "Guardian",
                value =
                    guardian?.displayName
                        ?.ifBlank {
                            guardian.email.ifBlank {
                                "Select a guardian"
                            }
                        }
                        ?: "Select a guardian"
            )
            ReviewSummaryRow(
                label = "Gate",
                value =
                    state.selectedPickupGate
                        ?.displayName
                        ?: if (
                            state.pickupGatesLoaded &&
                            state.pickupGates.isEmpty()
                        ) {
                            "No gate configured"
                        } else {
                            "Select a gate"
                        }
            )
            ReviewSummaryRow(
                label = "Reason",
                value =
                    state.reason
                        .trim()
                        .ifBlank {
                            "Enter an audit reason"
                        }
            )
        }
    }
}

@Composable
private fun ReviewSummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier = Modifier.weight(0.32f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.68f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlineValidation(
    message: String
) {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = message,
            style =
                MaterialTheme.typography.bodySmall,
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
    val guardian =
        state.selectedGuardian ?: return

    val relationship =
        student.guardians[guardian.uid]
            ?.relationship
            .orEmpty()
            .trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme
                        .tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme
                                .onTertiaryContainer
                    )
                }
            }
        },
        title = {
            Text(
                text = "Confirm manual release",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text =
                        "Check these details before creating an audited dismissal record.",
                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow
                ) {
                    Column(
                        modifier =
                            Modifier.padding(Spacing.md),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                Spacing.sm
                            )
                    ) {
                        ReviewRow(
                            "Student",
                            student.fullName.ifBlank {
                                "Unnamed student"
                            }
                        )
                        ReviewRow(
                            "Student details",
                            "${studentIdentifier(student)} · ${studentClassLabel(student)}"
                        )
                        ReviewRow(
                            "Guardian",
                            guardian.displayName.ifBlank {
                                guardian.email.ifBlank {
                                    "Authorized guardian"
                                }
                            }
                        )
                        if (relationship.isNotBlank()) {
                            ReviewRow(
                                "Relationship",
                                relationship
                            )
                        }
                        ReviewRow(
                            "Pickup gate",
                            state.selectedPickupGate
                                ?.displayName
                                ?: "No gate configured"
                        )
                        ReviewRow(
                            "Reason",
                            state.reason.trim()
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color =
                        MaterialTheme.colorScheme
                            .tertiaryContainer.copy(
                                alpha = 0.48f
                            )
                ) {
                    Row(
                        modifier =
                            Modifier.padding(Spacing.md),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                Spacing.sm
                            ),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint =
                                MaterialTheme.colorScheme
                                    .onTertiaryContainer,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Text(
                            text =
                                "Confirm the guardian's identity before continuing. This action bypasses QR verification and is recorded for audit.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onTertiaryContainer
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
                        color =
                            MaterialTheme.colorScheme
                                .onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Releasing…")
                } else {
                    Text(
                        "Confirm release",
                        fontWeight = FontWeight.Bold
                    )
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
private fun ReviewRow(
    label: String,
    value: String
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium,
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
    val success = successAccent()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(Spacing.md)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = success.copy(alpha = 0.06f),
            border = BorderStroke(
                1.dp,
                success.copy(alpha = 0.18f)
            ),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    color = success.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = success,
                            modifier =
                                Modifier.size(42.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Student released",
                        style =
                            MaterialTheme.typography
                                .headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            MaterialTheme.colorScheme
                                .onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            state.success
                                ?: "Release approved and recorded",
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme
                            .outlineVariant
                )

                if (student != null) {
                    SuccessDetail(
                        label = "Student",
                        value =
                            student.fullName.ifBlank {
                                "Unnamed student"
                            }
                    )
                }

                if (guardian != null) {
                    SuccessDetail(
                        label = "Guardian",
                        value =
                            guardian.displayName.ifBlank {
                                guardian.email.ifBlank {
                                    "Authorized guardian"
                                }
                            }
                    )
                }

                SuccessDetail(
                    label = "Pickup gate",
                    value =
                        state.selectedPickupGate
                            ?.displayName
                            ?: "No gate configured"
                )

                SuccessDetail(
                    label = "Release method",
                    value = "Manual override"
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow
                ) {
                    Row(
                        modifier =
                            Modifier.padding(Spacing.md),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                Spacing.sm
                            ),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = success,
                            modifier =
                                Modifier.size(18.dp)
                        )
                        Text(
                            text =
                                "The release has been recorded in PickupPass as an audited manual override.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Done",
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(onClick = onReleaseAnother) {
            Text("Release another student")
        }
    }
}

@Composable
private fun SuccessDetail(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.62f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStudentsCard(
    hasError: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MaterialTheme.shapes.large,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (hasError) {
                            Icons.Filled.ErrorOutline
                        } else {
                            Icons.Filled.Person
                        },
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer
                    )
                }
            }

            Text(
                text =
                    if (hasError) {
                        "Student roster unavailable"
                    } else {
                        "No students available"
                    },
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (hasError) {
                        "PickupPass could not load the active school roster. Try again before attempting a manual release."
                    } else {
                        "No active students are currently available for manual release."
                    },
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            OutlinedButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Try again")
            }
        }
    }
}

@Composable
private fun successAccent(): Color =
    if (isSystemInDarkTheme()) {
        Success500
    } else {
        Success600
    }

private fun studentMatchesQuery(
    student: Student,
    query: String
): Boolean =
    listOf(
        student.fullName,
        student.studentNumber,
        student.grade,
        student.section,
        "grade ${student.grade}",
        "${student.grade} ${student.section}"
    ).any {
        it.lowercase().contains(query)
    }

private fun studentIdentifier(
    student: Student
): String =
    if (student.studentNumber.isNotBlank()) {
        "#${student.studentNumber}"
    } else {
        "Student record"
    }

private fun studentClassLabel(
    student: Student
): String {
    val grade = student.grade.trim()
    val section = student.section.trim()

    return when {
        grade.isNotBlank() &&
            section.isNotBlank() ->
            "Grade $grade · $section"

        grade.isNotBlank() ->
            "Grade $grade"

        section.isNotBlank() ->
            section

        else ->
            "Class not assigned"
    }
}

private fun buildGuardianLabel(
    relationship: String,
    isPrimary: Boolean
): String =
    when {
        relationship.isNotBlank() &&
            isPrimary ->
            "$relationship · Primary guardian"

        relationship.isNotBlank() ->
            relationship

        isPrimary ->
            "Primary guardian"

        else ->
            "Authorized guardian"
    }

private fun releaseDetailsComplete(
    state: ManualPickupUiState
): Boolean =
    state.pickupGatesLoaded &&
        (
            state.pickupGates.isEmpty() ||
                state.selectedPickupGate != null
            ) &&
        state.reason.trim().length >= 5
