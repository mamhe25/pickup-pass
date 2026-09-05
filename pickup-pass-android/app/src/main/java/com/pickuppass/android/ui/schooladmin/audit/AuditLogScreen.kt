package com.pickuppass.android.ui.schooladmin.audit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.AuditEvent
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("all") }

    val roles = remember(state.events) {
        state.events.map { it.actorRole.ifBlank { "unknown" } }.distinct().sorted()
    }
    val filtered = remember(state.events, query, roleFilter) {
        val q = query.trim()
        state.events.filter { event ->
            val roleMatches = roleFilter == "all" || event.actorRole.ifBlank { "unknown" } == roleFilter
            val queryMatches = q.isBlank() ||
                event.action.contains(q, true) ||
                event.resourceType.contains(q, true) ||
                event.resourceId.contains(q, true) ||
                event.actorRole.contains(q, true) ||
                event.details.entries.any { "${it.key} ${it.value}".contains(q, true) }
            roleMatches && queryMatches
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Audit Log", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Administrative activity trail",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && state.events.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) { FullScreenLoading() }
            return@Scaffold
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 860.dp).align(Alignment.TopCenter),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text("Trace privileged actions", fontWeight = FontWeight.Bold)
                                Text(
                                    "Use the audit trail to review administrative changes, release overrides, access changes, and configuration activity.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                state.error?.let { item { ErrorBanner(it) } }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Metric("Loaded events", state.events.size, Modifier.weight(1f))
                        Metric("Actor roles", roles.size, Modifier.weight(1f))
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Search audit events") },
                        placeholder = { Text("Action, resource, actor role or detail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (roles.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            FilterChip(
                                selected = roleFilter == "all",
                                onClick = { roleFilter = "all" },
                                label = { Text("All roles") }
                            )
                            roles.forEach { role ->
                                FilterChip(
                                    selected = roleFilter == role,
                                    onClick = { roleFilter = role },
                                    label = { Text(role.pretty()) }
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.FactCheck, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    if (state.events.isEmpty()) "No audit events yet" else "No matching events",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (state.events.isEmpty()) "Administrative events will appear here as actions are recorded."
                                    else "Try another search or actor role.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { event -> AuditCard(event) }
            }
        }
    }
}

@Composable
private fun AuditCard(event: AuditEvent) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(event.action.pretty(), fontWeight = FontWeight.Bold)
                    Text(
                        "${event.actorRole.ifBlank { "unknown" }.pretty()} · ${event.resourceType.pretty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                event.timestamp?.let {
                    Text(
                        formatTimestamp(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (event.resourceId.isNotBlank()) {
                Text(
                    "Resource ID: ${event.resourceId}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (event.details.isNotEmpty()) {
                HorizontalDivider()
                event.details.entries.take(8).forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.key.pretty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            entry.value?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (event.details.size > 8) {
                    Text(
                        "+${event.details.size - 8} more detail(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTimestamp(raw: String): String = try {
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(raw))
} catch (_: Exception) {
    raw
}

private fun String.pretty(): String =
    replace('.', ' ').replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
