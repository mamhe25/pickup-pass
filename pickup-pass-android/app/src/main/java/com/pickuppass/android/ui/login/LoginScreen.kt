package com.pickuppass.android.ui.login

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.session.SessionEndReason
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.PickupPassBrandMark
import com.pickuppass.android.ui.common.PickupPassWordmark
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.TotpCodeField
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onParentHome: () -> Unit,
    onTeacherHome: () -> Unit,
    onSchoolAdminHome: () -> Unit,
    onMasterAdminHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loginResult by viewModel.loginResult.collectAsStateWithLifecycle()

    val passwordFocusRequester = remember { FocusRequester() }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(loginResult) {
        when (loginResult) {
            LoginResult.ParentHome -> {
                onParentHome()
                viewModel.consumeLoginResult()
            }
            LoginResult.TeacherHome -> {
                onTeacherHome()
                viewModel.consumeLoginResult()
            }
            LoginResult.SchoolAdminHome -> {
                onSchoolAdminHome()
                viewModel.consumeLoginResult()
            }
            LoginResult.MasterAdminHome -> {
                onMasterAdminHome()
                viewModel.consumeLoginResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !uiState.requiredMfaEnrollment &&
                            !uiState.mfaChallengeRequired
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
                .padding(
                    horizontal = Spacing.lg,
                    vertical = Spacing.sm
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PickupPassBrandMark(
                size = 72.dp,
                contentDescription = "PickupPass"
            )

            Spacer(Modifier.height(Spacing.sm))

            PickupPassWordmark(
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(Spacing.lg))

            Text(
                "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Sign in with the account provided by your school.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.lg))

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg)
                ) {
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                passwordFocusRequester.requestFocus()
                            }
                        ),
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(Spacing.sm))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (showPassword) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    }
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.signIn() }
                        ),
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester)
                    )

                    Spacer(Modifier.height(Spacing.md))

                    uiState.sessionEndReason?.let { reason ->
                        val title: String
                        val message: String
                        val tone: FeedbackTone

                        when (reason) {
                            SessionEndReason.EXPIRED_OR_REVOKED -> {
                                title = "You've been signed out"
                                message =
                                    "This device's session expired or was signed out from another device. Sign in again to continue."
                                tone = FeedbackTone.Info
                            }

                            SessionEndReason.ACCOUNT_DISABLED -> {
                                title = "Account access disabled"
                                message =
                                    "Your account has been disabled. Contact your school administrator for help."
                                tone = FeedbackTone.Warning
                            }

                            SessionEndReason.UNAUTHORIZED -> {
                                title = "Account access changed"
                                message =
                                    "Your account no longer has access to PickupPass. Contact your school administrator if you think this is a mistake."
                                tone = FeedbackTone.Warning
                            }
                        }

                        FeedbackCard(
                            message = message,
                            tone = tone,
                            title = title,
                            modifier =
                                Modifier.padding(
                                    bottom = Spacing.md
                                )
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.error != null,
                        enter = fadeIn() + expandVertically()
                    ) {
                        uiState.error?.let {
                            ErrorBanner(
                                it,
                                modifier = Modifier.padding(bottom = Spacing.md)
                            )
                        }
                    }

                    uiState.setupCompleteMessage?.let {
                        SuccessBanner(it)
                        Spacer(Modifier.height(Spacing.md))
                    }

                    if (uiState.resetEmailSent) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Password reset email sent. Check your inbox.",
                                modifier = Modifier.padding(Spacing.sm),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = viewModel::sendPasswordReset,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Text("Forgot password?")
                        }

                        PrimaryButton(
                            text = "Sign in",
                            onClick = viewModel::signIn,
                            loading = uiState.isLoading,
                            modifier = Modifier.widthIn(min = 132.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Platform owners and school administrators use required two-factor authentication. Parents and teachers can enable it from Account security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (uiState.mfaChallengeRequired) {
        MfaChallengeDialog(
            state = uiState,
            onCodeChange = viewModel::onMfaCodeChange,
            onVerify = viewModel::verifyMfaChallenge,
            onCancel = viewModel::cancelMfaChallenge
        )
    }

    if (uiState.requiredMfaEnrollment) {
        RequiredMfaEnrollmentDialog(
            state = uiState,
            onCodeChange = viewModel::onMfaCodeChange,
            onSendVerificationEmail = viewModel::sendRequiredVerificationEmail,
            onRefreshVerification = viewModel::refreshRequiredEmailVerification,
            onBeginSetup = viewModel::beginRequiredMfaEnrollment,
            onOpenAuthenticator = viewModel::openAuthenticatorApp,
            onFinishSetup = viewModel::finishRequiredMfaEnrollment,
            onCancel = viewModel::cancelRequiredMfaEnrollment
        )
    }
}

@Composable
private fun MfaChallengeDialog(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Verify it's you") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Open your authenticator app and enter the current 6-digit PickupPass code.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TotpCodeField(
                    value = state.mfaCode,
                    onValueChange = onCodeChange,
                    onComplete = onVerify,
                    enabled = !state.mfaBusy,
                    isError = state.mfaError != null,
                    autoFocus = false
                )
                Text(
                    "Paste or enter the 6-digit code. Verification starts automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.mfaError?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = onVerify,
                enabled = !state.mfaBusy && state.mfaCode.length == 6
            ) {
                if (state.mfaBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text("Verify")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !state.mfaBusy
            ) {
                Text("Cancel sign-in")
            }
        }
    )
}

