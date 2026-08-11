package com.pickuppass.android.ui.schooladmin.staffmanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(viewModel: StaffManagementViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingStatus by remember { mutableStateOf<Pair<TeacherWithSections, Boolean>?>(null) }
    var pendingRevoke by remember { mutableStateOf<TeacherWithSections?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Teacher Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }) }) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator(Modifier.padding(Spacing.xl)) }
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item {
                    Text("Deactivate access when a teacher leaves or a device/account may be compromised. Session revocation forces fresh authentication.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { Spacer(Modifier.height(Spacing.sm)); ErrorBanner(it) }
                    state.message?.let { Spacer(Modifier.height(Spacing.sm)); Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                items(state.teachers, key = { it.uid }) { teacher ->
                    ElevatedCard {
                        Column(Modifier.padding(Spacing.md)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(teacher.displayName ?: "Teacher", fontWeight = FontWeight.SemiBold)
                                    teacher.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text(if (teacher.isActive) "Active" else "Inactive", style = MaterialTheme.typography.labelMedium, color = if (teacher.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                }
                                Switch(checked = teacher.isActive, onCheckedChange = { pendingStatus = teacher to it }, enabled = state.busyUid == null)
                            }
                            Spacer(Modifier.height(Spacing.sm))
                            OutlinedButton(onClick = { pendingRevoke = teacher }, enabled = teacher.isActive && state.busyUid == null, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Devices, null); Spacer(Modifier.width(Spacing.xs)); Text("Sign Out All Devices")
                            }
                            if (state.busyUid == teacher.uid) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.sm))
                        }
                    }
                }
            }
        }
    }

    pendingStatus?.let { (teacher, active) ->
        AlertDialog(onDismissRequest = { pendingStatus = null }, title = { Text(if (active) "Reactivate teacher?" else "Deactivate teacher?") }, text = { Text(if (active) "${teacher.displayName ?: teacher.email} will be able to sign in again." else "${teacher.displayName ?: teacher.email} will lose access and all active sessions will be revoked.") }, confirmButton = { Button(onClick = { pendingStatus = null; viewModel.setActive(teacher, active) }) { Text(if (active) "Reactivate" else "Deactivate") } }, dismissButton = { TextButton(onClick = { pendingStatus = null }) { Text("Cancel") } })
    }
    pendingRevoke?.let { teacher ->
        AlertDialog(onDismissRequest = { pendingRevoke = null }, title = { Text("Sign out all devices?") }, text = { Text("All refresh sessions for ${teacher.displayName ?: teacher.email} will be revoked. They can sign in again with valid credentials.") }, confirmButton = { Button(onClick = { pendingRevoke = null; viewModel.revokeSessions(teacher) }) { Text("Revoke sessions") } }, dismissButton = { TextButton(onClick = { pendingRevoke = null }) { Text("Cancel") } })
    }
}
