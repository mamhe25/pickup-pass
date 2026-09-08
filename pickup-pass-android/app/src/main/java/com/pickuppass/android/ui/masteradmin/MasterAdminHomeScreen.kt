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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.NotificationActionButton
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
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
    onSignedOut: () -> Unit
) {
    var section by remember { mutableStateOf(MasterAdminSection.OVERVIEW) }
    var showPlatformTools by remember { mutableStateOf(false) }

    if (section == MasterAdminSection.ADVANCED) {
        MasterAdminAdvancedConsole(
            viewModel = viewModel,
            onOpenProfile = onOpenProfile,
            onOpenNotifications = onOpenNotifications,
            onSignedOut = onSignedOut,
            onBackToOverview = { section = MasterAdminSection.OVERVIEW }
        )
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner,
        viewModel
    ) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshNotificationCount()
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 7.dp
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        "PLATFORM OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f)
                    )
                    Text(
                        "${state.activeSchools} active schools",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${state.totalSchools} total · ${state.suspendedSchools} suspended",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .76f)
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MasterMetric(
                    "Attention",
                    state.operations?.metrics?.attentionNeededSchools?.toString() ?: "—",
                    Modifier.weight(1f)
                )
                MasterMetric(
                    "Security",
                    state.security?.metrics?.activeAlerts?.toString() ?: "—",
                    Modifier.weight(1f)
                )
                MasterMetric(
                    "5xx",
                    state.observability?.http?.errors5xx?.toString() ?: "—",
                    Modifier.weight(1f)
                )
            }
        }

        item { MasterAreaCard("Schools", "Tenant status, plans and launch state", onSchools) }
        item { MasterAreaCard("Operations", "Billing, quota, delivery and runtime health", onOperations) }
        item { MasterAreaCard("Security", "Authentication, sessions and privileged actions", onSecurity) }
        item { MasterAreaCard("Advanced platform tools", "Billing actions, recovery, exports and tenant administration", onAdvanced) }
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
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MasterMetric("Schools", total.toString(), Modifier.weight(1f))
                MasterMetric("Active", active.toString(), Modifier.weight(1f))
                MasterMetric("Suspended", suspended.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("School tenants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Quick health view. Use Advanced for management actions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onManage) { Text("Manage") }
            }
        }
        if (schools.isEmpty()) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("No school tenants yet.", Modifier.padding(Spacing.lg))
                }
            }
        } else {
            items(schools, key = { it.schoolId }) { school ->
                MasterSchoolSummary(school)
            }
        }
    }
}

@Composable
private fun MasterOperations(
    state: MasterAdminUiState,
    onAdvanced: () -> Unit
) {
    val operations = state.operations
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Operations health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Billing, quota and delivery risk across tenants.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (operations == null) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("Operations data unavailable.", Modifier.padding(Spacing.lg))
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MasterMetric("Healthy", operations.metrics.healthySchools.toString(), Modifier.weight(1f))
                    MasterMetric("Attention", operations.metrics.attentionNeededSchools.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MasterMetric("Billing risk", operations.metrics.billingRiskSchools.toString(), Modifier.weight(1f))
                    MasterMetric("Over quota", operations.metrics.overQuotaSchools.toString(), Modifier.weight(1f))
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            "${operations.alerts.size} active operational alert(s)",
                            fontWeight = FontWeight.Bold,
                            color = if (operations.alerts.isEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Pending GCash ${operations.metrics.pendingGcashReviews} · " +
                                "Overdue invoices ${operations.metrics.overdueInvoices} · " +
                                "Quota warnings ${operations.metrics.quotaWarnings}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                Text("Open operational actions")
            }
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
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Security center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Authentication, session and privileged-action monitoring.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (security == null) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("Security data unavailable.", Modifier.padding(Spacing.lg))
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MasterMetric("Active", security.metrics.activeAlerts.toString(), Modifier.weight(1f))
                    MasterMetric("High", security.metrics.high.toString(), Modifier.weight(1f))
                    MasterMetric("Medium", security.metrics.medium.toString(), Modifier.weight(1f))
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            if (security.alerts.isEmpty())
                                "No active security alerts"
                            else
                                "${security.alerts.size} security alert(s) need review",
                            fontWeight = FontWeight.Bold,
                            color = if (security.alerts.isEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Open ${security.metrics.openAlerts} · Acknowledged ${security.metrics.acknowledged}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                Text("Open security actions")
            }
        }
    }
}

@Composable
private fun MasterMetric(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MasterAreaCard(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MasterSchoolSummary(school: MasterSchoolItem) {
    val active = school.status == "active"
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Business,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(school.schoolName, fontWeight = FontWeight.Bold)
                Text(
                    "${school.plan.replaceFirstChar { it.uppercase() }} · " +
                        school.subscriptionStatus.replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Launch: ${school.launchStatus.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (active) "Active" else "Suspended",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