@Composable
private fun RequiredMfaEnrollmentDialog(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onSendVerificationEmail: () -> Unit,
    onRefreshVerification: () -> Unit,
    onBeginSetup: () -> Unit,
    onOpenAuthenticator: () -> Unit,
    onFinishSetup: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true
        ),
        icon = {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Two-factor authentication required")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    "Your administrator role protects student and school data, so PickupPass requires an authenticator app before you can continue.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!state.emailVerified) {
                    SecurityStep(
                        number = "1",
                        title = "Verify your email",
                        description = "Firebase requires a verified sign-in email before an authenticator can be enrolled."
                    )

                    if (state.verificationEmailSent) {
                        SuccessBanner(
                            "Verification email sent to ${state.email}. Open the link, then return here."
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        FilledTonalButton(
                            onClick = onSendVerificationEmail,
                            enabled = !state.mfaBusy
                        ) {
                            Text(
                                if (state.verificationEmailSent) {
                                    "Resend email"
                                } else {
                                    "Send verification"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = onRefreshVerification,
                            enabled = !state.mfaBusy
                        ) {
                            Text("I've verified")
                        }
                    }
                } else if (!state.totpSetupReady) {
                    SecurityStep(
                        number = "1",
                        title = "Add PickupPass to your authenticator",
                        description = "Select Set up authenticator. PickupPass will generate a private setup key after securely re-checking your password."
                    )
                    Button(
                        onClick = onBeginSetup,
                        enabled = !state.mfaBusy
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Set up authenticator")
                    }
                } else {
                    SecurityStep(
                        number = "1",
                        title = "Add the setup key",
                        description = "Open your authenticator app and add PickupPass. You can launch a compatible app automatically or enter this key manually."
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(
                                "SETUP KEY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Open authenticator app")
                    }

                    SecurityStep(
                        number = "2",
                        title = "Confirm a code",
                        description = "Enter the current 6-digit code shown for PickupPass."
                    )

                    TotpCodeField(
                        value = state.mfaCode,
                        onValueChange = onCodeChange,
                        onComplete = onFinishSetup,
                        enabled = !state.mfaBusy,
                        isError = state.mfaError != null,
                        autoFocus = false
                    )
                    Text(
                        "Paste or enter the 6-digit code. Setup completes automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.mfaError?.let { ErrorBanner(it) }

                if (state.mfaBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                Text(
                    "Keep your authenticator available. You will be asked for a fresh code when signing in.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (state.totpSetupReady) {
                Button(
                    onClick = onFinishSetup,
                    enabled = !state.mfaBusy && state.mfaCode.length == 6
                ) {
                    Text("Enable 2FA")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !state.mfaBusy
            ) {
                Text("Sign out")
            }
        }
    )
}

@Composable
private fun SecurityStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
