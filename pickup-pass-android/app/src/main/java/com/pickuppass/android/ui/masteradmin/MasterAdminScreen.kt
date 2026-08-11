package com.pickuppass.android.ui.masteradmin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.MasterPlanDefinition
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.data.model.MasterInvoiceItem
import com.pickuppass.android.data.model.MasterBillingProfileResponse
import com.pickuppass.android.data.model.GcashPaymentNoticeItem
import com.pickuppass.android.data.model.MasterOperationalAlert
import com.pickuppass.android.data.model.MasterTenantHealthItem
import com.pickuppass.android.data.model.MasterSecurityAlert
import com.pickuppass.android.data.model.MasterPrivilegedAuditEvent
import com.pickuppass.android.data.model.MasterBackupItem
import com.pickuppass.android.data.model.MasterRecoveryJobItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterAdminScreen(
    viewModel: MasterAdminViewModel = hiltViewModel(),
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val payload = state.invoicePdf
        if (uri != null && payload != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(payload.bytes) } }
        }
        viewModel.clearInvoicePdf()
    }
    LaunchedEffect(state.invoicePdf?.fileName) {
        state.invoicePdf?.let { pdfLauncher.launch(it.fileName) }
    }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val payload = state.dataExportZip
        if (uri != null && payload != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(payload.bytes) } }
        }
        viewModel.clearDataExportZip()
    }
    LaunchedEffect(state.dataExportZip?.fileName) {
        state.dataExportZip?.let { zipLauncher.launch(it.fileName) }
    }
    var createSchool by remember { mutableStateOf(false) }
    var adminForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }
    var subscriptionForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }
    var billingForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }
    var revokeSecurityUser by remember { mutableStateOf<MasterSecurityAlert?>(null) }
    var recoveryProtectionChoice by remember { mutableStateOf<String?>(null) }
    var recoveryBackup by remember { mutableStateOf<MasterBackupItem?>(null) }
    val healthBySchool = state.operations?.tenants?.associateBy { it.schoolId }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PickupPass SaaS Console") },
                actions = {
                    IconButton(onClick = { viewModel.signOut(); onSignedOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createSchool = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create school")
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricCard("Schools", state.totalSchools.toString(), Modifier.weight(1f))
                    MetricCard("Active", state.activeSchools.toString(), Modifier.weight(1f))
                    MetricCard("Suspended", state.suspendedSchools.toString(), Modifier.weight(1f))
                }
            }
            state.operations?.let { operations ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Operations health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Actionable subscription, billing, quota, and delivery risks across all tenants.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(onClick = { viewModel.refreshOperations() }, enabled = !state.saving && !state.operationsLoading) {
                                Text("Refresh")
                            }
                        }
                        if (state.operationsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MetricCard("Healthy", operations.metrics.healthySchools.toString(), Modifier.weight(1f))
                            MetricCard("Needs attention", operations.metrics.attentionNeededSchools.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MetricCard("Billing risk", operations.metrics.billingRiskSchools.toString(), Modifier.weight(1f))
                            MetricCard("Over quota", operations.metrics.overQuotaSchools.toString(), Modifier.weight(1f))
                        }
                    }
                }
                if (operations.alerts.isNotEmpty()) {
                    item {
                        Text("Needs your attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    items(operations.alerts.take(8), key = { "ops-" + it.alertId }) { alert ->
                        OperationsAlertCard(
                            alert = alert,
                            saving = state.saving,
                            onReview = {
                                val school = state.schools.firstOrNull { it.schoolId == alert.schoolId }
                                if (school != null) {
                                    when (alert.action) {
                                        "review_payment", "billing" -> {
                                            billingForSchool = school
                                            viewModel.loadInvoices(school.schoolId)
                                        }
                                        "subscription", "usage" -> subscriptionForSchool = school
                                    }
                                }
                            }
                        )
                    }
                } else {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Text(
                                "No active operational alerts. All monitored tenants are currently healthy.",
                                modifier = Modifier.padding(Spacing.md),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    val m = operations.metrics
                    Text(
                        "Pending GCash ${m.pendingGcashReviews} · Overdue invoices ${m.overdueInvoices} · Expiring subscriptions ${m.expiringSubscriptions} · Quota warnings ${m.quotaWarnings} · Email failures ${m.billingEmailFailures}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.security?.let { security ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Security Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Privacy-preserving authentication, session, and privileged-action monitoring.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.loadSecurity() }, enabled = !state.securityLoading) {
                            Text(if (state.securityLoading) "Loading…" else "Refresh")
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MetricCard("Active alerts", security.metrics.activeAlerts.toString(), Modifier.weight(1f))
                        MetricCard("High", security.metrics.high.toString(), Modifier.weight(1f))
                        MetricCard("Medium", security.metrics.medium.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "Open ${security.metrics.openAlerts} · Acknowledged ${security.metrics.acknowledged}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (security.alerts.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Text("No active security alerts.", Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(security.alerts.take(10), key = { "security-" + it.id }) { alert ->
                        SecurityAlertCard(
                            alert = alert,
                            saving = state.saving,
                            onAcknowledge = { viewModel.acknowledgeSecurityAlert(alert.id) },
                            onResolve = { viewModel.resolveSecurityAlert(alert.id, "Reviewed and resolved") },
                            onRevokeSessions = { revokeSecurityUser = alert }
                        )
                    }
                }
                if (security.recentPrivilegedActions.isNotEmpty()) {
                    item { Text("Recent privileged actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(security.recentPrivilegedActions.take(8), key = { "audit-" + it.id }) { event ->
                        PrivilegedActionCard(event)
                    }
                }
            }

            state.disasterRecovery?.let { recovery ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup & Disaster Recovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Native Firestore protection, retention guardrails, and isolated recovery drills.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.loadDisasterRecovery() }, enabled = !state.disasterRecoveryLoading) {
                            Text(if (state.disasterRecoveryLoading) "Loading…" else "Refresh")
                        }
                    }
                }
                if (!recovery.enabled) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("Recovery integration not enabled", fontWeight = FontWeight.SemiBold)
                                Text(
                                    recovery.message ?: "Configure the backend disaster-recovery environment variables before enabling this control.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!recovery.configured) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("Recovery configuration needs attention", fontWeight = FontWeight.SemiBold)
                                Text(recovery.message ?: "PickupPass could not read the Firestore recovery configuration.")
                            }
                        }
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            MetricCard("PITR", if (recovery.pitrEnabled) "On" else "Off", Modifier.weight(1f))
                            MetricCard("Delete protect", if (recovery.deleteProtectionEnabled) "On" else "Off", Modifier.weight(1f))
                            MetricCard("Ready backups", recovery.backups.count { it.state == "READY" }.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("Protection profile", fontWeight = FontWeight.SemiBold)
                                Text("Database ${recovery.databaseId} · ${recovery.locationId ?: "location unavailable"}")
                                Text(
                                    if (recovery.latestBackupAgeHours >= 0)
                                        "Health ${recovery.healthState.ifBlank { if (recovery.protectionHealthy) "healthy" else "warning" }} · latest READY backup ${recovery.latestBackupAgeHours}h old · target ≤ ${recovery.maxBackupAgeHours}h"
                                    else
                                        "Health ${recovery.healthState.ifBlank { "warning" }} · no READY backup age available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (recovery.protectionHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Daily ${recovery.dailySchedule?.retentionDays ?: 0} days · Weekly ${recovery.weeklySchedule?.retentionDays ?: 0} days",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "PITR version window: ${recovery.versionRetentionPeriod ?: "not reported"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Active profile: ${recovery.activeProfile.replaceFirstChar { it.uppercase() }}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Platform owner controls native Firestore backup. School admins cannot enable PITR, schedules, or restore databases.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (recovery.paidProtectionStillEnabled && recovery.activeProfile != "growth") {
                                    Text(
                                        "A paid/growth protection component is already enabled (PITR or weekly backup). Startup/free profile actions preserve stronger existing protection instead of silently disabling it. Review Google Cloud billing before turning anything off manually.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (recovery.databaseProtectionUpdatePending) {
                                    Text(
                                        "Database protection update is still being applied by Google Cloud. Refresh this page shortly.",
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        OutlinedButton(
                                            onClick = { recoveryProtectionChoice = "free" },
                                            enabled = !state.saving && !recovery.deleteProtectionEnabled
                                        ) { Text("Free safeguards") }
                                        FilledTonalButton(
                                            onClick = { recoveryProtectionChoice = "startup" },
                                            enabled = !state.saving && (!recovery.deleteProtectionEnabled || recovery.dailySchedule == null)
                                        ) { Text("Startup backup") }
                                    }
                                    TextButton(
                                        onClick = { recoveryProtectionChoice = "growth" },
                                        enabled = !state.saving && (!recovery.pitrEnabled || recovery.weeklySchedule == null)
                                    ) { Text("Growth protection (paid services)") }
                                    if (recovery.databaseProtectionUpdateStatus == "failed") {
                                        Text("The previous database-protection operation failed. Review Google Cloud/IAM before retrying.", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Recent Firestore backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (recovery.backups.isEmpty()) {
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Text(
                                    "No backup is visible yet. A newly created schedule may need to complete its first backup before a recovery drill is available.",
                                    Modifier.padding(Spacing.md),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(recovery.backups.take(6), key = { "backup-" + it.name }) { backup ->
                            BackupItemCard(
                                backup = backup,
                                allowRestoreDrills = recovery.allowRestoreDrills,
                                saving = state.saving,
                                onTestRestore = { recoveryBackup = backup }
                            )
                        }
                    }
                    if (recovery.recoveryJobs.isNotEmpty()) {
                        item { Text("Recovery drills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        items(recovery.recoveryJobs.take(6), key = { "dr-job-" + it.jobId }) { job ->
                            RecoveryJobCard(job = job, saving = state.saving, onRefresh = { viewModel.refreshRecoveryDrill(job.jobId) })
                        }
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("Retention guardrails", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Technical retry/security telemetry can use TTL. Student release, audit, and financial records are never auto-deleted by PickupPass without an explicit retention policy.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                recovery.retentionPolicies.filter { it.ttlEligible }.forEach { policy ->
                                    Text("• ${policy.collection}: ${policy.retentionDays} days via ${policy.ttlField}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let { item { ErrorBanner(it) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item {
                Text("Tenant management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Manage school access, subscription plans, feature flags, and initial administrators.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.schools.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.lg)) {
                            Text("No schools yet", fontWeight = FontWeight.SemiBold)
                            Text("Tap + to create the first PickupPass tenant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(state.schools, key = { it.schoolId }) { school ->
                SchoolCard(
                    school = school,
                    health = healthBySchool[school.schoolId],
                    saving = state.saving,
                    onToggle = { viewModel.setSchoolActive(school.schoolId, school.status != "active") },
                    onAddAdmin = { adminForSchool = school },
                    onManageSubscription = { subscriptionForSchool = school },
                    onBilling = { billingForSchool = school; viewModel.loadInvoices(school.schoolId) },
                    onReconcileSubscription = { viewModel.reconcileSubscription(school.schoolId) },
                    onToggleExportAccess = { viewModel.setSchoolDataExportAccess(school.schoolId, !school.selfServiceDataExportEnabled) },
                    onExportData = { viewModel.downloadSchoolDataExport(school) }
                )
            }
        }
    }

    recoveryProtectionChoice?.let { profile ->
        val title = when (profile) {
            "free" -> "Enable free safeguards"
            "growth" -> "Enable growth protection"
            else -> "Enable startup backup"
        }
        val description = when (profile) {
            "free" -> "Enables Firestore database delete protection only. PickupPass does not create a scheduled backup or enable PITR in this profile."
            "growth" -> "Enables daily and weekly native backups, PITR, and delete protection. Google Cloud charges may apply. Use this after the platform has paying schools and stronger recovery requirements."
            else -> "Recommended for the startup stage: enables delete protection and one daily Firestore backup with short retention. It does not enable PITR or create a weekly backup."
        }
        AlertDialog(
            onDismissRequest = { recoveryProtectionChoice = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(description)
                    Text(
                        "This is a platform-level infrastructure setting. Tenant/school administrators cannot change it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (profile == "startup") {
                        Text("Existing stronger protections are preserved; this action does not silently turn off an already-enabled PITR or weekly schedule.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(enabled = !state.saving, onClick = {
                    when (profile) {
                        "free" -> viewModel.applyFreeRecoveryProtection()
                        "growth" -> viewModel.applyRecommendedRecoveryProtection()
                        else -> viewModel.applyStartupRecoveryProtection()
                    }
                    recoveryProtectionChoice = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { recoveryProtectionChoice = null }) { Text("Cancel") } }
        )
    }

    recoveryBackup?.let { backup ->
        var reason by remember(backup.name) { mutableStateOf("") }
        var confirmation by remember(backup.name) { mutableStateOf("") }
        val requiredPhrase = "RESTORE TO ISOLATED DATABASE"
        AlertDialog(
            onDismissRequest = { recoveryBackup = null },
            title = { Text("Start isolated recovery drill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("This creates a separate Firestore database from the selected backup. PickupPass never switches production automatically.")
                    Text("Backup: ${backup.snapshotTime?.take(19) ?: backup.name.substringAfterLast('/')}" , fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason for drill") }, minLines = 2)
                    Text("Type $requiredPhrase to confirm.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmation") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.saving && reason.trim().length >= 10 && confirmation.trim() == requiredPhrase,
                    onClick = {
                        viewModel.startRecoveryDrill(backup.name, reason.trim(), confirmation.trim())
                        recoveryBackup = null
                    }
                ) { Text("Start recovery drill") }
            },
            dismissButton = { TextButton(onClick = { recoveryBackup = null }) { Text("Cancel") } }
        )
    }

    if (createSchool) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createSchool = false },
            title = { Text("Create school") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(name, { name = it }, label = { Text("School name") }, singleLine = true)
                    Text("New tenants start on a 30-day Trial plan.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && !state.saving, onClick = {
                    viewModel.createSchool(name); createSchool = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createSchool = false }) { Text("Cancel") } }
        )
    }

    adminForSchool?.let { school ->
        var email by remember(school.schoolId) { mutableStateOf("") }
        var first by remember(school.schoolId) { mutableStateOf("") }
        var last by remember(school.schoolId) { mutableStateOf("") }
        var middle by remember(school.schoolId) { mutableStateOf("") }
        var suffix by remember(school.schoolId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adminForSchool = null },
            title = { Text("Add administrator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(school.schoolName, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                    OutlinedTextField(first, { first = it }, label = { Text("First name") }, singleLine = true)
                    OutlinedTextField(last, { last = it }, label = { Text("Last name") }, singleLine = true)
                    OutlinedTextField(middle, { middle = it }, label = { Text("Middle initial (optional)") }, singleLine = true)
                    OutlinedTextField(suffix, { suffix = it }, label = { Text("Suffix (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = email.isNotBlank() && first.isNotBlank() && last.isNotBlank() && !state.saving,
                    onClick = {
                        viewModel.createSchoolAdmin(school.schoolId, email, last, first, middle, suffix)
                        adminForSchool = null
                    }
                ) { Text("Create admin") }
            },
            dismissButton = { TextButton(onClick = { adminForSchool = null }) { Text("Cancel") } }
        )
    }

    billingForSchool?.let { school ->
        BillingDialog(
            school = school,
            invoices = if (state.billingSchoolId == school.schoolId) state.invoices else emptyList(),
            paymentNotices = if (state.billingSchoolId == school.schoolId) state.gcashPaymentNotices else emptyList(),
            loading = state.billingLoading,
            saving = state.saving,
            billingProfile = state.billingProfile,
            onDismiss = { billingForSchool = null },
            onSaveProfile = { name, email, address, taxId -> viewModel.saveBillingProfile(school.schoolId, name, email, address, taxId) },
            onCreate = { amountMinor, dueAt, note -> viewModel.createInvoice(school.schoolId, amountMinor, dueAt, note) },
            onEmail = { invoiceId, recipient -> viewModel.emailInvoice(school.schoolId, invoiceId, recipient) },
            onDownloadPdf = { invoice -> viewModel.downloadInvoicePdf(invoice) },
            onPaid = { invoiceId, reference, method, note -> viewModel.markInvoicePaid(school.schoolId, invoiceId, reference, method, note) },
            onVoid = { invoiceId, reason -> viewModel.voidInvoice(school.schoolId, invoiceId, reason) },
            onConfirmGcash = { noticeId, note -> viewModel.confirmGcashPayment(school.schoolId, noticeId, note) },
            onRejectGcash = { noticeId, reason -> viewModel.rejectGcashPayment(school.schoolId, noticeId, reason) },
            onReconcile = { viewModel.reconcileOverdueInvoices(school.schoolId) }
        )
    }

    revokeSecurityUser?.let { alert ->
        var reason by remember(alert.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { revokeSecurityUser = null },
            title = { Text("Revoke all user sessions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("This signs the affected account out from all registered devices and revokes Firebase refresh tokens.")
                    Text("User: ${alert.uid ?: "Unknown"}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.saving && alert.uid != null && reason.trim().length >= 5,
                    onClick = {
                        alert.uid?.let { viewModel.revokeSecurityUserSessions(it, reason.trim()) }
                        revokeSecurityUser = null
                    }
                ) { Text("Revoke sessions") }
            },
            dismissButton = { TextButton(onClick = { revokeSecurityUser = null }) { Text("Cancel") } }
        )
    }

    subscriptionForSchool?.let { school ->
        SubscriptionDialog(
            school = school,
            plans = state.plans,
            featureKeys = state.featureKeys,
            saving = state.saving,
            onDismiss = { subscriptionForSchool = null },
            onSave = { plan, status, overrides, autoRenew, cancelAtPeriodEnd, startNewPeriod, extendTrialDays ->
                viewModel.updateSubscription(
                    school.schoolId, plan, status, overrides,
                    autoRenew, cancelAtPeriodEnd, startNewPeriod, extendTrialDays
                )
                subscriptionForSchool = null
            }
        )
    }
}

@Composable
private fun SubscriptionDialog(
    school: MasterSchoolItem,
    plans: Map<String, MasterPlanDefinition>,
    featureKeys: List<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Map<String, Boolean>, Boolean, Boolean, Boolean, Int) -> Unit
) {
    var selectedPlan by remember(school.schoolId) { mutableStateOf(school.plan) }
    var selectedStatus by remember(school.schoolId) { mutableStateOf(school.subscriptionStatus) }
    var customize by remember(school.schoolId) { mutableStateOf(school.featureOverrides.isNotEmpty()) }
    var featureValues by remember(school.schoolId) { mutableStateOf(school.features) }
    var autoRenew by remember(school.schoolId) { mutableStateOf(school.autoRenew) }
    var cancelAtPeriodEnd by remember(school.schoolId) { mutableStateOf(school.cancelAtPeriodEnd) }
    var startNewPeriod by remember(school.schoolId) { mutableStateOf(false) }
    var extendTrial30 by remember(school.schoolId) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plan & features") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item { Text(school.schoolName, fontWeight = FontWeight.SemiBold) }
                item { Text("Plan", fontWeight = FontWeight.SemiBold) }
                items(listOf("trial", "starter", "school", "enterprise")) { key ->
                    val definition = plans[key]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedPlan == key,
                            onClick = {
                                selectedPlan = key
                                if (!customize) featureValues = definition?.features ?: emptyMap()
                            }
                        )
                        Column {
                            Text(definition?.displayName ?: key.replaceFirstChar { it.uppercase() })
                            definition?.let {
                                Text(
                                    "Students ${limitLabel(it.maxStudents)} · Staff ${limitLabel(it.maxStaff)} · Campuses ${limitLabel(it.maxCampuses)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Text("Subscription status", fontWeight = FontWeight.SemiBold) }
                items(listOf("trialing", "active", "past_due", "cancelled")) { status ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedStatus == status, onClick = { selectedStatus = status })
                        Text(status.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Billing lifecycle", fontWeight = FontWeight.SemiBold)
                        school.trialEndsAt?.let { Text("Trial ends: ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                        school.currentPeriodEnd?.let { Text("Current period ends: ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                        school.graceEndsAt?.let { Text("Grace ends: ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Text(
                            if (school.subscriptionAccessActive) "Optional SaaS features available" else "Optional SaaS features blocked; core QR pickup remains available",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (school.subscriptionAccessActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoRenew, onCheckedChange = { autoRenew = it })
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text("Auto-renew billing period", fontWeight = FontWeight.SemiBold)
                            Text("Extends the 30-day service period automatically", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = cancelAtPeriodEnd, onCheckedChange = { cancelAtPeriodEnd = it })
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text("Cancel at period end", fontWeight = FontWeight.SemiBold)
                            Text("Current access stays until the period ends", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (selectedStatus == "active") {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = startNewPeriod, onCheckedChange = { startNewPeriod = it })
                            Text("Start a new 30-day billing period now")
                        }
                    }
                }
                if (selectedPlan == "trial") {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = extendTrial30, onCheckedChange = { extendTrial30 = it })
                            Text("Extend trial by 30 days")
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = customize,
                            onCheckedChange = {
                                customize = it
                                if (it) featureValues = plans[selectedPlan]?.features ?: school.features
                            }
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text("Customize feature access", fontWeight = FontWeight.SemiBold)
                            Text("Off = inherit the selected plan defaults", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (customize) {
                    items(featureKeys.sorted()) { feature ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(featureLabel(feature), Modifier.weight(1f))
                            Switch(
                                checked = featureValues[feature] == true,
                                onCheckedChange = { enabled -> featureValues = featureValues + (feature to enabled) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !saving && plans.containsKey(selectedPlan), onClick = {
                onSave(
                    selectedPlan,
                    selectedStatus,
                    if (customize) featureValues else emptyMap(),
                    autoRenew,
                    cancelAtPeriodEnd,
                    startNewPeriod,
                    if (extendTrial30) 30 else 0
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun limitLabel(value: Int): String = if (value < 0) "Unlimited" else value.toString()
private fun featureLabel(value: String): String = value.split('_').joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
private fun dateLabel(value: String): String = value.take(10)

@Composable
private fun SecurityAlertCard(
    alert: MasterSecurityAlert,
    saving: Boolean,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit,
    onRevokeSessions: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(alert.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(alert.severity.uppercase(), style = MaterialTheme.typography.labelMedium, color = securitySeverityColor(alert.severity))
            }
            Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${alert.status.replaceFirstChar { it.uppercase() }} · Occurrences: ${alert.occurrences}" +
                    (alert.role?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (alert.status.equals("open", ignoreCase = true)) {
                    TextButton(onClick = onAcknowledge, enabled = !saving) { Text("Acknowledge") }
                }
                TextButton(onClick = onResolve, enabled = !saving) { Text("Resolve") }
                if (!alert.uid.isNullOrBlank()) {
                    TextButton(onClick = onRevokeSessions, enabled = !saving) { Text("Revoke sessions") }
                }
            }
        }
    }
}

@Composable
private fun PrivilegedActionCard(event: MasterPrivilegedAuditEvent) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(event.action.replace('_', ' '), fontWeight = FontWeight.SemiBold)
            Text("${event.resourceType} · ${event.resourceId}", style = MaterialTheme.typography.bodySmall)
            Text("Actor ${event.actorRole} · ${event.actorUid.take(12)}" + (event.timestamp?.let { " · ${it.take(16).replace('T',' ')}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun securitySeverityColor(severity: String) = when (severity.lowercase()) {
    "critical", "high" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun BackupItemCard(
    backup: MasterBackupItem,
    allowRestoreDrills: Boolean,
    saving: Boolean,
    onTestRestore: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(backup.snapshotTime?.replace('T', ' ')?.take(19) ?: "Backup", fontWeight = FontWeight.SemiBold)
                    Text("${backup.state} · expires ${backup.expireTime?.take(10) ?: "n/a"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (allowRestoreDrills && backup.state == "READY") {
                    TextButton(enabled = !saving, onClick = onTestRestore) { Text("Test restore") }
                }
            }
        }
    }
}

@Composable
private fun RecoveryJobCard(job: MasterRecoveryJobItem, saving: Boolean, onRefresh: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.targetDatabaseId.ifBlank { "Recovery database" }, fontWeight = FontWeight.SemiBold)
                    Text(job.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = if (job.status == "verified") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    job.requestedAt?.let { Text("Started ${it.take(19).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall) }
                    if (job.sourceVerified) Text("Backup source verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    job.error?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                if (job.status == "running" || job.status == "restore_completed_unverified") {
                    TextButton(enabled = !saving, onClick = onRefresh) { Text("Refresh") }
                }
            }
            Text("Production cutover: manual only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SchoolCard(
    school: MasterSchoolItem,
    health: MasterTenantHealthItem?,
    saving: Boolean,
    onToggle: () -> Unit,
    onAddAdmin: () -> Unit,
    onManageSubscription: () -> Unit,
    onBilling: () -> Unit,
    onReconcileSubscription: () -> Unit,
    onToggleExportAccess: () -> Unit,
    onExportData: () -> Unit
) {
    val active = school.status == "active"
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(school.schoolName, fontWeight = FontWeight.SemiBold)
                    health?.let {
                        Text(
                            healthLabel(it.healthState) + if (it.activeAlertCount > 0) " · ${it.activeAlertCount} alert(s)" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = healthColor(it.healthState)
                        )
                    }
                    Text(
                        "${school.plan.replaceFirstChar { it.uppercase() }} · ${school.subscriptionStatus.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (active) "Active tenant" else "Suspended tenant",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    if (!school.subscriptionAccessActive) {
                        Text(
                            "Optional features blocked by subscription state",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    school.currentPeriodEnd?.let {
                        Text("Period ends ${dateLabel(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = active, enabled = !saving, onCheckedChange = { onToggle() })
            }
            val u = school.usage
            Text(
                "Students ${usageLabel(u.activeStudents, u.studentLimit)} · Staff ${usageLabel(u.activeStaff, u.staffLimit)} · Campuses ${usageLabel(u.activeCampuses, u.campusLimit)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (u.studentsOverLimit || u.staffOverLimit || u.campusesOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Lifetime pickups: QR ${u.totalQrPickups} · Manual ${u.totalManualPickups}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilledTonalButton(onClick = onManageSubscription, enabled = !saving) { Text("Plan") }
                FilledTonalButton(onClick = onBilling, enabled = !saving) { Text("Billing records") }
                FilledTonalButton(onClick = onAddAdmin, enabled = active && !saving) { Text("Add admin") }
            }
            TextButton(onClick = onReconcileSubscription, enabled = !saving) {
                Text("Check subscription lifecycle now")
            }
            HorizontalDivider()
            Text(
                "Tenant data export",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (school.selfServiceDataExportEnabled)
                    "School admin self-service export is enabled. Exports are direct downloads and are not stored in PickupPass cloud storage."
                else
                    "School admin self-service export is disabled. The platform owner can still export this tenant when support or recovery requires it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onExportData, enabled = !saving) { Text("Owner export") }
                TextButton(onClick = onToggleExportAccess, enabled = !saving) {
                    Text(if (school.selfServiceDataExportEnabled) "Disable school export" else "Enable school export")
                }
            }
        }
    }
}


@Composable
private fun OperationsAlertCard(
    alert: MasterOperationalAlert,
    saving: Boolean,
    onReview: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(alert.title, fontWeight = FontWeight.SemiBold, color = severityColor(alert.severity))
                    Text(alert.schoolNameSnapshot, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(alert.severity.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
            }
            Text(alert.message, style = MaterialTheme.typography.bodySmall)
            if (alert.action in setOf("review_payment", "billing", "subscription", "usage")) {
                TextButton(onClick = onReview, enabled = !saving) {
                    Text(
                        when (alert.action) {
                            "review_payment" -> "Review payment"
                            "billing" -> "Open billing"
                            "usage" -> "Review plan"
                            else -> "Review subscription"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun severityColor(value: String) = if (value == "critical") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

@Composable
private fun healthColor(value: String) = when (value) {
    "suspended", "over_quota", "billing_risk" -> MaterialTheme.colorScheme.error
    "attention_needed" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun healthLabel(value: String): String = when (value) {
    "attention_needed" -> "Attention needed"
    "billing_risk" -> "Billing risk"
    "over_quota" -> "Over quota"
    "suspended" -> "Suspended"
    else -> "Healthy"
}


@Composable
private fun BillingDialog(
    school: MasterSchoolItem,
    invoices: List<MasterInvoiceItem>,
    paymentNotices: List<GcashPaymentNoticeItem>,
    loading: Boolean,
    saving: Boolean,
    billingProfile: MasterBillingProfileResponse?,
    onDismiss: () -> Unit,
    onSaveProfile: (String, String, String, String) -> Unit,
    onCreate: (Long, String?, String) -> Unit,
    onEmail: (String, String) -> Unit,
    onDownloadPdf: (MasterInvoiceItem) -> Unit,
    onPaid: (String, String, String, String) -> Unit,
    onVoid: (String, String) -> Unit,
    onConfirmGcash: (String, String) -> Unit,
    onRejectGcash: (String, String) -> Unit,
    onReconcile: () -> Unit
) {
    var showCreate by remember(school.schoolId) { mutableStateOf(false) }
    var showProfile by remember(school.schoolId) { mutableStateOf(false) }
    var emailInvoice by remember(school.schoolId) { mutableStateOf<MasterInvoiceItem?>(null) }
    var payInvoice by remember(school.schoolId) { mutableStateOf<MasterInvoiceItem?>(null) }
    var voidInvoice by remember(school.schoolId) { mutableStateOf<MasterInvoiceItem?>(null) }
    var reviewGcashNotice by remember(school.schoolId) { mutableStateOf<GcashPaymentNoticeItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Billing records") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item { Text(school.schoolName, fontWeight = FontWeight.SemiBold) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { showCreate = true }, enabled = !saving) { Text("New invoice") }
                            FilledTonalButton(onClick = { showProfile = true }, enabled = !saving) { Text("Billing profile") }
                        }
                        TextButton(onClick = onReconcile, enabled = !saving) { Text("Check overdue invoices") }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                val pendingGcash = paymentNotices.filter { it.status == "pending_review" }
                if (pendingGcash.isNotEmpty()) {
                    item { Text("GCash payments awaiting verification", fontWeight = FontWeight.SemiBold) }
                    items(pendingGcash, key = { "gcash-" + it.noticeId }) { notice ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("${notice.invoiceNumber} · ${notice.currency} ${moneyLabel(notice.amountMinor)}", fontWeight = FontWeight.SemiBold)
                                Text("Payer: ${notice.payerName}", style = MaterialTheme.typography.bodySmall)
                                Text("Reference: ${notice.referenceNumber}", style = MaterialTheme.typography.bodySmall)
                                Text("Claimed paid: ${notice.paidAtClaimed?.replace('T',' ')?.take(16) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { reviewGcashNotice = notice }, enabled = !saving) { Text("Verify payment") }
                            }
                        }
                    }
                }
                if (!loading && invoices.isEmpty()) item { Text("No billing records yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(invoices, key = { it.invoiceId }) { invoice ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(invoice.invoiceNumber, fontWeight = FontWeight.SemiBold)
                                Text(invoice.status.replace('_',' ').replaceFirstChar { it.uppercase() })
                            }
                            Text("${invoice.currency} ${moneyLabel(invoice.amountMinor)} · due ${invoice.dueAt?.take(10) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            if (invoice.note.isNotBlank()) Text(invoice.note, style = MaterialTheme.typography.bodySmall)
                            if (invoice.lastEmailedAt != null) {
                                Text("Last emailed ${invoice.lastEmailedAt.take(10)} to ${invoice.lastEmailedTo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TextButton(onClick = { onDownloadPdf(invoice) }, enabled = !saving) { Text("PDF") }
                                if (invoice.status != "void") {
                                    TextButton(onClick = { emailInvoice = invoice }, enabled = !saving) { Text("Email") }
                                }
                            }
                            if (invoice.status == "paid") {
                                Text("Paid ${invoice.paidAt?.take(10) ?: ""} ${invoice.paymentReference}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            } else if (invoice.status != "void") {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    TextButton(onClick = { payInvoice = invoice }, enabled = !saving) { Text("Mark paid") }
                                    TextButton(onClick = { voidInvoice = invoice }, enabled = !saving) { Text("Void") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showProfile) {
        var billingName by remember(billingProfile?.schoolId, billingProfile?.billingName) { mutableStateOf(billingProfile?.billingName ?: school.schoolName) }
        var billingEmail by remember(billingProfile?.schoolId, billingProfile?.billingEmail) { mutableStateOf(billingProfile?.billingEmail ?: "") }
        var billingAddress by remember(billingProfile?.schoolId, billingProfile?.billingAddress) { mutableStateOf(billingProfile?.billingAddress ?: "") }
        var billingTaxId by remember(billingProfile?.schoolId, billingProfile?.billingTaxId) { mutableStateOf(billingProfile?.billingTaxId ?: "") }
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Billing profile") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("These details are snapshotted into new invoices.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(billingName, { billingName = it }, label = { Text("Billing name") })
                OutlinedTextField(billingEmail, { billingEmail = it }, label = { Text("Billing email") }, singleLine = true)
                OutlinedTextField(billingAddress, { billingAddress = it }, label = { Text("Billing address") })
                OutlinedTextField(billingTaxId, { billingTaxId = it }, label = { Text("Tax / Registration ID (optional)") })
            } },
            confirmButton = { Button(enabled = billingName.isNotBlank() && !saving, onClick = {
                onSaveProfile(billingName, billingEmail, billingAddress, billingTaxId); showProfile = false
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Cancel") } }
        )
    }

    if (showCreate) {
        var amount by remember { mutableStateOf("") }
        var dueAt by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        val amountMinor = amount.toDoubleOrNull()?.let { (it * 100.0).toLong() }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New invoice") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (PHP)") }, singleLine = true)
                OutlinedTextField(dueAt, { dueAt = it }, label = { Text("Due date/time ISO-8601 (optional)") })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            } },
            confirmButton = { Button(enabled = amountMinor != null && amountMinor >= 0 && !saving, onClick = { onCreate(amountMinor!!, dueAt.trim().ifBlank { null }, note); showCreate = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    emailInvoice?.let { invoice ->
        var recipient by remember(invoice.invoiceId) { mutableStateOf(billingProfile?.billingEmail?.ifBlank { invoice.billingEmailSnapshot } ?: invoice.billingEmailSnapshot) }
        AlertDialog(
            onDismissRequest = { emailInvoice = null },
            title = { Text("Email invoice") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(invoice.invoiceNumber, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(recipient, { recipient = it }, label = { Text("Recipient email") }, singleLine = true)
                Text("A generated PDF invoice will be attached.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { Button(enabled = recipient.isNotBlank() && !saving, onClick = {
                onEmail(invoice.invoiceId, recipient); emailInvoice = null
            }) { Text("Send") } },
            dismissButton = { TextButton(onClick = { emailInvoice = null }) { Text("Cancel") } }
        )
    }

    payInvoice?.let { invoice ->
        var reference by remember(invoice.invoiceId) { mutableStateOf("") }
        var method by remember(invoice.invoiceId) { mutableStateOf("Manual") }
        var note by remember(invoice.invoiceId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payInvoice = null }, title = { Text("Mark invoice paid") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(invoice.invoiceNumber)
                OutlinedTextField(reference, { reference = it }, label = { Text("Payment reference (optional)") })
                OutlinedTextField(method, { method = it }, label = { Text("Payment method") })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            } },
            confirmButton = { Button(enabled = !saving, onClick = { onPaid(invoice.invoiceId, reference, method, note); payInvoice = null }) { Text("Mark paid") } },
            dismissButton = { TextButton(onClick = { payInvoice = null }) { Text("Cancel") } }
        )
    }

    reviewGcashNotice?.let { notice ->
        var note by remember(notice.noticeId) { mutableStateOf("") }
        var rejecting by remember(notice.noticeId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { reviewGcashNotice = null },
            title = { Text(if (rejecting) "Reject GCash payment" else "Confirm GCash payment") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("${notice.invoiceNumber} · ${notice.currency} ${moneyLabel(notice.amountMinor)}", fontWeight = FontWeight.SemiBold)
                Text("Payer: ${notice.payerName}")
                Text("GCash reference: ${notice.referenceNumber}")
                Text("Check the actual receiving GCash transaction before confirming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(note, { note = it }, label = { Text(if (rejecting) "Rejection reason" else "Verification note (optional)") })
            } },
            confirmButton = {
                Button(enabled = !saving && (!rejecting || note.isNotBlank()), onClick = {
                    if (rejecting) onRejectGcash(notice.noticeId, note) else onConfirmGcash(notice.noticeId, note)
                    reviewGcashNotice = null
                }) { Text(if (rejecting) "Reject" else "Confirm paid") }
            },
            dismissButton = {
                Row {
                    if (!rejecting) TextButton(onClick = { rejecting = true }) { Text("Reject instead") }
                    TextButton(onClick = { reviewGcashNotice = null }) { Text("Cancel") }
                }
            }
        )
    }

    voidInvoice?.let { invoice ->
        var reason by remember(invoice.invoiceId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidInvoice = null }, title = { Text("Void invoice") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }) },
            confirmButton = { Button(enabled = reason.isNotBlank() && !saving, onClick = { onVoid(invoice.invoiceId, reason); voidInvoice = null }) { Text("Void") } },
            dismissButton = { TextButton(onClick = { voidInvoice = null }) { Text("Cancel") } }
        )
    }
}

private fun moneyLabel(amountMinor: Long): String = "%.2f".format(amountMinor / 100.0)

private fun usageLabel(current: Long, limit: Long): String = if (limit < 0) "$current/∞" else "$current/$limit"
