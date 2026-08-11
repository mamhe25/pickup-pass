package com.pickuppass.android.ui.schooladmin.branding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
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
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onImagePicked(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School Admin") },
                actions = {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
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
                .padding(Spacing.lg),
        ) {
            // --- Branding: logo picker ---
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (uiState.schoolName.isNotBlank()) {
                    Text(uiState.schoolName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${uiState.plan.replaceFirstChar { it.uppercase() }} · ${uiState.subscriptionStatus.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                }
                Text(
                    "This logo is shown to parents and staff so it's always clear which school they're signed into.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pickImage.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    // Cross-fade between the placeholder and the actual logo so a
                    // newly uploaded school image eases in rather than snapping.
                    Crossfade(
                        targetState = uiState.logoUrl != null,
                        animationSpec = tween(300),
                        label = "logoPhase"
                    ) { hasLogo ->
                        if (hasLogo) {
                            SmartImage(
                                model = uiState.logoUrl,
                                contentDescription = "School logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.sm)
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "No logo yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs)
                                )
                            }
                        }
                    }

                    if (uiState.isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                TextButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(if (uiState.logoUrl != null) "Change Logo" else "Choose Logo Image")
                }

                Text(
                    "PNG, JPEG, or WebP · under 2MB · square logos with a transparent background look best. It's automatically resized on upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
                )

                uiState.successMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
                uiState.error?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    ErrorBanner(it)
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // --- Primary daily action: gets real visual weight, everything
            // else here is either occasional (invite a teacher, assign
            // sections) or a different kind of action entirely
            // (broadcasting), not a same-weight peer of "run the scanner." ---
            PrimaryActionButton(
                icon = Icons.Filled.QrCode2,
                label = "Go to Dismissal Scanner",
                onClick = onGoToScanner
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionLabel("Dismissal Operations")
            GroupedActionList {
                NavListItem(Icons.Filled.Dashboard, "Live Dismissal Dashboard", onGoToDismissalDashboard)
                NavListItem(Icons.Filled.History, "Dismissal History", onGoToExitLogs)
                if (uiState.features["advanced_reporting"] != false) {
                    NavListItem(Icons.Filled.Assessment, "Dismissal Reports & Export", onGoToDismissalReports)
                }
                if (uiState.features["manual_override"] != false) {
                    NavListItem(Icons.Filled.WarningAmber, "Manual Pickup Override", onGoToManualPickup)
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionLabel("Manage")
            GroupedActionList {
                NavListItem(Icons.Filled.Groups, "Manage Students", onGoToStudents)
                NavListItem(Icons.Filled.Class, "School Year & Sections", onGoToAcademicStructure)
                if (uiState.features["bulk_student_import"] != false) {
                    NavListItem(Icons.Filled.UploadFile, "Bulk Import Students", onGoToBulkStudentImport)
                }
                NavListItem(Icons.Filled.PersonOff, "Student Lifecycle & Promotion", onGoToStudentLifecycle)
                if (uiState.features["guardian_verification"] != false) {
                    NavListItem(Icons.Filled.VerifiedUser, "Guardian Verification", onGoToGuardianVerification)
                }
                NavListItem(Icons.Filled.Schedule, "Pickup Policy", onGoToPickupPolicy)
                NavListItem(Icons.Filled.LocationOn, "Campuses & Pickup Gates", onGoToCampusGates)
                if (uiState.features["staff_gate_restrictions"] != false) {
                    NavListItem(Icons.Filled.LocationOn, "Staff Pickup Gates", onGoToStaffPickupGates)
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionLabel("Administration")
            GroupedActionList {
                NavListItem(Icons.Filled.PersonAdd, "Invite a Teacher", onGoToInviteTeacher)
                NavListItem(Icons.Filled.Class, "Teacher Sections", onGoToManageSections)
                NavListItem(Icons.Filled.AdminPanelSettings, "Teacher Accounts", onGoToStaffManagement)
                NavListItem(Icons.Filled.FactCheck, "Audit Log", onGoToAuditLog)
                NavListItem(Icons.Filled.ReceiptLong, "Subscription & Billing", onGoToBilling)
                NavListItem(Icons.Filled.Download, "Data Backup & Export", onGoToDataExport)
            }

            Spacer(Modifier.height(Spacing.lg))
            PrimaryActionButton(
                icon = Icons.Filled.Campaign,
                label = "Send Announcement",
                onClick = onGoToBroadcast,
                containerColor = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)
    )
}

/** Visually groups related nav items inside one bordered surface, rather than leaving the grouping implied only by a text header above a run of identical buttons. */
@Composable
private fun GroupedActionList(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun NavListItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun PrimaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
