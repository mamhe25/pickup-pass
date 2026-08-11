package com.pickuppass.android.ui.schooladmin.manualpickup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPickupScreen(viewModel: ManualPickupViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var studentMenu by remember { mutableStateOf(false) }
    var guardianMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Manual Pickup Override") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Use only when normal QR verification cannot be completed. Every override is permanently audited.", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it) }
            state.success?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }

            ExposedDropdownMenuBox(expanded = studentMenu, onExpandedChange = { studentMenu = !studentMenu }) {
                OutlinedTextField(value = state.selectedStudent?.let { "${it.fullName} · Grade ${it.grade} ${it.section}" } ?: "", onValueChange = {}, readOnly = true, label = { Text("Student") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                    state.students.forEach { s -> DropdownMenuItem(text = { Text("${s.fullName} · Grade ${s.grade} ${s.section}") }, onClick = { viewModel.selectStudent(s); studentMenu = false }) }
                }
            }

            ExposedDropdownMenuBox(expanded = guardianMenu, onExpandedChange = { if (state.selectedStudent != null) guardianMenu = !guardianMenu }) {
                OutlinedTextField(value = state.selectedGuardian?.displayName ?: "", onValueChange = {}, readOnly = true, enabled = state.selectedStudent != null, label = { Text("Authorized guardian") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(guardianMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = guardianMenu, onDismissRequest = { guardianMenu = false }) {
                    if (state.guardians.isEmpty()) DropdownMenuItem(text = { Text("No guardians available") }, onClick = {})
                    state.guardians.forEach { g -> DropdownMenuItem(text = { Text(g.displayName.ifBlank { g.email }) }, onClick = { viewModel.selectGuardian(g); guardianMenu = false }) }
                }
            }

            OutlinedTextField(value = state.reason, onValueChange = viewModel::setReason, label = { Text("Reason for override") }, supportingText = { Text("Required. Example: guardian phone battery dead; ID checked by school admin.") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            Button(onClick = { confirm = true }, enabled = !state.isSubmitting && state.selectedStudent != null && state.selectedGuardian != null && state.reason.trim().length >= 5, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Approve Manual Release")
            }
        }
    }

    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confirm manual release") }, text = { Text("This bypasses QR verification and creates an immutable dismissal record. Confirm the guardian's identity before continuing.") }, confirmButton = { Button(onClick = { confirm = false; viewModel.submit() }) { Text("Confirm release") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
