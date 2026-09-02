package com.pickuppass.android.ui.teacher.exitlogs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.ExitLogEntry
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FilterDropdown
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitLogsScreen(
    viewModel: ExitLogsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var filtersOpen by remember {
        mutableStateOf(false)
    }

    val activeFilterCount =
        listOf(
            uiState.gradeFilter,
            uiState.sectionFilter,
            uiState.staffFilter
        ).count { !it.isNullOrBlank() }

    val todayCount =
        uiState.allLogs.count { log ->
            log.timestampMillis?.let(::isToday) == true
        }

    val uniqueStudents =
        uiState.allLogs
            .map { it.studentName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

    val approverCount =
        uiState.allLogs
            .map { it.staffName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Dismissal History")
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (
                !uiState.isLoading &&
                uiState.error == null
            ) {
                CompactSummaryBar(
                    todayCount = todayCount,
                    uniqueStudents = uniqueStudents,
                    approverCount = approverCount
                )

                CompactToolbar(
                    searchTerm = uiState.searchTerm,
                    onSearchChange = viewModel::onSearchChange,
                    activeFilterCount = activeFilterCount,
                    onFilterClick = {
                        filtersOpen = true
                    }
                )

                ActiveFiltersRow(
                    uiState = uiState,
                    onGradeClear = {
                        viewModel.onGradeFilterChange(null)
                    },
                    onSectionClear = {
                        viewModel.onSectionFilterChange(null)
                    },
                    onStaffClear = {
                        viewModel.onStaffFilterChange(null)
                    }
                )
            }

            val phase = when {
                uiState.isLoading ->
                    "loading"

                uiState.error != null ->
                    "error"

                uiState.allLogs.isEmpty() ->
                    "empty"

                uiState.filteredLogs.isEmpty() ->
                    "nomatch"

                else ->
                    "list"
            }

            Crossfade(
                targetState = phase,
                animationSpec = tween(180),
                label = "dismissalHistoryPhase",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { state ->
                when (state) {
                    "loading" ->
                        FullScreenLoading()

                    "error" ->
                        HistoryErrorState(
                            message =
                                uiState.error
                                    ?: "Couldn't load dismissal history",
                            onRetry = viewModel::load
                        )

                    "empty" ->
                        HistoryEmptyState(
                            title = "No dismissal records yet",
                            detail =
                                "Approved releases will appear here after the first completed pickup."
                        )

                    "nomatch" ->
                        HistoryEmptyState(
                            title = "No matching records",
                            detail =
                                "Change the search or clear a filter."
                        )

                    else ->
                        LazyColumn(
                            contentPadding =
                                PaddingValues(
                                    start = Spacing.md,
                                    end = Spacing.md,
                                    top = 2.dp,
                                    bottom = Spacing.xl
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                Text(
                                    text =
                                        "${uiState.filteredLogs.size} record${if (uiState.filteredLogs.size == 1) "" else "s"}",
                                    style =
                                        MaterialTheme.typography.labelSmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            items(
                                items = uiState.filteredLogs,
                                key = { it.id }
                            ) { log ->
                                CompactExitLogRow(log)
                            }
                        }
                }
            }
        }
    }

    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                filtersOpen = false
            }
        ) {
            HistoryFilterSheet(
                uiState = uiState,
                onGradeChange = viewModel::onGradeFilterChange,
                onSectionChange = viewModel::onSectionFilterChange,
                onStaffChange = viewModel::onStaffFilterChange,
                onClear = {
                    viewModel.onGradeFilterChange(null)
                    viewModel.onSectionFilterChange(null)
                    viewModel.onStaffFilterChange(null)
                },
                onDone = {
                    filtersOpen = false
                }
            )
        }
    }
}

