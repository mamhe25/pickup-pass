package com.pickuppass.android.ui.teacher.scanner

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Gray900
                ),
                actions = {
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

                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }

                    IconButton(
                        onClick = {
                            menuExpanded = true
                        }
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Notifications")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onGoToNotifications()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Pickup Operations")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onGoToOperations()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Send Announcement")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Campaign,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onGoToBroadcast()
                            }
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.signOut()
                        }
                    ) {
                        Icon(
                            Icons.Filled.Logout,
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
                !cameraPermission.status.isGranted -> {
                    CameraPermissionRequest(
                        shouldShowRationale =
                            cameraPermission.status.shouldShowRationale,
                        onRequest = {
                            cameraPermission
                                .launchPermissionRequest()
                        }
                    )
                }

                uiState is ScannerUiState.Approved -> {
                    ApprovedOverlay(
                        gateLabel =
                            (uiState as ScannerUiState.Approved)
                                .gateLabel,
                        onDone =
                            viewModel::resetToScanning
                    )
                }

                else -> {
                    ScanAndVerifyContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        pickupGates = pickupGates,
                        selectedPickupGate =
                            selectedPickupGate,
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
            (
                pickupGates.isEmpty() ||
                    selectedPickupGate != null
                )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        QrScannerView(
            paused =
                uiState !is ScannerUiState.Scanning ||
                    !gateReady,
            onQrDetected =
                viewModel::onQrCodeScanned
        )

        ScannerTopChrome(
            gates = pickupGates,
            selected = selectedPickupGate,
            loading = gateLoading,
            error = gateError,
            onSelect = viewModel::selectPickupGate,
            onRetry = viewModel::loadPickupGates,
            modifier = Modifier.align(
                Alignment.TopCenter
            )
        )

        when (uiState) {
            is ScannerUiState.Scanning -> {
                ScanningOverlay(
                    gateReady = gateReady,
                    selectedGateLabel =
                        selectedPickupGate
                            ?.displayName
                            .orEmpty(),
                    hasConfiguredGates =
                        pickupGates.isNotEmpty()
                )
            }

            is ScannerUiState.Verifying -> {
                RequestOverlay("Verifying pass…")
            }

            is ScannerUiState.Verified -> {
                VerifiedPanel(
                    state = uiState,
                    selectedPickupGate =
                        selectedPickupGate,
                    viewModel = viewModel
                )

                if (uiState.isApproving) {
                    RequestOverlay(
                        "Approving release…"
                    )
                }
            }

            is ScannerUiState.Error -> {
                ErrorPanel(
                    message = uiState.message,
                    onDismiss =
                        viewModel::resetToScanning
                )
            }

            is ScannerUiState.Approved -> Unit
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
            .padding(Spacing.sm),
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            )
        ) {
            ScannerStepHeader()

            Spacer(Modifier.height(Spacing.sm))

            when {
                loading -> {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                Spacing.sm
                            )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(
                                18.dp
                            ),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )

                        Text(
                            "Loading pickup gates…",
                            color = Color.White,
                            style =
                                MaterialTheme.typography
                                    .bodySmall
                        )
                    }
                }

                error != null -> {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                Spacing.sm
                            )
                    ) {
                        Text(
                            text = error,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .errorContainer,
                            modifier =
                                Modifier.weight(1f),
                            style =
                                MaterialTheme.typography
                                    .bodySmall
                        )

                        TextButton(
                            onClick = onRetry
                        ) {
                            Text("Retry")
                        }
                    }
                }

                gates.isEmpty() -> {
                    Text(
                        text =
                            "No pickup gate configured · normal scanner mode",
                        color = Gray300,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }

                gates.size == 1 -> {
                    GateSummary(
                        gate =
                            selected ?: gates.first()
                    )
                }

                else -> {
                    var expanded by remember {
                        mutableStateOf(false)
                    }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            expanded = !expanded
                        }
                    ) {
                        OutlinedTextField(
                            value =
                                selected
                                    ?.displayName
                                    .orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text(
                                    "Active pickup gate"
                                )
                            },
                            placeholder = {
                                Text(
                                    "Select pickup gate"
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults
                                    .TrailingIcon(
                                        expanded = expanded
                                    )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors =
                                OutlinedTextFieldDefaults
                                    .colors(
                                        focusedTextColor =
                                            Color.White,
                                        unfocusedTextColor =
                                            Color.White,
                                        focusedBorderColor =
                                            MaterialTheme
                                                .colorScheme
                                                .secondary,
                                        unfocusedBorderColor =
                                            Gray400,
                                        focusedLabelColor =
                                            Gray300,
                                        unfocusedLabelColor =
                                            Gray300,
                                        focusedPlaceholderColor =
                                            Gray400,
                                        unfocusedPlaceholderColor =
                                            Gray400
                                    )
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = {
                                expanded = false
                            }
                        ) {
                            gates.forEach { gate ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                gate.name
                                            )

                                            if (
                                                gate.campusName
                                                    .isNotBlank()
                                            ) {
                                                Text(
                                                    gate.campusName,
                                                    style =
                                                        MaterialTheme
                                                            .typography
                                                            .bodySmall,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant
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
private fun ScannerStepHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        ScannerStep(
            number = "1",
            label = "Scan",
            emphasized = true,
            modifier = Modifier.weight(1f)
        )

        ScannerStep(
            number = "2",
            label = "Verify",
            emphasized = false,
            modifier = Modifier.weight(1f)
        )

        ScannerStep(
            number = "3",
            label = "Release",
            emphasized = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScannerStep(
    number: String,
    label: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color =
            if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.08f)
            }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 6.dp
            ),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = number,
                color =
                    if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Gray300
                    },
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.width(5.dp))

            Text(
                text = label,
                color =
                    if (emphasized) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Gray300
                    },
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GateSummary(
    gate: PickupGateItem
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "ACTIVE PICKUP GATE",
                    color = Gray400,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = gate.displayName,
                    color = Color.White,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme
                        .secondary
                        .copy(alpha = 0.18f)
            ) {
                Text(
                    text = "Ready",
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 5.dp
                    ),
                    color =
                        MaterialTheme.colorScheme
                            .secondary,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
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
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color =
                        if (gateReady) {
                            MaterialTheme
                                .colorScheme
                                .primary
                                .copy(alpha = 0.24f)
                        } else {
                            MaterialTheme
                                .colorScheme
                                .error
                                .copy(alpha = 0.22f)
                        }
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            if (gateReady) {
                                Icons.Filled.VerifiedUser
                            } else {
                                Icons.Filled.Security
                            },
                            contentDescription = null,
                            tint =
                                if (gateReady) {
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .errorContainer
                                }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            if (gateReady) {
                                "Ready to scan a parent pass"
                            } else {
                                "Select a pickup gate first"
                            },
                        color = Color.White,
                        style =
                            MaterialTheme.typography
                                .titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text =
                            if (
                                hasConfiguredGates &&
                                selectedGateLabel
                                    .isNotBlank()
                            ) {
                                "Release location: $selectedGateLabel"
                            } else if (gateReady) {
                                "Hold the QR inside the camera frame."
                            } else {
                                "Scanning stays paused until a release location is selected."
                            },
                        color = Gray300,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestOverlay(
    message: String
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Gray800,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.xl,
                        vertical = Spacing.lg
                    ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color =
                            MaterialTheme
                                .colorScheme.secondary
                    )

                    Spacer(
                        Modifier.height(
                            Spacing.md
                        )
                    )

                    Text(
                        message,
                        color = Color.White,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.VerifiedPanel(
    state: ScannerUiState.Verified,
    selectedPickupGate: PickupGateItem?,
    viewModel: ScannerViewModel
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        color = Gray800,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(
                        weight = 1f,
                        fill = false
                    )
                    .heightIn(max = 460.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(Spacing.md)
            ) {
                VerifyStepHeader()

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text =
                        "Does the person present match this authorized guardian?",
                    color = Color.White,
                    style =
                        MaterialTheme.typography
                            .titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    text =
                        "Confirm the guardian's face before releasing the student. A valid QR alone is not enough.",
                    color = Gray300,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )

                Spacer(Modifier.height(Spacing.md))

                GuardianIdentityCard(state)

                Spacer(Modifier.height(Spacing.sm))

                StudentIdentityCard(state)

                selectedPickupGate?.let { gate ->
                    Spacer(
                        Modifier.height(
                            Spacing.sm
                        )
                    )

                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            MaterialTheme.shapes.large,
                        color =
                            Color.Black.copy(
                                alpha = 0.20f
                            )
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    Spacing.md
                                )
                        ) {
                            Text(
                                text =
                                    "RELEASE LOCATION",
                                color = Gray400,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    2.dp
                                )
                            )

                            Text(
                                text =
                                    gate.displayName,
                                color = Color.White,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                if (!state.guardianPhotoReady) {
                    Spacer(
                        Modifier.height(
                            Spacing.sm
                        )
                    )

                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            MaterialTheme.shapes.large,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                                .copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(
                                    Spacing.md
                                ),
                            verticalAlignment =
                                Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .errorContainer
                            )

                            Spacer(
                                Modifier.width(
                                    Spacing.sm
                                )
                            )

                            Text(
                                text =
                                    "Guardian photo is unavailable. QR release is blocked. Use the school's manual identity-verification process.",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .errorContainer,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Gray900,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    )
                ) {
                    Button(
                        onClick =
                            viewModel::approveRelease,
                        enabled =
                            state.guardianPhotoReady &&
                                !state.isApproving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .secondary,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSecondary
                            )
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null
                        )

                        Spacer(
                            Modifier.width(
                                Spacing.sm
                            )
                        )

                        Text(
                            text =
                                if (
                                    state.guardianPhotoReady
                                ) {
                                    "APPROVE RELEASE"
                                } else {
                                    "RELEASE BLOCKED"
                                },
                            fontWeight =
                                FontWeight.ExtraBold
                        )
                    }

                    TextButton(
                        onClick =
                            viewModel::resetToScanning,
                        enabled =
                            !state.isApproving,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterHorizontally
                                )
                                .heightIn(
                                    min = 44.dp
                                )
                    ) {
                        Text(
                            "Cancel / Scan Next",
                            color = Gray300
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyStepHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {
        VerifyStepChip(
            number = "1",
            label = "Scanned",
            complete = true,
            modifier = Modifier.weight(1f)
        )

        VerifyStepChip(
            number = "2",
            label = "Verify",
            active = true,
            modifier = Modifier.weight(1f)
        )

        VerifyStepChip(
            number = "3",
            label = "Release",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VerifyStepChip(
    number: String,
    label: String,
    active: Boolean = false,
    complete: Boolean = false,
    modifier: Modifier = Modifier
) {
    val container = when {
        active ->
            MaterialTheme.colorScheme.primary

        complete ->
            MaterialTheme.colorScheme.secondary
                .copy(alpha = 0.24f)

        else ->
            Color.White.copy(alpha = 0.07f)
    }

    val content = when {
        active ->
            MaterialTheme.colorScheme.onPrimary

        complete ->
            MaterialTheme.colorScheme.secondary

        else ->
            Gray400
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                number,
                color = content,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.width(4.dp))

            Text(
                label,
                color = content,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuardianIdentityCard(
    state: ScannerUiState.Verified
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Gray900
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                contentAlignment =
                    Alignment.BottomEnd
            ) {
                GuardianAvatar(
                    photoUrl =
                        state.guardian.photoUrl,
                    size = 96.dp
                )

                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color =
                        if (
                            state.guardianPhotoReady
                        ) {
                            MaterialTheme
                                .colorScheme
                                .secondary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .error
                        }
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            if (
                                state.guardianPhotoReady
                            ) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.Close
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier =
                                Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "AUTHORIZED GUARDIAN",
                    color = Gray400,
                    style =
                        MaterialTheme.typography
                            .labelSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text =
                        state.guardian.displayName,
                    color = Color.White,
                    style =
                        MaterialTheme.typography
                            .titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text =
                        if (
                            state.guardianPhotoReady
                        ) {
                            "Photo available · visually compare with the person present"
                        } else {
                            "Identity photo unavailable"
                        },
                    color =
                        if (
                            state.guardianPhotoReady
                        ) {
                            MaterialTheme
                                .colorScheme
                                .secondary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .errorContainer
                        },
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun StudentIdentityCard(
    state: ScannerUiState.Verified
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            Color.Black.copy(alpha = 0.20f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = "STUDENT TO RELEASE",
                color = Gray400,
                style =
                    MaterialTheme.typography
                        .labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = state.student.fullName,
                color = Color.White,
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text =
                    "Grade ${state.student.grade} · Section ${state.student.section}",
                color = Gray300,
                style =
                    MaterialTheme.typography
                        .bodyMedium
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
                tint =
                    MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Pass could not be verified")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Scan Again")
            }
        }
    )
}

@Composable
private fun ApprovedOverlay(
    gateLabel: String,
    onDone: () -> Unit
) {
    val scale = remember {
        Animatable(0.4f)
    }

    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
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
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(86.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme
                            .secondary
                            .copy(alpha = 0.18f)
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .secondary,
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    scaleX =
                                        scale.value
                                    scaleY =
                                        scale.value
                                }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Text(
                    "Release Logged",
                    color = Color.White,
                    style =
                        MaterialTheme.typography
                            .headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    "Student handoff recorded successfully.",
                    color = Gray300,
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    textAlign = TextAlign.Center
                )

                if (gateLabel.isNotBlank()) {
                    Spacer(
                        Modifier.height(
                            Spacing.sm
                        )
                    )

                    Text(
                        gateLabel,
                        color =
                            MaterialTheme
                                .colorScheme.secondary,
                        style =
                            MaterialTheme.typography
                                .bodyMedium,
                        fontWeight = FontWeight.Bold
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color =
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.20f)
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme
                            .primaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            "Camera access is required",
            color = Color.White,
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography
                    .titleLarge,
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
            style =
                MaterialTheme.typography
                    .bodyMedium
        )

        Spacer(Modifier.height(Spacing.lg))

        PrimaryButton(
            text = "Grant Camera Access",
            onClick = onRequest
        )
    }
}
