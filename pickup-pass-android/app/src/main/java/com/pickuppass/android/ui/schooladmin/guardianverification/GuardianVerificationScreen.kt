package com.pickuppass.android.ui.schooladmin.guardianverification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.GuardianVerificationItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianVerificationScreen(
    viewModel: GuardianVerificationViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<GuardianVerificationItem, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian Verification") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator(Modifier.padding(Spacing.xl)) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                ElevatedCard {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Require verified guardians", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "When enabled, new guardians added by parents must be verified by the school before their QR can be used. Existing legacy guardians remain verified unless you change their status.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.verificationRequired,
                                onCheckedChange = viewModel::setPolicy,
                                enabled = !state.policyBusy
                            )
                        }
                        if (state.policyBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.sm))
                    }
                }
                state.error?.let { Spacer(Modifier.height(Spacing.sm)); ErrorBanner(it) }
                state.message?.let { Spacer(Modifier.height(Spacing.sm)); Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(Spacing.sm))
                Text("GUARDIANS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (state.guardians.isEmpty()) {
                item { Text("No linked guardians found for this school.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(state.guardians, key = { it.uid }) { guardian ->
                ElevatedCard {
                    Column(Modifier.padding(Spacing.md)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(guardian.displayName.ifBlank { guardian.email.ifBlank { "Guardian" } }, fontWeight = FontWeight.SemiBold)
                                if (guardian.email.isNotBlank()) Text(guardian.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (guardian.studentNames.isNotEmpty()) Text("Students: ${guardian.studentNames.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusBadge(guardian.status)
                        }
                        if (guardian.verificationReason.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text("Reason: ${guardian.verificationReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedButton(onClick = { pending = guardian to "pending" }, enabled = state.busyUid == null, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PauseCircle, null); Spacer(Modifier.width(4.dp)); Text("Pending")
                            }
                            Button(onClick = { pending = guardian to "verified" }, enabled = state.busyUid == null, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(4.dp)); Text("Verify")
                            }
                        }
                        TextButton(onClick = { pending = guardian to "suspended" }, enabled = state.busyUid == null, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Report, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Suspend Pickup Access", color = MaterialTheme.colorScheme.error)
                        }
                        if (state.busyUid == guardian.uid) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    pending?.let { (guardian, status) ->
        var reason by remember(guardian.uid, status) { mutableStateOf(guardian.verificationReason) }
        val title = when (status) {
            "verified" -> "Verify guardian?"
            "suspended" -> "Suspend guardian pickup access?"
            else -> "Mark guardian pending?"
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = {
                Column {
                    Text(guardian.displayName.ifBlank { guardian.email })
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(if (status == "verified") "Verification note (optional)" else "Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (status != "verified") {
                        Spacer(Modifier.height(Spacing.xs))
                        Text("Any unused QR pass held by this guardian will be invalidated immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { pending = null; viewModel.updateStatus(guardian, status, reason.trim()) }) {
                    Text(when (status) { "verified" -> "Verify"; "suspended" -> "Suspend"; else -> "Mark Pending" })
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val label = status.replaceFirstChar { it.uppercase() }
    val container = when (status.lowercase()) {
        "verified" -> MaterialTheme.colorScheme.primaryContainer
        "suspended" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}
