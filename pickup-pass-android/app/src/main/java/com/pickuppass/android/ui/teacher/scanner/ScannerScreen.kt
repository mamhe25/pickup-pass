package com.pickuppass.android.ui.teacher.scanner

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.GuardianAvatar
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Gray300
import com.pickuppass.android.ui.theme.Gray400
import com.pickuppass.android.ui.theme.Gray800
import com.pickuppass.android.ui.theme.Gray900
import com.pickuppass.android.ui.theme.Spacing

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onGoToStudents: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onGoToNotifications: () -> Unit,
    onGoToBroadcast: () -> Unit,
    onGoToOperations: () -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val school by viewModel.school.collectAsStateWithLifecycle()
    val pickupGates by viewModel.pickupGates.collectAsStateWithLifecycle()
    val selectedPickupGate by viewModel.selectedPickupGate.collectAsStateWithLifecycle()
    val gateLoading by viewModel.gateLoading.collectAsStateWithLifecycle()
    val gateError by viewModel.gateError.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(signedOut) {
        if (signedOut) onSignOut()
    }

    val screenTitle = when (uiState) {
        is ScannerUiState.Verifying -> "Verifying Pass"
        is ScannerUiState.Verified -> "Verify Release"
        is ScannerUiState.Approved -> "Release Complete"
        else -> "Dismissal Scanner"
    }

    Scaffold(
        containerColor = Gray900,
        topBar = {
            TopAppBar(
                title = {
                    BrandedTitle(
                        screenTitle,
                        school,
                        titleColor = Color.White,
                        subtitleColor = Gray400
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Gray900
                ),
                actions = {
                    if (uiState is ScannerUiState.Scanning) {
                        IconButton(onClick = onGoToStudents) {
                            Icon(
                                Icons.Filled.People,
                                contentDescription = "Students",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onGoToExitLogs) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = "Dismissal History",
                                tint = Color.White
                            )
                        }

                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Notifications") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Notifications, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onGoToNotifications()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pickup Operations") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onGoToOperations()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send Announcement") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Campaign, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onGoToBroadcast()
                                }
                            )
                        }
                        IconButton(onClick = viewModel::signOut) {
                            Icon(
                                Icons.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = Color.White
                            )
                        }
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
            when {
                !cameraPermission.status.isGranted -> {
                    CameraPermissionRequest(
                        shouldShowRationale = cameraPermission.status.shouldShowRationale,
                        onRequest = { cameraPermission.launchPermissionRequest() }
                    )
                }

                uiState is ScannerUiState.Approved -> {
                    ApprovedOverlay(
                        gateLabel = (uiState as ScannerUiState.Approved).gateLabel,
                        onDone = viewModel::resetToScanning
                    )
                }

                else -> {
                    ScanAndVerifyContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        pickupGates = pickupGates,
                        selectedPickupGate = selectedPickupGate,
                        gateLoading = gateLoading,
                        gateError = gateError
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanAndVerifyContent(
    uiState: ScannerUiState,
    viewModel: ScannerViewModel,
    pickupGates: List<PickupGateItem>,
    selectedPickupGate: PickupGateItem?,
    gateLoading: Boolean,
    gateError: String?
) {
    val gateReady =
        !gateLoading &&
            gateError == null &&
            (pickupGates.isEmpty() || selectedPickupGate != null)

    when (uiState) {
        is ScannerUiState.Scanning -> {
            Box(modifier = Modifier.fillMaxSize()) {
                QrScannerView(
                    paused = !gateReady,
                    onQrDetected = viewModel::onQrCodeScanned
                )

                ScannerTopChrome(
                    gates = pickupGates,
                    selected = selectedPickupGate,
                    loading = gateLoading,
                    error = gateError,
                    onSelect = viewModel::selectPickupGate,
                    onRetry = viewModel::loadPickupGates,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                ScanningOverlay(
                    gateReady = gateReady,
                    selectedGateLabel = selectedPickupGate?.displayName.orEmpty(),
                    hasConfiguredGates = pickupGates.isNotEmpty()
                )
            }
        }

        is ScannerUiState.Verifying -> {
            VerificationProgressContent()
        }

        is ScannerUiState.Verified -> {
            VerifiedReviewContent(
                state = uiState,
                selectedPickupGate = selectedPickupGate,
                viewModel = viewModel
            )
            if (uiState.isApproving) {
                RequestOverlay("Recording release…")
            }
        }

        is ScannerUiState.Error -> {
            ErrorPanel(
                message = uiState.message,
                onDismiss = viewModel::resetToScanning
            )
        }

        is ScannerUiState.Approved -> Unit
    }
}

@Composable
private fun VerificationProgressContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray900),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            shape = MaterialTheme.shapes.extraLarge,
            color = Gray800,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Checking pickup pass",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Confirming pass validity and guardian authorization…",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerTopChrome(
    gates: List<PickupGateItem>,
    selected: PickupGateItem?,
    loading: Boolean,
    error: String?,
    onSelect: (PickupGateItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = 8.dp),
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = 10.dp
            )
        ) {
            when {
                loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Text(
                            "Loading pickup gates…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                error != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }

                gates.isEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Scanner ready",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                gates.size == 1 -> GateSummary(selected ?: gates.first())

                else -> {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selected?.displayName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Pickup gate") },
                            placeholder = { Text("Select pickup gate") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = Gray400,
                                focusedLabelColor = Gray300,
                                unfocusedLabelColor = Gray300,
                                focusedPlaceholderColor = Gray400,
                                unfocusedPlaceholderColor = Gray400
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            gates.forEach { gate ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(gate.name)
                                            if (gate.campusName.isNotBlank()) {
                                                Text(
                                                    gate.campusName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelect(gate)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GateSummary(gate: PickupGateItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PICKUP GATE",
                color = Gray400,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = gate.displayName,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
        ) {
            Text(
                text = "Ready",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScanningOverlay(
    gateReady: Boolean,
    selectedGateLabel: String,
    hasConfiguredGates: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            color = Color.Black.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = Spacing.md,
                    vertical = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = if (gateReady) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (gateReady) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                            contentDescription = null,
                            tint = if (gateReady) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (gateReady) "Ready to scan" else "Select a pickup gate",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            hasConfiguredGates && selectedGateLabel.isNotBlank() -> selectedGateLabel
                            gateReady -> "Hold the QR inside the camera frame."
                            else -> "Scanning is paused."
                        },
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestOverlay(message: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Gray800,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                        vertical = Spacing.md
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        message,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifiedReviewContent(
    state: ScannerUiState.Verified,
    selectedPickupGate: PickupGateItem?,
    viewModel: ScannerViewModel
) {
    var showGuardianPhoto by remember(state.guardian.photoUrl) {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray900)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        VerificationHeader(selectedPickupGate)
        Spacer(Modifier.height(10.dp))

        GuardianIdentityCard(
            state = state,
            onPhotoClick = { showGuardianPhoto = true }
        )

        Spacer(Modifier.height(8.dp))
        StudentIdentityCard(state)

        if (!state.guardianPhotoReady) {
            Spacer(Modifier.height(8.dp))
            MissingPhotoNotice()
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = viewModel::resetToScanning,
                enabled = !state.isApproving,
                modifier = Modifier
                    .weight(0.38f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Gray300
                )
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = viewModel::approveRelease,
                enabled = state.guardianPhotoReady && !state.isApproving,
                modifier = Modifier
                    .weight(0.62f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (state.guardianPhotoReady) {
                        "Release student"
                    } else {
                        "Release blocked"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }

    if (showGuardianPhoto && state.guardianPhotoReady) {
        GuardianPhotoPreviewDialog(
            photoUrl = state.guardian.photoUrl,
            guardianName = state.guardian.displayName,
            onDismiss = { showGuardianPhoto = false }
        )
    }
}

@Composable
private fun VerificationHeader(selectedPickupGate: PickupGateItem?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.White.copy(alpha = 0.055f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirm guardian",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Match the photo to the person present.",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "QR VERIFIED",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            selectedPickupGate?.let { gate ->
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "Release at ${gate.displayName}",
                    color = Gray400,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GuardianIdentityCard(
    state: ScannerUiState.Verified,
    onPhotoClick: () -> Unit
) {
    val canInspect =
        state.guardianPhotoReady && !state.guardian.photoUrl.isNullOrBlank()
    val guardianEntry = state.student.guardians[state.guardian.uid]
    val relationship = guardianEntry
        ?.relationship
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
        ?: "Guardian"
    val relationshipLabel = if (guardianEntry?.isPrimary == true) {
        "$relationship · Primary"
    } else {
        relationship
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.White.copy(alpha = 0.065f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = canInspect,
                    onClick = onPhotoClick
                ),
                contentAlignment = Alignment.BottomEnd
            ) {
                GuardianAvatar(
                    photoUrl = state.guardian.photoUrl,
                    size = 78.dp
                )
                if (canInspect) {
                    Surface(
                        modifier = Modifier
                            .padding(1.dp)
                            .size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.ZoomIn,
                                contentDescription = "Open guardian photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AUTHORIZED GUARDIAN",
                    color = Gray400,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.guardian.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = relationshipLabel,
                    color = Gray300,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (state.guardianPhotoReady) {
                    Icons.Filled.VerifiedUser
                } else {
                    Icons.Filled.Security
                },
                contentDescription = null,
                tint = if (state.guardianPhotoReady) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StudentIdentityCard(state: ScannerUiState.Verified) {
    val gradeSection = buildString {
        if (state.student.grade.isNotBlank()) append("Grade ${state.student.grade}")
        if (state.student.section.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append("Section ${state.student.section}")
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = 0.20f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STUDENT",
                    color = Gray400,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = state.student.fullName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (gradeSection.isNotBlank()) {
                    Text(
                        text = gradeSection,
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.student.studentNumber.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = state.student.studentNumber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Gray300,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingPhotoNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Guardian photo unavailable · use manual identity verification.",
                color = MaterialTheme.colorScheme.errorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GuardianPhotoPreviewDialog(
    photoUrl: String?,
    guardianName: String,
    onDismiss: () -> Unit
) {
    var scale by remember(photoUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(photoUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(photoUrl) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        if (nextScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm)
                .statusBarsPadding()
                .navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = Gray900,
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GUARDIAN PHOTO",
                            color = Gray400,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = guardianName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close guardian photo",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Gray800)
                        .transformable(transformState),
                    contentAlignment = Alignment.Center
                ) {
                    SmartImage(
                        model = photoUrl,
                        contentDescription = "Enlarged guardian photo for $guardianName",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinch to zoom · drag while zoomed",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (scale > 1.01f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

private data class ScanErrorPresentation(
    val eyebrow: String,
    val title: String,
    val detail: String,
    val guidance: String
)

private fun scanErrorPresentation(message: String): ScanErrorPresentation {
    val normalized = message.lowercase()
    return when {
        normalized.contains("already been dismissed today") -> {
            ScanErrorPresentation(
                eyebrow = "RELEASE ALREADY RECORDED",
                title = "Student already released",
                detail = "This student has already been dismissed today. No additional release is allowed.",
                guidance = "Check Dismissal History if you need to confirm the earlier release."
            )
        }

        normalized.contains("already used") ||
            normalized.contains("superseded") ||
            normalized.contains("replaced") -> {
            ScanErrorPresentation(
                eyebrow = "PASS NO LONGER ACTIVE",
                title = "Pass already used",
                detail = "This pickup pass has already been used or replaced by a newer pass.",
                guidance = "If the student was already released, do not release them again. Otherwise ask for the latest active pass."
            )
        }

        normalized.contains("expired") ||
            normalized.contains("dismissal window") -> {
            ScanErrorPresentation(
                eyebrow = "PASS EXPIRED",
                title = "Pickup pass expired",
                detail = "This pass is outside its allowed pickup window and can no longer be used.",
                guidance = "Ask the guardian to present a current active pickup pass."
            )
        }

        normalized.contains("does not belong to this school") -> {
            ScanErrorPresentation(
                eyebrow = "WRONG SCHOOL",
                title = "Pass not valid here",
                detail = "This pickup pass belongs to a different school.",
                guidance = "Confirm the student and school before scanning another pass."
            )
        }

        normalized.contains("unknown or revoked") ||
            normalized.contains("revoked token") -> {
            ScanErrorPresentation(
                eyebrow = "PASS REVOKED",
                title = "Pass is no longer valid",
                detail = "This pickup pass has been revoked and cannot authorize a release.",
                guidance = "Ask the guardian to present a new active pass."
            )
        }

        normalized.contains("guardian") &&
            (normalized.contains("not authorized") ||
                normalized.contains("suspended") ||
                normalized.contains("revoked")) -> {
            ScanErrorPresentation(
                eyebrow = "GUARDIAN NOT AUTHORIZED",
                title = "Release blocked",
                detail = message,
                guidance = "Do not release the student. Follow the school's identity-verification process."
            )
        }

        normalized.contains("timeout") ||
            normalized.contains("network") ||
            normalized.contains("reachable") ||
            normalized.contains("could not verify qr code") -> {
            ScanErrorPresentation(
                eyebrow = "VERIFICATION UNAVAILABLE",
                title = "Could not verify pass",
                detail = "PickupPass could not confirm this pass right now.",
                guidance = "Check connectivity and try again. Keep the student with staff until verification succeeds."
            )
        }

        else -> {
            ScanErrorPresentation(
                eyebrow = "PASS NOT VERIFIED",
                title = "Release blocked",
                detail = message,
                guidance = "Do not release the student until the pass and guardian are verified."
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onDismiss: () -> Unit
) {
    val presentation = remember(message) { scanErrorPresentation(message) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray900),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            shape = MaterialTheme.shapes.extraLarge,
            color = Gray800,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = presentation.eyebrow,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = presentation.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = presentation.detail,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(Spacing.sm))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = Color.White.copy(alpha = 0.055f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = Gray300,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = presentation.guidance,
                            color = Gray300,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        "Back to scanner",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovedOverlay(
    gateLabel: String,
    onDone: () -> Unit
) {
    val scale = remember { Animatable(0.4f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
        )
        kotlinx.coroutines.delay(1500)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Gray800,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 38.dp,
                    vertical = 30.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(78.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Release Logged",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Student handoff recorded successfully.",
                    color = Gray300,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                if (gateLabel.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        gateLabel,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(
    shouldShowRationale: Boolean,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            "Camera access is required",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (shouldShowRationale) {
                "Allow camera access in Android settings so staff can scan secure pickup passes."
            } else {
                "PickupPass uses the camera only to scan secure pickup QR codes during dismissal."
            },
            color = Gray400,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = "Grant Camera Access",
            onClick = onRequest
        )
    }
}
