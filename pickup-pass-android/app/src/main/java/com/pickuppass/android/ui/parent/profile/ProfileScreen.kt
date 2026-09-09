package com.pickuppass.android.ui.parent.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Spacing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenAccountSecurity: () -> Unit,
    onOpenDevices: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var showPhotoSourcePicker by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFeedbackMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onImagePicked(context, it)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri =
            pendingCameraUri
                ?.let(Uri::parse)

        pendingCameraUri = null

        if (captured && uri != null) {
            viewModel.onImagePicked(context, uri)
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                createGuardianCameraUri(context)
            }.onSuccess { uri ->
                pendingCameraUri = uri.toString()
                takePhoto.launch(uri)
            }.onFailure {
                cameraFeedbackMessage =
                    "PickupPass couldn't prepare the camera. Please try again or choose a photo from your gallery."
            }
        } else {
            cameraFeedbackMessage =
                "Allow camera access to take a verification photo. You can still choose an existing photo from your gallery."
        }
    }

    fun launchCameraCapture() {
        cameraFeedbackMessage = null
        viewModel.clearFeedback()
        showPhotoSourcePicker = false

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                createGuardianCameraUri(context)
            }.onSuccess { uri ->
                pendingCameraUri = uri.toString()
                takePhoto.launch(uri)
            }.onFailure {
                cameraFeedbackMessage =
                    "PickupPass couldn't prepare the camera. Please try again or choose a photo from your gallery."
            }
        } else {
            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "My profile",
                subtitle = "Pickup identity & account protection",
                onBack = onBack,
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
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ProfileHero(
                    displayName = uiState.displayName,
                    email = uiState.email
                )

                PickupPassPhotoEligibilityCard(
                    photoValidationStatus =
                        uiState.photoValidationStatus,
                    hasPhoto =
                        !uiState.photoUrl.isNullOrBlank()
                )

                IdentityPhotoCard(
                    photoUrl = uiState.photoUrl,
                    displayName = uiState.displayName,
                    photoValidationStatus =
                        uiState.photoValidationStatus,
                    isUploading = uiState.isUploading,
                    onChoosePhoto = {
                        if (!uiState.isUploading) {
                            viewModel.clearFeedback()
                            cameraFeedbackMessage = null
                            showPhotoSourcePicker = true
                        }
                    },
                    onViewPhoto = {
                        if (!uiState.photoUrl.isNullOrBlank()) {
                            showPhotoViewer = true
                        }
                    }
                )

                PhotoGuidelinesCard()

                AccountDetailsCard(
                    displayName = uiState.displayName,
                    email = uiState.email,
                    isLoading = uiState.isLoading
                )

                SecurityCard(
                    onOpenAccountSecurity = onOpenAccountSecurity,
                    onOpenDevices = onOpenDevices
                )

                SignOutCard(
                    onSignOut = { showSignOutConfirmation = true }
                )

                Text(
                    "PickupPass uses Google Cloud Vision through the secure backend to check photo quality and that one human face is visible. It does not perform facial recognition or automatically decide a person's identity. School staff still make the final visual verification at pickup.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSignOutConfirmation) {
        PremiumConfirmDialog(
            title = "Sign out of PickupPass?",
            message = "You'll need to sign in again before viewing students, pickup passes, or guardian settings on this device.",
            confirmLabel = "Sign out",
            destructive = true,
            icon = Icons.AutoMirrored.Filled.Logout,
            onDismiss = { showSignOutConfirmation = false },
            onConfirm = {
                showSignOutConfirmation = false
                viewModel.signOut()
            },
        )
    }

    if (showPhotoViewer) {
        ProfilePhotoDialog(
            photoUrl = uiState.photoUrl,
            displayName = uiState.displayName,
            onDismiss = { showPhotoViewer = false }
        )
    }

    if (showPhotoSourcePicker) {
        PhotoSourcePickerSheet(
            onDismiss = {
                showPhotoSourcePicker = false
            },
            onTakePhoto = ::launchCameraCapture,
            onChooseGallery = {
                showPhotoSourcePicker = false
                cameraFeedbackMessage = null
                viewModel.clearFeedback()
                pickImage.launch("image/*")
            }
        )
    }

    uiState.uploadSuccessMessage?.let { message ->
        FeedbackCard(
            message = message,
            title = uiState.uploadSuccessTitle ?: "Photo verified",
            tone = FeedbackTone.Success,
            onDismiss = viewModel::clearFeedback
        )
    }

    uiState.error?.let { message ->
        FeedbackCard(
            message = message,
            title = uiState.errorTitle ?: "Photo or profile issue",
            tone = FeedbackTone.Error,
            onDismiss = viewModel::clearFeedback
        )
    }

    cameraFeedbackMessage?.let { message ->
        FeedbackCard(
            message = message,
            title = "Camera access needed",
            tone = FeedbackTone.Warning,
            onDismiss = {
                cameraFeedbackMessage = null
            }
        )
    }
}

