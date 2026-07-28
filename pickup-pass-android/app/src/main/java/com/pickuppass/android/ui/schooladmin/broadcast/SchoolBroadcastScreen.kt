package com.pickuppass.android.ui.schooladmin.broadcast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
                .padding(20.dp)
        ) {
            Text(
                "Goes out immediately to everyone you select — as a push and in their notification inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                minLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Text("Send to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiState.includeTeachers, onCheckedChange = { viewModel.setIncludeTeachers(it) })
                Text("Teachers")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiState.includeParents, onCheckedChange = { viewModel.setIncludeParents(it) })
                Text("Guardians")
            }

            Spacer(Modifier.height(12.dp))
            uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = 12.dp)) }
            uiState.successMessage?.let { SuccessBanner(it, modifier = Modifier.padding(bottom = 12.dp)) }

            PrimaryButton(
                text = "Send Announcement",
                loading = uiState.isSubmitting,
                onClick = { viewModel.send(title.trim(), body.trim()) }
            )
        }
    }
}
