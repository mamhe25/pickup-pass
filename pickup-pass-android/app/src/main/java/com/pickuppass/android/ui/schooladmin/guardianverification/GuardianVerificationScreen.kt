package com.pickuppass.android.ui.schooladmin.guardianverification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.GuardianVerificationItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class GuardianStatusFilter(
    val label: String,
    val value: String?
) {
    ALL("All", null),
    PENDING("Pending", "pending"),
    VERIFIED("Verified", "verified"),
    SUSPENDED("Suspended", "suspended")
}

private data class GuardianAction(
    val guardian: GuardianVerificationItem,
    val targetStatus: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianVerificationScreen(
    viewModel: GuardianVerificationViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(GuardianStatusFilter.ALL) }
    var pendingAction by remember { mutableStateOf<GuardianAction?>(null) }
    var pendingPolicyChange by remember { mutableStateOf<Boolean?>(null) }

    val guardians = state.guardians
    val pendingCount = remember(guardians) {
        guardians.count { it.status.equals("pending", ignoreCase = true) }
    }
    val verifiedCount = remember(guardians) {
        guardians.count { it.status.equals("verified", ignoreCase = true) }
    }
    val suspendedCount = remember(guardians) {
        guardians.count { it.status.equals("suspended", ignoreCase = true) }
    }

    val filteredGuardians = remember(guardians, query, statusFilter) {
        val normalizedQuery = query.trim().lowercase()
        guardians.filter { guardian ->
            val statusMatches =
                statusFilter.value == null ||
                    guardian.status.equals(statusFilter.value, ignoreCase = true)

            val queryMatches = normalizedQuery.isBlank() ||
                guardian.displayName.lowercase().contains(normalizedQuery) ||
                guardian.email.lowercase().contains(normalizedQuery) ||
                guardian.studentNames.any { it.lowercase().contains(normalizedQuery) }

            statusMatches && queryMatches
        }.sortedWith(
            compareBy<GuardianVerificationItem> {
                when (it.status.lowercase()) {
                    "pending" -> 0
                    "suspended" -> 1
                    "verified" -> 2
                    else -> 3
                }
            }.thenBy { it.displayName.lowercase() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Guardian Verification",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Identity assurance & pickup access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh guardian verification"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && guardians.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                FullScreenLoading()
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.sm,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                state.error?.let { message ->
                    item {
                        ErrorBanner(message)
                    }
                }

                item {
                    VerificationPolicyCard(
                        verificationRequired = state.verificationRequired,
                        pendingCount = pendingCount,
                        enabled = !state.isLoading,
                        onToggle = { required ->
                            pendingPolicyChange = required
                        }
                    )
                }

                item {
                    VerificationOverview(
                        total = guardians.size,
                        pending = pendingCount,
                        verified = verifiedCount,
                        suspended = suspendedCount
                    )
                }

                if (pendingCount > 0) {
                    item {
                        AttentionCard(pendingCount)
                    }
                }

                item {
                    GuardianSearchField(
                        query = query,
                        onQueryChange = { query = it }
                    )
                }

                item {
                    StatusFilterRow(
                        selected = statusFilter,
                        counts = mapOf(
                            GuardianStatusFilter.ALL to guardians.size,
                            GuardianStatusFilter.PENDING to pendingCount,
                            GuardianStatusFilter.VERIFIED to verifiedCount,
                            GuardianStatusFilter.SUSPENDED to suspendedCount
                        ),
                        onSelect = { statusFilter = it }
                    )
                }

                if (filteredGuardians.isEmpty()) {
                    item {
                        GuardianEmptyState(
                            hasGuardians = guardians.isNotEmpty(),
                            hasFilter = query.isNotBlank() || statusFilter != GuardianStatusFilter.ALL,
                            onClearFilters = {
                                query = ""
                                statusFilter = GuardianStatusFilter.ALL
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredGuardians,
                        key = { it.uid }
                    ) { guardian ->
                        GuardianReviewCard(
                            guardian = guardian,
                            enabled = !state.isLoading,
                            onVerify = {
                                pendingAction = GuardianAction(
                                    guardian = guardian,
                                    targetStatus = "verified"
                                )
                            },
                            onSuspend = {
                                pendingAction = GuardianAction(
                                    guardian = guardian,
                                    targetStatus = "suspended"
                                )
                            }
                        )
                    }
                }

                item {
                    SecurityFootnote()
                }
            }
        }
    }

    pendingAction?.let { action ->
        GuardianStatusDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirm = { reason ->
                viewModel.updateGuardianStatus(
                    guardianUid = action.guardian.uid,
                    status = action.targetStatus,
                    reason = reason
                )
                pendingAction = null
            }
        )
    }

    pendingPolicyChange?.let { required ->
        PolicyChangeDialog(
            required = required,
            pendingCount = pendingCount,
            onDismiss = { pendingPolicyChange = null },
            onConfirm = {
                viewModel.setVerificationRequired(required)
                pendingPolicyChange = null
            }
        )
    }
}

@Composable
private fun VerificationPolicyCard(
    verificationRequired: Boolean,
    pendingCount: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val containerColor = if (verificationRequired) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (verificationRequired) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (verificationRequired) 7.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = if (verificationRequired) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (verificationRequired) {
                                Icons.Filled.VerifiedUser
                            } else {
                                Icons.Filled.Policy
                            },
                            contentDescription = null,
                            tint = if (verificationRequired) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (verificationRequired) {
                            "Identity verification required"
                        } else {
                            "Identity verification optional"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (verificationRequired) {
                            "New parent-added guardians require school review before they can authorize pickup."
                        } else {
                            "New guardians can become pickup-authorized without a separate school identity review."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f)
                    )
                }

                Spacer(Modifier.width(Spacing.sm))
                Switch(
                    checked = verificationRequired,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    colors = if (verificationRequired) {
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        SwitchDefaults.colors()
                    }
                )
            }

            if (verificationRequired && pendingCount > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PendingActions,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "$pendingCount guardian${if (pendingCount == 1) "" else "s"} waiting for review",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationOverview(
    total: Int,
    pending: Int,
    verified: Int,
    suspended: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Guardian assurance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "$total guardian${if (total == 1) "" else "s"} linked to students",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = pending,
                label = "Pending",
                icon = Icons.Filled.Schedule,
                emphasis = pending > 0,
                kind = MetricKind.ATTENTION
            )
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = verified,
                label = "Verified",
                icon = Icons.Filled.Verified,
                emphasis = false,
                kind = MetricKind.POSITIVE
            )
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = suspended,
                label = "Suspended",
                icon = Icons.Filled.Block,
                emphasis = suspended > 0,
                kind = MetricKind.DANGER
            )
        }
    }
}

