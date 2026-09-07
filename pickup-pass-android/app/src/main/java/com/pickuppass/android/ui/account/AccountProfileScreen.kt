package com.pickuppass.android.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.PremiumConfirmDialog
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountProfileScreen(
    viewModel: AccountProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "My profile",
                subtitle = uiState.roleLabel.ifBlank { "PickupPass account" },
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
                    .widthIn(max = 680.dp)
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            if (!uiState.photoUrl.isNullOrBlank()) {
                                SmartImage(
                                    model = uiState.photoUrl,
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(Spacing.md))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                        ) {
                            Text(
                                uiState.roleLabel.ifBlank { "PickupPass user" },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(Spacing.sm))

                        Text(
                            if (uiState.isLoading) "Loading profile…" else
                                uiState.displayName.ifBlank {
                                    uiState.roleLabel.ifBlank { "PickupPass user" }
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        if (uiState.email.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                uiState.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (uiState.roleDescription.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                uiState.roleDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                uiState.error?.let { ErrorBanner(it) }

                if (uiState.schoolName.isNotBlank()) {
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
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    "SCHOOL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    uiState.schoolName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

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

                        if (uiState.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text("Loading account details…")
                            }
                        } else {
                            DetailRow(Icons.Filled.Badge, "Name", uiState.displayName.ifBlank { "Not provided" })
                            HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                            DetailRow(Icons.Filled.Security, "Role", uiState.roleLabel.ifBlank { "PickupPass user" })
                            HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                            DetailRow(Icons.Filled.Email, "Email", uiState.email.ifBlank { "Not available" })
                        }
                    }
                }

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
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Lock,
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
                                "Change your sign-in email or password with secure Firebase reauthentication.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        FilledTonalButton(
                            onClick = onOpenSecurity,
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Text("Manage")
                        }
                    }
                }

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
                                "Finished on this device?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Sign out to prevent anyone else from using your PickupPass role.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        TextButton(
                            onClick = { confirmSignOut = true },
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

                Text(
                    "Profile identity details are managed by your PickupPass administrator. Sign-in credentials can be changed securely from Account security.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (confirmSignOut) {
        PremiumConfirmDialog(
            title = "Sign out of PickupPass?",
            message = "You'll need to sign in again before accessing PickupPass on this device.",
            confirmLabel = "Sign out",
            destructive = true,
            icon = Icons.AutoMirrored.Filled.Logout,
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
