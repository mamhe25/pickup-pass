package com.pickuppass.android.ui.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Spacing

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigate: (SplashDestination) -> Unit
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        if (destination !in listOf(
                SplashDestination.Loading,
                SplashDestination.Offline,
                SplashDestination.ServiceUnavailable
            )) {
            onNavigate(destination)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (destination) {
            SplashDestination.Offline -> StatusPanel(
                icon = { Icon(Icons.Filled.CloudOff, null) },
                title = "No Internet Connection",
                message = "Your saved sign-in is still on this device, but PickupPass must reach the server to verify that the session is still authorized.",
                onRetry = viewModel::checkSession
            )
            SplashDestination.ServiceUnavailable -> StatusPanel(
                icon = { Icon(Icons.Filled.ErrorOutline, null) },
                title = "Service Temporarily Unavailable",
                message = "PickupPass could not verify your session because the server is unavailable. Your local sign-in has not been removed.",
                onRetry = viewModel::checkSession
            )
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Pickup Pass", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Digital Pickup Pass System",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg)
                )
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun StatusPanel(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl)
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.md))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
        )
        PrimaryButton(text = "Try Again", onClick = onRetry)
    }
}
