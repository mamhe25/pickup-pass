package com.pickuppass.android.ui.schooladmin.pickuppolicy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupPolicyScreen(
    viewModel: PickupPolicyViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pickup Policy", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Release rules & fallback controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text("School-wide dismissal rule", fontWeight = FontWeight.Bold)
                            Text(
                                "These settings apply to QR approvals at dismissal. Changes should match the school's written pickup procedure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SectionTitle("QR pickup availability", "Choose when a valid PickupPass QR may be approved.")

                PolicyOption(
                    selected = !state.restrictedToTimeWindow,
                    title = "Unrestricted",
                    description = "Authorized guardians may use a currently valid QR until it expires or is consumed.",
                    onClick = { viewModel.setRestricted(false) }
                )

                PolicyOption(
                    selected = state.restrictedToTimeWindow,
                    title = "School pickup time window",
                    description = "Valid QR codes are accepted only inside the configured dismissal hours.",
                    onClick = { viewModel.setRestricted(true) }
                )

                if (state.restrictedToTimeWindow) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text("Dismissal window", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                OutlinedTextField(
                                    value = state.startTime,
                                    onValueChange = viewModel::setStartTime,
                                    label = { Text("Starts") },
                                    placeholder = { Text("14:00") },
                                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.endTime,
                                    onValueChange = viewModel::setEndTime,
                                    label = { Text("Ends") },
                                    placeholder = { Text("18:00") },
                                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                "24-hour HH:mm format · ${state.timeZone}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SectionTitle("Administrative fallback", "Control whether school admins may document an exception.")

                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (state.allowManualOverride) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("Allow manual pickup override", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.allowManualOverride)
                                    "School admins may use the audited Manual Release flow when normal QR pickup cannot be completed."
                                else
                                    "Manual Release is disabled; staff must complete pickup through the normal QR flow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.allowManualOverride,
                            onCheckedChange = viewModel::setManualOverride,
                            enabled = !state.isSaving
                        )
                    }
                }

                state.error?.let { ErrorBanner(it) }
                state.successMessage?.let { SuccessBanner(it) }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.widthIn(min = 180.dp, max = 320.dp).height(52.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Saving…")
                        } else {
                            Text("Save pickup policy")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .26f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(Spacing.xs))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
