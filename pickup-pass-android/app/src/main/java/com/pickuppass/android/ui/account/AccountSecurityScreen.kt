package com.pickuppass.android.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    viewModel: AccountSecurityViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var newEmail by remember { mutableStateOf("") }
    var emailPassword by remember { mutableStateOf("") }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    var mfaPassword by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIdentity(showBusy = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.emailSuccess) {
        if (uiState.emailSuccess != null) {
            newEmail = ""
            emailPassword = ""
        }
    }

    LaunchedEffect(uiState.passwordSuccess) {
        if (uiState.passwordSuccess != null) {
            currentPassword = ""
            newPassword = ""
            confirmation = ""
        }
    }

    LaunchedEffect(uiState.totpSetupReady) {
        if (uiState.totpSetupReady) {
            mfaPassword = ""
            mfaCode = ""
        }
    }

    LaunchedEffect(uiState.mfaEnabled) {
        if (uiState.mfaEnabled) {
            mfaPassword = ""
            mfaCode = ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Account security",
                subtitle = "Sign-in & two-factor protection",
                onBack = onBack,
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(
                        start = Spacing.md,
                        top = Spacing.sm,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(Spacing.md))

                        Column {
                            Text(
                                "PROTECT YOUR ACCOUNT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "Security built for school data",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Use a unique password and an authenticator app for stronger account protection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onPrimaryContainer.copy(alpha = .78f)
                            )
                        }
                    }
                }

                IdentityCard(
                    state = uiState,
                    onRefresh = {
                        viewModel.refreshIdentity()
                    }
                )

                TwoFactorCard(
                    state = uiState,
                    password = mfaPassword,
                    onPasswordChange = {
                        mfaPassword = it
                        viewModel.clearMfaFeedback()
                    },
                    code = mfaCode,
                    onCodeChange = {
                        mfaCode = it.filter(Char::isDigit).take(6)
                        viewModel.clearMfaFeedback()
                    },
                    onSendVerification = viewModel::sendMfaVerificationEmail,
                    onRefreshVerification = {
                        viewModel.refreshIdentity()
                    },
                    onBegin = {
                        viewModel.beginMfaEnrollment(mfaPassword)
                    },
                    onOpenAuthenticator = viewModel::openAuthenticatorApp,
                    onFinish = {
                        viewModel.finishMfaEnrollment(mfaCode)
                    },
                    onCancel = {
                        mfaPassword = ""
                        mfaCode = ""
                        viewModel.cancelMfaEnrollment()
                    },
                    onDisable = {
                        viewModel.disableMfa(mfaPassword)
                    }
                )

                uiState.mfaSuccess?.let { SuccessBanner(it) }
                uiState.mfaError?.let { ErrorBanner(it) }

                CredentialCard(
                    title = "Change email",
                    subtitle =
                        "We'll send a verification link to the new address. Your current email remains active until the new one is verified."
                ) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = {
                            newEmail = it
                            viewModel.clearEmailFeedback()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.emailBusy,
                        singleLine = true,
                        label = { Text("New email") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Email,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    PasswordField(
                        value = emailPassword,
                        onValueChange = {
                            emailPassword = it
                            viewModel.clearEmailFeedback()
                        },
                        label = "Current password",
                        enabled = !uiState.emailBusy
                    )

                    Text(
                        "Your password is sent only to Firebase Authentication for reauthentication. PickupPass does not store it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            viewModel.requestEmailChange(
                                currentPassword = emailPassword,
                                newEmail = newEmail
                            )
                        },
                        enabled =
                            !uiState.emailBusy &&
                                uiState.currentEmail.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 46.dp)
                    ) {
                        if (uiState.emailBusy) {
                            BusyIndicator()
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Sending…")
                        } else {
                            Text("Send verification email")
                        }
                    }
                }

                uiState.emailSuccess?.let { SuccessBanner(it) }
                uiState.emailError?.let { ErrorBanner(it) }

                CredentialCard(
                    title = "Change password",
                    subtitle =
                        "Create a password different from your current one. Your current password authorizes the change."
                ) {
                    PasswordField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            viewModel.clearPasswordFeedback()
                        },
                        label = "Current password",
                        enabled = !uiState.passwordBusy
                    )

                    PasswordField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            viewModel.clearPasswordFeedback()
                        },
                        label = "New password",
                        enabled = !uiState.passwordBusy
                    )

                    PasswordField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = it
                            viewModel.clearPasswordFeedback()
                        },
                        label = "Confirm new password",
                        enabled = !uiState.passwordBusy
                    )

                    PasswordRequirement(
                        newPassword.length >= 8,
                        "At least 8 characters"
                    )
                    PasswordRequirement(
                        newPassword.isNotEmpty() &&
                            newPassword != currentPassword,
                        "Different from current password"
                    )
                    PasswordRequirement(
                        newPassword.isNotEmpty() &&
                            newPassword == confirmation,
                        "Confirmation matches"
                    )

                    Button(
                        onClick = {
                            viewModel.changePassword(
                                currentPassword = currentPassword,
                                newPassword = newPassword,
                                confirmation = confirmation
                            )
                        },
                        enabled = !uiState.passwordBusy,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 46.dp)
                    ) {
                        if (uiState.passwordBusy) {
                            BusyIndicator()
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Updating…")
                        } else {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Change password")
                        }
                    }
                }

                uiState.passwordSuccess?.let { SuccessBanner(it) }
                uiState.passwordError?.let { ErrorBanner(it) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme
                        .surfaceVariant.copy(alpha = .55f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                "Security policy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Two-factor authentication is mandatory for Platform Owner and School Admin accounts. Parent and Teacher accounts can opt in at any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(
    state: AccountSecurityUiState,
    onRefresh: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "Current sign-in email",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        state.isLoading -> "Loading…"
                        state.currentEmail.isBlank() -> "Not available"
                        else -> state.currentEmail
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (state.emailVerified) {
                        "Verified"
                    } else {
                        "Verification required before enrolling an authenticator"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.emailVerified) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoading && !state.isRefreshing
            ) {
                Text(if (state.isRefreshing) "Refreshing…" else "Refresh")
            }
        }
    }
}

