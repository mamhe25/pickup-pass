package com.pickuppass.android.ui.teacher.exitlogs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val todayCount = uiState.allLogs.count { log ->
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
                title = { Text("Dismissal History") },
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
            if (!uiState.isLoading && uiState.error == null) {
                HistoryHero(
                    todayCount = todayCount,
                    uniqueStudents = uniqueStudents,
                    approverCount = approverCount
                )

                HistoryFilters(
                    uiState = uiState,
                    onSearchChange = viewModel::onSearchChange,
                    onGradeChange = viewModel::onGradeFilterChange,
                    onSectionChange = viewModel::onSectionFilterChange,
                    onStaffChange = viewModel::onStaffFilterChange
                )
            }

            val phase = when {
                uiState.isLoading -> "loading"
                uiState.error != null -> "error"
                uiState.allLogs.isEmpty() -> "empty"
                uiState.filteredLogs.isEmpty() -> "nomatch"
                else -> "list"
            }

            Crossfade(
                targetState = phase,
                animationSpec = tween(220),
                label = "dismissalHistoryPhase",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { state ->
                when (state) {
                    "loading" -> FullScreenLoading()

                    "error" -> HistoryErrorState(
                        message = uiState.error
                            ?: "Couldn't load dismissal history",
                        onRetry = viewModel::load
                    )

                    "empty" -> HistoryEmptyState(
                        title = "No dismissal records yet",
                        detail = "Approved student releases will appear here after the first completed pickup."
                    )

                    "nomatch" -> HistoryEmptyState(
                        title = "No records match these filters",
                        detail = "Try a different student name, grade, section, or approving staff member."
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = Spacing.md,
                            end = Spacing.md,
                            top = Spacing.xs,
                            bottom = Spacing.xl
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(Spacing.sm)
                    ) {
                        item {
                            Text(
                                text =
                                    "${uiState.filteredLogs.size} of ${uiState.allLogs.size} records",
                                style =
                                    MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(
                            items = uiState.filteredLogs,
                            key = { it.id }
                        ) { log ->
                            ExitLogCard(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHero(
    todayCount: Int,
    uniqueStudents: Int,
    approverCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "RELEASE RECORDS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.68f)
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = if (todayCount > 0) {
                    "$todayCount release${if (todayCount == 1) "" else "s"} recorded today"
                } else {
                    "Review completed student handoffs"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text =
                    "Every entry shows the student, authorized guardian, approving staff member, and recorded release time.",
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
                HistoryMetric(
                    value = todayCount.toString(),
                    label = "Today",
                    modifier = Modifier.weight(1f)
                )

                HistoryMetric(
                    value = uniqueStudents.toString(),
                    label = "Students",
                    modifier = Modifier.weight(1f)
                )

                HistoryMetric(
                    value = approverCount.toString(),
                    label = "Approvers",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HistoryMetric(
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
private fun HistoryFilters(
    uiState: ExitLogsUiState,
    onSearchChange: (String) -> Unit,
    onGradeChange: (String?) -> Unit,
    onSectionChange: (String?) -> Unit,
    onStaffChange: (String?) -> Unit
) {
    Column(
        modifier = Modifier.padding(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        )
    ) {
        OutlinedTextField(
            value = uiState.searchTerm,
            onValueChange = onSearchChange,
            placeholder = {
                Text("Search student name")
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.sm))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            FilterDropdown(
                label = "Grade",
                options = uiState.availableGrades,
                selected = uiState.gradeFilter,
                onSelect = onGradeChange,
                modifier = Modifier.weight(1f)
            )

            FilterDropdown(
                label = "Section",
                options = uiState.availableSections,
                selected = uiState.sectionFilter,
                onSelect = onSectionChange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        FilterDropdown(
            label = "Approved By",
            options = uiState.availableStaff,
            selected = uiState.staffFilter,
            onSelect = onStaffChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExitLogCard(
    log: ExitLogEntry
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = log.studentName.ifBlank {
                            "Unknown student"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text =
                            "Grade ${log.grade.ifBlank { "—" }} · Section ${log.section.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = log.timestampMillis?.let {
                        formatHistoryTime(it)
                    } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color =
                    MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.62f)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    HistoryDetailRow(
                        label = "Picked up by",
                        value = log.guardianName.ifBlank {
                            "Unknown guardian"
                        }
                    )

                    Spacer(Modifier.height(Spacing.xs))

                    HistoryDetailRow(
                        label = "Approved by",
                        value = log.staffName.ifBlank {
                            "Unknown staff"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )

        Spacer(Modifier.width(Spacing.sm))

        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(66.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun isToday(
    millis: Long
): Boolean {
    val target = Calendar.getInstance().apply {
        timeInMillis = millis
    }

    val now = Calendar.getInstance()

    return target.get(Calendar.YEAR) ==
        now.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) ==
        now.get(Calendar.DAY_OF_YEAR)
}

private fun formatHistoryTime(
    millis: Long
): String {
    val date = Date(millis)

    return if (isToday(millis)) {
        SimpleDateFormat(
            "'Today' · h:mm a",
            Locale.getDefault()
        ).format(date)
    } else {
        SimpleDateFormat(
            "MMM d, yyyy\nh:mm a",
            Locale.getDefault()
        ).format(date)
    }
}
