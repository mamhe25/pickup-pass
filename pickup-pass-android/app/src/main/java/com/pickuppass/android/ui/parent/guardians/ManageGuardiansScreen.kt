package com.pickuppass.android.ui.parent.guardians

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.GuardianAvatar
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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ManageGuardiansScreen(
    studentId: String,
    viewModel: ManageGuardiansViewModel = hiltViewModel(),
    onBack: () -> Unit,
    canRegisterPrimary: Boolean = false,
    onRegisterPrimary: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmRemoveRow by remember {
        mutableStateOf<GuardianRow?>(null)
    }

    var scheduleRow by remember {
        mutableStateOf<GuardianRow?>(null)
    }

    var addMode by remember {
        mutableStateOf(GuardianAddMode.PERMANENT)
    }

    val primaryGuardian =
        uiState.guardians.firstOrNull {
            it.entry.isPrimary
        }
    val additionalGuardians =
        uiState.guardians.filterNot {
            it.entry.isPrimary
        }
    val hasPrimaryGuardian =
        primaryGuardian != null

    LaunchedEffect(studentId) {
        viewModel.load(studentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authorized Guardians") },
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.md,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                GuardianHero(
                    studentName = uiState.studentName,
                    guardians = uiState.guardians
                )
            }

            uiState.listError?.let { message ->
                item {
                    ErrorBanner(message)
                }
            }

            item {
                SectionHeading(
                    title = "Primary guardian",
                    detail = "The primary guardian is the accountable guardian of record. Only school staff can register this role."
                )
            }

            if (uiState.isLoading) {
                item {
                    GuardiansLoadingCard()
                }
            } else if (primaryGuardian != null) {
                item(key = "primary-${primaryGuardian.uid}") {
                    GuardianRowCard(
                        row = primaryGuardian,
                        onRemoveClick = {},
                        onScheduleClick = {},
                        scheduleEnabled = false,
                        modifier = Modifier.animateItemPlacement(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    )
                }
            } else {
                item {
                    MissingPrimaryGuardianCard(
                        canRegisterPrimary = canRegisterPrimary,
                        onRegisterPrimary = onRegisterPrimary
                    )
                }
            }

            item {
                SectionHeading(
                    title = "Backup & temporary guardians",
                    detail = "Backup and one-day access can be adjusted without changing the protected primary guardian."
                )
            }

            if (!uiState.isLoading) {
                if (additionalGuardians.isEmpty()) {
                    item {
                        NoAdditionalGuardiansCard(
                            hasPrimaryGuardian = hasPrimaryGuardian
                        )
                    }
                } else {
                    items(
                        items = additionalGuardians,
                        key = { it.uid }
                    ) { row ->
                        GuardianRowCard(
                            row = row,
                            onRemoveClick = {
                                confirmRemoveRow = row
                            },
                            onScheduleClick = {
                                scheduleRow = row
                            },
                            scheduleEnabled =
                                uiState.guardianSchedulesEnabled,
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.xs))

                SectionHeading(
                    title = "Add pickup access",
                    detail = if (hasPrimaryGuardian) {
                        "Add a backup guardian or one-day authorization. These roles never replace the primary guardian."
                    } else {
                        "Register the primary guardian first. Backup and one-day authorization stays locked until a primary guardian is on record."
                    }
                )
            }

            if (hasPrimaryGuardian) {
                item {
                    AddModePicker(
                        mode = addMode,
                        oneDayEnabled =
                            uiState.temporaryGuardiansEnabled,
                        onModeChange = { addMode = it }
                    )
                }

                item {
                    when (addMode) {
                        GuardianAddMode.PERMANENT -> {
                            AddGuardianForm(
                                isSubmitting =
                                    uiState.isSubmitting,
                                formError =
                                    uiState.formError,
                                formSuccess =
                                    uiState.formSuccess,
                                formIsWarning =
                                    uiState.formIsWarning,
                                onSubmit = {
                                        lastName,
                                        firstName,
                                        mi,
                                        suffix,
                                        email,
                                        relationship ->
                                    viewModel.addGuardian(
                                        lastName,
                                        firstName,
                                        mi,
                                        suffix,
                                        email,
                                        relationship
                                    )
                                }
                            )
                        }

                        GuardianAddMode.ONE_DAY -> {
                            if (uiState.temporaryGuardiansEnabled) {
                                TemporaryGuardianForm(
                                    isSubmitting =
                                        uiState.isSubmitting,
                                    formError =
                                        uiState.formError,
                                    formSuccess =
                                        uiState.formSuccess,
                                    formIsWarning =
                                        uiState.formIsWarning,
                                    onSubmit = {
                                            lastName,
                                            firstName,
                                            mi,
                                            suffix,
                                            email,
                                            relationship,
                                            validDate ->
                                        viewModel.addTemporaryGuardian(
                                            lastName,
                                            firstName,
                                            mi,
                                            suffix,
                                            email,
                                            relationship,
                                            validDate
                                        )
                                    }
                                )
                            } else {
                                FeatureUnavailableCard()
                            }
                        }
                    }
                }
            } else {
                item {
                    PrimaryRequiredCard(
                        canRegisterPrimary = canRegisterPrimary,
                        onRegisterPrimary = onRegisterPrimary
                    )
                }
            }

            item {
                SafetyNote()
            }
        }
    }

    scheduleRow?.let { row ->
        GuardianScheduleDialog(
            row = row,
            isSubmitting = uiState.isSubmitting,
            onDismiss = {
                scheduleRow = null
            },
            onSave = {
                    enabled,
                    days,
                    startDate,
                    endDate ->
                viewModel.updatePickupSchedule(
                    row.uid,
                    enabled,
                    days,
                    startDate,
                    endDate
                )
                scheduleRow = null
            }
        )
    }

    confirmRemoveRow?.let { row ->
        val name =
            row.profile?.displayName
                ?.ifBlank { "this guardian" }
                ?: "this guardian"

        AlertDialog(
            onDismissRequest = {
                confirmRemoveRow = null
            },
            title = {
                Text("Remove $name?")
            },
            text = {
                Text(
                    "Their pickup authorization will end immediately. Any unused QR pass they hold will stop working."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSubmitting,
                    onClick = {
                        viewModel.removeGuardian(row.uid)
                        confirmRemoveRow = null
                    }
                ) {
                    Text(
                        "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSubmitting,
                    onClick = {
                        confirmRemoveRow = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GuardianHero(
    studentName: String,
    guardians: List<GuardianRow>
) {
    val primaryCount =
        guardians.count { it.entry.isPrimary }

    val oneDayCount =
        guardians.count {
            it.entry.authorizationType.equals(
                "temporary",
                ignoreCase = true
            )
        }

    val backupCount =
        (guardians.size - primaryCount - oneDayCount)
            .coerceAtLeast(0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "PICKUP AUTHORIZATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.68f)
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = studentName.ifBlank {
                    "Student guardians"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Control who may present a pickup pass for this student. School staff still verifies the person before release.",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroMetric(
                    value = primaryCount.toString(),
                    label = "Primary",
                    modifier = Modifier.weight(1f)
                )

                HeroMetric(
                    value = backupCount.toString(),
                    label = "Backup",
                    modifier = Modifier.weight(1f)
                )

                HeroMetric(
                    value = oneDayCount.toString(),
                    label = "One-day",
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
        color =
            MaterialTheme.colorScheme.onPrimary
                .copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.md
            ),
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
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.70f)
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
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
                text = "Loading authorized guardians…",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
            containerColor =
                MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PersonAddAlt1,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No primary guardian registered",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (canRegisterPrimary) {
                        "Register the student's accountable guardian before adding backup or one-day pickup access."
                    } else {
                        "The school must register the student's accountable primary guardian before additional pickup access can be added."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (canRegisterPrimary && onRegisterPrimary != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Button(
                        onClick = onRegisterPrimary,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Register primary guardian")
                    }
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
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = if (hasPrimaryGuardian) {
                    "No backup or one-day guardians"
                } else {
                    "Additional access unavailable"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = if (hasPrimaryGuardian) {
                    "Only the primary guardian is currently authorized. Add backup access below when another person needs pickup permission."
                } else {
                    "Register a primary guardian before adding another authorized pickup person."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = "Primary guardian required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Backup and one-day pickup permissions are secondary authorizations and cannot be created until the student has a primary guardian.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canRegisterPrimary && onRegisterPrimary != null) {
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = onRegisterPrimary,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Register primary guardian")
                }
            }
        }
    }
}

@Composable
private fun GuardianRowCard(
    row: GuardianRow,
    onRemoveClick: () -> Unit,
    onScheduleClick: () -> Unit,
    scheduleEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val temporary =
        row.entry.authorizationType.equals(
            "temporary",
            ignoreCase = true
        )

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuardianAvatar(
                    photoUrl = row.profile?.photoUrl,
                    size = 54.dp
                )

                Spacer(Modifier.width(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            row.profile?.displayName
                                ?.ifBlank {
                                    "Guardian identity unavailable"
                                }
                                ?: "Guardian identity unavailable",
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text =
                            row.entry.relationship
                                .ifBlank {
                                    "Authorized pickup"
                                },
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                GuardianTypeBadge(
                    primary = row.entry.isPrimary,
                    temporary = temporary
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.62f)
            ) {
                Text(
                    text = guardianPermissionSummary(row),
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            if (!row.entry.isPrimary) {
                Spacer(Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (!temporary && scheduleEnabled) {
                        TextButton(
                            onClick = onScheduleClick,
                            modifier =
                                Modifier.heightIn(min = 44.dp)
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
                        modifier =
                            Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint =
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            "Remove",
                            color =
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(Spacing.sm))

                Text(
                    text = "Primary guardian access is protected and cannot be removed from this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
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
        primary ->
            MaterialTheme.colorScheme.primaryContainer

        temporary ->
            MaterialTheme.colorScheme.secondaryContainer

        else ->
            MaterialTheme.colorScheme.surfaceVariant
    }

    val content = when {
        primary ->
            MaterialTheme.colorScheme.onPrimaryContainer

        temporary ->
            MaterialTheme.colorScheme.onSecondaryContainer

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container
    ) {
        Text(
            text = label,
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

private fun guardianPermissionSummary(
    row: GuardianRow
): String {
    if (
        row.entry.authorizationType.equals(
            "temporary",
            ignoreCase = true
        )
    ) {
        val date =
            row.entry.validDate.ifBlank {
                "authorized date"
            }

        return "Valid $date · one pickup only"
    }

    if (row.entry.pickupScheduleEnabled) {
        val days =
            row.entry.pickupDays.joinToString(", ") {
                it.take(3)
                    .lowercase()
                    .replaceFirstChar { c ->
                        c.uppercase()
                    }
            }.ifBlank {
                "No days selected"
            }

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
private fun AddModePicker(
    mode: GuardianAddMode,
    oneDayEnabled: Boolean,
    onModeChange: (GuardianAddMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(
            selected =
                mode == GuardianAddMode.PERMANENT,
            onClick = {
                onModeChange(
                    GuardianAddMode.PERMANENT
                )
            },
            label = {
                Text("Backup guardian")
            }
        )

        FilterChip(
            selected =
                mode == GuardianAddMode.ONE_DAY,
            enabled = oneDayEnabled,
            onClick = {
                onModeChange(
                    GuardianAddMode.ONE_DAY
                )
            },
            label = {
                Text("One-day pickup")
            }
        )
    }
}

@Composable
private fun GuardianScheduleDialog(
    row: GuardianRow,
    isSubmitting: Boolean,
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
        mutableStateOf(
            row.entry.pickupScheduleEnabled
        )
    }

    var selectedDays by remember(row.uid) {
        mutableStateOf(
            row.entry.pickupDays.toSet()
        )
    }

    var startDate by remember(row.uid) {
        mutableStateOf(
            row.entry.scheduleStartDate
        )
    }

    var endDate by remember(row.uid) {
        mutableStateOf(
            row.entry.scheduleEndDate
        )
    }

    val invalidDateRange =
        startDate.isNotBlank() &&
            endDate.isNotBlank() &&
            endDate < startDate

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
            }
        },
        title = {
            Text("Pickup schedule")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text =
                        row.profile?.displayName
                            ?.ifBlank { "Guardian" }
                            ?: "Guardian",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Optional. Leave scheduling off to allow pickup on any day permitted by the school's policy.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                        }
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Text(
                        "Limit pickup to selected days"
                    )
                }

                if (enabled) {
                    Text(
                        text = "Authorized weekdays",
                        style =
                            MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    allDays.forEach { day ->
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked =
                                    selectedDays.contains(day),
                                onCheckedChange = { checked ->
                                    selectedDays =
                                        if (checked) {
                                            selectedDays + day
                                        } else {
                                            selectedDays - day
                                        }
                                }
                            )

                            Text(
                                day.lowercase()
                                    .replaceFirstChar { c ->
                                        c.uppercase()
                                    }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                        },
                        label = {
                            Text(
                                "Start date (optional)"
                            )
                        },
                        supportingText = {
                            Text("YYYY-MM-DD")
                        },
                        isError = invalidDateRange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = {
                            endDate = it
                        },
                        label = {
                            Text(
                                "End date (optional)"
                            )
                        },
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color =
                            MaterialTheme.colorScheme
                                .surfaceVariant
                    ) {
                        Text(
                            text = "Saving a schedule invalidates any unused QR already issued to this guardian.",
                            modifier =
                                Modifier.padding(Spacing.sm),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    !isSubmitting &&
                        !invalidDateRange &&
                        (!enabled ||
                            selectedDays.isNotEmpty()),
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
                        color =
                            MaterialTheme.colorScheme
                                .onPrimary
                    )

                    Spacer(Modifier.width(Spacing.sm))
                }

                Text(
                    if (isSubmitting) {
                        "Saving…"
                    } else {
                        "Save schedule"
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemporaryGuardianForm(
    isSubmitting: Boolean,
    formError: String?,
    formSuccess: String?,
    formIsWarning: Boolean,
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

    var email by remember {
        mutableStateOf("")
    }

    var relationship by remember {
        mutableStateOf("authorized pickup")
    }

    var validDate by remember {
        mutableStateOf(
            LocalDate.now().toString()
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "One-day pickup authorization",
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "For a trusted person who should pick up this student once on a specific date. Access is removed after the successful pickup.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
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
                modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                },
                label = {
                    Text("Guardian email")
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Email
                    ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = relationship,
                onValueChange = {
                    relationship = it
                },
                label = {
                    Text("Relationship")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = validDate,
                onValueChange = {
                    validDate = it
                },
                label = {
                    Text(
                        "Pickup date (YYYY-MM-DD)"
                    )
                },
                supportingText = {
                    Text(
                        "Today through the next 30 days"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.md))

            FormFeedback(
                formError = formError,
                formSuccess = formSuccess,
                formIsWarning = formIsWarning
            )

            CompactSubmitButton(
                text = "Authorize for one day",
                busyText = "Authorizing…",
                isSubmitting = isSubmitting,
                onClick = {
                    onSubmit(
                        lastName.trim(),
                        firstName.trim(),
                        middleInitial.trim(),
                        suffix.trim(),
                        email.trim(),
                        relationship.trim(),
                        validDate.trim()
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGuardianForm(
    isSubmitting: Boolean,
    formError: String?,
    formSuccess: String?,
    formIsWarning: Boolean,
    onSubmit: (
        lastName: String,
        firstName: String,
        middleInitial: String,
        suffix: String,
        email: String,
        relationship: String
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

    var email by remember {
        mutableStateOf("")
    }

    var relationship by remember {
        mutableStateOf(
            relationshipOptions.first().first
        )
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val firstNameFocus =
        remember { FocusRequester() }

    val emailFocus =
        remember { FocusRequester() }

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "Permanent backup guardian",
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Add a trusted person who may pick up this student when you are unavailable. They receive their own account and identity profile.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
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
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = {
                            firstNameFocus.requestFocus()
                        }
                    ),
                modifier = Modifier.fillMaxWidth()
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
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = {
                            emailFocus.requestFocus()
                        }
                    ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstNameFocus)
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                },
                label = {
                    Text("Email address")
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
            )

            Spacer(Modifier.height(Spacing.sm))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = it
                }
            ) {
                OutlinedTextField(
                    value =
                        relationshipOptions.first {
                            it.first == relationship
                        }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Relationship")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults
                            .TrailingIcon(
                                expanded = expanded
                            )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    relationshipOptions.forEach {
                            (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(label)
                            },
                            onClick = {
                                relationship = value
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            FormFeedback(
                formError = formError,
                formSuccess = formSuccess,
                formIsWarning = formIsWarning
            )

            CompactSubmitButton(
                text = "Add permanent guardian",
                busyText = "Adding…",
                isSubmitting = isSubmitting,
                onClick = {
                    onSubmit(
                        lastName.trim(),
                        firstName.trim(),
                        middleInitial.trim(),
                        suffix.trim(),
                        email.trim(),
                        relationship
                    )
                }
            )
        }
    }
}

@Composable
private fun FormFeedback(
    formError: String?,
    formSuccess: String?,
    formIsWarning: Boolean
) {
    formError?.let { message ->
        ErrorBanner(
            message,
            modifier =
                Modifier.padding(bottom = Spacing.md)
        )
    }

    formSuccess?.let { message ->
        if (formIsWarning) {
            WarningBanner(
                message,
                modifier =
                    Modifier.padding(
                        bottom = Spacing.md
                    )
            )
        } else {
            SuccessBanner(
                message,
                modifier =
                    Modifier.padding(
                        bottom = Spacing.md
                    )
            )
        }
    }
}

@Composable
private fun CompactSubmitButton(
    text: String,
    busyText: String,
    isSubmitting: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            enabled = !isSubmitting,
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
                    color =
                        MaterialTheme.colorScheme.onPrimary
                )

                Spacer(Modifier.width(Spacing.sm))
            }

            Text(
                if (isSubmitting) {
                    busyText
                } else {
                    text
                }
            )
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
            text = "One-day pickup authorization is not enabled for this school.",
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SafetyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = 0.65f)
    ) {
        Text(
            text = "Changing guardian access does not release a student. Every pickup still requires a valid pass and staff identity verification.",
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