private fun createGuardianCameraUri(
    context: Context
): Uri {
    val directory =
        File(
            context.cacheDir,
            "guardian_verification_photos"
        ).apply {
            mkdirs()
        }

    val file =
        File.createTempFile(
            "guardian_",
            ".jpg",
            directory
        )

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourcePickerSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseGallery: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 660.dp)
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    "Add verification photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Use a fresh camera photo or choose an existing image. Either option goes through the same secure Google Vision quality check before PickupPass accepts it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PhotoSourceOption(
                icon = Icons.Filled.CameraAlt,
                title = "Take photo",
                subtitle = "Recommended · Capture a clear, recent photo now.",
                onClick = onTakePhoto
            )

            PhotoSourceOption(
                icon = Icons.Filled.PhotoLibrary,
                title = "Choose from gallery",
                subtitle = "Select a clear existing photo from this device.",
                onClick = onChooseGallery
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.52f
                )
            ) {
                Text(
                    "For best results: face the camera, use even lighting, keep your full face visible, and make sure no one else is in the photo.",
                    modifier = Modifier.padding(Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PhotoSourceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.46f
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileHero(
    displayName: String,
    email: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "PICKUP IDENTITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                displayName.ifBlank { "Your family profile" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Keep your identity photo current so school staff can confidently verify who is present at pickup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            if (email.isNotBlank()) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ) {
                    Text(
                        email,
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
private fun PickupPassPhotoEligibilityCard(
    photoValidationStatus: String,
    hasPhoto: Boolean
) {
    val verified =
        hasPhoto &&
            photoValidationStatus.equals(
                "verified",
                ignoreCase = true
            )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            if (verified) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        border = BorderStroke(
            1.dp,
            if (verified) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector =
                    if (verified) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Security
                    },
                contentDescription = null,
                tint =
                    if (verified) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text =
                        if (verified) {
                            "Pickup pass photo ready"
                        } else {
                            "Pickup pass locked until photo verification"
                        },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color =
                        if (verified) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        if (verified) {
                            "You can generate pickup QR passes when your other guardian permissions are valid."
                        } else {
                            "Add an accepted verification photo first. PickupPass will block QR generation until the photo passes validation."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (verified) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                )
            }
        }
    }
}

@Composable
private fun PhotoGuidelinesCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "Tips for a photo that passes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "PickupPass validates the image before saving it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PhotoTipRow(
                good = true,
                text = "Only you are in the photo — no group pictures."
            )
            PhotoTipRow(
                good = true,
                text = "Face the camera directly with your full face visible."
            )
            PhotoTipRow(
                good = true,
                text = "Use even front lighting and a sharp, recent photo."
            )
            PhotoTipRow(
                good = true,
                text = "Keep your face large enough to recognize, with your full head visible."
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.xs)
            )

            PhotoTipRow(
                good = false,
                text = "Avoid masks, dark sunglasses, heavy shadows, blur, and strong side angles."
            )
            PhotoTipRow(
                good = false,
                text = "Do not upload pets, scenery, IDs, screenshots, cartoons, or another person's photo."
            )
        }
    }
}

@Composable
private fun PhotoTipRow(
    good: Boolean,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector =
                if (good) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.WarningAmber
                },
            contentDescription = null,
            tint =
                if (good) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdentityPhotoCard(
    photoUrl: String?,
    displayName: String,
    photoValidationStatus: String,
    isUploading: Boolean,
    onChoosePhoto: () -> Unit,
    onViewPhoto: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Verification photo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "School staff compare this photo with the guardian who presents the pickup pass.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.sm))

            Surface(
                shape = CircleShape,
                color =
                    if (
                        photoValidationStatus.equals(
                            "verified",
                            ignoreCase = true
                        )
                    ) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            if (
                                photoValidationStatus.equals(
                                    "verified",
                                    ignoreCase = true
                                )
                            ) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.WarningAmber
                            },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text =
                            if (
                                photoValidationStatus.equals(
                                    "verified",
                                    ignoreCase = true
                                )
                            ) {
                                "Photo verified"
                            } else {
                                "Photo required"
                            },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isUploading,
                        onClick = if (photoUrl.isNullOrBlank()) {
                            onChoosePhoto
                        } else {
                            onViewPhoto
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrBlank()) {
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

            if (!photoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Tap photo to view",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Text("Validating photo…")
                } else {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(if (photoUrl.isNullOrBlank()) "Add photo" else "Change photo")
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
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "Account details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Spacing.md))

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Loading account details…",
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
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecurityCard(
    onOpenAccountSecurity: () -> Unit,
    onOpenDevices: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Account security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Manage your sign-in email and password, or review devices that have used this account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilledTonalButton(
                    onClick = onOpenAccountSecurity,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sign-in")
                }

                OutlinedButton(
                    onClick = onOpenDevices,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
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
            Column(Modifier.weight(1f)) {
                Text(
                    "Using a shared device?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Sign out when you're finished so another person can't access your family pickup information.",
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
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfilePhotoDialog(
    photoUrl: String?,
    displayName: String,
    onDismiss: () -> Unit
) {
    if (photoUrl.isNullOrBlank()) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName.ifBlank { "Verification photo" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Pickup identity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                SmartImage(
                    model = photoUrl,
                    contentDescription = "Verification photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                )
            }
        }
    }
}
