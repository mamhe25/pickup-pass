package com.pickuppass.android.ui.masteradmin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.MasterPlanDefinition
import com.pickuppass.android.data.model.MasterSchoolItem
import com.pickuppass.android.data.model.MasterInvoiceItem
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
    var createSchool by remember { mutableStateOf(false) }
    var adminForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }
    var subscriptionForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }
    var billingForSchool by remember { mutableStateOf<MasterSchoolItem?>(null) }

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
                    saving = state.saving,
                    onToggle = { viewModel.setSchoolActive(school.schoolId, school.status != "active") },
                    onAddAdmin = { adminForSchool = school },
                    onManageSubscription = { subscriptionForSchool = school },
                    onBilling = { billingForSchool = school; viewModel.loadInvoices(school.schoolId) },
                    onReconcileSubscription = { viewModel.reconcileSubscription(school.schoolId) }
                )
            }
        }
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
            loading = state.billingLoading,
            saving = state.saving,
            onDismiss = { billingForSchool = null },
            onCreate = { amountMinor, dueAt, note -> viewModel.createInvoice(school.schoolId, amountMinor, dueAt, note) },
            onPaid = { invoiceId, reference, method, note -> viewModel.markInvoicePaid(school.schoolId, invoiceId, reference, method, note) },
            onVoid = { invoiceId, reason -> viewModel.voidInvoice(school.schoolId, invoiceId, reason) },
            onReconcile = { viewModel.reconcileOverdueInvoices(school.schoolId) }
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
    saving: Boolean,
    onToggle: () -> Unit,
    onAddAdmin: () -> Unit,
    onManageSubscription: () -> Unit,
    onBilling: () -> Unit,
    onReconcileSubscription: () -> Unit
) {
    val active = school.status == "active"
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(school.schoolName, fontWeight = FontWeight.SemiBold)
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
        }
    }
}


@Composable
private fun BillingDialog(
    school: MasterSchoolItem,
    invoices: List<MasterInvoiceItem>,
    loading: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (Long, String?, String) -> Unit,
    onPaid: (String, String, String, String) -> Unit,
    onVoid: (String, String) -> Unit,
    onReconcile: () -> Unit
) {
    var showCreate by remember(school.schoolId) { mutableStateOf(false) }
    var payInvoice by remember(school.schoolId) { mutableStateOf<MasterInvoiceItem?>(null) }
    var voidInvoice by remember(school.schoolId) { mutableStateOf<MasterInvoiceItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Billing records") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item { Text(school.schoolName, fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { showCreate = true }, enabled = !saving) { Text("New invoice") }
                        TextButton(onClick = onReconcile, enabled = !saving) { Text("Check overdue") }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
