package com.pickuppass.android.ui.teacher.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherBroadcastScreen(
    viewModel: TeacherBroadcastViewModel = hiltViewModel(),
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
                "Goes out immediately to the guardians of students in your assigned section(s) — as a push and in their notification inbox.",
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
            Spacer(Modifier.height(Spacing.md))

            AnimatedVisibility(visible = uiState.error != null, enter = fadeIn() + expandVertically()) {
                uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
            }
            AnimatedVisibility(visible = uiState.successMessage != null, enter = fadeIn() + expandVertically()) {
                uiState.successMessage?.let { SuccessBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
            }

            PrimaryButton(
                text = "Send Announcement",
                loading = uiState.isSubmitting,
                onClick = { viewModel.send(title.trim(), body.trim()) }
            )
        }
    }
}
