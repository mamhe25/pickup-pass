package com.pickuppass.android.ui.schooladmin.broadcast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolBroadcastScreen(
    viewModel: SchoolBroadcastViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            title = ""
            body = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send an Announcement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.lg)
        ) {
            Text(
                "Goes out immediately to everyone you select — as a push and in their notification inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                minLines = 4,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.lg))

            Text(
                "SEND TO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = Spacing.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setIncludeTeachers(!uiState.includeTeachers) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Checkbox(checked = uiState.includeTeachers, onCheckedChange = { viewModel.setIncludeTeachers(it) })
                        Text("Teachers", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setIncludeParents(!uiState.includeParents) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Checkbox(checked = uiState.includeParents, onCheckedChange = { viewModel.setIncludeParents(it) })
                        Text("Guardians", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
            uiState.successMessage?.let { SuccessBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }

            PrimaryButton(
                text = "Send Announcement",
                loading = uiState.isSubmitting,
                onClick = { viewModel.send(title.trim(), body.trim()) }
            )
        }
    }
}
