package com.pickuppass.android.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onParentHome: () -> Unit,
    onTeacherHome: () -> Unit,
    onSchoolAdminHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loginResult by viewModel.loginResult.collectAsStateWithLifecycle()

    LaunchedEffect(loginResult) {
        when (loginResult) {
            LoginResult.ParentHome -> { onParentHome(); viewModel.consumeLoginResult() }
            LoginResult.TeacherHome -> { onTeacherHome(); viewModel.consumeLoginResult() }
            LoginResult.SchoolAdminHome -> { onSchoolAdminHome(); viewModel.consumeLoginResult() }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 8.dp)
            )
            Text("Pickup Pass", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            uiState.error?.let {
                ErrorBanner(it, modifier = Modifier.padding(bottom = 16.dp))
            }
            if (uiState.resetEmailSent) {
                Text(
                    "Password reset email sent — check your inbox.",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            PrimaryButton(
                text = "Sign In",
                onClick = viewModel::signIn,
                loading = uiState.isLoading
            )

            TextButton(onClick = viewModel::sendPasswordReset, modifier = Modifier.padding(top = 8.dp)) {
                Text("Forgot password?")
            }
        }
    }
}
