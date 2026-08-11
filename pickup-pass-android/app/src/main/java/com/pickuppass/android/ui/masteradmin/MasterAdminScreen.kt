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
                    onManageSubscription = { subscriptionForSchool = school }
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

    subscriptionForSchool?.let { school ->
        SubscriptionDialog(
            school = school,
            plans = state.plans,
            featureKeys = state.featureKeys,
            saving = state.saving,
            onDismiss = { subscriptionForSchool = null },
            onSave = { plan, status, overrides ->
                viewModel.updateSubscription(school.schoolId, plan, status, overrides)
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
    onSave: (String, String, Map<String, Boolean>) -> Unit
) {
    var selectedPlan by remember(school.schoolId) { mutableStateOf(school.plan) }
    var selectedStatus by remember(school.schoolId) { mutableStateOf(school.subscriptionStatus) }
    var customize by remember(school.schoolId) { mutableStateOf(school.featureOverrides.isNotEmpty()) }
    var featureValues by remember(school.schoolId) { mutableStateOf(school.features) }

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
                onSave(selectedPlan, selectedStatus, if (customize) featureValues else emptyMap())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun limitLabel(value: Int): String = if (value < 0) "Unlimited" else value.toString()
private fun featureLabel(value: String): String = value.split('_').joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }

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
    onManageSubscription: () -> Unit
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
                FilledTonalButton(onClick = onManageSubscription, enabled = !saving) { Text("Plan & features") }
                FilledTonalButton(onClick = onAddAdmin, enabled = active && !saving) { Text("Add admin") }
            }
        }
    }
}

private fun usageLabel(current: Long, limit: Long): String = if (limit < 0) "$current/∞" else "$current/$limit"
