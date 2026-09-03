package com.pickuppass.android.ui.teacher.registerparent

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.WarningBanner
import com.pickuppass.android.ui.theme.Spacing

private const val MIDDLE_INITIAL_LIMIT = 4
private const val SUFFIX_LIMIT = 16

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
    var relationshipExpanded by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val firstNameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }

    val normalizedEmail = email.trim()
    val relationshipLabel = relationshipOptions
        .firstOrNull { it.first == relationship }
        ?.second
        ?: "Parent / Guardian"

    val formComplete = !uiState.hasPrimaryGuardian &&
        lastName.isNotBlank() &&
        firstName.isNotBlank() &&
        normalizedEmail.isNotBlank()

    LaunchedEffect(studentId) {
        viewModel.loadStudent(studentId)
    }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            lastName = ""
            firstName = ""
            middleInitial = ""
            suffix = ""
            email = ""
            relationship = relationshipOptions.first().first
            localValidationError = null
            showConfirmation = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register primary guardian") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            StudentContextCard(uiState.studentLabel)

            Spacer(Modifier.height(Spacing.md))

            if (uiState.hasPrimaryGuardian) {
                PrimaryGuardianExistsCard(onBack = onBack)
                Spacer(Modifier.height(Spacing.md))
            }

            RegistrationOutcomeCard()

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Guardian details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Enter the person's legal or commonly used name and the email they will use for PickupPass.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = lastName,
                onValueChange = {
                    lastName = it
                    localValidationError = null
                },
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
                onValueChange = {
                    firstName = it
                    localValidationError = null
                },
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
                    onValueChange = {
                        if (it.length <= MIDDLE_INITIAL_LIMIT) {
                            middleInitial = it
                        }
                    },
                    label = { Text("M.I.") },
                    supportingText = { Text("Optional") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = suffix,
                    onValueChange = {
                        if (it.length <= SUFFIX_LIMIT) {
                            suffix = it
                        }
                    },
                    label = { Text("Suffix") },
                    supportingText = { Text("Optional") },
                    placeholder = { Text("Jr., III") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1.35f)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    localValidationError = null
                },
                label = { Text("Email address") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null
                    )
                },
                supportingText = { Text("Used for sign-in and account setup.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (formComplete) {
                            val error = validateDraft(lastName, firstName, normalizedEmail)
                            if (error == null) {
                                showConfirmation = true
                            } else {
                                localValidationError = error
                            }
                        }
                    }
                ),
                isError = localValidationError
                    ?.contains("email", ignoreCase = true) == true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
            )

            Spacer(Modifier.height(Spacing.sm))

            ExposedDropdownMenuBox(
                expanded = relationshipExpanded,
                onExpandedChange = { relationshipExpanded = it }
            ) {
                OutlinedTextField(
                    value = relationshipLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Relationship") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = relationshipExpanded
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = relationshipExpanded,
                    onDismissRequest = { relationshipExpanded = false }
                ) {
                    relationshipOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second) },
                            onClick = {
                                relationship = option.first
                                relationshipExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            AnimatedVisibility(
                visible = localValidationError != null,
                enter = fadeIn() + expandVertically()
            ) {
                localValidationError?.let { message ->
                    ErrorBanner(
                        message,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically()
            ) {
                uiState.error?.let { message ->
                    ErrorBanner(
                        message,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.successMessage != null,
                enter = fadeIn() + expandVertically()
            ) {
                uiState.successMessage?.let { message ->
                    if (uiState.successIsWarning) {
                        WarningBanner(
                            message,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    } else {
                        SuccessBanner(
                            message,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val error = validateDraft(lastName, firstName, normalizedEmail)
                    if (error == null) {
                        localValidationError = null
                        showConfirmation = true
                    } else {
                        localValidationError = error
                    }
                },
                enabled = formComplete && !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAddAlt1,
                    contentDescription = null
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Review primary guardian")
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }

    if (showConfirmation) {
        RegistrationConfirmationDialog(
            studentLabel = uiState.studentLabel,
            guardianName = buildGuardianName(
                firstName = firstName,
                middleInitial = middleInitial,
                lastName = lastName,
                suffix = suffix
            ),
            email = normalizedEmail,
            relationship = relationshipLabel,
            isSubmitting = uiState.isSubmitting,
            onDismiss = {
                if (!uiState.isSubmitting) {
                    showConfirmation = false
                }
            },
            onConfirm = {
                viewModel.register(
                    studentId = studentId,
                    lastName = lastName.trim(),
                    firstName = firstName.trim(),
                    middleInitial = middleInitial.trim(),
                    suffix = suffix.trim(),
                    parentEmail = normalizedEmail,
                    relationship = relationship
                )
            }
        )
    }
}

@Composable
private fun StudentContextCard(studentLabel: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Registering for",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = studentLabel.ifBlank { "Loading student…" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun PrimaryGuardianExistsCard(
    onBack: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Primary guardian already registered",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "This student already has the protected primary guardian of record. Return to guardian management to add or update backup and one-day pickup access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Back to guardian management")
            }
        }
    }
}

@Composable
private fun RegistrationOutcomeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = "What happens next",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "This creates the student’s one protected primary guardian. If the email already belongs to PickupPass, that account is linked; otherwise PickupPass creates the account and sends setup instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "The guardian must complete account setup and upload their identity photo before using the secure pickup workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RegistrationConfirmationDialog(
    studentLabel: String,
    guardianName: String,
    email: String,
    relationship: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PersonAddAlt1,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        title = {
            Text(
                text = "Register this primary guardian?",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Confirm the identity and email before PickupPass establishes this person as the student’s protected primary guardian.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                ReviewRow("Student", studentLabel.ifBlank { "Selected student" })
                ReviewRow("Guardian", guardianName)
                ReviewRow("Relationship", relationship)
                ReviewRow("Email", email)
                Spacer(Modifier.height(Spacing.sm))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "A student can have only one primary guardian. After registration, additional people must be added as backup or one-day guardians.",
                        modifier = Modifier.padding(Spacing.sm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Keep editing")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (isSubmitting) "Registering…" else "Register primary guardian")
            }
        }
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun validateDraft(
    lastName: String,
    firstName: String,
    email: String
): String? {
    if (lastName.isBlank() || firstName.isBlank()) {
        return "Enter the guardian's last name and first name."
    }
    if (email.isBlank()) {
        return "Enter the guardian's email address."
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return "Enter a valid email address."
    }
    return null
}

private fun buildGuardianName(
    firstName: String,
    middleInitial: String,
    lastName: String,
    suffix: String
): String = listOf(
    firstName.trim(),
    middleInitial.trim(),
    lastName.trim(),
    suffix.trim()
)
    .filter { it.isNotBlank() }
    .joinToString(" ")
