package com.pickuppass.android.ui.teacher.scanner

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pickuppass.android.ui.common.BrandedTitle
import com.pickuppass.android.ui.common.GuardianAvatar
import com.pickuppass.android.ui.common.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel(),
    onGoToStudents: () -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val school by viewModel.school.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(signedOut) {
        if (signedOut) onSignOut()
    }

    Scaffold(
        containerColor = Color(0xFF111827),
        topBar = {
            TopAppBar(
                title = { BrandedTitle("Dismissal Scanner", school, titleColor = Color.White, subtitleColor = Color(0xFF9CA3AF)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111827)),
                actions = {
                    IconButton(onClick = onGoToStudents) {
                        Icon(Icons.Filled.People, contentDescription = "Students", tint = Color.White)
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
                uiState is ScannerUiState.Approved -> ApprovedOverlay(onDone = viewModel::resetToScanning)
                else -> ScanAndVerifyContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ScanAndVerifyContent(uiState: ScannerUiState, viewModel: ScannerViewModel) {
    Box(Modifier.fillMaxSize()) {
        // Camera stays mounted underneath so it doesn't need to re-initialize
        // between scans — only the analyzer's `paused` flag toggles.
        QrScannerView(
            paused = uiState !is ScannerUiState.Scanning,
            onQrDetected = viewModel::onQrCodeScanned
        )

        when (uiState) {
            is ScannerUiState.Scanning -> ScanningOverlay()
            is ScannerUiState.Verifying -> VerifyingOverlay()
            is ScannerUiState.Verified -> VerifiedPanel(uiState, viewModel)
            is ScannerUiState.Error -> ErrorPanel(uiState.message, onDismiss = viewModel::resetToScanning)
            is ScannerUiState.Approved -> Unit
        }
    }
}

@Composable
private fun ScanningOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Point camera at parent's pass",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun VerifyingOverlay() {
    Surface(color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun BoxScope.VerifiedPanel(state: ScannerUiState.Verified, viewModel: ScannerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    ) {
        Surface(color = Color(0xFF1F2937), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: student info
                    Surface(
                        color = Color(0xFF111827),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("STUDENT", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.student.fullName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Grade ${state.student.grade}", color = Color(0xFFD1D5DB), style = MaterialTheme.typography.bodyMedium)
                            Text("Section ${state.student.section}", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Right: guardian photo for face match
                    Surface(
                        color = Color(0xFF111827),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("VERIFY FACE", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            GuardianAvatar(photoUrl = state.guardian?.photoUrl, size = 64.dp)
                            Spacer(Modifier.height(6.dp))
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

                Spacer(Modifier.height(16.dp))

                PrimaryButton(
                    text = "\u2705  APPROVE RELEASE",
                    onClick = viewModel::approveRelease,
                    loading = state.isApproving,
                    containerColor = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::resetToScanning,
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
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Surface(color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(message, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ApprovedOverlay(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Release Logged", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CameraPermissionRequest(shouldShowRationale: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
            Spacer(Modifier.height(8.dp))
            Text(
                "Please allow camera access in Settings to use the scanner.",
                color = Color(0xFF9CA3AF),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Grant Camera Access", onClick = onRequest)
    }
}
