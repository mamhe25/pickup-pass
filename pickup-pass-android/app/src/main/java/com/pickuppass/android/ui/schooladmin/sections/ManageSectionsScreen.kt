package com.pickuppass.android.ui.schooladmin.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSectionsScreen(
    viewModel: ManageSectionsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Sections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> FullScreenLoading()
            uiState.error != null -> Box(Modifier.padding(padding).padding(24.dp)) { ErrorBanner(uiState.error!!) }
            uiState.teachers.isEmpty() -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No teachers yet — invite one first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Assign each teacher's grade/section so their broadcasts reach the right guardians.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(uiState.teachers, key = { it.uid }) { teacher ->
                    TeacherSectionsCard(
                        teacher = teacher,
                        saveStatus = uiState.saveStatusByUid[teacher.uid],
                        onAddSection = { grade, section -> viewModel.addSection(teacher.uid, grade, section) },
                        onRemoveSection = { index -> viewModel.removeSection(teacher.uid, index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherSectionsCard(
    teacher: TeacherWithSections,
    saveStatus: String?,
    onAddSection: (grade: String, section: String) -> Unit,
    onRemoveSection: (index: Int) -> Unit
) {
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }

    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(teacher.displayName ?: teacher.email ?: "Teacher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            teacher.email?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            if (teacher.assignedSections.isEmpty()) {
                Text("No sections assigned yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // A simple wrapping Row-of-Rows rather than pulling in a
                // separate FlowRow dependency for what's typically just a
                // handful of chips per teacher.
                teacher.assignedSections.chunked(2).forEach { rowChips ->
                    Row(modifier = Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowChips.forEach { chip ->
                            val actualIndex = teacher.assignedSections.indexOf(chip)
                            AssistChip(
                                onClick = { onRemoveSection(actualIndex) },
                                label = { Text("Grade ${chip.grade} · ${chip.section}") },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Section") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = {
                    onAddSection(grade, section)
                    grade = ""
                    section = ""
                }) {
                    Text("Add")
                }
            }

            saveStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
