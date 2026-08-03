package com.pickuppass.android.ui.schooladmin.staff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner
import com.pickuppass.android.ui.theme.Spacing

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
    val firstNameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }

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
                .padding(Spacing.lg)
        ) {
            Text(
                "They'll get an email to set their password. Once signed in, they can run the dismissal scanner and register parent pickup contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { firstNameFocus.requestFocus() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstNameFocus)
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = middleInitial,
                    onValueChange = { middleInitial = it },
                    label = { Text("M.I.") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it },
                    label = { Text("Suffix") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(2f)
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
            )
            Spacer(Modifier.height(Spacing.md))

            uiState.error?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
            uiState.successMessage?.let {
                if (uiState.successIsWarning) {
                    WarningBanner(it, modifier = Modifier.padding(bottom = Spacing.sm))
                } else {
                    SuccessBanner(it, modifier = Modifier.padding(bottom = Spacing.sm))
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
