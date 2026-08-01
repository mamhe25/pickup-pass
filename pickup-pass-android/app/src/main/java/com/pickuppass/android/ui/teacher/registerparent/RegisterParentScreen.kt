package com.pickuppass.android.ui.teacher.registerparent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner
import com.pickuppass.android.ui.theme.Spacing

private val relationshipOptions = listOf(
    "parent/guardian" to "Parent / Guardian",
    "grandparent" to "Grandparent",
    "relative" to "Other Relative",
    "caregiver" to "Caregiver / Nanny",
    "authorized pickup" to "Other Authorized Pickup"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterParentScreen(
    studentId: String,
    viewModel: RegisterParentViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(relationshipOptions.first().first) }
    var expanded by remember { mutableStateOf(false) }
    val firstNameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }

    LaunchedEffect(studentId) { viewModel.loadStudent(studentId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Parent") },
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
            if (uiState.studentLabel.isNotBlank()) {
                Text(
                    uiState.studentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
            }

            Text(
                "They'll get an email to set their password and upload a photo. Once set up, they can generate pickup passes for this student.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Parent's last name") },
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
                label = { Text("Parent's first name") },
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
            Spacer(Modifier.height(Spacing.sm))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = relationshipOptions.first { it.first == relationship }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Relationship") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    relationshipOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            relationship = value
                            expanded = false
                        })
                    }
                }
            }

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
                text = "Register Parent",
                loading = uiState.isSubmitting,
                onClick = {
                    viewModel.register(
                        studentId, lastName.trim(), firstName.trim(),
                        middleInitial.trim(), suffix.trim(), email.trim(), relationship
                    )
                }
            )
        }
    }
}
