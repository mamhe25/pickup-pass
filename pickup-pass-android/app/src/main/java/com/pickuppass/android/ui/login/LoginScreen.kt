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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel =
        hiltViewModel(),
    onBack: () -> Unit,
    onParentHome: () -> Unit,
    onTeacherHome: () -> Unit,
    onSchoolAdminHome: () -> Unit,
    onMasterAdminHome: () -> Unit
) {
    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    val loginResult by
        viewModel.loginResult
            .collectAsStateWithLifecycle()

    val passwordFocusRequester =
        remember {
            FocusRequester()
        }

    var showPassword by remember {
        mutableStateOf(false)
    }

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

            else ->
                Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription =
                                "Back"
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
                .verticalScroll(
                    rememberScrollState()
                )
                .imePadding()
                .padding(
                    horizontal = Spacing.lg,
                    vertical = Spacing.sm
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Surface(
                modifier =
                    Modifier.size(68.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Shield,
                        contentDescription = null,
                        modifier =
                            Modifier.size(34.dp),
                        tint =
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(
                Modifier.height(Spacing.md)
            )

            Text(
                text = "Welcome back",
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Spacer(
                Modifier.height(Spacing.xs)
            )

            Text(
                text =
                    "Sign in with the account provided by your school.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                Modifier.height(Spacing.xl)
            )

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape =
                    MaterialTheme.shapes.extraLarge,
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface
                    ),
                elevation =
                    CardDefaults.elevatedCardElevation(
                        defaultElevation = 2.dp
                    )
            ) {
                Column(
                    modifier =
                        Modifier.padding(Spacing.lg)
                ) {
                    Text(
                        text = "Account access",
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(
                        Modifier.height(Spacing.xs)
                    )

                    Text(
                        text =
                            "Your role and school access are verified after sign-in.",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        Modifier.height(Spacing.lg)
                    )

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange =
                            viewModel::onEmailChange,
                        label = {
                            Text("Email")
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    Icons.Filled.Email,
                                contentDescription = null
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Email,
                                imeAction =
                                    ImeAction.Next
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onNext = {
                                    passwordFocusRequester
                                        .requestFocus()
                                }
                            ),
                        shape =
                            MaterialTheme.shapes.small,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        Modifier.height(Spacing.sm)
                    )

                    OutlinedTextField(
                        value =
                            uiState.password,
                        onValueChange =
                            viewModel::onPasswordChange,
                        label = {
                            Text("Password")
                        },
                        singleLine = true,
                        visualTransformation =
                            if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    Icons.Filled.Lock,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showPassword =
                                        !showPassword
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        if (showPassword) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                    contentDescription =
                                        if (showPassword) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        }
                                )
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Password,
                                imeAction =
                                    ImeAction.Done
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    viewModel.signIn()
                                }
                            ),
                        shape =
                            MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(
                                passwordFocusRequester
                            )
                    )

                    Spacer(
                        Modifier.height(Spacing.md)
                    )

                    AnimatedVisibility(
                        visible =
                            uiState.error != null,
                        enter =
                            fadeIn() +
                                expandVertically()
                    ) {
                        uiState.error?.let {
                                message ->
                            ErrorBanner(
                                message,
                                modifier =
                                    Modifier.padding(
                                        bottom =
                                            Spacing.md
                                    )
                            )
                        }
                    }

                    if (
                        uiState.resetEmailSent
                    ) {
                        Surface(
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                MaterialTheme.shapes.small,
                            color =
                                MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text =
                                    "Password reset email sent. Check your inbox.",
                                modifier =
                                    Modifier.padding(Spacing.sm),
                                style =
                                    MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(
                            Modifier.height(Spacing.sm)
                        )
                    }

                    PrimaryButton(
                        text = "Sign in",
                        onClick =
                            viewModel::signIn,
                        loading =
                            uiState.isLoading
                    )

                    TextButton(
                        onClick =
                            viewModel::sendPasswordReset,
                        enabled =
                            !uiState.isLoading,
                        modifier = Modifier
                            .align(
                                Alignment.CenterHorizontally
                            )
                            .heightIn(min = 44.dp)
                    ) {
                        Text("Forgot password?")
                    }
                }
            }

            Spacer(
                Modifier.height(Spacing.lg)
            )

            Text(
                text =
                    "PickupPass does not create public self-service accounts. Contact your school administrator if you need access.",
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.widthIn(max = 440.dp),
                textAlign =
                    androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(
                Modifier.height(Spacing.lg)
            )
        }
    }
}
