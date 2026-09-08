package com.pickuppass.android.ui.schooladmin.dataexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolDataExportScreen(
    viewModel: SchoolDataExportViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmExport by remember {
        mutableStateOf(false)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/zip"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.createExport(
                context = context,
                uri = uri
            )
        } else {
            viewModel.cancelExportSave()
        }
    }

    LaunchedEffect(state.exportFileName) {
        state.exportFileName?.let {
            saveLauncher.launch(it)
        }
    }

    Scaffold(
        containerColor =
            MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Data backup & export",
                subtitle =
                    "Tenant portability & secure download",
                onBack = {
                    if (!state.exporting) {
                        onBack()
                    }
                },
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            enabled = !state.exporting,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        top = Spacing.md,
                        end = Spacing.md,
                        bottom = Spacing.xl
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            Spacing.md
                        )
                ) {
                    item(key = "hero") {
                        PremiumHeroCard(
                            eyebrow =
                                "School data portability",
                            title =
                                if (state.enabled) {
                                    "Secure export is available"
                                } else {
                                    "Export access is restricted"
                                },
                            message =
                                state.statusMessage
                                    .ifBlank {
                                        if (state.enabled) {
                                            "Create a tenant-scoped archive and save it directly to a destination you choose."
                                        } else {
                                            "The platform owner must enable self-service export before this school can create an archive."
                                        }
                                    },
                            icon =
                                if (state.enabled) {
                                    Icons.Filled.FolderZip
                                } else {
                                    Icons.Filled.Lock
                                }
                        )
                    }

                    item(key = "status") {
                        ExportStatusCard(
                            enabled = state.enabled,
                            storageMode =
                                state.storageMode,
                            cloudCopyCreated =
                                state.cloudCopyCreated,
                            platformBackupControlledBy =
                                state.platformBackupControlledBy
                        )
                    }

                    if (state.exporting) {
                        item(key = "working") {
                            ExportProgressCard()
                        }
                    }

                    item(key = "included-header") {
                        PremiumSectionHeader(
                            title = "Included in the archive",
                            subtitle =
                                "Tenant-scoped operational records only."
                        )
                    }

                    item(key = "included") {
                        OutlinedCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                MaterialTheme.shapes
                                    .extraLarge
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        Spacing.md
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        Spacing.sm
                                    )
                            ) {
                                IncludedRow(
                                    "School record and configuration"
                                )
                                IncludedRow(
                                    "Students and stored guardian relationships"
                                )
                                IncludedRow(
                                    "School users and academic structure"
                                )
                                IncludedRow(
                                    "Campuses and pickup gates"
                                )
                                IncludedRow(
                                    "Broadcast jobs and exit logs"
                                )
                                IncludedRow(
                                    "School audit-event history"
                                )
                                IncludedRow(
                                    "Manifest with counts, generation time, and redaction metadata"
                                )
                            }
                        }
                    }

                    item(key = "excluded-header") {
                        PremiumSectionHeader(
                            title = "Excluded or redacted",
                            subtitle =
                                "Security-sensitive and platform-level data do not leave PickupPass."
                        )
                    }

                    item(key = "excluded") {
                        OutlinedCard(
                            modifier =
                                Modifier.fillMaxWidth(),
                            shape =
                                MaterialTheme.shapes
                                    .extraLarge
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        Spacing.md
                                    ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        Spacing.sm
                                    )
                            ) {
                                ExcludedRow(
                                    "Passwords, signing secrets, private keys, verification codes and credentials"
                                )
                                ExcludedRow(
                                    "Access, refresh, FCM and other security tokens"
                                )
                                ExcludedRow(
                                    "Billing and payment internals"
                                )
                                ExcludedRow(
                                    "Webhook, nonce and session-key material"
                                )
                                ExcludedRow(
                                    "Platform security telemetry and master-admin backup controls"
                                )
                            }
                        }
                    }

                    item(key = "security") {
                        SecurityGuaranteesCard()
                    }

                    if (!state.enabled) {
                        item(key = "restricted") {
                            Surface(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                shape =
                                    MaterialTheme.shapes
                                        .large,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .tertiaryContainer
                            ) {
                                Row(
                                    modifier =
                                        Modifier.padding(
                                            Spacing.md
                                        ),
                                    verticalAlignment =
                                        Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier =
                                            Modifier.size(
                                                18.dp
                                            ),
                                        tint =
                                            MaterialTheme
                                                .colorScheme
                                                .onTertiaryContainer
                                    )

                                    Spacer(
                                        Modifier.width(
                                            Spacing.sm
                                        )
                                    )

                                    Text(
                                        text =
                                            "Self-service export is controlled by the platform owner. This prevents large tenant data archives from being generated without operator authorization.",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item(key = "action") {
                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = Spacing.xs
                                ),
                            horizontalArrangement =
                                Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    confirmExport = true
                                },
                                enabled =
                                    state.enabled &&
                                        !state.exporting &&
                                        !state.refreshing,
                                modifier = Modifier
                                    .widthIn(
                                        min = 220.dp,
                                        max = 360.dp
                                    )
                                    .heightIn(
                                        min = 52.dp
                                    )
                            ) {
                                if (state.exporting) {
                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(
                                                18.dp
                                            ),
                                        strokeWidth =
                                            2.dp
                                    )
                                    Spacer(
                                        Modifier.width(
                                            Spacing.xs
                                        )
                                    )
                                    Text(
                                        "Creating export…"
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier =
                                            Modifier.size(
                                                18.dp
                                            )
                                    )
                                    Spacer(
                                        Modifier.width(
                                            Spacing.xs
                                        )
                                    )
                                    Text(
                                        "Create school export"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmExport) {
        ExportConfirmationDialog(
            busy = state.exporting,
            onDismiss = {
                if (!state.exporting) {
                    confirmExport = false
                }
            },
            onConfirm = {
                confirmExport = false
                viewModel.requestExportSave()
            }
        )
    }

    // Terminal feedback stays last so it renders above confirmation UI.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title =
                state.errorTitle
                    ?: "Data export not completed",
            onDismiss =
                viewModel::clearFeedback
        )
    }

    state.success?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title =
                state.successTitle
                    ?: "School export saved",
            onDismiss =
                viewModel::clearFeedback
        )
    }
}

