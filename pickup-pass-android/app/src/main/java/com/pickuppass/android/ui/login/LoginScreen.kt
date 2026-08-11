package com.pickuppass.android.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onParentHome: () -> Unit,
    onTeacherHome: () -> Unit,
    onSchoolAdminHome: () -> Unit,
    onMasterAdminHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loginResult by viewModel.loginResult.collectAsStateWithLifecycle()
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(loginResult) {
        when (loginResult) {
            LoginResult.ParentHome -> { onParentHome(); viewModel.consumeLoginResult() }
            LoginResult.TeacherHome -> { onTeacherHome(); viewModel.consumeLoginResult() }
            LoginResult.SchoolAdminHome -> { onSchoolAdminHome(); viewModel.consumeLoginResult() }
            LoginResult.MasterAdminHome -> { onMasterAdminHome(); viewModel.consumeLoginResult() }
            else -> Unit
        }
    }

    // A brief, one-shot fade-in on first composition — cheap (runs once,
    // not continuous) and gives the screen a touch of life without
    // costing anything on repeated recompositions from typing/state changes.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "loginContentFadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            // Hero mark: icon inside a tonal container rather than a bare
            // floating icon — a small, deliberate touch that reads as
            // "designed" instead of "default," and reuses the exact
            // primary/primaryContainer roles completed in Phase 1 rather
            // than a one-off color.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text("Pickup Pass", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xl)
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
            )
            Spacer(Modifier.height(Spacing.md))

            AnimatedVisibility(visible = uiState.error != null, enter = fadeIn() + expandVertically()) {
                uiState.error?.let {
                    ErrorBanner(it, modifier = Modifier.padding(bottom = Spacing.md))
                }
            }
            if (uiState.resetEmailSent) {
                Text(
                    "Password reset email sent — check your inbox.",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }

            PrimaryButton(
                text = "Sign In",
                onClick = viewModel::signIn,
                loading = uiState.isLoading
            )

            TextButton(onClick = viewModel::sendPasswordReset, modifier = Modifier.padding(top = Spacing.sm)) {
                Text("Forgot password?")
            }
        }
    }
}
