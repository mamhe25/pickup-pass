package com.pickuppass.android.ui.schooladmin.staffmanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.TeacherWithSections
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(
    viewModel: StaffManagementViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingStatus by remember { mutableStateOf<Pair<TeacherWithSections, Boolean>?>(null) }
    var pendingRevoke by remember { mutableStateOf<TeacherWithSections?>(null) }

    val filtered = remember(state.teachers, query) {
        val q = query.trim()
        if (q.isBlank()) state.teachers
        else state.teachers.filter {
            it.displayName.orEmpty().contains(q, true) ||
                it.email.orEmpty().contains(q, true) ||
                it.assignedSections.any { section ->
                    section.grade.contains(q, true) || section.section.contains(q, true)
                }
        }
    }
    val activeCount = state.teachers.count { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Teacher Accounts", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Access & session security",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 820.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.SupervisorAccount, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text("Account access control", fontWeight = FontWeight.Bold)
                                Text(
                                    "Deactivate access when a teacher leaves. Revoke sessions when a device or account may be compromised.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }
                state.message?.let { item { SuccessBanner(it) } }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Metric("Teachers", state.teachers.size, Modifier.weight(1f))
                        Metric("Active", activeCount, Modifier.weight(1f))
                        Metric("Inactive", state.teachers.size - activeCount, Modifier.weight(1f))
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Search teachers") },
                        placeholder = { Text("Name, email, grade or section") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.SupervisorAccount, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.sm))
                                Text("No matching teacher accounts", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.teachers.isEmpty()) "Invite a teacher to create the first account."
                                    else "Try another search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(filtered, key = { it.uid }) { teacher ->
                    TeacherAccountCard(
                        teacher = teacher,
                        busy = state.busyUid == teacher.uid,
                        globallyBusy = state.busyUid != null,
                        onToggle = { pendingStatus = teacher to it },
                        onRevoke = { pendingRevoke = teacher }
                    )
                }
            }
        }
    }

    pendingStatus?.let { (teacher, active) ->
        AlertDialog(
            onDismissRequest = { if (state.busyUid == null) pendingStatus = null },
            title = { Text(if (active) "Reactivate teacher?" else "Deactivate teacher?") },
            text = {
                Text(
                    if (active)
                        "${teacher.displayName ?: teacher.email ?: "This teacher"} will be able to sign in again."
                    else
                        "${teacher.displayName ?: teacher.email ?: "This teacher"} will lose access immediately and all active sessions will be revoked."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingStatus = null
                        viewModel.setActive(teacher, active)
                    },
                    enabled = state.busyUid == null
                ) { Text(if (active) "Reactivate" else "Deactivate") }
            },
            dismissButton = { TextButton(onClick = { pendingStatus = null }) { Text("Cancel") } }
        )
    }

    pendingRevoke?.let { teacher ->
        AlertDialog(
            onDismissRequest = { if (state.busyUid == null) pendingRevoke = null },
            icon = { Icon(Icons.Filled.Devices, null) },
            title = { Text("Sign out all devices?") },
            text = {
                Text(
                    "All active sessions for ${teacher.displayName ?: teacher.email ?: "this teacher"} will be revoked. They can sign in again with valid credentials."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRevoke = null
                        viewModel.revokeSessions(teacher)
                    },
                    enabled = state.busyUid == null
                ) { Text("Revoke sessions") }
            },
            dismissButton = { TextButton(onClick = { pendingRevoke = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TeacherAccountCard(
    teacher: TeacherWithSections,
    busy: Boolean,
    globallyBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRevoke: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        teacher.displayName.orEmpty().trim().take(1).uppercase().ifBlank { "T" },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        teacher.displayName ?: "Teacher",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    teacher.email?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${teacher.assignedSections.size} assigned section${if (teacher.assignedSections.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (teacher.isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (teacher.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Switch(
                        checked = teacher.isActive,
                        onCheckedChange = onToggle,
                        enabled = !globallyBusy
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onRevoke,
                    enabled = teacher.isActive && !globallyBusy
                ) {
                    Icon(Icons.Filled.Devices, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Sign out all devices")
                }
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
