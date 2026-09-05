package com.pickuppass.android.ui.schooladmin.branding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.common.SuccessBanner
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
    onGoToLaunchReadiness: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onImagePicked(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("School Admin", fontWeight = FontWeight.ExtraBold)
                        if (uiState.schoolName.isNotBlank()) {
                            Text(
                                uiState.schoolName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { confirmSignOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 860.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    SchoolIdentityCard(
                        schoolName = uiState.schoolName,
                        logoUrl = uiState.logoUrl,
                        plan = uiState.plan,
                        subscriptionStatus = uiState.subscriptionStatus,
                        uploading = uiState.isUploading,
                        onChooseLogo = { pickImage.launch("image/*") }
                    )
                }

                uiState.error?.let { item { ErrorBanner(it) } }
                uiState.successMessage?.let { item { SuccessBanner(it) } }

                item {
                    Text(
                        "Daily dismissal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        PrimaryActionCard(
                            icon = Icons.Filled.QrCode2,
                            title = "Scanner",
                            subtitle = "Verify & release",
                            modifier = Modifier.weight(1f),
                            onClick = onGoToScanner
                        )
                        PrimaryActionCard(
                            icon = Icons.Filled.Dashboard,
                            title = "Live dashboard",
                            subtitle = "Today's progress",
                            modifier = Modifier.weight(1f),
                            onClick = onGoToDismissalDashboard
                        )
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        PrimaryActionCard(
                            icon = Icons.Filled.Campaign,
                            title = "Announcement",
                            subtitle = "Notify school",
                            modifier = Modifier.weight(1f),
                            onClick = onGoToBroadcast
                        )
                        if (uiState.features["manual_override"] != false) {
                            PrimaryActionCard(
                                icon = Icons.Filled.WarningAmber,
                                title = "Manual release",
                                subtitle = "Audited fallback",
                                modifier = Modifier.weight(1f),
                                onClick = onGoToManualPickup
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item { SectionLabel("Dismissal operations") }
                item {
                    GroupedActionList {
                        NavListItem(
                            Icons.Filled.History,
                            "Dismissal history",
                            "Review completed student releases",
                            onGoToExitLogs
                        )
                        if (uiState.features["advanced_reporting"] != false) {
                            HorizontalDivider()
                            NavListItem(
                                Icons.Filled.Assessment,
                                "Dismissal reports & export",
                                "Analyze date ranges and export CSV",
                                onGoToDismissalReports
                            )
                        }
                        HorizontalDivider()
                        NavListItem(
                            Icons.Filled.Schedule,
                            "Pickup policy",
                            "Configure QR availability and fallback rules",
                            onGoToPickupPolicy
                        )
                    }
                }

                item { SectionLabel("Students & guardians") }
                item {
                    GroupedActionList {
                        NavListItem(Icons.Filled.Groups, "Manage students", "Student records and guardians", onGoToStudents)
                        HorizontalDivider()
                        NavListItem(Icons.Filled.Class, "School year & sections", "Academic structure used across PickupPass", onGoToAcademicStructure)
                        if (uiState.features["bulk_student_import"] != false) {
                            HorizontalDivider()
                            NavListItem(Icons.Filled.UploadFile, "Bulk import students", "Validate and import roster files", onGoToBulkStudentImport)
                        }
                        HorizontalDivider()
                        NavListItem(Icons.Filled.PersonOff, "Student lifecycle", "Status history and year-end promotion", onGoToStudentLifecycle)
                        if (uiState.features["guardian_verification"] != false) {
                            HorizontalDivider()
                            NavListItem(Icons.Filled.VerifiedUser, "Guardian verification", "Identity assurance and pickup access", onGoToGuardianVerification)
                        }
                    }
                }

                item { SectionLabel("Staff & locations") }
                item {
                    GroupedActionList {
                        NavListItem(Icons.Filled.PersonAdd, "Invite a teacher", "Create a school staff account", onGoToInviteTeacher)
                        HorizontalDivider()
                        NavListItem(Icons.Filled.Class, "Teacher sections", "Assign roster and broadcast scope", onGoToManageSections)
                        HorizontalDivider()
                        NavListItem(Icons.Filled.AdminPanelSettings, "Teacher accounts", "Access status and session security", onGoToStaffManagement)
                        HorizontalDivider()
                        NavListItem(Icons.Filled.LocationOn, "Campuses & pickup gates", "Configure dismissal release locations", onGoToCampusGates)
                        if (uiState.features["staff_gate_restrictions"] != false) {
                            HorizontalDivider()
                            NavListItem(Icons.Filled.Security, "Staff pickup gates", "Limit scanner access by gate", onGoToStaffPickupGates)
                        }
                    }
                }

                item { SectionLabel("School administration") }
                item {
                    GroupedActionList {
                        NavListItem(Icons.AutoMirrored.Filled.FactCheck, "Launch readiness", "Production setup and on-site checks", onGoToLaunchReadiness)
                        HorizontalDivider()
                        NavListItem(Icons.AutoMirrored.Filled.FactCheck, "Audit log", "Administrative activity trail", onGoToAuditLog)
                        HorizontalDivider()
                        NavListItem(Icons.AutoMirrored.Filled.ReceiptLong, "Subscription & billing", "Invoices, GCash and receipts", onGoToBilling)
                        HorizontalDivider()
                        NavListItem(Icons.Filled.Download, "Data backup & export", "Tenant data portability export", onGoToDataExport)
                    }
                }

                item {
                    Text(
                        "PickupPass school administration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)
                    )
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of PickupPass?") },
            text = { Text("This device will stop receiving notifications for this account until you sign in again.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSignOut = false
                        viewModel.signOut()
                    }
                ) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SchoolIdentityCard(
    schoolName: String,
    logoUrl: String?,
    plan: String,
    subscriptionStatus: String,
    uploading: Boolean,
    onChooseLogo: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !uploading, onClick = onChooseLogo),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl != null) {
                    SmartImage(
                        model = logoUrl,
                        contentDescription = "School logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(Spacing.xs)
                    )
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = "Choose school logo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uploading) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .42f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    schoolName.ifBlank { "Your school" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(plan.pretty()) })
                    AssistChip(onClick = {}, enabled = false, label = { Text(subscriptionStatus.pretty()) })
                }
                TextButton(
                    onClick = onChooseLogo,
                    enabled = !uploading,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (logoUrl == null) "Add school logo" else "Change school logo")
                }
                Text(
                    "PNG, JPEG or WebP · under 2 MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.sm))
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun GroupedActionList(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun NavListItem(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun String.pretty(): String =
    replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
