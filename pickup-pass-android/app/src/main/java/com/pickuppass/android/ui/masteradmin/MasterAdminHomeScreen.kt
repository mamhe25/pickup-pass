package com.pickuppass.android.ui.masteradmin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.NotificationActionButton
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.PremiumHeroCard
import com.pickuppass.android.ui.common.PremiumSectionHeader
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.theme.Spacing

private enum class MasterAdminSection(val label: String) {
    OVERVIEW("Overview"),
    SCHOOLS("Schools"),
    OPERATIONS("Operations"),
    SECURITY("Security"),
    ADVANCED("Advanced")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterAdminScreen(
    viewModel: MasterAdminViewModel = hiltViewModel(),
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignedOut: () -> Unit,
    initialLaunchReadinessSchoolId: String? = null
) {
    var section by remember { mutableStateOf(MasterAdminSection.OVERVIEW) }
    var showPlatformTools by remember { mutableStateOf(false) }

    LaunchedEffect(initialLaunchReadinessSchoolId) {
        if (!initialLaunchReadinessSchoolId.isNullOrBlank()) {
            section = MasterAdminSection.ADVANCED
        }
    }

    if (section == MasterAdminSection.ADVANCED) {
        MasterAdminAdvancedConsole(
            viewModel = viewModel,
            onOpenProfile = onOpenProfile,
            onOpenNotifications = onOpenNotifications,
            onSignedOut = onSignedOut,
            initialLaunchReadinessSchoolId = initialLaunchReadinessSchoolId,
            onBackToOverview = { section = MasterAdminSection.OVERVIEW }
        )
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title =
                    if (section == MasterAdminSection.OVERVIEW) {
                        "PickupPass control center"
                    } else {
                        section.label
                    },
                subtitle = "Platform owner",
                onBack =
                    if (section == MasterAdminSection.OVERVIEW) {
                        null
                    } else {
                        { section = MasterAdminSection.OVERVIEW }
                    },
                actions = {
                    NotificationActionButton(
                        unreadCount = state.unreadNotifications,
                        onClick = onOpenNotifications,
                    )
                    IconButton(onClick = { showPlatformTools = true }) {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = "Open platform tools"
                        )
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "My profile"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading && state.schools.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        PickupPassPullToRefresh(
            refreshing = state.loading,
            onRefresh = {
                if (!state.saving) {
                    viewModel.load()
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            state.error?.let {
                ErrorBanner(it, modifier = Modifier.padding(horizontal = Spacing.md))
            }

            when (section) {
                MasterAdminSection.OVERVIEW -> MasterOverview(
                    state,
                    onSchools = { section = MasterAdminSection.SCHOOLS },
                    onOperations = { section = MasterAdminSection.OPERATIONS },
                    onSecurity = { section = MasterAdminSection.SECURITY },
                    onAdvanced = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.SCHOOLS -> MasterSchools(
                    state.schools,
                    state.totalSchools,
                    state.activeSchools,
                    state.suspendedSchools,
                    onManage = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.OPERATIONS -> MasterOperations(
                    state,
                    onAdvanced = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.SECURITY -> MasterSecurity(
                    state,
                    onAdvanced = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.ADVANCED -> Unit
            }
            }
        }
    }

    if (showPlatformTools) {
        PlatformToolsSheet(
            currentSection = section,
            onDismiss = { showPlatformTools = false },
            onSelect = { selected ->
                showPlatformTools = false
                section = selected
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformToolsSheet(
    currentSection: MasterAdminSection,
    onDismiss: () -> Unit,
    onSelect: (MasterAdminSection) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "PLATFORM TOOLS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Control center",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Choose an area to review. Primary navigation stays out of the way until you need it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.xs))

            MasterAdminSection.entries.forEach { item ->
                val selected = currentSection == item
                OutlinedCard(
                    onClick = { onSelect(item) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor =
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = MaterialTheme.shapes.medium,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            contentColor =
                                if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (item) {
                                        MasterAdminSection.OVERVIEW -> Icons.Filled.Dashboard
                                        MasterAdminSection.SCHOOLS -> Icons.Filled.Business
                                        MasterAdminSection.OPERATIONS -> Icons.Filled.Speed
                                        MasterAdminSection.SECURITY -> Icons.Filled.Security
                                        MasterAdminSection.ADVANCED -> Icons.Filled.Settings
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (item) {
                                    MasterAdminSection.OVERVIEW ->
                                        "Platform summary and areas requiring attention"
                                    MasterAdminSection.SCHOOLS ->
                                        "Tenant status, plans and launch state"
                                    MasterAdminSection.OPERATIONS ->
                                        "Billing, quota, delivery and runtime health"
                                    MasterAdminSection.SECURITY ->
                                        "Authentication, sessions and privileged actions"
                                    MasterAdminSection.ADVANCED ->
                                        "Recovery, exports and tenant administration"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selected) {
                            AssistChip(
                                onClick = { onSelect(item) },
                                label = { Text("Current") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterOverview(
    state: MasterAdminUiState,
    onSchools: () -> Unit,
    onOperations: () -> Unit,
    onSecurity: () -> Unit,
    onAdvanced: () -> Unit
) {
    val attention = state.operations?.metrics?.attentionNeededSchools ?: 0
    val alerts = state.security?.metrics?.activeAlerts ?: 0
    val httpErrors = state.observability?.http?.errors5xx ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            top = Spacing.md,
            end = Spacing.md,
            bottom = Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PremiumHeroCard(
                eyebrow = "Platform overview",
                title =
                    if (attention == 0 && alerts == 0 && httpErrors == 0L) {
                        "Platform is operating normally"
                    } else {
                        "${attention + alerts + httpErrors} signal(s) need review"
                    },
                message =
                    "${state.activeSchools} active of ${state.totalSchools} schools. " +
                        "Start with high-signal health, then open a focused workspace for deeper actions.",
                icon = Icons.Filled.Dashboard
            )
        }

        item {
            PremiumSectionHeader(
                title = "Platform pulse",
                subtitle = "A concise view of tenant, operational and security health."
            )
        }

        item {
            MasterMetricPair(
                firstLabel = "Active schools",
                firstValue = state.activeSchools.toString(),
                firstIcon = Icons.Filled.Business,
                secondLabel = "Need attention",
                secondValue = state.operations?.metrics?.attentionNeededSchools?.toString() ?: "—",
                secondIcon = Icons.Filled.WarningAmber
            )
        }

        item {
            MasterMetricPair(
                firstLabel = "Security alerts",
                firstValue = state.security?.metrics?.activeAlerts?.toString() ?: "—",
                firstIcon = Icons.Filled.Security,
                secondLabel = "HTTP 5xx",
                secondValue = state.observability?.http?.errors5xx?.toString() ?: "—",
                secondIcon = Icons.Filled.ErrorOutline
            )
        }

        item {
            val healthy = attention == 0 && alerts == 0 && state.suspendedSchools == 0
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color =
                    if (healthy) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .52f)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (healthy) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint =
                            if (healthy) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (healthy) "No priority issues detected" else "Review priority signals",
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (healthy) {
                                "${state.activeSchools} active schools are clear of monitored priority conditions."
                            } else {
                                "${state.suspendedSchools} suspended · $attention operational · $alerts security"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            PremiumSectionHeader(
                title = "Workspaces",
                subtitle = "Routine monitoring stays separate from privileged administration."
            )
        }

        item {
            MasterAreaCard(
                title = "Schools",
                subtitle = "Tenant status, subscription state and launch readiness.",
                icon = Icons.Filled.Business,
                badge = "${state.activeSchools}/${state.totalSchools} active",
                onClick = onSchools
            )
        }
        item {
            MasterAreaCard(
                title = "Operations",
                subtitle = "Billing risk, quotas, delivery and backend health.",
                icon = Icons.Filled.Speed,
                badge = if (attention > 0) "$attention need review" else "Healthy",
                onClick = onOperations
            )
        }
        item {
            MasterAreaCard(
                title = "Security",
                subtitle = "Authentication alerts and privileged activity.",
                icon = Icons.Filled.Security,
                badge = if (alerts > 0) "$alerts active" else "No active alerts",
                onClick = onSecurity
            )
        }
        item {
            MasterAreaCard(
                title = "Advanced platform tools",
                subtitle = "Tenant administration, billing, exports and recovery.",
                icon = Icons.Filled.Settings,
                badge = "Privileged",
                onClick = onAdvanced
            )
        }
    }
}

@Composable
private fun MasterSchools(
    schools: List<MasterSchoolItem>,
    total: Int,
    active: Int,
    suspended: Int,
    onManage: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            top = Spacing.md,
            end = Spacing.md,
            bottom = Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PremiumHeroCard(
                eyebrow = "Tenant portfolio",
                title = "$active active schools",
                message =
                    "$total total tenants · $suspended suspended. " +
                        "Review portfolio health here, then open privileged controls only when action is required.",
                icon = Icons.Filled.Business
            )
        }

        item {
            PremiumSectionHeader(
                title = "Tenant health",
                subtitle = "Status and launch posture without destructive controls."
            )
        }

        item {
            MasterMetricPair(
                firstLabel = "Active",
                firstValue = active.toString(),
                firstIcon = Icons.Filled.CheckCircle,
                secondLabel = "Suspended",
                secondValue = suspended.toString(),
                secondIcon = Icons.Filled.WarningAmber
            )
        }

        item {
            PremiumSectionHeader(
                title = "School tenants",
                subtitle = "Subscription, launch and account status at a glance.",
                trailing = {
                    FilledTonalButton(onClick = onManage) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Manage")
                    }
                }
            )
        }

        if (schools.isEmpty()) {
            item {
                MasterEmptyState(
                    icon = Icons.Filled.Business,
                    title = "No schools yet",
                    message = "Create the first school from Advanced platform tools."
                )
            }
        } else {
            items(schools, key = { it.schoolId }) { school ->
                MasterSchoolSummary(school)
            }
        }

        item {
            MasterInfoCard(
                icon = Icons.Filled.Settings,
                title = "Privileged tenant actions stay separate",
                message =
                    "Creating schools, changing plans, billing, exports and launch controls remain in Advanced so portfolio review stays low-risk."
            )
        }
    }
}

@Composable
private fun MasterOperations(
    state: MasterAdminUiState,
    onAdvanced: () -> Unit
) {
    val operations = state.operations
    val observability = state.observability

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            top = Spacing.md,
            end = Spacing.md,
            bottom = Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PremiumHeroCard(
                eyebrow = "Operations",
                title =
                    if (operations != null && operations.alerts.isEmpty()) {
                        "No active operational alerts"
                    } else {
                        "${operations?.alerts?.size ?: 0} operational alert(s)"
                    },
                message =
                    "Track billing, quota and runtime signals before opening tenant-level remediation tools.",
                icon = Icons.Filled.Speed
            )
        }

        if (operations == null) {
            item {
                MasterEmptyState(
                    icon = Icons.Filled.Speed,
                    title = "Operations data unavailable",
                    message = "Pull to refresh. Privileged actions remain available from Advanced."
                )
            }
        } else {
            item {
                PremiumSectionHeader(
                    title = "Tenant operations",
                    subtitle = "High-signal portfolio health across billing and usage."
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Healthy",
                    firstValue = operations.metrics.healthySchools.toString(),
                    firstIcon = Icons.Filled.CheckCircle,
                    secondLabel = "Need attention",
                    secondValue = operations.metrics.attentionNeededSchools.toString(),
                    secondIcon = Icons.Filled.WarningAmber
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Billing risk",
                    firstValue = operations.metrics.billingRiskSchools.toString(),
                    firstIcon = Icons.Filled.ReceiptLong,
                    secondLabel = "Over quota",
                    secondValue = operations.metrics.overQuotaSchools.toString(),
                    secondIcon = Icons.Filled.Storage
                )
            }

            item {
                MasterSnapshotCard(
                    icon = Icons.Filled.ReceiptLong,
                    eyebrow = "OPERATIONS SNAPSHOT",
                    title =
                        if (operations.alerts.isEmpty()) {
                            "No operational alerts"
                        } else {
                            "${operations.alerts.size} alert(s) need review"
                        },
                    rows = listOf(
                        "Pending GCash reviews" to operations.metrics.pendingGcashReviews.toString(),
                        "Overdue invoices" to operations.metrics.overdueInvoices.toString(),
                        "Quota warnings" to operations.metrics.quotaWarnings.toString()
                    ),
                    warning = operations.alerts.isNotEmpty()
                )
            }
        }

        observability?.let { health ->
            item {
                PremiumSectionHeader(
                    title = "Runtime health",
                    subtitle = "Lightweight backend telemetry for the current service instance."
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Uptime",
                    firstValue = compactMasterUptime(health.runtime.uptimeSeconds),
                    firstIcon = Icons.Filled.Speed,
                    secondLabel = "5xx errors",
                    secondValue = health.http.errors5xx.toString(),
                    secondIcon = Icons.Filled.ErrorOutline
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Memory used",
                    firstValue = "${health.memory.usedPercent}%",
                    firstIcon = Icons.Filled.Storage,
                    secondLabel = "Requests",
                    secondValue = health.http.requests.toString(),
                    secondIcon = Icons.Filled.Dashboard
                )
            }
        }

        item {
            MasterAdvancedActionCard(
                icon = Icons.Filled.Settings,
                title = "Operational actions",
                message =
                    "Review payments, subscriptions, tenant limits and incident actions from the privileged console.",
                actionLabel = "Open Advanced",
                onClick = onAdvanced
            )
        }
    }
}

@Composable
private fun MasterSecurity(
    state: MasterAdminUiState,
    onAdvanced: () -> Unit
) {
    val security = state.security

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            top = Spacing.md,
            end = Spacing.md,
            bottom = Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            PremiumHeroCard(
                eyebrow = "Security",
                title =
                    if (security != null && security.alerts.isEmpty()) {
                        "No active security alerts"
                    } else {
                        "${security?.metrics?.activeAlerts ?: 0} active alert(s)"
                    },
                message =
                    "Monitor authentication, session and privileged-action signals without mixing them into routine tenant administration.",
                icon = Icons.Filled.Security
            )
        }

        if (security == null) {
            item {
                MasterEmptyState(
                    icon = Icons.Filled.Security,
                    title = "Security data unavailable",
                    message = "Pull to refresh or open Advanced for privileged controls."
                )
            }
        } else {
            item {
                PremiumSectionHeader(
                    title = "Alert posture",
                    subtitle = "Current unresolved authentication and session risk."
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Active alerts",
                    firstValue = security.metrics.activeAlerts.toString(),
                    firstIcon = Icons.Filled.Security,
                    secondLabel = "High severity",
                    secondValue = security.metrics.high.toString(),
                    secondIcon = Icons.Filled.ErrorOutline
                )
            }

            item {
                MasterMetricPair(
                    firstLabel = "Medium",
                    firstValue = security.metrics.medium.toString(),
                    firstIcon = Icons.Filled.WarningAmber,
                    secondLabel = "Acknowledged",
                    secondValue = security.metrics.acknowledged.toString(),
                    secondIcon = Icons.Filled.CheckCircle
                )
            }

            item {
                MasterSnapshotCard(
                    icon = Icons.Filled.Security,
                    eyebrow = "SECURITY SNAPSHOT",
                    title =
                        if (security.metrics.openAlerts == 0) {
                            "No open alerts"
                        } else {
                            "${security.metrics.openAlerts} open alert(s)"
                        },
                    rows = listOf(
                        "Open" to security.metrics.openAlerts.toString(),
                        "Acknowledged" to security.metrics.acknowledged.toString(),
                        "Recent privileged actions" to security.recentPrivilegedActions.size.toString()
                    ),
                    warning = security.metrics.openAlerts > 0
                )
            }
        }

        item {
            MasterAdvancedActionCard(
                icon = Icons.Filled.Security,
                title = "Security actions",
                message =
                    "Acknowledge alerts, revoke sessions and inspect privileged audit activity from Advanced.",
                actionLabel = "Open Advanced",
                onClick = onAdvanced
            )
        }
    }
}

@Composable
private fun MasterMetricPair(
    firstLabel: String,
    firstValue: String,
    firstIcon: androidx.compose.ui.graphics.vector.ImageVector,
    secondLabel: String,
    secondValue: String,
    secondIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        MasterMetric(
            label = firstLabel,
            value = firstValue,
            icon = firstIcon,
            modifier = Modifier.weight(1f)
        )
        MasterMetric(
            label = secondLabel,
            value = secondValue,
            icon = secondIcon,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MasterMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MasterAreaCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(
                        horizontal = Spacing.sm,
                        vertical = Spacing.xs
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MasterSchoolSummary(school: MasterSchoolItem) {
    val active = school.status == "active"
    val launchApproved = school.launchStatus == "approved"

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = .28f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color =
                        if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Business,
                            contentDescription = null,
                            tint =
                                if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        school.schoolName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        school.plan.replaceFirstChar { it.uppercase() } + " plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color =
                        if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                ) {
                    Text(
                        if (active) "Active" else "Suspended",
                        modifier = Modifier.padding(
                            horizontal = Spacing.sm,
                            vertical = Spacing.xs
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color =
                            if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SUBSCRIPTION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        school.subscriptionStatus
                            .replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "LAUNCH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (launchApproved) {
                            "Approved"
                        } else {
                            school.launchStatus
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (launchApproved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                    )
                }
            }

            if (!school.subscriptionAccessActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .62f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "Subscription access is restricted",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterSnapshotCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    eyebrow: String,
    title: String,
    rows: List<Pair<String, String>>,
    warning: Boolean
) {
    val accent =
        if (warning) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, accent.copy(alpha = .24f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = accent.copy(alpha = .1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MasterAdvancedActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun MasterInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MasterEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun compactMasterUptime(seconds: Long): String {
    val days = seconds / 86_400
    if (days > 0) return "${days}d"
    val hours = seconds / 3_600
    if (hours > 0) return "${hours}h"
    val minutes = seconds / 60
    return "${minutes}m"
}
