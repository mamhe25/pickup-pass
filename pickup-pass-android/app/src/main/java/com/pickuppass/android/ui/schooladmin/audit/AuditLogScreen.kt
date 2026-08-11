package com.pickuppass.android.ui.schooladmin.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.AuditEvent
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(viewModel: AuditLogViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Audit Log") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } }, actions = { IconButton(onClick = viewModel::load) { Icon(Icons.Filled.Refresh, "Refresh") } }) }) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator(Modifier.padding(Spacing.xl)) }
            state.error != null -> Box(Modifier.padding(padding).padding(Spacing.lg)) { ErrorBanner(state.error ?: "") }
            state.events.isEmpty() -> Box(Modifier.padding(padding).padding(Spacing.lg)) { Text("No audit events yet.") }
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.events, key = { it.id }) { event -> AuditCard(event) }
            }
        }
    }
}

@Composable
private fun AuditCard(event: AuditEvent) {
    ElevatedCard {
        Column(Modifier.padding(Spacing.md)) {
            Text(event.action.replace('.', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
            Text("${event.actorRole.ifBlank { "unknown" }} · ${event.resourceType} ${event.resourceId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            event.timestamp?.let { Text(formatTimestamp(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (event.details.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(event.details.entries.joinToString(" · ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatTimestamp(raw: String): String = try {
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
} catch (_: Exception) { raw }
