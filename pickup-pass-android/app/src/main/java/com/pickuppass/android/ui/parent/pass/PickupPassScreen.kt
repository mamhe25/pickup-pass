package com.pickuppass.android.ui.parent.pass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Amber500
import com.pickuppass.android.ui.theme.Amber900
import com.pickuppass.android.ui.theme.Spacing

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
                .padding(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
            ) {
                PassCredential(
                    uiState = uiState
                )

                Spacer(Modifier.height(Spacing.md))

                PassStatus(
                    uiState = uiState
                )

                Spacer(Modifier.height(Spacing.md))

                PickupPolicyCard(
                    policyText = uiState.pickupPolicyText
                )

                Spacer(Modifier.height(Spacing.md))

                WhatHappensNext()

                uiState.error?.let { error ->
                    Spacer(Modifier.height(Spacing.md))
                    ErrorState(
                        message = error,
                        onRetry = {
                            viewModel.generatePass(studentId)
                        }
                    )
                }

                Spacer(Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            viewModel.generatePass(studentId)
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.heightIn(min = 46.dp),
                        contentPadding = PaddingValues(
                            horizontal = 18.dp,
                            vertical = 10.dp
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )

                            Spacer(Modifier.width(Spacing.sm))

                            Text("Generating…")
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(Spacing.sm))

                            Text(
                                if (uiState.secondsRemaining <= 0L &&
                                    uiState.qrBitmap != null
                                ) {
                                    "Generate new pass"
                                } else {
                                    "Regenerate pass"
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = "For security, use only the latest valid pass. School staff still verify the authorized guardian before releasing the student.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PassCredential(
    uiState: PickupPassUiState
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 5.dp
        )
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg)
                ) {
                    SchoolIdentity(uiState)

                    Spacer(Modifier.height(Spacing.lg))

                    Text(
                        text = "SECURE PICKUP PASS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                    )

                    Spacer(Modifier.height(Spacing.xs))

                    Text(
                        text = "Show this pass to authorized school staff.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(Spacing.xs))

                    Text(
                        text = "Staff will verify the guardian and student before approving release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(Spacing.md)
            ) {
                StudentIdentity(uiState)

                Spacer(Modifier.height(Spacing.lg))

                QrSection(uiState)
            }
        }
    }
}

@Composable
private fun SchoolIdentity(
    uiState: PickupPassUiState
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.18f)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (!uiState.schoolLogoUrl.isNullOrBlank()) {
                    SmartImage(
                        model = uiState.schoolLogoUrl,
                        contentDescription = "${uiState.schoolName} logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = schoolInitials(uiState.schoolName),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "PICKUPPASS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.62f)
            )

            Text(
                text = uiState.schoolName.ifBlank { "Your school" },
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StudentIdentity(
    uiState: PickupPassUiState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudentAvatar(
            name = uiState.studentName,
            photoUrl = uiState.studentPhotoUrl
        )

        Spacer(Modifier.width(Spacing.md))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "STUDENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = uiState.studentName.ifBlank { "Loading student…" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Grade ${uiState.studentGrade.ifBlank { "—" }} · Section ${uiState.studentSection.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentAvatar(
    name: String,
    photoUrl: String?
) {
    val initials = initials(name).ifBlank { "S" }

    Box(
        modifier = Modifier.size(58.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (!photoUrl.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape
            ) {
                SmartImage(
                    model = photoUrl,
                    contentDescription = "$name profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun QrSection(
    uiState: PickupPassUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White,
        border = BorderStroke(
            1.dp,
            Color(0xFFE2E8F0)
        ),
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            val qrSize =
                if (maxWidth < 320.dp) maxWidth else 320.dp

            Box(
                modifier = Modifier.size(qrSize),
                contentAlignment = Alignment.Center
            ) {
                val phase = when {
                    uiState.isLoading -> "loading"
                    uiState.qrBitmap != null -> "qr"
                    uiState.error != null -> "error"
                    else -> "empty"
                }

                Crossfade(
                    targetState = phase,
                    animationSpec = tween(240),
                    label = "pickupPassQrPhase"
                ) { state ->
                    when (state) {
                        "loading" -> Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()

                            Spacer(Modifier.height(Spacing.sm))

                            Text(
                                text = "Generating secure pass…",
                                color = Color(0xFF64748B),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        "qr" -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            uiState.qrBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Secure pickup QR code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (uiState.secondsRemaining <= 0L) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.95f),
                                    border = BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        text = "EXPIRED",
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        "error" -> Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(34.dp)
                            )

                            Spacer(Modifier.height(Spacing.sm))

                            Text(
                                text = "Pass unavailable",
                                color = Color(0xFF334155),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PassStatus(
    uiState: PickupPassUiState
) {
    val hasQr = uiState.qrBitmap != null
    val expired = hasQr && uiState.secondsRemaining <= 0L
    val urgent =
        hasQr &&
            !expired &&
            uiState.secondsRemaining <= URGENT_THRESHOLD_SECONDS

    val containerColor = when {
        expired ->
            MaterialTheme.colorScheme.errorContainer

        urgent ->
            Amber500.copy(alpha = 0.14f)

        hasQr ->
            MaterialTheme.colorScheme.primaryContainer

        else ->
            MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        expired ->
            MaterialTheme.colorScheme.onErrorContainer

        urgent ->
            Amber900

        hasQr ->
            MaterialTheme.colorScheme.onPrimaryContainer

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    val title = when {
        uiState.isLoading ->
            "Generating a secure pass"

        expired ->
            "This pass has expired"

        urgent ->
            "Pass expires soon"

        hasQr ->
            "Ready to scan"

        else ->
            "Pass unavailable"
    }

    val detail = when {
        uiState.isLoading ->
            "Please wait while PickupPass requests a fresh, time-limited token."

        expired ->
            "Generate a new pass before presenting it to school staff."

        urgent ->
            "You can still present this pass, but a fresh pass may be easier if you are not yet at the gate."

        hasQr ->
            "Keep this screen open and present the QR when staff asks for it."

        else ->
            "Generate a new pass when you are ready for pickup."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector =
                    if (expired) {
                        Icons.Filled.ErrorOutline
                    } else {
                        Icons.Filled.VerifiedUser
                    },
                contentDescription = null,
                tint = contentColor
            )

            Spacer(Modifier.width(Spacing.sm))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f)
                )
            }

            if (hasQr && !uiState.isLoading) {
                Spacer(Modifier.width(Spacing.sm))

                Text(
                    text = formatCountdown(uiState.secondsRemaining),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun PickupPolicyCard(
    policyText: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
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
                    text = "School pickup policy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = policyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WhatHappensNext() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = "What happens at pickup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.md))

            PickupStep(
                number = "1",
                title = "Staff scans the pass",
                detail = "The QR is checked against the current server-issued token."
            )

            PickupStep(
                number = "2",
                title = "Guardian identity is verified",
                detail = "Staff compares the person present with the authorized guardian profile."
            )

            PickupStep(
                number = "3",
                title = "Student release is approved",
                detail = "The student is released only after staff confirms the handoff."
            )
        }
    }
}

@Composable
private fun PickupStep(
    number: String,
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier.padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(Modifier.width(Spacing.sm))

            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(Modifier.width(Spacing.sm))

            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun formatCountdown(
    secondsRemaining: Long
): String {
    val safe = secondsRemaining.coerceAtLeast(0L)
    val minutes = safe / 60L
    val seconds = safe % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun initials(
    value: String
): String =
    value
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

private fun schoolInitials(
    value: String
): String =
    initials(value).ifBlank { "PP" }