@Composable
private fun ExportStatusCard(
    enabled: Boolean,
    storageMode: String,
    cloudCopyCreated: Boolean,
    platformBackupControlledBy: String
) {
    val scheme = MaterialTheme.colorScheme

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text = "EXPORT STATUS",
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        fontWeight =
                            FontWeight.ExtraBold,
                        color = scheme.primary
                    )

                    Text(
                        text =
                            if (enabled) {
                                "Self-service enabled"
                            } else {
                                "Self-service disabled"
                            },
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Surface(
                    shape =
                        MaterialTheme.shapes
                            .small,
                    color =
                        if (enabled) {
                            scheme.primaryContainer
                        } else {
                            scheme.surfaceVariant
                        }
                ) {
                    Text(
                        text =
                            if (enabled) {
                                "AVAILABLE"
                            } else {
                                "RESTRICTED"
                            },
                        modifier =
                            Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        fontWeight =
                            FontWeight.ExtraBold,
                        color =
                            if (enabled) {
                                scheme
                                    .onPrimaryContainer
                            } else {
                                scheme
                                    .onSurfaceVariant
                            }
                    )
                }
            }

            HorizontalDivider()

            ExportDetailRow(
                label = "Delivery",
                value =
                    storageMode
                        .replace('_', ' ')
                        .replaceFirstChar {
                            if (it.isLowerCase()) {
                                it.titlecase()
                            } else {
                                it.toString()
                            }
                        }
            )

            ExportDetailRow(
                label =
                    "PickupPass cloud copy",
                value =
                    if (cloudCopyCreated) {
                        "Created"
                    } else {
                        "Not created"
                    }
            )

            ExportDetailRow(
                label =
                    "Platform backups",
                value =
                    if (
                        platformBackupControlledBy ==
                        "master_admin"
                    ) {
                        "Platform owner controlled"
                    } else {
                        platformBackupControlledBy
                            .replace('_', ' ')
                    }
            )
        }
    }
}

