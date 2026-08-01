package com.pickuppass.android.ui.parent.guardians

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.GuardianAvatar
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
fun ManageGuardiansScreen(
    studentId: String,
    viewModel: ManageGuardiansViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoveUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(studentId) { viewModel.load(studentId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authorized Guardians") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                Text(uiState.studentName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (uiState.isLoading) {
                item { CircularProgressIndicator() }
            }

            uiState.listError?.let {
                item { ErrorBanner(it) }
            }

            items(uiState.guardians, key = { it.uid }) { row ->
                GuardianRowCard(
                    row = row,
                    onRemoveClick = { confirmRemoveUid = row.uid }
                )
            }

            item {
                Spacer(Modifier.height(Spacing.sm))
                AddGuardianForm(
                    isSubmitting = uiState.isSubmitting,
                    formError = uiState.formError,
                    formSuccess = uiState.formSuccess,
                    formIsWarning = uiState.formIsWarning,
                    onSubmit = { lastName, firstName, mi, suffix, email, relationship ->
                        viewModel.addGuardian(lastName, firstName, mi, suffix, email, relationship)
                    }
                )
            }
        }
    }

    confirmRemoveUid?.let { uid ->
        AlertDialog(
            onDismissRequest = { confirmRemoveUid = null },
            title = { Text("Remove this guardian?") },
            text = { Text("Any pickup pass they're currently holding will stop working immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeGuardian(uid)
                    confirmRemoveUid = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveUid = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GuardianRowCard(row: GuardianRow, onRemoveClick: () -> Unit) {
    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuardianAvatar(photoUrl = row.profile?.photoUrl, size = 48.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.profile?.displayName?.ifBlank { "Pending guardian" } ?: "Pending guardian",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (row.entry.isPrimary) {
                        Spacer(Modifier.width(Spacing.xs))
                        AssistChip(onClick = {}, label = { Text("Primary", style = MaterialTheme.typography.bodySmall) })
                    }
                }
                Text(
                    row.entry.relationship,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!row.entry.isPrimary) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGuardianForm(
    isSubmitting: Boolean,
    formError: String?,
    formSuccess: String?,
    formIsWarning: Boolean,
    onSubmit: (lastName: String, firstName: String, middleInitial: String, suffix: String, email: String, relationship: String) -> Unit
) {
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleInitial by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(relationshipOptions.first().first) }
    var expanded by remember { mutableStateOf(false) }
    val firstNameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }

    ElevatedCard(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Spacing.md)) {
            Text("Add a Backup Guardian", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add someone else who's allowed to pick up this student if you're unavailable. " +
                    "They'll get an email to set up their own account and photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
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

            Spacer(Modifier.height(Spacing.sm))
            formError?.let { ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.sm)) }
            formSuccess?.let {
                if (formIsWarning) {
                    WarningBanner(it, modifier = Modifier.padding(bottom = Spacing.sm))
                } else {
                    SuccessBanner(it, modifier = Modifier.padding(bottom = Spacing.sm))
                }
            }

            PrimaryButton(
                text = "Add Guardian",
                loading = isSubmitting,
                onClick = { onSubmit(lastName.trim(), firstName.trim(), middleInitial.trim(), suffix.trim(), email.trim(), relationship) }
            )
        }
    }
}
