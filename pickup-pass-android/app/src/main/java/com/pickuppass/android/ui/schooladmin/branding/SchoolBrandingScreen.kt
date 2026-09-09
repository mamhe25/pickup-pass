package com.pickuppass.android.ui.schooladmin.branding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.schooladmin.dashboard.DismissalDashboardScreen
import com.pickuppass.android.ui.theme.Spacing

/**
 * School Admin authenticated root.
 *
 * The legacy implementation used Branding as both settings and the entire admin
 * navigation hub. The route is retained for backwards compatibility, but its
 * user-facing home is now the live operations dashboard. Branding is opened as
 * a focused settings surface from the admin tools sheet.
 */
@Composable
fun SchoolBrandingScreen(
    viewModel: SchoolBrandingViewModel = hiltViewModel(),
    onGoToScanner: () -> Unit,
    onGoToStudents: () -> Unit,
    onGoToExitLogs: () -> Unit,
    onGoToInviteTeacher: () -> Unit,
    onGoToManageSections: () -> Unit,
    onGoToStaffManagement: () -> Unit,
    onGoToManualPickup: () -> Unit,
    onGoToAuditLog: () -> Unit,
    onGoToDismissalDashboard: () -> Unit,
    onGoToPickupPolicy: () -> Unit,
    onGoToAcademicStructure: () -> Unit,
    onGoToBulkStudentImport: () -> Unit,
    onGoToStudentLifecycle: () -> Unit,
    onGoToDismissalReports: () -> Unit,
    onGoToGuardianVerification: () -> Unit,
    onGoToCampusGates: () -> Unit,
    onGoToStaffPickupGates: () -> Unit,
    onGoToBroadcast: () -> Unit,
    onGoToBilling: () -> Unit,
    onGoToDataExport: () -> Unit,
    onGoToLaunchReadiness: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBrandingSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showBrandingSettings) {
        showBrandingSettings = false
    }

    if (!showBrandingSettings) {
        DismissalDashboardScreen(
            schoolName = uiState.schoolName,
            onOpenProfile = onOpenProfile,
            onOpenNotifications = onOpenNotifications,
            onOpenBranding = { showBrandingSettings = true },
            onGoToScanner = onGoToScanner,
            onGoToStudents = onGoToStudents,
            onGoToExitLogs = onGoToExitLogs,
            onGoToInviteTeacher = onGoToInviteTeacher,
            onGoToManageSections = onGoToManageSections,
            onGoToStaffManagement = onGoToStaffManagement,
            onGoToManualPickup = onGoToManualPickup.takeIf { uiState.features["manual_override"] != false },
            onGoToAuditLog = onGoToAuditLog,
            onGoToPickupPolicy = onGoToPickupPolicy,
            onGoToAcademicStructure = onGoToAcademicStructure,
            onGoToBulkStudentImport = onGoToBulkStudentImport.takeIf { uiState.features["bulk_student_import"] != false },
            onGoToStudentLifecycle = onGoToStudentLifecycle,
            onGoToDismissalReports = onGoToDismissalReports.takeIf { uiState.features["advanced_reporting"] != false },
            onGoToGuardianVerification = onGoToGuardianVerification.takeIf { uiState.features["guardian_verification"] != false },
            onGoToCampusGates = onGoToCampusGates,
            onGoToStaffPickupGates = onGoToStaffPickupGates.takeIf { uiState.features["staff_gate_restrictions"] != false },
            onGoToBroadcast = onGoToBroadcast,
            onGoToBilling = onGoToBilling,
            onGoToDataExport = onGoToDataExport,
            onGoToLaunchReadiness = onGoToLaunchReadiness,
        )
        return
    }

    BrandingSettings(
        uiState = uiState,
        onBack = { showBrandingSettings = false },
        onRefresh = viewModel::refresh,
        onImagePicked = viewModel::onImagePicked,
    )

    // Feedback is deliberately outside the scroll container. Upload/save
    // outcomes therefore stay visible regardless of scroll position.
    uiState.error?.let { ErrorBanner(it) }
    uiState.successMessage?.let { SuccessBanner(it) }
}

@Composable
private fun BrandingSettings(
    uiState: SchoolBrandingUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onImagePicked: (android.content.Context, android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { onImagePicked(context, it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "School branding",
                subtitle = "Identity & logo",
                onBack = onBack,
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            enabled = !uiState.isRefreshing && !uiState.isUploading,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "branding_intro") {
                    BrandingIntro()
                }

                item(key = "identity_card") {
                    SchoolIdentityCard(
                        schoolName = uiState.schoolName,
                        logoUrl = uiState.logoUrl,
                        plan = uiState.plan,
                        subscriptionStatus = uiState.subscriptionStatus,
                        uploading = uiState.isUploading,
                        onChooseLogo = { pickImage.launch("image/*") },
                    )
                }

                item(key = "usage_note") {
                    BrandingUsageCard()
                }
            }
        }
    }
}

@Composable
private fun BrandingIntro() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "One school identity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Your logo is reused across PickupPass so staff and families can immediately recognize the school context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SchoolIdentityCard(
    schoolName: String,
    logoUrl: String?,
    plan: String,
    subscriptionStatus: String,
    uploading: Boolean,
    onChooseLogo: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !uploading, onClick = onChooseLogo),
                contentAlignment = Alignment.Center,
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    SmartImage(
                        model = logoUrl,
                        contentDescription = "School logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.sm),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "Choose school logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp),
                    )
                }

                if (uploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = MaterialTheme.colorScheme.surface,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = schoolName.ifBlank { "Your school" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusPill(plan.pretty())
                StatusPill(subscriptionStatus.pretty())
            }

            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.lg))

            Button(
                onClick = onChooseLogo,
                enabled = !uploading,
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 13.dp),
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(if (uploading) "Updating logo…" else if (logoUrl.isNullOrBlank()) "Add school logo" else "Change school logo")
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "PNG, JPEG or WebP · under 2 MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BrandingUsageCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Where the logo appears",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "School-facing navigation, guardian verification, parent context and selected operational screens reuse this identity. Updating it here keeps those surfaces consistent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun String.pretty(): String =
    replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
