package com.pickuppass.android.ui.parent.pass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Amber500
import com.pickuppass.android.ui.theme.Amber900
import com.pickuppass.android.ui.theme.Spacing

/** Last minute of a pass's life gets a visible amber warning before it flips to red at 0 — a parent glancing at their phone in a pickup line should notice it's about to expire before it actually does. */
private const val URGENT_THRESHOLD_SECONDS = 60L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupPassScreen(
    studentId: String,
    viewModel: PickupPassViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(studentId) {
        viewModel.loadStudentName(studentId)
        viewModel.generatePass(studentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickup Pass") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.studentName.isNotBlank()) {
                Text(uiState.studentName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.md))
            }

            // Deliberately plain white, not MaterialTheme.colorScheme.surface —
            // QrCodeGenerator already bakes its own white background into the
            // bitmap itself, but the card framing it should match rather than
            // go dark-gray in dark mode and look like a mismatched cutout.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = androidx.compose.ui.graphics.Color.White,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    // Cross-fade between the three pass phases so a freshly
                    // generated QR settles in gently rather than popping into
                    // place — a calmer moment for a parent in the pickup line.
                    val phase = when {
                        uiState.isLoading -> "loading"
                        uiState.qrBitmap != null -> "qr"
                        uiState.error != null -> "error"
                        else -> "empty"
                    }
                    Crossfade(
                        targetState = phase,
                        animationSpec = tween(durationMillis = 300),
                        label = "qrPhase"
                    ) { state ->
                        when (state) {
                            "loading" -> CircularProgressIndicator()
                            "qr" -> uiState.qrBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Pickup QR code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "error" -> Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            if (uiState.qrBitmap != null) {
                val minutes = uiState.secondsRemaining / 60
                val seconds = uiState.secondsRemaining % 60
                val expired = uiState.secondsRemaining <= 0
                val urgent = !expired && uiState.secondsRemaining <= URGENT_THRESHOLD_SECONDS

                val (badgeContainer, badgeContent) = when {
                    expired -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f) to MaterialTheme.colorScheme.error
                    urgent -> Amber500.copy(alpha = 0.16f) to Amber900
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(color = badgeContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                        if (expired) "Expired — tap Regenerate" else "Expires in ${minutes}m ${seconds}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeContent,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Show this to school staff. Your photo will also be checked at pickup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            uiState.error?.let {
                Spacer(Modifier.height(Spacing.md))
                ErrorBanner(it)
            }

            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(
                text = "Regenerate Pass",
                onClick = { viewModel.generatePass(studentId) },
                loading = uiState.isLoading
            )
        }
    }
}
