package com.pickuppass.android.ui.schooladmin.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.AcademicPlacementOption
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner
import com.pickuppass.android.ui.theme.Spacing
import com.pickuppass.android.ui.common.PremiumTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteTeacherScreen(
    viewModel: InviteTeacherViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAcademicStructure: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadPlacements()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sectionQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    val selectedPlacements = remember(
        uiState.availablePlacements,
        selectedIds
    ) {
        uiState.availablePlacements.filter {
            it.gradeSectionId in selectedIds
        }
    }

    val filteredPlacements = remember(
        uiState.availablePlacements,
        sectionQuery
    ) {
        val query = sectionQuery.trim()
        if (query.isBlank()) {
            uiState.availablePlacements
        } else {
            uiState.availablePlacements.filter {
                it.grade.contains(query, ignoreCase = true) ||
                    it.section.contains(query, ignoreCase = true) ||
                    it.displayName.contains(query, ignoreCase = true)
            }
        }
    }

    val groupedPlacements = remember(filteredPlacements) {
        filteredPlacements.groupBy { it.grade }
    }

    LaunchedEffect(uiState.availablePlacements) {
        val allowedIds = uiState.availablePlacements
            .map { it.gradeSectionId }
            .toSet()
        selectedIds = selectedIds.intersect(allowedIds)
    }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            lastName = ""
            firstName = ""
            middleInitial = ""
            suffix = ""
            email = ""
            sectionQuery = ""
            selectedIds = emptySet()
        }
    }

    Scaffold(
        topBar = {
            PremiumTopAppBar(
                title = "Invite teacher",
                subtitle = "Teacher onboarding",
                onBack = { if (!uiState.isSubmitting) onBack() },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 780.dp)
                    .align(Alignment.TopCenter)
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "hero") {
                    InviteTeacherHero(
                        currentAcademicYearName = uiState.currentAcademicYearName,
                        availableCount = uiState.availablePlacements.size
                    )
                }

                item(key = "feedback") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        AnimatedVisibility(
                            visible = uiState.error != null,
                            enter = fadeIn() + expandVertically()
                        ) {
                            uiState.error?.let { ErrorBanner(it) }
                        }

                        AnimatedVisibility(
                            visible = uiState.successMessage != null,
                            enter = fadeIn() + expandVertically()
                        ) {
                            uiState.successMessage?.let {
                                if (uiState.successIsWarning) {
                                    WarningBanner(it)
                                } else {
                                    SuccessBanner(it)
                                }
                            }
                        }
                    }
                }

                item(key = "identity") {
                    TeacherIdentityCard(
                        lastName = lastName,
                        firstName = firstName,
                        middleInitial = middleInitial,
                        suffix = suffix,
                        email = email,
                        enabled = !uiState.isSubmitting,
                        onLastNameChange = {
                            lastName = it.take(80)
                            viewModel.clearFeedback()
                        },
                        onFirstNameChange = {
                            firstName = it.take(80)
                            viewModel.clearFeedback()
                        },
                        onMiddleInitialChange = {
                            middleInitial = it.take(2)
                            viewModel.clearFeedback()
                        },
                        onSuffixChange = {
                            suffix = it.take(20)
                            viewModel.clearFeedback()
                        },
                        onEmailChange = {
                            email = it.take(160)
                            viewModel.clearFeedback()
                        }
                    )
                }

                item(key = "assignment_intro") {
                    TeachingAssignmentHeader(
                        currentAcademicYearName = uiState.currentAcademicYearName,
                        selectedCount = selectedPlacements.size
                    )
                }

                when {
                    uiState.isLoadingPlacements -> {
                        item(key = "placements_loading") {
                            PlacementLoadingCard()
                        }
                    }

                    uiState.placementError != null -> {
                        item(key = "placements_error") {
                            PlacementSetupRequiredCard(
                                message = uiState.placementError.orEmpty(),
                                onOpenAcademicStructure = onOpenAcademicStructure
                            )
                        }
                    }

                    else -> {
                        if (uiState.availablePlacements.size > 6) {
                            item(key = "placement_search") {
                                OutlinedTextField(
                                    value = sectionQuery,
                                    onValueChange = {
                                        sectionQuery = it.take(80)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text("Find grade or section") },
                                    placeholder = { Text("e.g. Grade 6 or Rizal") },
                                    singleLine = true,
                                    enabled = !uiState.isSubmitting,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (filteredPlacements.isEmpty()) {
                            item(key = "placement_empty_filter") {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.lg),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(Spacing.sm))
                                        Text(
                                            "No classes match your search",
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(
                                            onClick = { sectionQuery = "" }
                                        ) {
                                            Text("Clear search")
                                        }
                                    }
                                }
                            }
                        } else {
                            groupedPlacements.forEach { (grade, placements) ->
                                item(key = "grade-" + grade) {
                                    GradeAssignmentCard(
                                        grade = grade,
                                        placements = placements,
                                        selectedIds = selectedIds,
                                        enabled = !uiState.isSubmitting,
                                        onToggle = { placement ->
                                            selectedIds =
                                                if (placement.gradeSectionId in selectedIds) {
                                                    selectedIds - placement.gradeSectionId
                                                } else {
                                                    selectedIds + placement.gradeSectionId
                                                }
                                            viewModel.clearFeedback()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "review") {
                    InviteReviewCard(
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        placements = selectedPlacements
                    )
                }

                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                lastName = ""
                                firstName = ""
                                middleInitial = ""
                                suffix = ""
                                email = ""
                                sectionQuery = ""
                                selectedIds = emptySet()
                                viewModel.clearFeedback()
                            },
                            enabled = !uiState.isSubmitting
                        ) {
                            Text("Clear")
                        }

                        Spacer(Modifier.width(Spacing.sm))

                        Button(
                            onClick = {
                                viewModel.invite(
                                    lastName = lastName,
                                    firstName = firstName,
                                    middleInitial = middleInitial,
                                    suffix = suffix,
                                    email = email,
                                    placements = selectedPlacements
                                )
                            },
                            enabled =
                                !uiState.isSubmitting &&
                                    !uiState.isLoadingPlacements &&
                                    uiState.placementError == null &&
                                    lastName.isNotBlank() &&
                                    firstName.isNotBlank() &&
                                    email.isNotBlank() &&
                                    selectedPlacements.isNotEmpty(),
                            modifier = Modifier.heightIn(min = 50.dp),
                            contentPadding = PaddingValues(
                                horizontal = 18.dp
                            )
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(Spacing.sm))
                            } else {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                            }

                            Text(
                                if (uiState.isSubmitting) {
                                    "Creating account…"
                                } else {
                                    "Send invite"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                item(key = "security_note") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "The teacher account is created only for this school. Roster access and section announcements are scoped to the current classes you select here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteTeacherHero(
    currentAcademicYearName: String,
    availableCount: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                "TEACHER ONBOARDING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "Invite with teaching scope already configured.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Create the account and assign its grade and section access in one controlled flow. No free-text class assignments are used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(Spacing.md))

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                HeroPill(
                    label = currentAcademicYearName.ifBlank {
                        "No current year"
                    }
                )
                HeroPill(
                    label = availableCount.toString() +
                        " active class" +
                        (if (availableCount == 1) "" else "es")
                )
            }
        }
    }
}

