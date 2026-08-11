package com.pickuppass.android.ui.teacher.scanner

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
                title = { BrandedTitle("Dismissal Scanner", school, titleColor = Color.White, subtitleColor = Gray400) },
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
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Notifications") },
                            leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                            onClick = { menuExpanded = false; onGoToNotifications() }
                        )
                        DropdownMenuItem(
                            text = { Text("Send Announcement") },
                            leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                            onClick = { menuExpanded = false; onGoToBroadcast() }
                        )
                    }
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
    val gateReady = !gateLoading && gateError == null && (pickupGates.isEmpty() || selectedPickupGate != null)

    Box(Modifier.fillMaxSize()) {
        QrScannerView(
            paused = uiState !is ScannerUiState.Scanning || !gateReady,
            onQrDetected = viewModel::onQrCodeScanned
        )

        GateSelectorBar(
            gates = pickupGates,
            selected = selectedPickupGate,
            loading = gateLoading,
            error = gateError,
            onSelect = viewModel::selectPickupGate,
            onRetry = viewModel::loadPickupGates,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        when (uiState) {
            is ScannerUiState.Scanning -> ScanningOverlay(
                gateReady = gateReady,
                selectedGateLabel = selectedPickupGate?.displayName.orEmpty(),
                hasConfiguredGates = pickupGates.isNotEmpty()
            )
            is ScannerUiState.Verifying -> RequestOverlay("Verifying pass…")
            is ScannerUiState.Verified -> {
                VerifiedPanel(uiState, selectedPickupGate, viewModel)
                if (uiState.isApproving) RequestOverlay("Approving release…")
            }
            is ScannerUiState.Error -> ErrorPanel(uiState.message, onDismiss = viewModel::resetToScanning)
            is ScannerUiState.Approved -> Unit
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("Loading pickup gates…", color = Color.White)
                    }
                }
                error != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
                gates.isEmpty() -> {
                    Text("No pickup gate configured · normal scanner mode", color = Gray300, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selected?.displayName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active pickup gate") },
                            placeholder = { Text("Select pickup gate") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            gates.forEach { gate ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(gate.name)
                                            if (gate.campusName.isNotBlank()) {
                                                Text(gate.campusName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ScanningOverlay(gateReady: Boolean, selectedGateLabel: String, hasConfiguredGates: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (gateReady) "Point camera at parent's pass" else "Select a pickup gate before scanning",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (hasConfiguredGates && selectedGateLabel.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Release location: $selectedGateLabel",
                        color = Gray300,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestOverlay(message: String) {
    Surface(color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(Spacing.md))
            Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BoxScope.VerifiedPanel(
    state: ScannerUiState.Verified,
    selectedPickupGate: PickupGateItem?,
    viewModel: ScannerViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    ) {
        Surface(color = Gray800, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Surface(
                        color = Gray900,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("STUDENT", color = Gray400, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                state.student.fullName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text("Grade ${state.student.grade}", color = Gray300, style = MaterialTheme.typography.bodyMedium)
                            Text("Section ${state.student.section}", color = Gray400, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Surface(
                        color = Gray900,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(Spacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("VERIFY FACE", color = Gray400, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(Spacing.xs))
                            GuardianAvatar(photoUrl = state.guardian?.photoUrl, size = 64.dp)
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                state.guardian?.displayName ?: "Unknown",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
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

                Spacer(Modifier.height(Spacing.md))

                PrimaryButton(
                    text = "APPROVE RELEASE",
                    icon = Icons.Filled.CheckCircle,
                    onClick = viewModel::approveRelease,
                    loading = state.isApproving,
                    containerColor = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = viewModel::resetToScanning,
                    enabled = !state.isApproving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Cancel / Scan Next", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ErrorPanel(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Pass could not be verified") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Scan Again") }
        }
    )
}

@Composable
private fun ApprovedOverlay(gateLabel: String, onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        kotlinx.coroutines.delay(1500)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            )
            Spacer(Modifier.height(Spacing.sm))
            Text("Release Logged", color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (gateLabel.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(gateLabel, color = Gray300, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(shouldShowRationale: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera access is needed to scan pickup passes.",
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        if (shouldShowRationale) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Please allow camera access in Settings to use the scanner.",
                color = Gray400,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(text = "Grant Camera Access", onClick = onRequest)
    }
}
