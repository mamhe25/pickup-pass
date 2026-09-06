package com.pickuppass.android.ui.parent.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.data.model.NotificationItem
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.FullScreenLoading
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class NotificationFilter {
    ALL,
    UNREAD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(NotificationFilter.ALL) }

    val unreadCount = uiState.notifications.count { !it.read }
    val visibleNotifications = remember(uiState.notifications, filter) {
        when (filter) {
            NotificationFilter.ALL -> uiState.notifications
            NotificationFilter.UNREAD -> uiState.notifications.filter { !it.read }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notifications", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "$unreadCount unread",
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
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.notifications.isEmpty() ->
                    FullScreenLoading()

                uiState.error != null && uiState.notifications.isEmpty() ->
                    NotificationErrorState(
                        message = uiState.error ?: "Couldn't load notifications",
                        onRetry = viewModel::load
                    )

                else -> {
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        item(key = "hero") {
                            NotificationsHero(
                                unreadCount = unreadCount,
                                totalCount = uiState.notifications.size,
                                hasUnread = uiState.hasUnread,
                                isUpdating = uiState.isUpdating,
                                onMarkAllRead = viewModel::markAllAsRead
                            )
                        }

                        uiState.error?.let { message ->
                            item(key = "error") { ErrorBanner(message) }
                        }

                        uiState.message?.let { message ->
                            item(key = "success") { SuccessBanner(message) }
                        }

                        item(key = "filters") {
                            NotificationFilterBar(
                                filter = filter,
                                unreadCount = unreadCount,
                                onFilterChange = { filter = it }
                            )
                        }

                        if (visibleNotifications.isEmpty()) {
                            item(key = "empty") {
                                NotificationEmptyState(
                                    unreadOnly = filter == NotificationFilter.UNREAD
                                )
                            }
                        } else {
                            items(
                                items = visibleNotifications,
                                key = { "notification-${it.id}" }
                            ) { notification ->
                                NotificationCard(
                                    notification = notification,
                                    updating = uiState.isUpdating,
                                    onClick = {
                                        viewModel.markAsRead(notification)
                                    }
                                )
                            }
                        }

                        if (uiState.notifications.isNotEmpty()) {
                            item(key = "safety") {
                                NotificationSafetyNote()
                            }
                        }
                    }

                    if (uiState.isUpdating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsHero(
    unreadCount: Int,
    totalCount: Int,
    hasUnread: Boolean,
    isUpdating: Boolean,
    onMarkAllRead: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                "FAMILY UPDATES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                when {
                    unreadCount > 0 ->
                        "$unreadCount update${if (unreadCount == 1) "" else "s"} need your attention"

                    totalCount > 0 ->
                        "You're all caught up"

                    else ->
                        "Important pickup updates live here"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                "Pickup confirmations, school announcements, and safety-related messages stay together in one place.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (unreadCount > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "$unreadCount unread · $totalCount total",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (hasUnread) {
                    TextButton(
                        onClick = onMarkAllRead,
                        enabled = !isUpdating,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Mark all read")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterBar(
    filter: NotificationFilter,
    unreadCount: Int,
    onFilterChange: (NotificationFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(
            selected = filter == NotificationFilter.ALL,
            onClick = { onFilterChange(NotificationFilter.ALL) },
            label = { Text("All") }
        )

        FilterChip(
            selected = filter == NotificationFilter.UNREAD,
            onClick = { onFilterChange(NotificationFilter.UNREAD) },
            label = {
                Text(if (unreadCount > 0) "Unread $unreadCount" else "Unread")
            }
        )
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    updating: Boolean,
    onClick: () -> Unit
) {
    val unread = !notification.read
    val presentation = notificationPresentation(notification.type)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = unread && !updating,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (unread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (unread) 2.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (unread) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        presentation.icon,
                        contentDescription = null,
                        tint = if (unread) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            notification.title.ifBlank { presentation.label },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (unread) {
                                FontWeight.ExtraBold
                            } else {
                                FontWeight.SemiBold
                            }
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            presentation.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (unread) {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(9.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                    }
                }

                if (notification.body.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        notification.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (
                    notification.type.equals("broadcast", ignoreCase = true) &&
                    !notification.senderName.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "From ${notification.senderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                notification.createdAtMillis?.let { millis ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        formatNotificationTime(millis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }

                if (unread) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Tap to mark as read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private data class NotificationPresentation(
    val label: String,
    val icon: ImageVector
)

private fun notificationPresentation(type: String): NotificationPresentation {
    val normalized = type.trim().lowercase()

    return when {
        normalized == "broadcast" || "announcement" in normalized ->
            NotificationPresentation(
                "School announcement",
                Icons.Filled.Campaign
            )

        "release" in normalized ||
            "pickup" in normalized ||
            "dismiss" in normalized ->
            NotificationPresentation(
                "Pickup update",
                Icons.Filled.CheckCircle
            )

        "guardian" in normalized ||
            "security" in normalized ||
            "verification" in normalized ->
            NotificationPresentation(
                "Safety update",
                Icons.Filled.VerifiedUser
            )

        else ->
            NotificationPresentation(
                "PickupPass update",
                Icons.Filled.Notifications
            )
    }
}

@Composable
private fun NotificationEmptyState(unreadOnly: Boolean) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.lg,
                    vertical = 34.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                if (unreadOnly) "You're all caught up" else "No notifications yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                if (unreadOnly) {
                    "There are no unread family updates right now."
                } else {
                    "Pickup confirmations and school announcements will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NotificationErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ErrorBanner(message)
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun NotificationSafetyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Notification messages are informational. Student release still requires the normal PickupPass verification workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatNotificationTime(millis: Long): String {
    val date = Date(millis)
    val target = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()

    val sameDay =
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

    return if (sameDay) {
        SimpleDateFormat("'Today,' h:mm a", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(date)
    }
}
