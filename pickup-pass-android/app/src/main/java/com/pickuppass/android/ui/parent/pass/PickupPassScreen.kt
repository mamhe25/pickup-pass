package com.pickuppass.android.ui.parent.pass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Amber500
import com.pickuppass.android.ui.theme.Amber900
import com.pickuppass.android.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val URGENT_THRESHOLD_SECONDS = 60L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupPassScreen(
    studentId: String,
    viewModel: PickupPassViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExpandedQr by remember { mutableStateOf(false) }

    LaunchedEffect(studentId) {
        viewModel.initialize(studentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pickup Pass", fontWeight = FontWeight.ExtraBold)
                        if (uiState.studentName.isNotBlank()) {
                            Text(
                                uiState.studentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 660.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.md,
                        top = Spacing.sm,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PassCredential(
                    uiState = uiState,
                    onExpandQr = {
                        if (
                            uiState.qrBitmap != null &&
                            uiState.secondsRemaining > 0 &&
                            !uiState.isLoading
                        ) {
                            showExpandedQr = true
                        }
                    }
                )

                Spacer(Modifier.height(Spacing.md))

                PassStatus(uiState)

                uiState.error?.let {
                    Spacer(Modifier.height(Spacing.md))
                    ErrorBanner(it)
                }

                Spacer(Modifier.height(Spacing.md))

                PickupPolicyCard(uiState.pickupPolicyText)

                Spacer(Modifier.height(Spacing.md))

                WhatHappensNext()

                Spacer(Modifier.height(Spacing.lg))

                Button(
                    onClick = { viewModel.generatePass(studentId) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(
                        horizontal = 20.dp,
                        vertical = 11.dp
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
                            if (
                                uiState.qrBitmap != null &&
                                uiState.secondsRemaining <= 0
                            ) {
                                "Generate new pass"
                            } else {
                                "Regenerate pass"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Text(
                    "Only the latest valid pass should be presented. Regenerating invalidates the previous pass.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showExpandedQr) {
        ExpandedQrDialog(
            uiState = uiState,
            onDismiss = { showExpandedQr = false }
        )
    }
}

@Composable
private fun PassCredential(
    uiState: PickupPassUiState,
    onExpandQr: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    SchoolIdentity(uiState)

                    Spacer(Modifier.height(Spacing.lg))

                    Text(
                        "SECURE PICKUP PASS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f)
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Ready for a verified handoff.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Present this screen only when authorized school staff asks to scan the pass.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    )
                }
            }

            Column(Modifier.padding(Spacing.md)) {
                StudentIdentity(uiState)

                Spacer(Modifier.height(Spacing.lg))

                QrSection(
                    uiState = uiState,
                    onExpandQr = onExpandQr
                )
            }
        }
    }
}

@Composable
private fun SchoolIdentity(uiState: PickupPassUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Box(contentAlignment = Alignment.Center) {
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
                        schoolInitials(uiState.schoolName),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(Modifier.weight(1f)) {
            Text(
                "PickupPass",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.62f)
            )
            Text(
                uiState.schoolName.ifBlank { "Your school" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudentIdentity(uiState: PickupPassUiState) {
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

        Column(Modifier.weight(1f)) {
            Text(
                "STUDENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                uiState.studentName.ifBlank { "Loading student…" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Grade ${uiState.studentGrade.ifBlank { "—" }} · Section ${
                    uiState.studentSection.ifBlank { "—" }
                }",
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
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initials,
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
    uiState: PickupPassUiState,
    onExpandQr: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(
                        enabled = uiState.qrBitmap != null &&
                            uiState.secondsRemaining > 0 &&
                            !uiState.isLoading,
                        onClick = onExpandQr
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isLoading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Generating secure pass…",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    uiState.qrBitmap != null -> {
                        Image(
                            bitmap = uiState.qrBitmap.asImageBitmap(),
                            contentDescription = "Secure pickup QR code",
                            modifier = Modifier.fillMaxSize()
                        )

                        if (uiState.secondsRemaining <= 0L) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.96f),
                                border = BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    "EXPIRED",
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.94f),
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    Icons.Filled.OpenInFull,
                                    contentDescription = "Enlarge QR code",
                                    tint = Color(0xFF334155),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Pass unavailable",
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassStatus(uiState: PickupPassUiState) {
    val hasQr = uiState.qrBitmap != null
    val expired = hasQr && uiState.secondsRemaining <= 0L
    val urgent = hasQr && !expired &&
        uiState.secondsRemaining <= URGENT_THRESHOLD_SECONDS

    val containerColor = when {
        expired -> MaterialTheme.colorScheme.errorContainer
        urgent -> Amber500.copy(alpha = 0.14f)
        hasQr -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        expired -> MaterialTheme.colorScheme.onErrorContainer
        urgent -> Amber900
        hasQr -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val title = when {
        uiState.isLoading -> "Generating a secure pass"
        expired -> "This pass has expired"
        urgent -> "Pass expires soon"
        hasQr -> "Ready to scan"
        else -> "Pass unavailable"
    }

    val detail = when {
        uiState.isLoading ->
            "PickupPass is requesting a fresh time-limited token."

        expired ->
            "Generate a new pass before presenting it to school staff."

        urgent ->
            "Present it now, or regenerate if you are not yet at the pickup point."

        hasQr ->
            "Keep this screen open until authorized staff scans it."

        else ->
            "Generate a new pass when you are ready for pickup."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        expired -> Icons.Filled.ErrorOutline
                        hasQr -> Icons.Filled.VerifiedUser
                        else -> Icons.Filled.Security
                    },
                    contentDescription = null,
                    tint = contentColor
                )

                Spacer(Modifier.width(Spacing.sm))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f)
                    )
                }

                if (hasQr) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        formatCountdown(uiState.secondsRemaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                }
            }

            if (hasQr && !expired) {
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = {
                        val total = uiState.validityWindowSeconds.coerceAtLeast(1L)
                        (uiState.secondsRemaining.toFloat() / total.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor
                )
            }

            uiState.expiresAt?.let { expiry ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Expires ${formatExpiry(expiry)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun PickupPolicyCard(policyText: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    "School pickup policy",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    policyText,
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
        Column(Modifier.padding(Spacing.md)) {
            Text(
                "What happens at pickup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.sm))

            StepRow("1", "Present the latest pass when school staff asks.")
            StepRow("2", "Staff scans the QR and verifies the authorized guardian.")
            StepRow("3", "The student is released only after the school approves the handoff.")
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
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
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandedQrDialog(
    uiState: PickupPassUiState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiState.studentName.ifBlank { "Pickup pass" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            "${formatCountdown(uiState.secondsRemaining)} remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF334155)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                uiState.qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Enlarged pickup QR code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                Text(
                    "Keep the full QR visible and steady for the scanner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatCountdown(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60, safe % 60)
}

private fun formatExpiry(date: Date): String =
    SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(date)

private fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

private fun schoolInitials(name: String): String =
    initials(name).ifBlank { "PP" }
