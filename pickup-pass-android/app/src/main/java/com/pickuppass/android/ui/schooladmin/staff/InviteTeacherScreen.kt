package com.pickuppass.android.ui.schooladmin.staff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteTeacherScreen(
    viewModel: InviteTeacherViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Clear the form only once a submission actually succeeds — clearing
    // eagerly on every tap would wipe what the admin typed if it failed.
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            lastName = ""
            firstName = ""
            middleInitial = ""
            suffix = ""
            email = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite a Teacher") },
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
                "They'll get an email to set their password. Once signed in, they can run the dismissal scanner and register parent pickup contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = middleInitial,
                    onValueChange = { middleInitial = it },
                    label = { Text("M.I.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it },
                    label = { Text("Suffix") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = 12.dp)) }
            uiState.successMessage?.let {
                if (uiState.successIsWarning) {
                    WarningBanner(it, modifier = Modifier.padding(bottom = 12.dp))
                } else {
                    SuccessBanner(it, modifier = Modifier.padding(bottom = 12.dp))
                }
            }

            PrimaryButton(
                text = "Send Invite",
                loading = uiState.isSubmitting,
                onClick = {
                    viewModel.invite(lastName.trim(), firstName.trim(), middleInitial.trim(), suffix.trim(), email.trim())
                }
            )
        }
    }
}
