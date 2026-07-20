package com.pickuppass.android.ui.parent.pass

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PrimaryButton

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.studentName.isNotBlank()) {
                Text(uiState.studentName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
            }

            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator()
                        uiState.qrBitmap != null -> Image(
                            bitmap = uiState.qrBitmap!!.asImageBitmap(),
                            contentDescription = "Pickup QR code",
                            modifier = Modifier.fillMaxSize()
                        )
                        uiState.error != null -> Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (uiState.qrBitmap != null) {
                val minutes = uiState.secondsRemaining / 60
                val seconds = uiState.secondsRemaining % 60
                val expired = uiState.secondsRemaining <= 0
                Text(
                    if (expired) "Expired — tap Regenerate" else "Expires in ${minutes}m ${seconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Show this to school staff. Your photo will also be checked at pickup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            uiState.error?.let {
                Spacer(Modifier.height(16.dp))
                ErrorBanner(it)
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = "Regenerate Pass",
                onClick = { viewModel.generatePass(studentId) },
                loading = uiState.isLoading
            )
        }
    }
}