private enum class MetricKind { ATTENTION, POSITIVE, DANGER }

@Composable
private fun VerificationMetric(
    modifier: Modifier,
    value: Int,
    label: String,
    icon: ImageVector,
    emphasis: Boolean,
    kind: MetricKind
) {
    val accent = when (kind) {
        MetricKind.ATTENTION -> MaterialTheme.colorScheme.tertiary
        MetricKind.POSITIVE -> MaterialTheme.colorScheme.primary
        MetricKind.DANGER -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (emphasis) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (emphasis) accent.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AttentionCard(pendingCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.AssignmentInd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Identity reviews need attention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "$pendingCount guardian${if (pendingCount == 1) " is" else "s are"} waiting for a school decision. Review identity before granting pickup access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.80f)
                )
            }
        }
    }
}

@Composable
private fun GuardianSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search guardians") },
        placeholder = { Text("Name, email or student") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )
}

@Composable
private fun StatusFilterRow(
    selected: GuardianStatusFilter,
    counts: Map<GuardianStatusFilter, Int>,
    onSelect: (GuardianStatusFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = 1.dp)
    ) {
        items(GuardianStatusFilter.entries) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text("${filter.label} ${counts[filter] ?: 0}")
                },
                leadingIcon = if (filter == selected) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun GuardianReviewCard(
    guardian: GuardianVerificationItem,
    enabled: Boolean,
    onVerify: () -> Unit,
    onSuspend: () -> Unit
) {
    val status = guardian.status.lowercase()
    val isPending = status == "pending"
    val isVerified = status == "verified"
    val isSuspended = status == "suspended"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPending) 3.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuardianInitialsAvatar(guardian.displayName)

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guardian.displayName.ifBlank { "Guardian" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (guardian.email.isNotBlank()) {
                        Text(
                            text = guardian.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.sm))
                GuardianStatusBadge(status)
            }

            if (guardian.studentNames.isNotEmpty()) {
                StudentLinksSummary(guardian.studentNames)
            }

            when {
                isPending -> {
                    ReviewContext(
                        icon = Icons.Filled.FactCheck,
                        title = "School review required",
                        detail = "Confirm this guardian's identity and relationship before granting pickup access."
                    )
                }

                isSuspended -> {
                    ReviewContext(
                        icon = Icons.Filled.Security,
                        title = "Pickup access suspended",
                        detail = guardian.verificationReason.ifBlank {
                            "This guardian cannot authorize student release until the school verifies them again."
                        },
                        danger = true
                    )
                }

                isVerified -> {
                    val meta = buildList {
                        guardian.verifiedAt?.takeIf { it.isNotBlank() }?.let {
                            add("Verified ${formatVerificationTime(it)}")
                        }
                        guardian.verificationReason.takeIf { it.isNotBlank() }?.let {
                            add(it)
                        }
                    }.joinToString(" · ")

                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isPending -> {
                        OutlinedButton(
                            onClick = onSuspend,
                            enabled = enabled,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Suspend")
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Button(
                            onClick = onVerify,
                            enabled = enabled,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Verify guardian", fontWeight = FontWeight.Bold)
                        }
                    }

                    isVerified -> {
                        OutlinedButton(
                            onClick = onSuspend,
                            enabled = enabled,
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Suspend access")
                        }
                    }

                    isSuspended -> {
                        Button(
                            onClick = onVerify,
                            enabled = enabled,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(
                                Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Verify & restore", fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {
                        Text(
                            text = "Status: ${guardian.status.ifBlank { "Unknown" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianInitialsAvatar(name: String) {
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "G" }
    }

    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun GuardianStatusBadge(status: String) {
    val (label, icon, container, content) = when (status) {
        "pending" -> StatusStyle(
            "Pending",
            Icons.Filled.Schedule,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        "verified" -> StatusStyle(
            "Verified",
            Icons.Filled.Verified,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        "suspended" -> StatusStyle(
            "Suspended",
            Icons.Filled.Block,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> StatusStyle(
            status.ifBlank { "Unknown" }.replaceFirstChar { it.uppercase() },
            Icons.Filled.HelpOutline,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = CircleShape,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = content
            )
        }
    }
}

private data class StatusStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@Composable
private fun StudentLinksSummary(studentNames: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (studentNames.size == 1) "Authorized student" else "Authorized students",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = studentNames.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReviewContext(
    icon: ImageVector,
    title: String,
    detail: String,
    danger: Boolean = false
) {
    val container = if (danger) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    }
    val content = if (danger) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.80f)
                )
            }
        }
    }
}

@Composable
private fun GuardianEmptyState(
    hasGuardians: Boolean,
    hasFilter: Boolean,
    onClearFilters: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hasGuardians) Icons.Filled.SearchOff else Icons.Filled.PeopleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = if (hasGuardians) "No guardians match" else "No guardians to review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (hasGuardians) {
                    "Try another name, email, student or verification status."
                } else {
                    "Guardian identities will appear here when they are linked to students."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasFilter) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear filters")
                }
            }
        }
    }
}