@Composable
private fun TwoFactorCard(
    state: AccountSecurityUiState,
    password: String,
    onPasswordChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onSendVerification: () -> Unit,
    onRefreshVerification: () -> Unit,
    onBegin: () -> Unit,
    onOpenAuthenticator: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDisable: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = if (state.mfaEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.mfaEnabled) {
                                Icons.Filled.VerifiedUser
                            } else {
                                Icons.Filled.Security
                            },
                            contentDescription = null,
                            tint = if (state.mfaEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Authenticator 2FA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        when {
                            state.mfaRequired && state.mfaEnabled ->
                                "Required for this administrator role · Enabled"
                            state.mfaRequired ->
                                "Required for this administrator role"
                            state.mfaEnabled ->
                                "Optional protection · Enabled"
                            else ->
                                "Optional protection"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            when {
                                state.mfaEnabled -> "ON"
                                state.mfaRequired -> "REQUIRED"
                                else -> "OFF"
                            }
                        )
                    }
                )
            }

            HorizontalDivider()

            if (state.mfaEnabled) {
                Text(
                    "Your authenticator app adds a second proof of identity after your password.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (state.mfaRequired) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "This factor cannot be disabled from PickupPass because your role requires 2FA.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Text(
                        "You can disable 2FA for this Parent or Teacher account after confirming your password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PasswordField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = "Current password",
                        enabled = !state.mfaBusy
                    )
                    OutlinedButton(
                        onClick = onDisable,
                        enabled = !state.mfaBusy && password.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Disable 2FA")
                    }
                }
            } else if (!state.emailVerified) {
                Text(
                    "Verify your sign-in email before adding an authenticator app.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (state.verificationEmailSent) {
                    SuccessBanner(
                        "Verification email sent. Open the link, then refresh your account."
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Button(
                        onClick = onSendVerification,
                        enabled = !state.mfaBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (state.verificationEmailSent) {
                                "Resend"
                            } else {
                                "Send verification"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = onRefreshVerification,
                        enabled = !state.mfaBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("I've verified")
                    }
                }
            } else if (!state.totpSetupReady) {
                Text(
                    if (state.mfaRequired) {
                        "Set up an authenticator app now. PickupPass will require it at every new sign-in."
                    } else {
                        "Add an authenticator app to require a rotating 6-digit code at sign-in."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                PasswordField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Current password",
                    enabled = !state.mfaBusy
                )

                Button(
                    onClick = onBegin,
                    enabled = !state.mfaBusy && password.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Set up authenticator")
                }
            } else {
                Text(
                    "Add PickupPass to your authenticator app using the key below. The setup key is shown only during enrollment.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier.padding(Spacing.md)
                    ) {
                        Text(
                            "SETUP KEY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                        Text(
                            state.totpSetupKey.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenAuthenticator,
                    enabled = !state.mfaBusy
                ) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Open authenticator app")
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("6-digit authenticator code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    enabled = !state.mfaBusy
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !state.mfaBusy
                    ) {
                        Text("Cancel setup")
                    }
                    Spacer(Modifier.width(Spacing.xs))
                    Button(
                        onClick = onFinish,
                        enabled = !state.mfaBusy && code.length == 6
                    ) {
                        Text("Enable 2FA")
                    }
                }
            }

            if (state.mfaBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Text(
                "The TOTP setup secret is handled only by Firebase Authentication and your authenticator app; PickupPass does not send it to the backend or Firestore.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CredentialCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Filled.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = if (visible) {
                        "Hide password"
                    } else {
                        "Show password"
                    }
                )
            }
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        )
    )
}

@Composable
private fun PasswordRequirement(
    satisfied: Boolean,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (satisfied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (satisfied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun BusyIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary
    )
}
