package com.pickuppass.android.ui.schooladmin.guardianverification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.GuardianVerificationItem
import com.pickuppass.android.ui.common.FeedbackCard
import com.pickuppass.android.ui.common.FeedbackTone
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.PremiumTopAppBar
import com.pickuppass.android.ui.common.PickupPassPullToRefresh
import com.pickuppass.android.ui.common.SmartImage
import com.pickuppass.android.ui.theme.Green500
import com.pickuppass.android.ui.theme.Green600
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

private enum class MetricKind {
    TOTAL,
    ATTENTION,
    POSITIVE,
    DANGER
}

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
    var photoGuardian by remember { mutableStateOf<GuardianVerificationItem?>(null) }

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

        guardians
            .filter { guardian ->
                val statusMatches =
                    statusFilter.value == null ||
                        guardian.status.equals(statusFilter.value, ignoreCase = true)

                val queryMatches =
                    normalizedQuery.isBlank() ||
                        guardian.displayName.lowercase().contains(normalizedQuery) ||
                        guardian.email.lowercase().contains(normalizedQuery) ||
                        guardian.studentNames.any {
                            it.lowercase().contains(normalizedQuery)
                        }

                statusMatches && queryMatches
            }
            .sortedWith(
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PremiumTopAppBar(
                title = "Guardian verification",
                subtitle = "Identity assurance & pickup access",
                onBack = onBack,
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

        PickupPassPullToRefresh(
            refreshing = state.isLoading && guardians.isNotEmpty(),
            onRefresh = viewModel::load,
            enabled = !state.policyBusy && state.busyUid == null,
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
                        .widthIn(max = 820.dp)
                        .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item(key = "policy") {
                    VerificationPolicyCard(
                        verificationRequired = state.verificationRequired,
                        pendingCount = pendingCount,
                        busy = state.policyBusy,
                        enabled = !state.isLoading && state.busyUid == null,
                        onToggle = { required ->
                            pendingPolicyChange = required
                        }
                    )
                }

                item(key = "overview") {
                    VerificationOverview(
                        total = guardians.size,
                        pending = pendingCount,
                        verified = verifiedCount,
                        suspended = suspendedCount
                    )
                }

                if (pendingCount > 0) {
                    item(key = "attention") {
                        AttentionCard(pendingCount)
                    }
                }

                item(key = "search") {
                    SearchAndFilterHeader(
                        visibleCount = filteredGuardians.size,
                        totalCount = guardians.size
                    )
                }

                item(key = "search-field") {
                    GuardianSearchField(
                        query = query,
                        onQueryChange = { query = it }
                    )
                }

                item(key = "filters") {
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
                    item(key = "empty") {
                        GuardianEmptyState(
                            hasGuardians = guardians.isNotEmpty(),
                            hasFilter =
                                query.isNotBlank() ||
                                    statusFilter != GuardianStatusFilter.ALL,
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
                            enabled = !state.policyBusy && state.busyUid == null,
                            busy = state.busyUid == guardian.uid,
                            onPhotoClick = {
                                if (!guardian.photoUrl.isNullOrBlank()) {
                                    photoGuardian = guardian
                                }
                            },
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

                item(key = "security-footnote") {
                    SecurityFootnote()
                }
                }
            }
        }
    }

    pendingAction?.let { action ->
        GuardianStatusDialog(
            action = action,
            onDismiss = {
                if (state.busyUid == null) {
                    pendingAction = null
                }
            },
            onConfirm = { reason ->
                viewModel.updateStatus(
                    guardian = action.guardian,
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
            onDismiss = {
                if (!state.policyBusy) {
                    pendingPolicyChange = null
                }
            },
            onConfirm = {
                viewModel.setPolicy(required)
                pendingPolicyChange = null
            }
        )
    }

    photoGuardian?.let { guardian ->
        GuardianVerificationPhotoDialog(
            guardian = guardian,
            onDismiss = { photoGuardian = null }
        )
    }

    // Keep terminal action feedback last so it remains the topmost modal.
    state.error?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Error,
            title = "Verification action not completed",
            onDismiss = viewModel::clearFeedback
        )
    }

    state.message?.let { message ->
        FeedbackCard(
            message = message,
            tone = FeedbackTone.Success,
            title = state.messageTitle ?: "Action completed",
            onDismiss = viewModel::clearFeedback
        )
    }
}

@Composable
private fun VerificationPolicyCard(
    verificationRequired: Boolean,
    pendingCount: Int,
    busy: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surface,
        border = BorderStroke(
            1.dp,
            if (verificationRequired) {
                scheme.primary.copy(alpha = 0.24f)
            } else {
                scheme.outlineVariant
            }
        ),
        shadowElevation = if (verificationRequired) 3.dp else 1.dp,
        tonalElevation = 1.dp
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
                    modifier = Modifier.size(50.dp),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (verificationRequired) {
                                Icons.Filled.VerifiedUser
                            } else {
                                Icons.Filled.Policy
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guardian verification policy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (verificationRequired) {
                            "School review required"
                        } else {
                            "School review optional"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (verificationRequired) {
                            "New parent-added guardians must be reviewed before they can authorize student pickup."
                        } else {
                            "New guardians can become pickup-authorized without a separate school identity review."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(
                        checked = verificationRequired,
                        onCheckedChange = onToggle,
                        enabled = enabled
                    )
                }
            }

            if (verificationRequired && pendingCount > 0) {
                HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.tertiaryContainer
                    ) {
                        Icon(
                            Icons.Filled.PendingActions,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(17.dp),
                            tint = scheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            text = "$pendingCount awaiting school review",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pending guardians cannot authorize pickup while verification is required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
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
        Column {
            Text(
                text = "Identity assurance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "A clear view of guardian pickup authorization across the school.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = total,
                label = "Total",
                icon = Icons.Filled.PeopleAlt,
                kind = MetricKind.TOTAL
            )
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = pending,
                label = "Pending",
                icon = Icons.Filled.Schedule,
                kind = MetricKind.ATTENTION
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = verified,
                label = "Verified",
                icon = Icons.Filled.Verified,
                kind = MetricKind.POSITIVE
            )
            VerificationMetric(
                modifier = Modifier.weight(1f),
                value = suspended,
                label = "Suspended",
                icon = Icons.Filled.Block,
                kind = MetricKind.DANGER
            )
        }
    }
}

@Composable
private fun VerificationMetric(
    modifier: Modifier,
    value: Int,
    label: String,
    icon: ImageVector,
    kind: MetricKind
) {
    val scheme = MaterialTheme.colorScheme
    val success = successAccent()
    val accent = when (kind) {
        MetricKind.TOTAL -> scheme.primary
        MetricKind.ATTENTION -> scheme.tertiary
        MetricKind.POSITIVE -> success
        MetricKind.DANGER -> scheme.error
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = if (value > 0) 0.22f else 0.10f)
        ),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttentionCard(pendingCount: Int) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.tertiaryContainer.copy(alpha = 0.52f),
        border = BorderStroke(
            1.dp,
            scheme.tertiary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = scheme.tertiary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AssignmentInd,
                        contentDescription = null,
                        tint = scheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Identity reviews need attention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer
                )
                Text(
                    text = "$pendingCount guardian${if (pendingCount == 1) " is" else "s are"} waiting for a school decision. Review identity and relationship before granting pickup access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onTertiaryContainer.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun SearchAndFilterHeader(
    visibleCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Review guardians",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Search by guardian, email, or linked student.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "$visibleCount of $totalCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        shape = MaterialTheme.shapes.large,
        label = { Text("Search guardians") },
        placeholder = { Text("Name, email or student") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        )
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
        items(
            items = GuardianStatusFilter.entries,
            key = { it.name }
        ) { filter ->
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
    busy: Boolean,
    onPhotoClick: () -> Unit,
    onVerify: () -> Unit,
    onSuspend: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val status = guardian.status.lowercase()
    val isPending = status == "pending"
    val isVerified = status == "verified"
    val isSuspended = status == "suspended"

    val accent = when {
        isVerified -> successAccent()
        isSuspended -> scheme.error
        else -> scheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface
        ),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = if (isPending || isSuspended) 0.24f else 0.14f)
        ),
        elevation = CardDefaults.cardElevation(
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
                GuardianIdentityAvatar(
                    guardian = guardian,
                    status = status,
                    onPhotoClick = onPhotoClick
                )

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = guardian.displayName.ifBlank { "Guardian" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (guardian.email.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = guardian.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
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
                    VerifiedContext(guardian)
                }
            }

            HorizontalDivider(
                color = scheme.outlineVariant
            )

            GuardianCardActions(
                isPending = isPending,
                isVerified = isVerified,
                isSuspended = isSuspended,
                enabled = enabled,
                busy = busy,
                onVerify = onVerify,
                onSuspend = onSuspend
            )
        }
    }
}

