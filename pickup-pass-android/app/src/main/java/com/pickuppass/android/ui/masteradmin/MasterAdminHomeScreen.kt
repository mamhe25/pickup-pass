package com.pickuppass.android.ui.masteradmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
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
    onSignedOut: () -> Unit
) {
    var section by remember { mutableStateOf(MasterAdminSection.OVERVIEW) }

    if (section == MasterAdminSection.ADVANCED) {
        MasterAdminAdvancedConsole(
            viewModel = viewModel,
            onSignedOut = onSignedOut,
            onBackToOverview = { section = MasterAdminSection.OVERVIEW }
        )
        return
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PickupPass Control Center")
                        Text(
                            "Platform owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !state.loading && !state.saving) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.signOut(); onSignedOut() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(vertical = Spacing.sm)
            ) {
                items(MasterAdminSection.entries) { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (item) {
                                    MasterAdminSection.OVERVIEW -> Icons.Filled.Dashboard
                                    MasterAdminSection.SCHOOLS -> Icons.Filled.Business
                                    MasterAdminSection.OPERATIONS -> Icons.Filled.Speed
                                    MasterAdminSection.SECURITY -> Icons.Filled.Security
                                    MasterAdminSection.ADVANCED -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    )
                }
            }

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
                    onRefresh = viewModel::refreshOperations,
                    onAdvanced = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.SECURITY -> MasterSecurity(
                    state,
                    onRefresh = { viewModel.loadSecurity() },
                    onAdvanced = { section = MasterAdminSection.ADVANCED }
                )
                MasterAdminSection.ADVANCED -> Unit
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
    onRefresh: () -> Unit,
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
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = !state.saving && !state.operationsLoading
                ) { Text(if (state.operationsLoading) "Refreshing…" else "Refresh") }
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
    onRefresh: () -> Unit,
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
                FilledTonalButton(onClick = onRefresh, enabled = !state.securityLoading) {
                    Text(if (state.securityLoading) "Refreshing…" else "Refresh")
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
