package com.pickuppass.android.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.common.TotpCodeField
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    viewModel: AccountSecurityViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showEmailSheet by remember {
        mutableStateOf(false)
    }
    var showPasswordSheet by remember {
        mutableStateOf(false)
    }

    var newEmail by remember {
        mutableStateOf("")
    }
    var emailPassword by remember {
        mutableStateOf("")
    }

    var currentPassword by remember {
        mutableStateOf("")
    }
    var newPassword by remember {
        mutableStateOf("")
    }
    var confirmation by remember {
        mutableStateOf("")
    }

    var mfaPassword by remember {
        mutableStateOf("")
    }
    var mfaCode by remember {
        mutableStateOf("")
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshIdentity(
                        showBusy = false
                    )
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.emailSuccess) {
        if (uiState.emailSuccess != null) {
            showEmailSheet = false
            newEmail = ""
            emailPassword = ""
        }
    }

    LaunchedEffect(uiState.passwordSuccess) {
        if (uiState.passwordSuccess != null) {
            showPasswordSheet = false
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
                subtitle = "Identity & sign-in protection",
                onBack = onBack,
            )
        }
    ) { padding ->
        PickupPassPullToRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = {
                viewModel.refreshIdentity()
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .imePadding()
                        .padding(
                            start = Spacing.md,
                            top = Spacing.sm,
                            end = Spacing.md,
                            bottom = Spacing.xl
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    PremiumHeroCard(
                        eyebrow = "Account protection",
                        title = "Secure your PickupPass identity",
                        message =
                            "Review your sign-in identity and manage sensitive account changes from one protected place.",
                        icon = Icons.Filled.Shield
                    )

                    IdentityCard(
                        state = uiState
                    )

                    PremiumSectionHeader(
                        title = "Security status",
                        subtitle =
                            "The protections currently applied to this account."
                    )

                    SecurityStatusCard(
                        title = "Authenticator 2FA",
                        value =
                            if (uiState.mfaEnabled) {
                                "Enabled"
                            } else if (
                                uiState.mfaRequired
                            ) {
                                "Setup required"
                            } else {
                                "Optional"
                            },
                        message =
                            if (uiState.mfaRequired) {
                                "Required for this administrator role."
                            } else {
                                "Optional additional sign-in protection."
                            },
                        icon = Icons.Filled.VerifiedUser,
                        positive = uiState.mfaEnabled
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
                            mfaCode =
                                it.filter(Char::isDigit)
                                    .take(6)
                            viewModel.clearMfaFeedback()
                        },
                        onSendVerification =
                            viewModel::sendMfaVerificationEmail,
                        onRefreshVerification = {
                            viewModel.refreshIdentity()
                        },
                        onBegin = {
                            viewModel.beginMfaEnrollment(
                                mfaPassword
                            )
                        },
                        onOpenAuthenticator =
                            viewModel::openAuthenticatorApp,
                        onFinish = {
                            viewModel.finishMfaEnrollment(
                                mfaCode
                            )
                        },
                        onCancel = {
                            mfaPassword = ""
                            mfaCode = ""
                            viewModel.cancelMfaEnrollment()
                        },
                        onDisable = {
                            viewModel.disableMfa(
                                mfaPassword
                            )
                        }
                    )

                    PremiumSectionHeader(
                        title = "Sign-in credentials",
                        subtitle =
                            "Sensitive changes open in a focused verification flow."
                    )

                    SecurityActionCard(
                        icon = Icons.Filled.Email,
                        title = "Change sign-in email",
                        message =
                            "Verify a new email before it replaces your current address.",
                        status =
                            when (uiState.emailVerified) {
                                true ->
                                    "Current email verified"
                                false ->
                                    "Verification required"
                                null ->
                                    "Checking verification…"
                            },
                        onClick = {
                            viewModel.clearEmailFeedback()
                            showEmailSheet = true
                        }
                    )

                    SecurityActionCard(
                        icon = Icons.Filled.Lock,
                        title = "Change password",
                        message =
                            "Reauthenticate with your current password before setting a new one.",
                        status = "Protected change",
                        onClick = {
                            viewModel.clearPasswordFeedback()
                            showPasswordSheet = true
                        }
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color =
                            MaterialTheme.colorScheme
                                .surfaceVariant
                                .copy(alpha = .55f)
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(Spacing.md),
                            verticalAlignment =
                                Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint =
                                    MaterialTheme.colorScheme
                                        .primary
                            )
                            Spacer(
                                Modifier.width(Spacing.sm)
                            )
                            Column {
                                Text(
                                    "Administrator security policy",
                                    style =
                                        MaterialTheme.typography
                                            .titleSmall,
                                    fontWeight =
                                        FontWeight.ExtraBold
                                )
                                Spacer(
                                    Modifier.height(2.dp)
                                )
                                Text(
                                    "Platform Owner and School Admin accounts require authenticator 2FA. Email changes keep the existing address active until Firebase verifies the new address.",
                                    style =
                                        MaterialTheme.typography
                                            .bodySmall,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.mfaSuccess?.let { message ->
        SuccessBanner(
            message = message,
            onDismiss = viewModel::clearMfaFeedback
        )
    }
    uiState.mfaError?.let {
        ErrorBanner(it)
    }

    uiState.emailSuccess?.let { message ->
        SuccessBanner(
            message = message,
            onDismiss = viewModel::clearEmailFeedback
        )
    }
    uiState.emailError?.let {
        ErrorBanner(it)
    }

    uiState.passwordSuccess?.let { message ->
        SuccessBanner(
            message = message,
            onDismiss =
                viewModel::clearPasswordFeedback
        )
    }
    uiState.passwordError?.let {
        ErrorBanner(it)
    }

    if (showEmailSheet) {
        EmailChangeSheet(
            state = uiState,
            newEmail = newEmail,
            currentPassword = emailPassword,
            onEmailChange = {
                newEmail = it
                viewModel.clearEmailFeedback()
            },
            onPasswordChange = {
                emailPassword = it
                viewModel.clearEmailFeedback()
            },
            onDismiss = {
                if (!uiState.emailBusy) {
                    showEmailSheet = false
                }
            },
            onSubmit = {
                viewModel.requestEmailChange(
                    currentPassword =
                        emailPassword,
                    newEmail = newEmail
                )
            }
        )
    }

    if (showPasswordSheet) {
        PasswordChangeSheet(
            state = uiState,
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmation = confirmation,
            onCurrentPasswordChange = {
                currentPassword = it
                viewModel.clearPasswordFeedback()
            },
            onNewPasswordChange = {
                newPassword = it
                viewModel.clearPasswordFeedback()
            },
            onConfirmationChange = {
                confirmation = it
                viewModel.clearPasswordFeedback()
            },
            onDismiss = {
                if (!uiState.passwordBusy) {
                    showPasswordSheet = false
                }
            },
            onSubmit = {
                viewModel.changePassword(
                    currentPassword =
                        currentPassword,
                    newPassword = newPassword,
                    confirmation = confirmation
                )
            }
        )
    }
}

@Composable
private fun IdentityCard(
    state: AccountSecurityUiState
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme
                            .secondaryContainer
                ) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            tint =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "SIGN-IN IDENTITY",
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                    Text(
                        when {
                            state.isLoading ->
                                "Loading…"
                            state.currentEmail.isBlank() ->
                                "Email unavailable"
                            else ->
                                state.currentEmail
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color =
                    when (state.emailVerified) {
                        true ->
                            MaterialTheme.colorScheme
                                .primaryContainer
                                .copy(alpha = .55f)
                        false ->
                            MaterialTheme.colorScheme
                                .errorContainer
                                .copy(alpha = .55f)
                        null ->
                            MaterialTheme.colorScheme
                                .surfaceVariant
                                .copy(alpha = .7f)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Spacing.sm,
                        vertical = Spacing.xs
                    ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (state.emailVerified == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (state.emailVerified == true) {
                                Icons.Filled.Check
                            } else {
                                Icons.Filled.Email
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint =
                                if (state.emailVerified == true) {
                                    MaterialTheme.colorScheme
                                        .primary
                                } else {
                                    MaterialTheme.colorScheme
                                        .error
                                }
                        )
                    }
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        when (state.emailVerified) {
                            true -> "Email verified"
                            false -> "Email verification required"
                            null -> "Checking verification…"
                        },
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "Pull down to refresh after completing an email verification link.",
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecurityStatusCard(
    title: String,
    value: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    positive: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.medium,
                color =
                    if (positive) {
                        MaterialTheme.colorScheme
                            .primaryContainer
                    } else {
                        MaterialTheme.colorScheme
                            .surfaceVariant
                    }
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint =
                            if (positive) {
                                MaterialTheme.colorScheme
                                    .primary
                            } else {
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            }
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    message,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(value)
                }
            )
        }
    }
}

@Composable
private fun SecurityActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    status: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
                        .copy(alpha = .55f)
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme
                                .primary
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    message,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    status,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight = FontWeight.Bold,
                    color =
                        MaterialTheme.colorScheme
                            .primary
                )
            }

            Text(
                "Open",
                style =
                    MaterialTheme.typography
                        .labelLarge,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailChangeSheet(
    state: AccountSecurityUiState,
    newEmail: String,
    currentPassword: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
                .imePadding()
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl
                ),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "SECURE IDENTITY CHANGE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Change sign-in email",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Your current address stays active until Firebase verifies the new one.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .surfaceVariant
                        .copy(alpha = .55f)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement =
                        Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "CURRENT EMAIL",
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                    Text(
                        state.currentEmail
                            .ifBlank {
                                "Unavailable"
                            },
                        modifier = Modifier.fillMaxWidth(),
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = newEmail,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.emailBusy,
                singleLine = true,
                label = {
                    Text("New email")
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType =
                        KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            PasswordField(
                value = currentPassword,
                onValueChange = onPasswordChange,
                label = "Current password",
                enabled = !state.emailBusy
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
                        .copy(alpha = .4f)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        "What happens next",
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "1. PickupPass reauthenticates this signed-in account with your current password.",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Firebase sends a verification link to the new email.",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Your existing email remains the sign-in address until the new one is verified.",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                    if (state.mfaRequired) {
                        Text(
                            "This administrator account also remains protected by required authenticator 2FA at sign-in.",
                            style =
                                MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                MaterialTheme.colorScheme
                                    .primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.emailBusy
                ) {
                    Text("Cancel")
                }

                Spacer(Modifier.width(Spacing.sm))

                Button(
                    onClick = onSubmit,
                    enabled =
                        !state.emailBusy &&
                            newEmail.isNotBlank() &&
                            currentPassword.isNotBlank()
                ) {
                    if (state.emailBusy) {
                        BusyIndicator()
                        Spacer(
                            Modifier.width(Spacing.sm)
                        )
                        Text("Sending…")
                    } else {
                        Text("Send verification")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordChangeSheet(
    state: AccountSecurityUiState,
    currentPassword: String,
    newPassword: String,
    confirmation: String,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
                .imePadding()
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl
                ),
            verticalArrangement =
                Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "SECURE CREDENTIAL CHANGE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Change password",
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Confirm your current password, then choose a new password for future sign-ins.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )

            PasswordField(
                value = currentPassword,
                onValueChange =
                    onCurrentPasswordChange,
                label = "Current password",
                enabled = !state.passwordBusy
            )

            PasswordField(
                value = newPassword,
                onValueChange =
                    onNewPasswordChange,
                label = "New password",
                enabled = !state.passwordBusy
            )

            PasswordField(
                value = confirmation,
                onValueChange =
                    onConfirmationChange,
                label = "Confirm new password",
                enabled = !state.passwordBusy
            )

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(Spacing.xs)
            ) {
                PasswordRequirement(
                    newPassword.length >= 8,
                    "At least 8 characters"
                )
                PasswordRequirement(
                    newPassword.isNotEmpty() &&
                        newPassword !=
                            currentPassword,
                    "Different from current password"
                )
                PasswordRequirement(
                    newPassword.isNotEmpty() &&
                        newPassword ==
                            confirmation,
                    "Confirmation matches"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.passwordBusy
                ) {
                    Text("Cancel")
                }

                Spacer(Modifier.width(Spacing.sm))

                Button(
                    onClick = onSubmit,
                    enabled =
                        !state.passwordBusy &&
                            currentPassword.isNotBlank() &&
                            newPassword.length >= 8 &&
                            newPassword == confirmation
                ) {
                    if (state.passwordBusy) {
                        BusyIndicator()
                        Spacer(
                            Modifier.width(Spacing.sm)
                        )
                        Text("Updating…")
                    } else {
                        Text("Change password")
                    }
                }
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
            } else if (state.emailVerified == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(Spacing.sm)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "Checking email verification…",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (state.emailVerified == false) {
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

                TotpCodeField(
                    value = code,
                    onValueChange = onCodeChange,
                    onComplete = onFinish,
                    enabled = !state.mfaBusy,
                    isError = state.mfaError != null,
                    autoFocus = false
                )
                Text(
                    "Paste or enter the 6-digit code. Setup completes automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
