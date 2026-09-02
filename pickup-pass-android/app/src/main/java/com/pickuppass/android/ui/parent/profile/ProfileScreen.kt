package com.pickuppass.android.ui.parent.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSignOutConfirmation by remember {
        mutableStateOf(false)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onImagePicked(context, it)
        }
    }

    LaunchedEffect(signedOut) {
        if (signedOut) {
            onSignedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ProfileHero(
                    displayName = uiState.displayName,
                    email = uiState.email
                )

                IdentityPhotoCard(
                    photoUrl = uiState.photoUrl,
                    displayName = uiState.displayName,
                    isUploading = uiState.isUploading,
                    onChoosePhoto = {
                        if (!uiState.isUploading) {
                            pickImage.launch("image/*")
                        }
                    }
                )

                uiState.uploadSuccessMessage?.let { message ->
                    SuccessBanner(message)
                }

                uiState.error?.let { message ->
                    ErrorBanner(message)
                }

                AccountDetailsCard(
                    displayName = uiState.displayName,
                    email = uiState.email,
                    isLoading = uiState.isLoading
                )

                SecurityCard(
                    onOpenDevices = onOpenDevices
                )

                SignOutCard(
                    onSignOut = {
                        showSignOutConfirmation = true
                    }
                )

                Text(
                    text = "Your pickup identity photo is shown only as part of the authorized verification workflow. Student release still requires a valid PickupPass and staff confirmation.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showSignOutConfirmation = false
            },
            title = {
                Text("Sign out of PickupPass?")
            },
            text = {
                Text(
                    "You'll need to sign in again before viewing students, pickup passes, or guardian settings on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        viewModel.signOut()
                    }
                ) {
                    Text(
                        "Sign out",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileHero(
    displayName: String,
    email: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "PICKUP IDENTITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f)
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = displayName.ifBlank { "Your family profile" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Keep your identity photo current so school staff can confidently verify who is present at pickup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            )

            if (email.isNotBlank()) {
                Spacer(Modifier.height(Spacing.md))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = email,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityPhotoCard(
    photoUrl: String?,
    displayName: String,
    isUploading: Boolean,
    onChoosePhoto: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Verification photo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "Use a clear, recent photo of your face. Avoid sunglasses, masks, or group photos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.lg))

            Box(
                modifier = Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isUploading,
                        onClick = onChoosePhoto
                    ),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = !photoUrl.isNullOrBlank(),
                    animationSpec = tween(260),
                    label = "profileAvatarPhase"
                ) { hasPhoto ->
                    if (hasPhoto) {
                        SmartImage(
                            model = photoUrl,
                            contentDescription = "${displayName.ifBlank { "Guardian" }} profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                    }
                }

                if (isUploading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Button(
                onClick = onChoosePhoto,
                enabled = !isUploading,
                modifier = Modifier.heightIn(min = 46.dp),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 10.dp
                )
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Text("Saving photo…")
                } else {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Text(
                        if (!photoUrl.isNullOrBlank()) {
                            "Change photo"
                        } else {
                            "Add photo"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDetailsCard(
    displayName: String,
    email: String,
    isLoading: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = "Account details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(Spacing.md))

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(Modifier.width(Spacing.sm))

                    Text(
                        text = "Loading account details…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ProfileDetailRow(
                    label = "Name",
                    value = displayName.ifBlank { "Not provided" }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md)
                )

                ProfileDetailRow(
                    label = "Email",
                    value = email.ifBlank { "Not available" }
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecurityCard(
    onOpenDevices: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Account security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Review devices that have used this PickupPass account and revoke sessions you no longer trust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            OutlinedButton(
                onClick = onOpenDevices,
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text("Devices")
            }
        }
    }
}

@Composable
private fun SignOutCard(
    onSignOut: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Using a shared device?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Sign out when you're finished so another person can't access your family pickup information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            TextButton(
                onClick = onSignOut,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    "Sign out",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
