package com.pickuppass.android.ui.schooladmin.bulkimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

@Composable
fun BulkStudentImportScreen(
    viewModel: BulkStudentImportViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmImport by remember {
        mutableStateOf(false)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.selectFile(context, it)
        }
    }

    val preview = state.preview
    val importCompleted = state.importFinished

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Bulk import students",
                subtitle = "Dry-run validation before write",
                onBack = {
                    if (!state.isWorking) {
                        onBack()
                    }
                },
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
                    .widthIn(max = 820.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "hero") {
                    PremiumHeroCard(
                        eyebrow = "Roster onboarding",
                        title = "Import students safely",
                        message = "PickupPass validates the entire roster first. No student record is written until the dry run is clean and you explicitly confirm the import.",
                        icon = Icons.Filled.UploadFile
                    )
                }

                item(key = "steps") {
                    ImportSteps(
                        hasFile =
                            state.filename.isNotBlank(),
                        validated =
                            preview != null,
                        imported = importCompleted
                    )
                }

                item(key = "format") {
                    RosterFormatCard()
                }

                item(key = "file") {
                    SelectedRosterCard(
                        filename = state.filename,
                        working = state.isWorking,
                        importCompleted = importCompleted,
                        onChoose = {
                            viewModel.clearFeedback()
                            picker.launch(
                                arrayOf(
                                    "text/csv",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        onReset = viewModel::resetImport
                    )
                }

                if (state.isWorking) {
                    item(key = "working") {
                        WorkingCard(
                            message =
                                state.workingMessage
                                    .ifBlank {
                                        "Processing roster securely…"
                                    }
                        )
                    }
                }

                preview?.let { result ->
                    item(key = "validation-header") {
                        PremiumSectionHeader(
                            title =
                                if (importCompleted) {
                                    "Import result"
                                } else {
                                    "Validation result"
                                },
                            subtitle =
                                validationSubtitle(result)
                        )
                    }

                    item(key = "metrics-top") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    Spacing.sm
                                )
                        ) {
                            MetricCard(
                                label = "Rows",
                                value =
                                    result.totalRows
                                        .toString(),
                                modifier =
                                    Modifier.weight(1f)
                            )
                            MetricCard(
                                label =
                                    if (importCompleted) {
                                        "Imported"
                                    } else {
                                        "Ready"
                                    },
                                value =
                                    if (importCompleted) {
                                        result.importedRows
                                            .toString()
                                    } else {
                                        result.validRows
                                            .toString()
                                    },
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "metrics-bottom") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    Spacing.sm
                                )
                        ) {
                            MetricCard(
                                label = "Invalid",
                                value =
                                    result.invalidRows
                                        .toString(),
                                modifier =
                                    Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Duplicates",
                                value =
                                    result.duplicateRows
                                        .toString(),
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    if (
                        !importCompleted &&
                        result.invalidRows > 0
                    ) {
                        item(key = "invalid-notice") {
                            ValidationNotice(
                                tone =
                                    ValidationNoticeTone.Error,
                                title =
                                    "Roster needs correction",
                                message =
                                    "No student records were created. Fix the invalid rows in the source file, then upload it again."
                            )
                        }
                    } else if (
                        !importCompleted &&
                        result.validRows == 0 &&
                        result.duplicateRows > 0
                    ) {
                        item(key = "duplicates-only") {
                            ValidationNotice(
                                tone =
                                    ValidationNoticeTone.Info,
                                title =
                                    "Nothing new to import",
                                message =
                                    "Every valid row matches a student already in PickupPass or another row in this file. Duplicate rows are skipped."
                            )
                        }
                    } else if (
                        !importCompleted &&
                        result.readyToImport
                    ) {
                        item(key = "ready-notice") {
                            ValidationNotice(
                                tone =
                                    ValidationNoticeTone.Success,
                                title =
                                    "Roster is ready",
                                message =
                                    if (
                                        result.duplicateRows >
                                        0
                                    ) {
                                        result.validRows
                                            .toString() +
                                            " new student record" +
                                            if (
                                                result.validRows ==
                                                1
                                            ) {
                                                " is ready. "
                                            } else {
                                                "s are ready. "
                                            } +
                                            result.duplicateRows +
                                            " duplicate row" +
                                            if (
                                                result.duplicateRows ==
                                                1
                                            ) {
                                                " will be skipped."
                                            } else {
                                                "s will be skipped."
                                            }
                                    } else {
                                        "All validated rows are ready to be written to the current school tenant."
                                    }
                            )
                        }
                    }

                    if (result.errors.isNotEmpty()) {
                        item(key = "errors-header") {
                            PremiumSectionHeader(
                                title = "Rows to fix",
                                subtitle =
                                    "Showing up to the first 30 validation issues returned by the dry run."
                            )
                        }

                        items(
                            items =
                                result.errors.take(30),
                            key = {
                                it.row.toString() +
                                    ":" +
                                    it.field +
                                    ":" +
                                    it.message
                            }
                        ) { error ->
                            ValidationErrorCard(
                                row = error.row,
                                field = error.field,
                                message = error.message
                            )
                        }

                        if (result.errors.size > 30) {
                            item(key = "errors-more") {
                                Text(
                                    text =
                                        "There are " +
                                            (
                                                result.errors.size -
                                                    30
                                                ) +
                                            " additional reported validation issues.",
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

                    if (result.sample.isNotEmpty()) {
                        item(key = "sample-header") {
                            PremiumSectionHeader(
                                title = "Student preview",
                                subtitle =
                                    "A sample of validated rows before import."
                            )
                        }

                        items(
                            items = result.sample,
                            key = {
                                it.studentNumber +
                                    "|" +
                                    it.fullName +
                                    "|" +
                                    it.grade +
                                    "|" +
                                    it.section
                            }
                        ) { row ->
                            StudentPreviewCard(
                                fullName = row.fullName,
                                studentNumber =
                                    row.studentNumber,
                                grade = row.grade,
                                section = row.section
                            )
                        }
                    }

                    if (
                        result.readyToImport &&
                        result.importedRows == 0
                    ) {
                        item(key = "import-action") {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        confirmImport = true
                                    },
                                    enabled =
                                        !state.isWorking,
                                    modifier =
                                        Modifier
                                            .widthIn(
                                                min = 220.dp,
                                                max = 360.dp
                                            )
                                            .heightIn(
                                                min = 52.dp
                                            )
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier =
                                            Modifier.size(18.dp)
                                    )
                                    Spacer(
                                        Modifier.width(
                                            Spacing.xs
                                        )
                                    )
                                    Text(
                                        "Review & import " +
                                            result.validRows
                                    )
                                }
                            }
                        }
                    }

                    if (importCompleted) {
                        item(key = "another") {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick =
                                        viewModel::resetImport,
                                    enabled =
                                        !state.isWorking
                                ) {
                                    Icon(
                                        Icons.Filled.UploadFile,
                                        contentDescription = null,
                                        modifier =
                                            Modifier.size(18.dp)
                                    )
                                    Spacer(
                                        Modifier.width(
                                            Spacing.xs
                                        )
                                    )
                                    Text("Import another roster")
                                }
                            }
                        }
                    }
                }

                if (
                    state.filename.isNotBlank() &&
                    preview == null &&
                    !state.isWorking
                ) {
                    item(key = "retry") {
                        OutlinedButton(
                            onClick =
                                viewModel::retryValidation,
                            modifier =
                                Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(18.dp)
                            )
                            Spacer(
                                Modifier.width(Spacing.xs)
                            )
                            Text("Retry validation")
                        }
                    }
                }

                item(key = "safety") {
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
                            verticalAlignment =
                                Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .primary,
                                modifier =
                                    Modifier.size(18.dp)
                            )
                            Spacer(
                                Modifier.width(Spacing.sm)
                            )
                            Text(
                                text = "The server validates the file again immediately before writing. Invalid rows block the import, while duplicate rows are skipped. A single import is limited to 5,000 rows and 10 MB.",
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
        }
    }

    if (confirmImport) {
        val result = state.preview

        PremiumConfirmDialog(
            title = "Import validated students?",
            message =
                buildImportConfirmationMessage(result),
            confirmLabel = "Import students",
            destructive = false,
            icon = Icons.Filled.UploadFile,
            onDismiss = {
                if (!state.isWorking) {
                    confirmImport = false
                }
            },
            onConfirm = {
                confirmImport = false
                viewModel.importConfirmed()
            }
        )
    }

    // Keep terminal feedback topmost.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                state.errorTitle
                    ?: "Roster action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.success?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                state.successTitle
                    ?: "Student import completed",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun ImportSteps(
    hasFile: Boolean,
    validated: Boolean,
    imported: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(Spacing.xs)
    ) {
        StepChip(
            number = "1",
            label = "Choose",
            complete = hasFile || imported,
            active = !hasFile && !imported,
            modifier = Modifier.weight(1f)
        )
        StepChip(
            number = "2",
            label = "Validate",
            complete = validated || imported,
            active = hasFile && !validated,
            modifier = Modifier.weight(1f)
        )
        StepChip(
            number = "3",
            label = "Import",
            complete = imported,
            active = validated && !imported,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepChip(
    number: String,
    label: String,
    complete: Boolean,
    active: Boolean,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val container = when {
        complete ->
            scheme.primaryContainer
        active ->
            scheme.secondaryContainer
        else ->
            scheme.surfaceVariant
    }

    val content = when {
        complete ->
            scheme.onPrimaryContainer
        active ->
            scheme.onSecondaryContainer
        else ->
            scheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border =
            if (active) {
                BorderStroke(
                    1.dp,
                    scheme.secondary
                        .copy(alpha = 0.24f)
                )
            } else {
                null
            },
        modifier = modifier
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.sm),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text =
                    if (complete) "✓" else number,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RosterFormatCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "Roster format",
                style =
                    MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text =
                    "Required columns: firstName, lastName, grade, section",
                style =
                    MaterialTheme.typography.bodySmall
            )
            Text(
                text =
                    "Optional: studentNumber / LRN, middleInitial, suffix",
                style =
                    MaterialTheme.typography.bodySmall
            )
            Text(
                text =
                    "CSV, XLS, or XLSX · maximum 5,000 rows · maximum 10 MB",
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedRosterCard(
    filename: String,
    working: Boolean,
    importCompleted: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier =
                        Modifier.padding(12.dp),
                    tint =
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text =
                        filename.ifBlank {
                            if (importCompleted) {
                                "Roster import completed"
                            } else {
                                "No roster selected"
                            }
                        },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text =
                        when {
                            importCompleted ->
                                "Choose another file to start a new import."

                            filename.isBlank() ->
                                "Choose a CSV or Excel roster to begin."

                            else ->
                                "This file is validated on the server before any student record is created."
                        },
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            if (importCompleted) {
                FilledTonalButton(
                    onClick = onReset,
                    enabled = !working
                ) {
                    Text("New")
                }
            } else {
                FilledTonalButton(
                    onClick = onChoose,
                    enabled = !working
                ) {
                    Icon(
                        Icons.Filled.UploadFile,
                        contentDescription = null,
                        modifier =
                            Modifier.size(18.dp)
                    )
                    Spacer(
                        Modifier.width(Spacing.xs)
                    )
                    Text(
                        if (filename.isBlank()) {
                            "Choose"
                        } else {
                            "Replace"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkingCard(
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .secondaryContainer
    ) {
        Row(
            modifier =
                Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = message,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text =
                        "Do not leave this screen until the operation finishes.",
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md)
        ) {
            Text(
                text = value,
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

private enum class ValidationNoticeTone {
    Success,
    Info,
    Error
}

@Composable
private fun ValidationNotice(
    tone: ValidationNoticeTone,
    title: String,
    message: String
) {
    val scheme = MaterialTheme.colorScheme

    val container = when (tone) {
        ValidationNoticeTone.Success ->
            scheme.primaryContainer

        ValidationNoticeTone.Info ->
            scheme.tertiaryContainer

        ValidationNoticeTone.Error ->
            scheme.errorContainer
    }

    val content = when (tone) {
        ValidationNoticeTone.Success ->
            scheme.onPrimaryContainer

        ValidationNoticeTone.Info ->
            scheme.onTertiaryContainer

        ValidationNoticeTone.Error ->
            scheme.onErrorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ValidationErrorCard(
    row: Int,
    field: String,
    message: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error
                .copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(Spacing.xs))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text =
                        "Row " +
                            row +
                            " · " +
                            field,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StudentPreviewCard(
    fullName: String,
    studentNumber: String,
    grade: String,
    section: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text =
                            fullName
                                .trim()
                                .take(1)
                                .uppercase()
                                .ifBlank { "S" },
                        fontWeight = FontWeight.ExtraBold,
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fullName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text =
                        buildString {
                            if (
                                studentNumber.isNotBlank()
                            ) {
                                append("#")
                                append(studentNumber)
                                append(" · ")
                            }
                            append("Grade ")
                            append(grade)
                            append(" → ")
                            append(section)
                        },
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

private fun validationSubtitle(
    result: com.pickuppass.android.data.model.BulkStudentImportResponse
): String {
    return when {
        !result.dryRun && result.importedRows > 0 ->
            "The confirmed roster was written successfully."

        !result.dryRun &&
            result.importedRows == 0 &&
            result.duplicateRows > 0 ->
            "The final validation found no new students to write."

        result.invalidRows > 0 ->
            "Resolve the validation issues before importing."

        result.validRows == 0 &&
            result.duplicateRows > 0 ->
            "All valid rows are duplicates, so there is nothing new to import."

        result.readyToImport ->
            "The dry run is clean and ready for confirmation."

        else ->
            "Review the dry-run result before continuing."
    }
}

private fun buildImportConfirmationMessage(
    result:
        com.pickuppass.android.data.model.BulkStudentImportResponse?
): String {
    if (result == null) {
        return "No validated roster is available."
    }

    val base =
        "Create " +
            result.validRows +
            " validated student record" +
            if (result.validRows == 1) {
                " in the current school tenant?"
            } else {
                "s in the current school tenant?"
            }

    return if (result.duplicateRows > 0) {
        base +
            " " +
            result.duplicateRows +
            " duplicate row" +
            if (result.duplicateRows == 1) {
                " will be skipped."
            } else {
                "s will be skipped."
            }
    } else {
        base
    }
}