@Composable
private fun CompactSummaryBar(
    todayCount: Int,
    uniqueStudents: Int,
    approverCount: Int
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
        SummaryPill(
            value = todayCount.toString(),
            label = "Today",
            modifier = Modifier.weight(1f)
        )

        SummaryPill(
            value = uniqueStudents.toString(),
            label = "Students",
            modifier = Modifier.weight(1f)
        )

        SummaryPill(
            value = approverCount.toString(),
            label = "Staff",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryPill(
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
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
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactToolbar(
    searchTerm: String,
    onSearchChange: (String) -> Unit,
    activeFilterCount: Int,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.md,
                vertical = 5.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
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

        FilledTonalButton(
            onClick = onFilterClick,
            modifier =
                Modifier.heightIn(min = 48.dp),
            contentPadding =
                PaddingValues(
                    horizontal = 12.dp
                )
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(5.dp))

            Text(
                if (activeFilterCount > 0) {
                    "Filters $activeFilterCount"
                } else {
                    "Filters"
                }
            )
        }
    }
}

@Composable
private fun ActiveFiltersRow(
    uiState: ExitLogsUiState,
    onGradeClear: () -> Unit,
    onSectionClear: () -> Unit,
    onStaffClear: () -> Unit
) {
    val hasAny =
        !uiState.gradeFilter.isNullOrBlank() ||
            !uiState.sectionFilter.isNullOrBlank() ||
            !uiState.staffFilter.isNullOrBlank()

    if (!hasAny) {
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(
                rememberScrollState()
            )
            .padding(
                start = Spacing.md,
                end = Spacing.md,
                bottom = 4.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        uiState.gradeFilter?.let { value ->
            InputChip(
                selected = true,
                onClick = onGradeClear,
                label = {
                    Text("Grade $value ×")
                }
            )
        }

        uiState.sectionFilter?.let { value ->
            InputChip(
                selected = true,
                onClick = onSectionClear,
                label = {
                    Text("Section $value ×")
                }
            )
        }

        uiState.staffFilter?.let { value ->
            InputChip(
                selected = true,
                onClick = onStaffClear,
                label = {
                    Text("$value ×")
                }
            )
        }
    }
}

@Composable
private fun CompactExitLogRow(
    log: ExitLogEntry
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
        Row(
            modifier = Modifier.padding(
                horizontal = 11.dp,
                vertical = 8.dp
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text =
                            log.studentName.ifBlank {
                                "Unknown student"
                            },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(Modifier.width(7.dp))

                    Text(
                        text =
                            log.timestampMillis?.let {
                                formatCompactTime(it)
                            } ?: "—",
                        style =
                            MaterialTheme.typography.labelSmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(1.dp))

                Text(
                    text =
                        "G${log.grade.ifBlank { "—" }} · ${log.section.ifBlank { "—" }}  •  ${log.guardianName.ifBlank { "Unknown guardian" }}",
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(1.dp))

                Text(
                    text =
                        "Approved by ${log.staffName.ifBlank { "Unknown staff" }}",
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterSheet(
    uiState: ExitLogsUiState,
    onGradeChange: (String?) -> Unit,
    onSectionChange: (String?) -> Unit,
    onStaffChange: (String?) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl
            )
    ) {
        Text(
            text = "Filter history",
            style =
                MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.md))

        FilterDropdown(
            label = "Grade",
            options = uiState.availableGrades,
            selected = uiState.gradeFilter,
            onSelect = onGradeChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        FilterDropdown(
            label = "Section",
            options = uiState.availableSections,
            selected = uiState.sectionFilter,
            onSelect = onSectionChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        FilterDropdown(
            label = "Approved by",
            options = uiState.availableStaff,
            selected = uiState.staffFilter,
            onSelect = onStaffChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.End,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onClear,
                modifier =
                    Modifier.heightIn(min = 44.dp)
            ) {
                Text("Clear")
            }

            Spacer(Modifier.width(Spacing.sm))

            Button(
                onClick = onDone,
                modifier =
                    Modifier.heightIn(min = 44.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(
    title: String,
    detail: String
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
                    Icons.Filled.History,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(27.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = title,
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = detail,
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HistoryErrorState(
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

private fun isToday(
    millis: Long
): Boolean {
    val target =
        Calendar.getInstance().apply {
            timeInMillis = millis
        }

    val now =
        Calendar.getInstance()

    return target.get(Calendar.YEAR) ==
        now.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) ==
        now.get(Calendar.DAY_OF_YEAR)
}

private fun formatCompactTime(
    millis: Long
): String {
    val date = Date(millis)

    return if (isToday(millis)) {
        SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        ).format(date)
    } else {
        SimpleDateFormat(
            "MMM d · h:mm a",
            Locale.getDefault()
        ).format(date)
    }
}