@Composable
private fun ExportDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography
                    .bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(Modifier.width(Spacing.sm))

        Text(
            text = value,
            style =
                MaterialTheme.typography
                    .bodySmall,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecurityGuaranteesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            MaterialTheme.colorScheme
                .surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme
                .outlineVariant
        )
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md),
            verticalArrangement =
                Arrangement.spacedBy(
                    Spacing.sm
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme
                            .primary,
                    modifier =
                        Modifier.size(20.dp)
                )
                Spacer(
                    Modifier.width(Spacing.sm)
                )
                Text(
                    text =
                        "Export security guarantees",
                    style =
                        MaterialTheme.typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            SecurityPoint(
                "The archive is tenant-scoped to this school."
            )
            SecurityPoint(
                "Sensitive fields are redacted recursively before serialization."
            )
            SecurityPoint(
                "Document-count and archive-size safety limits are enforced server-side."
            )
            SecurityPoint(
                "Every generated export is recorded in the school audit history."
            )
            SecurityPoint(
                "The archive is streamed directly to the destination you select instead of being retained in Android memory."
            )
            SecurityPoint(
                "PickupPass computes a SHA-256 hash while saving so the downloaded file has an integrity fingerprint."
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    MaterialTheme.shapes.medium,
                color =
                    MaterialTheme.colorScheme
                        .tertiaryContainer
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            Spacing.sm
                        ),
                    verticalAlignment =
                        Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier =
                            Modifier.size(18.dp),
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onTertiaryContainer
                    )
                    Spacer(
                        Modifier.width(
                            Spacing.xs
                        )
                    )
                    Text(
                        text =
                            "This portability archive is not a recurring backup and cannot automatically restore production Firestore data. Platform-level backup and disaster recovery remain separate master-admin controls.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityPoint(
    text: String
) {
    Row(
        verticalAlignment =
            Alignment.Top
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .primary,
            modifier =
                Modifier.size(18.dp)
        )
        Spacer(
            Modifier.width(Spacing.sm)
        )
        Text(
            text = text,
            style =
                MaterialTheme.typography
                    .bodySmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun ExportProgressCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme
                .secondaryContainer
    ) {
        Row(
            modifier =
                Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier =
                    Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
            Spacer(
                Modifier.width(Spacing.sm)
            )
            Column {
                Text(
                    text =
                        "Generating & saving export…",
                    fontWeight =
                        FontWeight.SemiBold
                )
                Text(
                    text =
                        "Keep this screen open while the ZIP is streamed to your selected destination.",
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ExportConfirmationDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var acknowledged by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.FolderZip,
                contentDescription = null
            )
        },
        title = {
            Text(
                text =
                    "Create confidential school export?",
                fontWeight =
                    FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        Spacing.sm
                    )
            ) {
                Text(
                    text =
                        "PickupPass will generate a tenant-scoped ZIP and ask where to save it. The file may contain student, guardian, staff, dismissal and audit records even though security-sensitive fields are redacted."
                )

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        MaterialTheme.shapes.medium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .errorContainer
                            .copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                Spacing.sm
                            ),
                        verticalAlignment =
                            Alignment.Top
                    ) {
                        Checkbox(
                            checked =
                                acknowledged,
                            onCheckedChange = {
                                acknowledged = it
                            },
                            enabled = !busy
                        )
                        Spacer(
                            Modifier.width(
                                Spacing.xs
                            )
                        )
                        Text(
                            text =
                                "I understand this archive contains confidential school data and I am responsible for storing and sharing it securely.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled =
                    acknowledged &&
                        !busy
            ) {
                Text(
                    "Choose save location"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IncludedRow(
    text: String
) {
    Row(
        verticalAlignment =
            Alignment.Top
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .primary,
            modifier =
                Modifier.size(18.dp)
        )
        Spacer(
            Modifier.width(Spacing.sm)
        )
        Text(
            text = text,
            style =
                MaterialTheme.typography
                    .bodyMedium
        )
    }
}

@Composable
private fun ExcludedRow(
    text: String
) {
    Row(
        verticalAlignment =
            Alignment.Top
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.size(18.dp)
        )
        Spacer(
            Modifier.width(Spacing.sm)
        )
        Text(
            text = text,
            style =
                MaterialTheme.typography
                    .bodyMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}