@Composable
private fun HeroPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = 6.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TeacherIdentityCard(
    lastName: String,
    firstName: String,
    middleInitial: String,
    suffix: String,
    email: String,
    enabled: Boolean,
    onLastNameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onMiddleInitialChange: (String) -> Unit,
    onSuffixChange: (String) -> Unit,
    onEmailChange: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "Teacher identity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "Use the teacher's official school name and the email they will use to sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = onLastNameChange,
                label = { Text("Last name") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = firstName,
                onValueChange = onFirstNameChange,
                label = { Text("First name") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = middleInitial,
                    onValueChange = onMiddleInitialChange,
                    label = { Text("M.I.") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = suffix,
                    onValueChange = onSuffixChange,
                    label = { Text("Suffix") },
                    placeholder = { Text("Jr., III") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(2f)
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Sign-in email") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AlternateEmail,
                        contentDescription = null
                    )
                },
                supportingText = {
                    Text(
                        "PickupPass sends the account setup link to this address."
                    )
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TeachingAssignmentHeader(
    currentAcademicYearName: String,
    selectedCount: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    "Teaching assignment",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    currentAcademicYearName.ifBlank {
                        "Current school year not configured"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        selectedCount.toString() + " selected"
                    )
                }
            )
        }
    }
}

@Composable
private fun PlacementLoadingCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    "Loading school setup…",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Checking the current academic year and active grade sections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlacementSetupRequiredCard(
    message: String,
    onOpenAcademicStructure: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                "School setup required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = onOpenAcademicStructure
            ) {
                Icon(
                    Icons.Filled.School,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Open School Year & Sections")
            }
        }
    }
}

@Composable
private fun GradeAssignmentCard(
    grade: String,
    placements: List<AcademicPlacementOption>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onToggle: (AcademicPlacementOption) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column {
            Column(
                modifier = Modifier.padding(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.sm
                )
            ) {
                Text(
                    if (grade.startsWith("Grade", ignoreCase = true)) {
                        grade
                    } else {
                        "Grade " + grade
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    placements.size.toString() +
                        " active section" +
                        (if (placements.size == 1) "" else "s"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            placements.forEachIndexed { index, placement ->
                if (index > 0) {
                    HorizontalDivider()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            onToggle(placement)
                        }
                        .padding(
                            horizontal = Spacing.md,
                            vertical = 11.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = placement.gradeSectionId in selectedIds,
                        onCheckedChange = if (enabled) {
                            { onToggle(placement) }
                        } else {
                            null
                        }
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Column(Modifier.weight(1f)) {
                        Text(
                            placement.section,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            placement.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteReviewCard(
    firstName: String,
    lastName: String,
    email: String,
    placements: List<AcademicPlacementOption>
) {
    val hasIdentity =
        firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            email.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (hasIdentity && placements.isNotEmpty()) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                "Review before inviting",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.sm))

            ReviewLine(
                label = "Teacher",
                value = listOf(firstName.trim(), lastName.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Not complete" }
            )
            ReviewLine(
                label = "Email",
                value = email.trim().ifBlank { "Not complete" }
            )
            ReviewLine(
                label = "Teaching scope",
                value = if (placements.isEmpty()) {
                    "Select at least one class"
                } else {
                    placements.size.toString() +
                        " class" +
                        (if (placements.size == 1) "" else "es")
                }
            )

            if (placements.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    placements.joinToString("  •  ") {
                        it.displayName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReviewLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
