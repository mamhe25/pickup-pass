package com.pickuppass.android.ui.teacher.exitlogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.ExitLogEntry
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FilterDropdown
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitLogsScreen(
    viewModel: ExitLogsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dismissal History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                OutlinedTextField(
                    value = uiState.searchTerm,
                    onValueChange = viewModel::onSearchChange,
                    placeholder = { Text("Search by student name...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterDropdown(
                        label = "Grade",
                        options = uiState.availableGrades,
                        selected = uiState.gradeFilter,
                        onSelect = viewModel::onGradeFilterChange,
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Section",
                        options = uiState.availableSections,
                        selected = uiState.sectionFilter,
                        onSelect = viewModel::onSectionFilterChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                FilterDropdown(
                    label = "Approved By",
                    options = uiState.availableStaff,
                    selected = uiState.staffFilter,
                    onSelect = viewModel::onStaffFilterChange,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!uiState.isLoading) {
                    Text(
                        "${uiState.filteredLogs.size} of ${uiState.allLogs.size} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
            }

            when {
                uiState.isLoading -> FullScreenLoading()
                uiState.error != null -> Box(Modifier.padding(Spacing.lg)) { ErrorBanner(uiState.error!!) }
                uiState.allLogs.isEmpty() -> EmptyState("No dismissal records yet.")
                uiState.filteredLogs.isEmpty() -> EmptyState("No records match your filters.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(uiState.filteredLogs, key = { it.id }) { log ->
                        ExitLogRow(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitLogRow(log: ExitLogEntry) {
    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(log.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    log.timestampMillis?.let { formatTime(it) } ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Grade ${log.grade.ifBlank { "-" }} · Section ${log.section.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Text("Picked up by ${log.guardianName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Approved by ${log.staffName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
