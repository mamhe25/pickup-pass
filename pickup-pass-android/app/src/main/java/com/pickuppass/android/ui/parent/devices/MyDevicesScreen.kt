package com.pickuppass.android.ui.parent.devices

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.DeviceSessionItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDevicesScreen(
    onBack: () -> Unit,
    viewModel: MyDevicesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmDevice by remember {
        mutableStateOf<DeviceSessionItem?>(null)
    }

    var confirmOthers by remember {
        mutableStateOf(false)
    }

    val activeCount =
        state.devices.count { it.active }

    val otherActiveCount =
        state.devices.count {
            it.active && !it.current
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices & Sessions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.loading
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh devices"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val phase = when {
            state.loading && state.devices.isEmpty() ->
                "loading"

            state.error != null &&
                state.devices.isEmpty() ->
                "error"

            else ->
                "content"
        }

        Crossfade(
            targetState = phase,
            animationSpec = tween(220),
            label = "deviceSessionPhase"
        ) { current ->
            when (current) {
                "loading" -> Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    FullScreenLoading()
                }

                "error" -> DeviceErrorState(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    message =
                        state.error
                            ?: "Couldn't load device sessions",
                    onRetry = viewModel::refresh
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.sm,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(Spacing.md)
                ) {
                    item {
                        DeviceSecurityHero(
                            activeCount = activeCount,
                            otherActiveCount =
                                otherActiveCount,
                            totalCount =
                                state.devices.size,
                            canRevokeOthers =
                                otherActiveCount > 0 &&
                                    !state.loading,
                            onRevokeOthers = {
                                confirmOthers = true
                            }
                        )
                    }

                    state.error?.let { message ->
                        item {
                            ErrorBanner(message)
                        }
                    }

                    state.message?.let { message ->
                        item {
                            SuccessBanner(message)
                        }
                    }

                    item {
                        SectionHeading(
                            title = "Signed-in devices",
                            detail =
                                "Review where your account has been used. Current and revoked sessions are kept visible so unfamiliar activity is easier to spot."
                        )
                    }

                    if (state.devices.isEmpty()) {
                        item {
                            EmptyDevicesState()
                        }
                    } else {
                        items(
                            items = state.devices,
                            key = { it.deviceId }
                        ) { device ->
                            DeviceSessionCard(
                                device = device,
                                busy = state.loading,
                                onRevoke = {
                                    confirmDevice = device
                                }
                            )
                        }
                    }

                    item {
                        DeviceSecurityNote()
                    }
                }
            }
        }
    }

    confirmDevice?.let { device ->
        AlertDialog(
            onDismissRequest = {
                if (!state.loading) {
                    confirmDevice = null
                }
            },
            icon = {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    if (device.current) {
                        "Sign out this device?"
                    } else {
                        "Sign out ${device.deviceName.ifBlank { "this device" }}?"
                    }
                )
            },
            text = {
                Text(
                    if (device.current) {
                        "You will be signed out here and returned to the login screen. Your other active sessions are not affected."
                    } else {
                        "This device will immediately lose authenticated PickupPass access. You can sign in again later if the device is trusted."
                    }
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.loading,
                    onClick = {
                        confirmDevice = null
                        viewModel.revoke(device)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error,
                            contentColor =
                                MaterialTheme.colorScheme.onError
                        )
                ) {
                    Text("Sign out device")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.loading,
                    onClick = {
                        confirmDevice = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmOthers) {
        AlertDialog(
            onDismissRequest = {
                if (!state.loading) {
                    confirmOthers = false
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null
                )
            },
            title = {
                Text("Sign out other devices?")
            },
            text = {
                Text(
                    "Every other active PickupPass session will be revoked. This device stays signed in."
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.loading,
                    onClick = {
                        confirmOthers = false
                        viewModel.revokeOthers()
                    }
                ) {
                    Text("Sign out others")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.loading,
                    onClick = {
                        confirmOthers = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceSecurityHero(
    activeCount: Int,
    otherActiveCount: Int,
    totalCount: Int,
    canRevokeOthers: Boolean,
    onRevokeOthers: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor =
            MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "ACCOUNT SECURITY",
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.68f)
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = when {
                    otherActiveCount > 0 ->
                        "Your account is active on ${otherActiveCount + 1} devices"

                    activeCount > 0 ->
                        "This is your only active device"

                    else ->
                        "Review your PickupPass sessions"
                },
                style =
                    MaterialTheme.typography
                        .headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text =
                    "If a phone or browser is lost, shared, or unfamiliar, revoke it here without changing your password.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroMetric(
                    value = activeCount.toString(),
                    label = "Active",
                    modifier = Modifier.weight(1f)
                )

                HeroMetric(
                    value = otherActiveCount.toString(),
                    label = "Other",
                    modifier = Modifier.weight(1f)
                )

                HeroMetric(
                    value = totalCount.toString(),
                    label = "Recorded",
                    modifier = Modifier.weight(1f)
                )
            }

            if (canRevokeOthers) {
                Spacer(Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRevokeOthers,
                        modifier =
                            Modifier.heightIn(min = 44.dp),
                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onPrimary
                                )
                    ) {
                        Text("Sign out other devices")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme.onPrimary
                .copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.md
            ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelSmall,
                color =
                    MaterialTheme.colorScheme.onPrimary
                        .copy(alpha = 0.70f)
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    detail: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceSessionCard(
    device: DeviceSessionItem,
    busy: Boolean,
    onRevoke: () -> Unit
) {
    val title =
        device.deviceName.ifBlank {
            "Unknown device"
        }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    if (device.current &&
                        device.active
                    ) {
                        MaterialTheme.colorScheme
                            .primaryContainer
                            .copy(alpha = 0.34f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation =
                    if (device.current &&
                        device.active
                    ) 3.dp else 1.dp
            )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color =
                        if (device.active) {
                            MaterialTheme.colorScheme
                                .primaryContainer
                        } else {
                            MaterialTheme.colorScheme
                                .surfaceVariant
                        }
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint =
                                if (device.active) {
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                                }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(3.dp))

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                Spacing.xs
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        SessionStatusBadge(
                            active = device.active
                        )

                        if (device.current) {
                            Surface(
                                shape = CircleShape,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primaryContainer
                            ) {
                                Text(
                                    text = "This device",
                                    modifier =
                                        Modifier.padding(
                                            horizontal =
                                                9.dp,
                                            vertical =
                                                4.dp
                                        ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            DeviceMetadata(
                label = "App version",
                value =
                    device.clientVersion
                        .ifBlank { "—" }
            )

            DeviceMetadata(
                label = "Last seen",
                value =
                    device.lastSeenAt
                        ?: "Not available"
            )

            device.createdAt?.let {
                DeviceMetadata(
                    label = "First registered",
                    value = it
                )
            }

            if (!device.active) {
                device.revokedAt?.let {
                    DeviceMetadata(
                        label = "Signed out",
                        value = it
                    )
                }
            }

            if (device.active) {
                Spacer(Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    TextButton(
                        onClick = onRevoke,
                        enabled = !busy,
                        modifier =
                            Modifier.heightIn(
                                min = 44.dp
                            )
                    ) {
                        Text(
                            if (device.current) {
                                "Sign out this device"
                            } else {
                                "Sign out device"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStatusBadge(
    active: Boolean
) {
    Surface(
        shape = CircleShape,
        color =
            if (active) {
                MaterialTheme.colorScheme
                    .secondaryContainer
            } else {
                MaterialTheme.colorScheme
                    .surfaceVariant
            }
    ) {
        Text(
            text =
                if (active) {
                    "Active"
                } else {
                    "Signed out"
                },
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 4.dp
            ),
            style =
                MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (active) {
                    MaterialTheme.colorScheme
                        .onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                }
        )
    }
}

@Composable
private fun DeviceMetadata(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.Top
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(Modifier.width(Spacing.md))

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style =
                MaterialTheme.typography.bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurface,
            textAlign =
                androidx.compose.ui.text.style
                    .TextAlign.End
        )
    }
}

@Composable
private fun EmptyDevicesState() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.lg,
                    vertical = 34.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Devices,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                        modifier =
                            Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "No device sessions recorded",
                style =
                    MaterialTheme.typography
                        .titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text =
                    "Once PickupPass records a device session, it will appear here for review.",
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceErrorState(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier =
            modifier.padding(Spacing.lg),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        ErrorBanner(message)

        Spacer(Modifier.height(Spacing.md))

        OutlinedButton(
            onClick = onRetry,
            modifier =
                Modifier.heightIn(min = 44.dp)
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun DeviceSecurityNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
                .copy(alpha = 0.64f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )

            Spacer(Modifier.width(Spacing.sm))

            Text(
                text =
                    "Revoking a device blocks its authenticated PickupPass requests. If you believe your password is known by someone else, change the password as well.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}