@Composable
private fun GuardianCardActions(
    isPending: Boolean,
    isVerified: Boolean,
    isSuspended: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onVerify: () -> Unit,
    onSuspend: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    if (busy) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Updating guardian…",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant
            )
        }
        return
    }

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
                    modifier = Modifier.heightIn(min = 44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = scheme.error
                    ),
                    border = BorderStroke(
                        1.dp,
                        scheme.error.copy(alpha = 0.42f)
                    )
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
                    Text(
                        "Verify guardian",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            isVerified -> {
                OutlinedButton(
                    onClick = onSuspend,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = scheme.error
                    ),
                    border = BorderStroke(
                        1.dp,
                        scheme.error.copy(alpha = 0.42f)
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
                    Text(
                        "Verify & restore",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GuardianIdentityAvatar(
    guardian: GuardianVerificationItem,
    status: String,
    onPhotoClick: () -> Unit
) {
    val photoUrl = guardian.photoUrl
    if (photoUrl.isNullOrBlank()) {
        GuardianInitialsAvatar(
            name = guardian.displayName,
            status = status
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    val accent = when (status) {
        "verified" -> successAccent()
        "suspended" -> scheme.error
        "pending" -> scheme.tertiary
        else -> scheme.primary
    }

    Box(
        modifier = Modifier.clickable(onClick = onPhotoClick),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(2.dp, accent.copy(alpha = 0.34f)),
            color = scheme.surfaceVariant
        ) {
            SmartImage(
                model = photoUrl,
                contentDescription = "Guardian photo for " + guardian.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.ZoomIn,
                    contentDescription = "View guardian photo",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GuardianVerificationPhotoDialog(
    guardian: GuardianVerificationItem,
    onDismiss: () -> Unit
) {
    val photoUrl = guardian.photoUrl ?: return
    var scale by remember(photoUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(photoUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(photoUrl) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale

        if (nextScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val scheme = MaterialTheme.colorScheme

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm)
                .statusBarsPadding()
                .navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surface,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GUARDIAN IDENTITY PHOTO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.primary
                        )
                        Text(
                            text = guardian.displayName.ifBlank { "Guardian" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Inspect the stored profile image before changing pickup authorization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close guardian photo"
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transformState),
                        contentAlignment = Alignment.Center
                    ) {
                        SmartImage(
                            model = photoUrl,
                            contentDescription = "Enlarged guardian photo for " + guardian.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinch to zoom · drag while zoomed",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (scale > 1.01f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianInitialsAvatar(
    name: String,
    status: String
) {
    val scheme = MaterialTheme.colorScheme
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "G" }
    }

    val accent = when (status) {
        "verified" -> successAccent()
        "suspended" -> scheme.error
        "pending" -> scheme.tertiary
        else -> scheme.primary
    }

    Surface(
        modifier = Modifier.size(52.dp),
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.18f)
        ),
        contentColor = accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuardianStatusBadge(status: String) {
    val scheme = MaterialTheme.colorScheme
    val success = successAccent()

    val style = when (status) {
        "pending" -> StatusStyle(
            label = "Pending",
            icon = Icons.Filled.Schedule,
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
        )

        "verified" -> StatusStyle(
            label = "Verified",
            icon = Icons.Filled.Verified,
            container = success.copy(alpha = 0.12f),
            content = success
        )

        "suspended" -> StatusStyle(
            label = "Suspended",
            icon = Icons.Filled.Block,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer
        )

        else -> StatusStyle(
            label = status
                .ifBlank { "Unknown" }
                .replaceFirstChar { it.uppercase() },
            icon = Icons.Filled.HelpOutline,
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant
        )
    }

    Surface(
        shape = CircleShape,
        color = style.container
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                style.icon,
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = style.content
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
private fun StudentLinksSummary(
    studentNames: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 11.dp
            ),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (studentNames.size == 1) {
                        "Authorized student"
                    } else {
                        "Authorized students"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
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
private fun VerifiedContext(
    guardian: GuardianVerificationItem
) {
    val success = successAccent()

    val meta = buildList {
        guardian.verifiedAt
            ?.takeIf { it.isNotBlank() }
            ?.let {
                add("Verified ${formatVerificationTime(it)}")
            }

        guardian.verificationReason
            .takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }.joinToString(" · ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = success.copy(alpha = 0.07f),
        border = BorderStroke(
            1.dp,
            success.copy(alpha = 0.16f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = success,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pickup identity verified",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = success
                )
                Text(
                    text = meta.ifBlank {
                        "This guardian is currently authorized under the school's verification policy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val scheme = MaterialTheme.colorScheme

    val container = if (danger) {
        scheme.errorContainer.copy(alpha = 0.55f)
    } else {
        scheme.tertiaryContainer.copy(alpha = 0.50f)
    }

    val content = if (danger) {
        scheme.onErrorContainer
    } else {
        scheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        border = BorderStroke(
            1.dp,
            if (danger) {
                scheme.error.copy(alpha = 0.16f)
            } else {
                scheme.tertiary.copy(alpha = 0.18f)
            }
        )
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
                    color = content.copy(alpha = 0.82f)
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
        color = MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.size(54.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hasGuardians) {
                            Icons.Filled.SearchOff
                        } else {
                            Icons.Filled.PeopleOutline
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = if (hasGuardians) {
                    "No guardians match"
                } else {
                    "No guardians to review"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hasGuardians) {
                    "Try another name, email, student or verification status."
                } else {
                    "Guardian identities will appear here when they are linked to students."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Guardian verification controls pickup authorization. Suspend access when pickup must be blocked, and verify only after the school has completed its identity-check process.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuardianStatusDialog(
    action: GuardianAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val isSuspension =
        action.targetStatus == "suspended"

    var reason by remember(
        action.guardian.uid,
        action.targetStatus
    ) {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = if (isSuspension) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Icon(
                    imageVector = if (isSuspension) {
                        Icons.Filled.GppBad
                    } else {
                        Icons.Filled.VerifiedUser
                    },
                    contentDescription = null,
                    tint = if (isSuspension) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(10.dp)
                )
            }
        },
        title = {
            Text(
                text = if (isSuspension) {
                    "Suspend pickup access?"
                } else {
                    "Verify this guardian?"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = if (isSuspension) {
                        "${action.guardian.displayName} will no longer be allowed to authorize student pickup until the school verifies them again."
                    } else {
                        "Confirm that the school reviewed ${action.guardian.displayName}'s identity and relationship before granting pickup access."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    label = {
                        Text(
                            if (isSuspension) {
                                "Suspension reason"
                            } else {
                                "Review note (optional)"
                            }
                        )
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
                    isError =
                        isSuspension &&
                            reason.isBlank(),
                    supportingText =
                        if (
                            isSuspension &&
                            reason.isBlank()
                        ) {
                            {
                                Text(
                                    "Enter a reason before suspending pickup access."
                                )
                            }
                        } else {
                            null
                        }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(reason.trim())
                },
                enabled =
                    !isSuspension ||
                        reason.isNotBlank(),
                colors = if (isSuspension) {
                    ButtonDefaults.buttonColors(
                        containerColor =
                            MaterialTheme.colorScheme.error,
                        contentColor =
                            MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    if (isSuspension) {
                        "Suspend access"
                    } else {
                        "Verify guardian"
                    }
                )
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
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (required) {
                        Icons.Filled.VerifiedUser
                    } else {
                        Icons.Filled.Policy
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        },
        title = {
            Text(
                text = if (required) {
                    "Require guardian verification?"
                } else {
                    "Make verification optional?"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
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
                        color =
                            MaterialTheme.colorScheme.tertiaryContainer
                                .copy(alpha = 0.55f)
                    ) {
                        Text(
                            text =
                                "$pendingCount currently pending guardian${if (pendingCount == 1) " remains" else "s remain"} pending until their status is changed explicitly.",
                            modifier = Modifier.padding(Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onTertiaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(
                    if (required) {
                        "Require verification"
                    } else {
                        "Make optional"
                    }
                )
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
private fun successAccent(): Color {
    return if (isSystemInDarkTheme()) {
        Green500
    } else {
        Green600
    }
}

private fun formatVerificationTime(
    value: String
): String {
    return try {
        val instant = Instant.parse(value)
        DateTimeFormatter
            .ofPattern("MMM d, yyyy · h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        value.take(16).replace('T', ' ')
    }
}
