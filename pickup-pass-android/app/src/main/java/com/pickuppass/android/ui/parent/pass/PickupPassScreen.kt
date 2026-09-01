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
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton
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

    val hasPass = uiState.qrBitmap != null
    val expired = hasPass && uiState.secondsRemaining <= 0L
    val urgent = hasPass && !expired && uiState.secondsRemaining <= URGENT_THRESHOLD_SECONDS

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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PickupCredential(uiState, expired, urgent)

                uiState.error?.let {
                    Spacer(Modifier.height(Spacing.md))
                    ErrorBanner(it)
                }

                Spacer(Modifier.height(Spacing.md))

                PrimaryButton(
                    text = when {
                        uiState.isLoading -> "Generating pass…"
                        expired -> "Generate new pass"
                        uiState.error != null -> "Try again"
                        else -> "Refresh secure pass"
                    },
                    onClick = { viewModel.generatePass(studentId) },
                    loading = uiState.isLoading
                )

                Spacer(Modifier.height(Spacing.lg))
                WhatHappensNext()
                Spacer(Modifier.height(Spacing.md))

                Text(
                    "For your security, use only the latest pass shown in PickupPass. " +
                        "Do not share screenshots of the QR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )

                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun PickupCredential(
    uiState: PickupPassUiState,
    expired: Boolean,
    urgent: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        shadowElevation = 6.dp
    ) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    SchoolIdentity(uiState.schoolName, uiState.schoolLogoUrl)
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        "Present this pass at pickup",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "School staff will scan the QR and verify the authorized guardian before release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StudentIdentity(uiState)
                Spacer(Modifier.height(Spacing.lg))
                QrSurface(uiState, expired)

                if (uiState.qrBitmap != null) {
                    Spacer(Modifier.height(Spacing.md))
                    PassStatus(uiState.secondsRemaining, expired, urgent)
                }

                Spacer(Modifier.height(Spacing.md))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            uiState.pickupPolicyText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolIdentity(schoolName: String, logoUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.95f)
        ) {
            if (!logoUrl.isNullOrBlank()) {
                SmartImage(
                    model = logoUrl,
                    contentDescription = "School logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(5.dp)
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "SECURE PICKUP PASS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
            )
            Text(
                schoolName.ifBlank { "PickupPass School" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudentIdentity(uiState: PickupPassUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            ) {
                if (!uiState.studentPhotoUrl.isNullOrBlank()) {
                    SmartImage(
                        model = uiState.studentPhotoUrl,
                        contentDescription = "${uiState.studentName} profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initialsFor(uiState.studentName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "STUDENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    uiState.studentName.ifBlank { "Loading student…" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val meta = studentMeta(uiState.studentGrade, uiState.studentSection)
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QrSurface(uiState: PickupPassUiState, expired: Boolean) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val qrSize = minOf(maxWidth, 330.dp)

        Surface(
            modifier = Modifier.size(qrSize),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 3.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val phase = when {
                    uiState.isLoading -> "loading"
                    uiState.qrBitmap != null -> "qr"
                    uiState.error != null -> "error"
                    else -> "empty"
                }

                Crossfade(
                    targetState = phase,
                    animationSpec = tween(durationMillis = 240),
                    label = "pickupPassQrPhase"
                ) { state ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (state) {
                            "loading" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    "Generating secure pass…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                            "qr" -> uiState.qrBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Secure pickup QR code",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(Spacing.md)
                                        .graphicsLayer(alpha = if (expired) 0.24f else 1f)
                                )
                            }
                            "error" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    "Pass unavailable",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }

                if (expired && uiState.qrBitmap != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            "EXPIRED",
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassStatus(secondsRemaining: Long, expired: Boolean, urgent: Boolean) {
    val minutes = secondsRemaining / 60L
    val seconds = secondsRemaining % 60L
    val time = "%02d:%02d".format(minutes, seconds)

    val containerColor: Color
    val contentColor: Color
    val title: String
    val detail: String

    when {
        expired -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            title = "Pass expired"
            detail = "Generate a new pass before approaching school staff."
        }
        urgent -> {
            containerColor = Amber500.copy(alpha = 0.16f)
            contentColor = Amber900
            title = "Expires soon"
            detail = "Keep this screen open or refresh before presenting it."
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            title = "Ready for staff verification"
            detail = "Present this live QR when you reach the pickup point."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = contentColor)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.78f))
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = contentColor)
        }
    }
}

@Composable
private fun WhatHappensNext() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "AT THE GATE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("What happens next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.md))
            StepRow("1", "Staff scans this QR", "PickupPass validates the pass before any release action.")
            Spacer(Modifier.height(Spacing.md))
            StepRow("2", "Guardian identity is checked", "Staff confirms the person present is an authorized guardian.")
            Spacer(Modifier.height(Spacing.md))
            StepRow("3", "Student release is approved", "The pickup is recorded only after staff completes verification.")
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun initialsFor(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "PP" }

private fun studentMeta(grade: String, section: String): String {
    val normalizedGrade = grade.trim().let {
        when {
            it.isBlank() -> ""
            it.startsWith("Grade", ignoreCase = true) -> it
            else -> "Grade $it"
        }
    }
    val normalizedSection = section.trim().let {
        if (it.isBlank()) "" else "Section $it"
    }
    return listOf(normalizedGrade, normalizedSection).filter { it.isNotBlank() }.joinToString(" • ")
}
