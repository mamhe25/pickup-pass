package com.pickuppass.android.ui.splash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@Composable
fun SplashScreen(
    viewModel: SplashViewModel =
        hiltViewModel(),
    onNavigate:
        (SplashDestination) -> Unit
) {
    val destination by
        viewModel.destination
            .collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        if (
            destination !in
            listOf(
                SplashDestination.Loading,
                SplashDestination.Offline,
                SplashDestination.ServiceUnavailable
            )
        ) {
            onNavigate(destination)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                WindowInsets.safeDrawing
                    .asPaddingValues()
            ),
        contentAlignment =
            Alignment.Center
    ) {
        when (destination) {
            SplashDestination.Offline ->
                StatusPanel(
                    icon =
                        Icons.Filled.CloudOff,
                    title =
                        "You're offline",
                    message =
                        "PickupPass must reach the server before it can verify that the saved session is still authorized.",
                    onRetry =
                        viewModel::checkSession
                )

            SplashDestination.ServiceUnavailable ->
                StatusPanel(
                    icon =
                        Icons.Filled.ErrorOutline,
                    title =
                        "PickupPass is temporarily unavailable",
                    message =
                        "The server could not verify this session. Your saved sign-in has not been removed.",
                    onRetry =
                        viewModel::checkSession
                )

            else ->
                LoadingBrand()
        }
    }
}

@Composable
private fun LoadingBrand() {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Surface(
            modifier =
                Modifier.size(86.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primary,
            shadowElevation = 7.dp
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
                        Modifier.size(43.dp),
                    tint =
                        MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(
            Modifier.height(Spacing.lg)
        )

        Text(
            text = "PickupPass",
            style =
                MaterialTheme.typography.headlineMedium,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(
            Modifier.height(Spacing.xs)
        )

        Text(
            text = "Secure school dismissal",
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            Modifier.height(Spacing.xl)
        )

        LinearProgressIndicator(
            modifier =
                Modifier.width(92.dp),
            color =
                MaterialTheme.colorScheme.primary,
            trackColor =
                MaterialTheme.colorScheme.primaryContainer
        )
    }
}

@Composable
private fun StatusPanel(
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl)
    ) {
        Surface(
            modifier =
                Modifier.size(68.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier =
                        Modifier.size(31.dp),
                    tint =
                        MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(
            Modifier.height(Spacing.md)
        )

        Text(
            text = title,
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.ExtraBold,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            Modifier.height(Spacing.sm)
        )

        Text(
            text = message,
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            Modifier.height(Spacing.lg)
        )

        PrimaryButton(
            text = "Try again",
            onClick = onRetry,
            modifier =
                Modifier.widthIn(max = 320.dp)
        )
    }
}
