package com.pickuppass.android.ui.parent.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.DeviceSessionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    onBack: () -> Unit,
    viewModel: MyDevicesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<DeviceSessionItem?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("My Devices") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { viewModel.revokeOthers() }, enabled = !state.loading) { Text("Sign out others") } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Review devices that have used your PickupPass account. You can sign out a lost or unfamiliar device without changing your password.", style = MaterialTheme.typography.bodyMedium)
                state.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
                if (state.loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            items(state.devices, key = { it.deviceId }) { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Icon(Icons.Default.PhoneAndroid, null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.deviceName.ifBlank { "Android device" }, style = MaterialTheme.typography.titleMedium)
                                if (device.current) Text("This device", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                Text(if (device.active) "Active" else "Signed out", style = MaterialTheme.typography.bodySmall)
                                if (device.clientVersion.isNotBlank()) Text("App ${device.clientVersion}", style = MaterialTheme.typography.bodySmall)
                                device.lastSeenAt?.let { Text("Last seen: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (device.active) {
                            TextButton(onClick = { confirm = device }, enabled = !state.loading) {
                                Text(if (device.current) "Sign out this device" else "Sign out device")
                            }
                        }
                    }
                }
            }
            if (!state.loading && state.devices.isEmpty()) item { Text("No registered devices yet.") }
        }
    }

    confirm?.let { device ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Sign out device?") },
            text = { Text(if (device.current) "You will be returned to the login screen on this device." else "That device will be blocked from authenticated PickupPass requests.") },
            confirmButton = { TextButton(onClick = { confirm = null; viewModel.revoke(device) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}