@Composable
private fun SecurityFootnote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "Guardian verification controls pickup authorization. Use suspension when access must be blocked, and verify only after the school has completed its identity-check process.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuardianStatusDialog(
    action: GuardianAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val isSuspension = action.targetStatus == "suspended"
    var reason by remember(action.guardian.uid, action.targetStatus) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isSuspension) Icons.Filled.GppBad else Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = if (isSuspension) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isSuspension) "Suspend pickup access?" else "Verify this guardian?",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = if (isSuspension) {
                        "${action.guardian.displayName} will no longer be allowed to authorize student pickup until the school verifies them again."
                    } else {
                        "Confirm that the school has reviewed ${action.guardian.displayName}'s identity before restoring or granting pickup access."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (isSuspension) "Suspension reason" else "Review note (optional)")
                    },
                    placeholder = {
                        Text(
                            if (isSuspension) {
                                "Required for the audit trail"
                            } else {
                                "e.g. Identity reviewed by school staff"
                            }
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    isError = isSuspension && reason.isBlank(),
                    supportingText = if (isSuspension && reason.isBlank()) {
                        { Text("Enter a reason before suspending pickup access.") }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = !isSuspension || reason.isNotBlank(),
                colors = if (isSuspension) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isSuspension) "Suspend access" else "Verify guardian")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PolicyChangeDialog(
    required: Boolean,
    pendingCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (required) Icons.Filled.VerifiedUser else Icons.Filled.Policy,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = if (required) {
                    "Require guardian verification?"
                } else {
                    "Make verification optional?"
                },
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = if (required) {
                        "New parent-added guardians will need a school review before they can authorize pickup."
                    } else {
                        "New guardians will no longer be held for a separate school identity review before pickup authorization."
                    }
                )
                if (!required && pendingCount > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "$pendingCount currently pending guardian${if (pendingCount == 1) " remains" else "s remain"} pending until their status is changed explicitly.",
                            modifier = Modifier.padding(Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (required) "Require verification" else "Make optional")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatVerificationTime(value: String): String {
    return try {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        value.take(16).replace('T', ' ')
    }
}
