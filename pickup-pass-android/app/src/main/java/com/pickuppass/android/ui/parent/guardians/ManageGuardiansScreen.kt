package com.pickuppass.android.ui.parent.guardians

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.GuardianAvatar
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner
import com.pickuppass.android.ui.theme.Spacing
import java.time.LocalDate

private val relationshipOptions = listOf(
    "parent/guardian" to "Parent / Guardian",
    "grandparent" to "Grandparent",
    "relative" to "Other Relative",
    "caregiver" to "Caregiver / Nanny",
    "authorized pickup" to "Other Authorized Pickup"
)

private enum class GuardianAddMode {
    PERMANENT,
    ONE_DAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageGuardiansScreen(
    studentId: String,
    viewModel: ManageGuardiansViewModel = hiltViewModel(),
    onBack: () -> Unit,
    canRegisterPrimary: Boolean = false,
    onRegisterPrimary: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmRemoveRow by remember { mutableStateOf<GuardianRow?>(null) }
    var scheduleRow by remember { mutableStateOf<GuardianRow?>(null) }
    var photoRow by remember { mutableStateOf<GuardianRow?>(null) }
    var addMode by remember { mutableStateOf(GuardianAddMode.PERMANENT) }

    val primaryGuardian = uiState.guardians.firstOrNull {
        it.entry.isPrimary == true
    }
    val additionalGuardians = uiState.guardians.filterNot {
        it.entry.isPrimary == true
    }
    val hasPrimaryGuardian = primaryGuardian != null

    val actionsBusy = uiState.isSubmitting || uiState.isLoading

    LaunchedEffect(studentId) {
        viewModel.load(studentId)
    }

    LaunchedEffect(uiState.completedAction) {
        when (uiState.completedAction) {
            "schedule-updated" -> scheduleRow = null
            "guardian-removed" -> confirmRemoveRow = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Authorized Guardians",
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState.studentName.isNotBlank()) {
                            Text(
                                uiState.studentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "hero") {
                    GuardianHero(
                        studentName = uiState.studentName,
                        guardians = uiState.guardians
                    )
                }

                uiState.listError?.let { message ->
                    item(key = "list_error") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            ErrorBanner(message)
                            OutlinedButton(
                                onClick = { viewModel.load(studentId) },
                                enabled = !uiState.isLoading
                            ) {
                                Text("Retry guardian details")
                            }
                        }
                    }
                }

                if (uiState.formError != null || uiState.formSuccess != null) {
                    item(key = "feedback") {
                        FormFeedback(
                            formError = uiState.formError,
                            formSuccess = uiState.formSuccess,
                            formIsWarning = uiState.formIsWarning
                        )
                    }
                }

                item(key = "primary_heading") {
                    SectionHeading(
                        title = "Primary guardian",
                        detail = "The accountable guardian of record. This protected role can only be registered by school staff."
                    )
                }

                if (uiState.isLoading) {
                    item(key = "loading") {
                        GuardiansLoadingCard()
                    }
                } else if (primaryGuardian != null) {
                    item(key = "primary-${primaryGuardian.uid}") {
                        GuardianRowCard(
                            row = primaryGuardian,
                            scheduleEnabled = false,
                            busy = actionsBusy,
                            onPhotoClick = {
                                if (!primaryGuardian.profile?.photoUrl.isNullOrBlank()) {
                                    photoRow = primaryGuardian
                                }
                            },
                            onScheduleClick = {},
                            onRemoveClick = {}
                        )
                    }
                } else {
                    item(key = "missing_primary") {
                        MissingPrimaryGuardianCard(
                            canRegisterPrimary = canRegisterPrimary,
                            onRegisterPrimary = onRegisterPrimary
                        )
                    }
                }

                item(key = "additional_heading") {
                    SectionHeading(
                        title = "Backup & temporary guardians",
                        detail = "Additional pickup access never replaces the protected primary guardian."
                    )
                }

                if (!uiState.isLoading) {
                    if (additionalGuardians.isEmpty()) {
                        item(key = "additional_empty") {
                            NoAdditionalGuardiansCard(
                                hasPrimaryGuardian = hasPrimaryGuardian
                            )
                        }
                    } else {
                        items(
                            items = additionalGuardians,
                            key = { "guardian-${it.uid}" }
                        ) { row ->
                            GuardianRowCard(
                                row = row,
                                scheduleEnabled =
                                    uiState.guardianSchedulesEnabled,
                                busy = actionsBusy,
                                onPhotoClick = {
                                    if (!row.profile?.photoUrl.isNullOrBlank()) {
                                        photoRow = row
                                    }
                                },
                                onScheduleClick = {
                                    viewModel.clearFeedback()
                                    scheduleRow = row
                                },
                                onRemoveClick = {
                                    viewModel.clearFeedback()
                                    confirmRemoveRow = row
                                }
                            )
                        }
                    }
                }

                item(key = "add_heading") {
                    SectionHeading(
                        title = "Add pickup access",
                        detail = if (hasPrimaryGuardian) {
                            "Add a permanent backup guardian or a one-day pickup authorization."
                        } else {
                            "Register the primary guardian first. Additional pickup access stays locked until that role exists."
                        }
                    )
                }

                if (hasPrimaryGuardian) {
                    item(key = "mode_picker") {
                        AddModePicker(
                            mode = addMode,
                            oneDayEnabled =
                                uiState.temporaryGuardiansEnabled,
                            onModeChange = {
                                viewModel.clearFeedback()
                                addMode = it
                            }
                        )
                    }

                    item(key = "add_form-${addMode.name}") {
                        when (addMode) {
                            GuardianAddMode.PERMANENT -> {
                                AddGuardianForm(
                                    isSubmitting = actionsBusy,
                                    onSubmit = viewModel::addGuardian
                                )
                            }

                            GuardianAddMode.ONE_DAY -> {
                                if (uiState.temporaryGuardiansEnabled) {
                                    TemporaryGuardianForm(
                                        isSubmitting = actionsBusy,
                                        onSubmit = viewModel::addTemporaryGuardian
                                    )
                                } else {
                                    FeatureUnavailableCard()
                                }
                            }
                        }
                    }
                } else {
                    item(key = "primary_required") {
                        PrimaryRequiredCard(
                            canRegisterPrimary = canRegisterPrimary,
                            onRegisterPrimary = onRegisterPrimary
                        )
                    }
                }

                item(key = "safety") {
                    SafetyNote()
                }
            }
        }
    }

    scheduleRow?.let { row ->
        GuardianScheduleDialog(
            row = row,
            isSubmitting = actionsBusy,
            error = uiState.formError,
            onDismiss = {
                if (!actionsBusy) {
                    scheduleRow = null
                    viewModel.clearFeedback()
                }
            },
            onSave = { enabled, days, startDate, endDate ->
                viewModel.updatePickupSchedule(
                    row.uid,
                    enabled,
                    days,
                    startDate,
                    endDate
                )
            }
        )
    }

    confirmRemoveRow?.let { row ->
        val name = row.profile?.displayName
            ?.ifBlank { "this guardian" }
            ?: "this guardian"

        AlertDialog(
            onDismissRequest = {
                if (!actionsBusy) {
                    confirmRemoveRow = null
                    viewModel.clearFeedback()
                }
            },
            title = { Text("Remove $name?") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        "Their pickup authorization will end immediately. Any unused QR pass they hold will stop working."
                    )
                    uiState.formError?.let { ErrorBanner(it) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionsBusy,
                    onClick = {
                        viewModel.removeGuardian(row.uid)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (actionsBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text("Remove access")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !actionsBusy,
                    onClick = {
                        confirmRemoveRow = null
                        viewModel.clearFeedback()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    photoRow?.let { row ->
        GuardianPhotoDialog(
            row = row,
            onDismiss = { photoRow = null }
        )
    }
}

@Composable
private fun GuardianHero(
    studentName: String,
    guardians: List<GuardianRow>
) {
    val primaryCount = guardians.count { it.entry.isPrimary == true }
    val oneDayCount = guardians.count {
        it.entry.authorizationType.equals("temporary", ignoreCase = true)
    }
    val backupCount =
        (guardians.size - primaryCount - oneDayCount).coerceAtLeast(0)

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
                "PICKUP AUTHORIZATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                studentName.ifBlank { "Student guardians" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Control who may present a pickup pass. School staff still verifies the person before release.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroMetric(
                    primaryCount.toString(),
                    "Primary",
                    Modifier.weight(1f)
                )
                HeroMetric(
                    backupCount.toString(),
                    "Backup",
                    Modifier.weight(1f)
                )
                HeroMetric(
                    oneDayCount.toString(),
                    "One-day",
                    Modifier.weight(1f)
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.md
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Spacer(Modifier.height(Spacing.xs))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuardiansLoadingCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                "Loading authorized guardians…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuardianRowCard(
    row: GuardianRow,
    scheduleEnabled: Boolean,
    busy: Boolean,
    onPhotoClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val temporary = row.entry.authorizationType.equals(
        "temporary",
        ignoreCase = true
    )

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
                Box(
                    modifier = Modifier.clickable(
                        enabled = !row.profile?.photoUrl.isNullOrBlank(),
                        onClick = onPhotoClick
                    )
                ) {
                    GuardianAvatar(
                        photoUrl = row.profile?.photoUrl,
                        size = 56.dp
                    )
                }

                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        row.profile?.displayName
                            ?.ifBlank { "Guardian identity unavailable" }
                            ?: "Guardian identity unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        row.entry.relationship.ifBlank {
                            "Authorized pickup"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!row.profile?.photoUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Tap photo to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                GuardianTypeBadge(
                    primary = row.entry.isPrimary == true,
                    temporary = temporary
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            ) {
                Text(
                    guardianPermissionSummary(row),
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (row.entry.isPrimary != true) {
                Spacer(Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (!temporary && scheduleEnabled) {
                        TextButton(
                            onClick = onScheduleClick,
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Schedule")
                        }
                    }

                    TextButton(
                        onClick = onRemoveClick,
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Remove",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Primary guardian access is protected and cannot be removed from this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuardianTypeBadge(
    primary: Boolean,
    temporary: Boolean
) {
    val label = when {
        primary -> "Primary"
        temporary -> "One-day"
        else -> "Backup"
    }

    val container = when {
        primary -> MaterialTheme.colorScheme.primaryContainer
        temporary -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val content = when {
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        temporary -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = container
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

private fun guardianPermissionSummary(row: GuardianRow): String {
    if (
        row.entry.authorizationType.equals(
            "temporary",
            ignoreCase = true
        )
    ) {
        return "Valid ${row.entry.validDate.ifBlank { "authorized date" }} · one pickup only"
    }

    if (row.entry.pickupScheduleEnabled) {
        val days = row.entry.pickupDays.joinToString(", ") {
            it.take(3)
                .lowercase()
                .replaceFirstChar { c -> c.uppercase() }
        }.ifBlank { "No days selected" }

        val range = when {
            row.entry.scheduleStartDate.isNotBlank() &&
                row.entry.scheduleEndDate.isNotBlank() ->
                " · ${row.entry.scheduleStartDate} to ${row.entry.scheduleEndDate}"

            row.entry.scheduleStartDate.isNotBlank() ->
                " · from ${row.entry.scheduleStartDate}"

            row.entry.scheduleEndDate.isNotBlank() ->
                " · through ${row.entry.scheduleEndDate}"

            else -> ""
        }

        return "Scheduled: $days$range"
    }

    return "No recurring restriction · may pick up on any day allowed by school policy"
}

@Composable
private fun MissingPrimaryGuardianCard(
    canRegisterPrimary: Boolean,
    onRegisterPrimary: (() -> Unit)?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PersonAddAlt1,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    "No primary guardian registered",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (canRegisterPrimary) {
                        "Register the guardian of record before adding backup or one-day pickup access."
                    } else {
                        "Contact school staff to register the guardian of record."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canRegisterPrimary && onRegisterPrimary != null) {
                Spacer(Modifier.width(Spacing.sm))
                FilledTonalButton(onClick = onRegisterPrimary) {
                    Text("Register")
                }
            }
        }
    }
}

@Composable
private fun NoAdditionalGuardiansCard(
    hasPrimaryGuardian: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (hasPrimaryGuardian) {
                    "No backup guardians yet"
                } else {
                    "Additional access unavailable"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (hasPrimaryGuardian) {
                    "Add trusted pickup contacts only when they genuinely need authorization."
                } else {
                    "A primary guardian must be registered first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrimaryRequiredCard(
    canRegisterPrimary: Boolean,
    onRegisterPrimary: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                "Primary guardian required",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Backup and one-day authorization remain locked until the protected primary guardian is registered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canRegisterPrimary && onRegisterPrimary != null) {
                Spacer(Modifier.height(Spacing.sm))
                FilledTonalButton(onClick = onRegisterPrimary) {
                    Text("Register primary guardian")
                }
            }
        }
    }
}

@Composable
private fun AddModePicker(
    mode: GuardianAddMode,
    oneDayEnabled: Boolean,
    onModeChange: (GuardianAddMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(
            selected = mode == GuardianAddMode.PERMANENT,
            onClick = { onModeChange(GuardianAddMode.PERMANENT) },
            label = { Text("Backup guardian") }
        )

        FilterChip(
            selected = mode == GuardianAddMode.ONE_DAY,
            enabled = oneDayEnabled,
            onClick = { onModeChange(GuardianAddMode.ONE_DAY) },
            label = { Text("One-day pickup") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGuardianForm(
    isSubmitting: Boolean,
    onSubmit: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        relationship: String
    ) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relationship by remember {
        mutableStateOf(relationshipOptions.first().first)
    }
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "Permanent backup guardian",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Add a trusted person who may pick up this student when you are unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))

            GuardianNameFields(
                lastName = lastName,
                onLastNameChange = { lastName = it },
                firstName = firstName,
                onFirstNameChange = { firstName = it },
                middleInitial = middleInitial,
                onMiddleInitialChange = { middleInitial = it.take(2) },
                suffix = suffix,
                onSuffixChange = { suffix = it }
            )

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.sm))

            RelationshipDropdown(
                relationship = relationship,
                expanded = expanded,
                enabled = !isSubmitting,
                onExpandedChange = { expanded = it },
                onSelect = {
                    relationship = it
                    expanded = false
                }
            )

            Spacer(Modifier.height(Spacing.md))

            CompactSubmitButton(
                text = "Add permanent guardian",
                busyText = "Adding…",
                isSubmitting = isSubmitting,
                enabled = lastName.isNotBlank() &&
                    firstName.isNotBlank() &&
                    email.isNotBlank(),
                onClick = {
                    onSubmit(
                        lastName,
                        firstName,
                        middleInitial,
                        suffix,
                        email,
                        relationship
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemporaryGuardianForm(
    isSubmitting: Boolean,
    onSubmit: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        relationship: String,
        validDate: String
    ) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("authorized pickup") }
    var validDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "One-day pickup authorization",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Use for a trusted person who should pick up this student once on a specific date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))

            GuardianNameFields(
                lastName = lastName,
                onLastNameChange = { lastName = it },
                firstName = firstName,
                onFirstNameChange = { firstName = it },
                middleInitial = middleInitial,
                onMiddleInitialChange = { middleInitial = it.take(2) },
                suffix = suffix,
                onSuffixChange = { suffix = it }
            )

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Guardian email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.sm))

            RelationshipDropdown(
                relationship = relationship,
                expanded = expanded,
                enabled = !isSubmitting,
                onExpandedChange = { expanded = it },
                onSelect = {
                    relationship = it
                    expanded = false
                }
            )

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = validDate,
                onValueChange = { validDate = it.take(10) },
                label = { Text("Pickup date") },
                supportingText = { Text("YYYY-MM-DD") },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.md))

            CompactSubmitButton(
                text = "Authorize one-day pickup",
                busyText = "Authorizing…",
                isSubmitting = isSubmitting,
                enabled = lastName.isNotBlank() &&
                    firstName.isNotBlank() &&
                    email.isNotBlank() &&
                    validDate.isNotBlank(),
                onClick = {
                    onSubmit(
                        lastName,
                        firstName,
                        middleInitial,
                        suffix,
                        email,
                        relationship,
                        validDate
                    )
                }
            )
        }
    }
}

@Composable
private fun GuardianNameFields(
    lastName: String,
    onLastNameChange: (String) -> Unit,
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    middleInitial: String,
    onMiddleInitialChange: (String) -> Unit,
    suffix: String,
    onSuffixChange: (String) -> Unit
) {
    OutlinedTextField(
        value = lastName,
        onValueChange = onLastNameChange,
        label = { Text("Last name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(Spacing.sm))

    OutlinedTextField(
        value = firstName,
        onValueChange = onFirstNameChange,
        label = { Text("First name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(Spacing.sm))

    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = middleInitial,
            onValueChange = onMiddleInitialChange,
            label = { Text("M.I.") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = suffix,
            onValueChange = onSuffixChange,
            label = { Text("Suffix") },
            singleLine = true,
            modifier = Modifier.weight(2f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelationshipDropdown(
    relationship: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) onExpandedChange(it)
        }
    ) {
        OutlinedTextField(
            value = relationshipOptions
                .firstOrNull { it.first == relationship }
                ?.second
                ?: "Authorized pickup",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Relationship") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            relationshipOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun CompactSubmitButton(
    text: String,
    busyText: String,
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            enabled = enabled && !isSubmitting,
            onClick = onClick,
            modifier = Modifier.heightIn(min = 46.dp),
            contentPadding = PaddingValues(
                horizontal = 18.dp,
                vertical = 10.dp
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(Spacing.sm))
            }

            Text(if (isSubmitting) busyText else text)
        }
    }
}

@Composable
private fun GuardianScheduleDialog(
    row: GuardianRow,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (
        enabled: Boolean,
        days: List<String>,
        startDate: String,
        endDate: String
    ) -> Unit
) {
    val allDays = listOf(
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY",
        "SATURDAY",
        "SUNDAY"
    )

    var enabled by remember(row.uid) {
        mutableStateOf(row.entry.pickupScheduleEnabled)
    }
    var selectedDays by remember(row.uid) {
        mutableStateOf(row.entry.pickupDays.toSet())
    }
    var startDate by remember(row.uid) {
        mutableStateOf(row.entry.scheduleStartDate)
    }
    var endDate by remember(row.uid) {
        mutableStateOf(row.entry.scheduleEndDate)
    }

    val invalidDateRange =
        startDate.isNotBlank() &&
            endDate.isNotBlank() &&
            endDate < startDate

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = { Text("Pickup schedule") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    row.profile?.displayName
                        ?.ifBlank { "Guardian" }
                        ?: "Guardian",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Optional. Leave scheduling off to allow pickup on any day permitted by the school's policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        enabled = !isSubmitting
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Limit pickup to selected days")
                }

                if (enabled) {
                    Text(
                        "Authorized weekdays",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    allDays.forEach { day ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedDays.contains(day),
                                onCheckedChange = { checked ->
                                    selectedDays = if (checked) {
                                        selectedDays + day
                                    } else {
                                        selectedDays - day
                                    }
                                },
                                enabled = !isSubmitting
                            )
                            Text(
                                day.lowercase()
                                    .replaceFirstChar { c -> c.uppercase() }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it.take(10) },
                        label = { Text("Start date (optional)") },
                        supportingText = { Text("YYYY-MM-DD") },
                        isError = invalidDateRange,
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it.take(10) },
                        label = { Text("End date (optional)") },
                        supportingText = {
                            Text(
                                if (invalidDateRange) {
                                    "End date cannot be before start date"
                                } else {
                                    "YYYY-MM-DD"
                                }
                            )
                        },
                        isError = invalidDateRange,
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "Saving a schedule invalidates any unused QR already issued to this guardian.",
                            modifier = Modifier.padding(Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting &&
                    !invalidDateRange &&
                    (!enabled || selectedDays.isNotEmpty()),
                onClick = {
                    onSave(
                        enabled,
                        selectedDays.toList(),
                        startDate.trim(),
                        endDate.trim()
                    )
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (isSubmitting) "Saving…" else "Save schedule")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FormFeedback(
    formError: String?,
    formSuccess: String?,
    formIsWarning: Boolean
) {
    formError?.let {
        ErrorBanner(it)
    }

    formSuccess?.let {
        if (formIsWarning) {
            WarningBanner(it)
        } else {
            SuccessBanner(it)
        }
    }
}

@Composable
private fun FeatureUnavailableCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            "One-day pickup authorization is not enabled for this school.",
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SafetyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Changing guardian access does not release a student. Every pickup still requires a valid pass and staff identity verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuardianPhotoDialog(
    row: GuardianRow,
    onDismiss: () -> Unit
) {
    val photoUrl = row.profile?.photoUrl
    if (photoUrl.isNullOrBlank()) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.profile?.displayName
                                ?.ifBlank { "Guardian" }
                                ?: "Guardian",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            row.entry.relationship.ifBlank { "Authorized pickup" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                SmartImage(
                    model = photoUrl,
                    contentDescription = "Guardian verification photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                )
            }
        }
    }
}
