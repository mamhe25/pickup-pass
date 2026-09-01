package com.pickuppass.android.ui.teacher.scanner

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.PrimaryButton
import com.pickuppass.android.ui.theme.Gray300
import com.pickuppass.android.ui.theme.Gray400
import com.pickuppass.android.ui.theme.Gray800
import com.pickuppass.android.ui.theme.Gray900
import com.pickuppass.android.ui.theme.Spacing

private val ScannerGreen = Color(0xFF34D399)
private val ScannerGreenDark = Color(0xFF047857)
private val ScannerAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onGoToStudents: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onGoToNotifications: () -> Unit,
    onGoToBroadcast: () -> Unit,
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

    Scaffold(
        containerColor = Gray900,
        topBar = {
            TopAppBar(
                title = {
                    BrandedTitle(
                        "Dismissal Scanner",
                        school,
                        titleColor = Color.White,
                        subtitleColor = Gray400
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Gray900),
                actions = {
                    IconButton(onClick = onGoToStudents) {
                        Icon(Icons.Filled.People, contentDescription = "Students", tint = Color.White)
                    }
                    IconButton(onClick = onGoToExitLogs) {
                        Icon(Icons.Filled.History, contentDescription = "Dismissal History", tint = Color.White)
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Notifications") },
                            leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onGoToNotifications()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send Announcement") },
                            leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onGoToBroadcast()
                            }
                        )
                    }

                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = Color.White
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
            when {
                !cameraPermission.status.isGranted -> CameraPermissionRequest(
                    shouldShowRationale = cameraPermission.status.shouldShowRationale,
                    onRequest = { cameraPermission.launchPermissionRequest() }
                )

                uiState is ScannerUiState.Approved -> ApprovedOverlay(
                    gateLabel = (uiState as ScannerUiState.Approved).gateLabel,
                    onDone = viewModel::resetToScanning
                )

                else -> ScanAndVerifyContent(
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

    Box(Modifier.fillMaxSize()) {
        QrScannerView(
            paused = uiState !is ScannerUiState.Scanning || !gateReady,
            onQrDetected = viewModel::onQrCodeScanned
        )

        when (uiState) {
            is ScannerUiState.Scanning -> {
                ScannerChrome(
                    pickupGates = pickupGates,
                    selectedPickupGate = selectedPickupGate,
                    gateLoading = gateLoading,
                    gateError = gateError,
                    gateReady = gateReady,
                    onSelectGate = viewModel::selectPickupGate,
                    onRetryGates = viewModel::loadPickupGates
                )
            }

            is ScannerUiState.Verifying -> {
                RequestOverlay("Verifying pass…")
            }

            is ScannerUiState.Verified -> {
                VerifiedIdentityScreen(
                    state = uiState,
                    selectedPickupGate = selectedPickupGate,
                    onApprove = viewModel::approveRelease,
                    onCancel = viewModel::resetToScanning
                )
                if (uiState.isApproving) {
                    RequestOverlay("Approving release…")
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
}

@Composable
private fun BoxScope.ScannerChrome(
    pickupGates: List<PickupGateItem>,
    selectedPickupGate: PickupGateItem?,
    gateLoading: Boolean,
    gateError: String?,
    gateReady: Boolean,
    onSelectGate: (PickupGateItem) -> Unit,
    onRetryGates: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
    ) {
        FlowProgress(activeStep = 1)

        GateSelectorBar(
            gates = pickupGates,
            selected = selectedPickupGate,
            loading = gateLoading,
            error = gateError,
            onSelect = onSelectGate,
            onRetry = onRetryGates
        )
    }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(280.dp)
            .border(
                width = 3.dp,
                color = if (gateReady) ScannerGreen else Gray400,
                shape = RoundedCornerShape(22.dp)
            )
    )

    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.74f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (gateReady) {
                    "Point the camera at the parent's live pass"
                } else {
                    "Select a pickup gate before scanning"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (selectedPickupGate != null) {
                    "Release location: ${selectedPickupGate.displayName}"
                } else if (gateReady) {
                    "Keep the QR inside the frame. Verification starts automatically."
                } else {
                    "The release location must be recorded before continuing."
                },
                color = Gray300,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlowProgress(activeStep: Int) {
    Surface(
        color = Color.Black.copy(alpha = 0.74f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ProgressStep(1, "Scan", activeStep, Modifier.weight(1f))
            ProgressStep(2, "Verify", activeStep, Modifier.weight(1f))
            ProgressStep(3, "Release", activeStep, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgressStep(
    step: Int,
    label: String,
    activeStep: Int,
    modifier: Modifier = Modifier
) {
    val active = step == activeStep
    val done = step < activeStep

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = when {
            active -> ScannerGreenDark.copy(alpha = 0.42f)
            done -> Color.White.copy(alpha = 0.09f)
            else -> Color.Black.copy(alpha = 0.12f)
        },
        border = if (active) BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.38f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = if (active || done) ScannerGreenDark else Gray800
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        step.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Text(
                label,
                color = if (active) Color.White else Gray300,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateSelectorBar(
    gates: List<PickupGateItem>,
    selected: PickupGateItem?,
    loading: Boolean,
    error: String?,
    onSelect: (PickupGateItem) -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.74f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            when {
                loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ScannerGreen
                        )
                        Text("Loading pickup gates…", color = Color.White)
                    }
                }

                error != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }

                gates.isEmpty() -> {
                    Text(
                        "No pickup gate configured · normal scanner mode",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                gates.size == 1 -> {
                    Text(
                        "Release location: ${gates.first().displayName}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

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
                            label = { Text("Release location") },
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
                                focusedBorderColor = ScannerGreen,
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
private fun BoxScope.VerifiedIdentityScreen(
    state: ScannerUiState.Verified,
    selectedPickupGate: PickupGateItem?,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    val relationship =
        state.student.guardians[state.guardian.uid]?.relationship.orEmpty()
    val photoAvailable = !state.guardian.photoUrl.isNullOrBlank()

    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize(),
        color = Gray900
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            FlowProgress(activeStep = 2)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ScannerGreenDark.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = ScannerGreen,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "PASS VERIFIED BY PICKUPPASS",
                            color = Color(0xFFA7F3D0),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Text(
                    "Verify the person in front of you",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    "The pass is valid. Compare the person present with the " +
                        "authorized guardian profile before release.",
                    color = Gray300,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    IdentityStudentCard(
                        state = state,
                        modifier = Modifier.weight(0.9f)
                    )
                    IdentityGuardianCard(
                        state = state,
                        relationship = relationship,
                        photoAvailable = photoAvailable,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Spacer(Modifier.height(Spacing.md))

                if (photoAvailable) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ScannerGreenDark.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.18f))
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Does the person present match this authorized guardian?",
                                color = Color(0xFFECFDF5),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Approve only after you have personally confirmed the identity.",
                                color = Color(0xFF9EC8B8),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ScannerAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ScannerAmber.copy(alpha = 0.32f))
                    ) {
                        Text(
                            "Guardian identity photo is unavailable. Do not approve a QR release. " +
                                "Use the school's manual identity-verification process instead.",
                            modifier = Modifier.padding(Spacing.md),
                            color = Color(0xFFFDE68A),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                selectedPickupGate?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Release location: ${it.displayName}",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = onApprove,
                    enabled = photoAvailable && !state.isApproving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerGreenDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Yes, identity matches — approve release",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.isApproving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, Gray400),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel / scan another pass")
                }
            }
        }
    }
}

@Composable
private fun IdentityStudentCard(
    state: ScannerUiState.Verified,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 206.dp),
        color = Gray800,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF344252))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            StudentAvatar(
                photoUrl = state.student.photoUrl,
                name = state.student.fullName
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                "STUDENT",
                color = Gray400,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                state.student.fullName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                studentMeta(state.student.grade, state.student.section),
                color = Gray300,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun IdentityGuardianCard(
    state: ScannerUiState.Verified,
    relationship: String,
    photoAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 206.dp),
        color = Gray800,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (photoAvailable) ScannerGreen.copy(alpha = 0.28f)
            else ScannerAmber.copy(alpha = 0.38f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "AUTHORIZED GUARDIAN",
                color = Gray400,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(Spacing.sm))

            Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = Color(0xFF15222D),
                border = BorderStroke(
                    3.dp,
                    if (photoAvailable) ScannerGreen else ScannerAmber
                )
            ) {
                if (photoAvailable) {
                    AsyncImage(
                        model = state.guardian.photoUrl,
                        contentDescription = "${state.guardian.displayName} guardian profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initialsFor(state.guardian.displayName),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                state.guardian.displayName.ifBlank { "Authorized guardian" },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Text(
                relationship.ifBlank { "Authorized for this student" },
                color = Gray300,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StudentAvatar(photoUrl: String?, name: String) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = Color(0xFF15222D),
        border = BorderStroke(2.dp, ScannerGreen.copy(alpha = 0.28f))
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name student profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initialsFor(name),
                    color = Color(0xFFD1FAE5),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RequestOverlay(message: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = ScannerGreen)
            Spacer(Modifier.height(Spacing.md))
            Text(
                message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BoxScope.ErrorPanel(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Pass could not be verified") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Scan again")
            }
        }
    )
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
        kotlinx.coroutines.delay(1400)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06110C)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(94.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                shape = CircleShape,
                color = ScannerGreenDark.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.28f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = ScannerGreen,
                        modifier = Modifier.size(62.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                "Release logged",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (gateLabel.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    gateLabel,
                    color = Color(0xFF9EC8B8),
                    style = MaterialTheme.typography.bodyMedium
                )
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
        Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = ScannerGreen,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Camera access is needed to scan pickup passes.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (shouldShowRationale) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Allow camera access in Settings to use the dismissal scanner.",
                color = Gray400,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = "Grant Camera Access",
            onClick = onRequest
        )
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

    return listOf(normalizedGrade, normalizedSection)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}