package com.pickuppass.android.ui.teacher.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickuppass.android.ui.common.ErrorBanner
import com.pickuppass.android.ui.common.SuccessBanner
import com.pickuppass.android.ui.theme.Spacing

private const val TITLE_LIMIT = 120
private const val BODY_LIMIT = 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherBroadcastScreen(
    viewModel:
        TeacherBroadcastViewModel =
        hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    var title by remember {
        mutableStateOf("")
    }

    var body by remember {
        mutableStateOf("")
    }

    var showConfirmation by remember {
        mutableStateOf(false)
    }

    val canSend =
        title.isNotBlank() &&
            body.isNotBlank() &&
            !uiState.isSubmitting

    LaunchedEffect(
        uiState.successMessage
    ) {
        if (
            uiState.successMessage != null
        ) {
            title = ""
            body = ""
            showConfirmation = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Announcement")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .imePadding()
                .padding(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                )
        ) {
            AudienceCard()

            Spacer(
                Modifier.height(Spacing.md)
            )

            Text(
                text = "Compose message",
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Spacer(
                Modifier.height(3.dp)
            )

            Text(
                text =
                    "Keep the message clear, specific, and useful to guardians.",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                Modifier.height(Spacing.md)
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (
                        it.length <=
                        TITLE_LIMIT
                    ) {
                        title = it
                    }
                },
                label = {
                    Text("Title")
                },
                placeholder = {
                    Text(
                        "e.g. Dismissal time reminder"
                    )
                },
                supportingText = {
                    Text(
                        "${title.length}/$TITLE_LIMIT"
                    )
                },
                singleLine = true,
                shape =
                    MaterialTheme.shapes.small,
                modifier =
                    Modifier.fillMaxWidth()
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            OutlinedTextField(
                value = body,
                onValueChange = {
                    if (
                        it.length <=
                        BODY_LIMIT
                    ) {
                        body = it
                    }
                },
                label = {
                    Text("Message")
                },
                placeholder = {
                    Text(
                        "What do guardians need to know?"
                    )
                },
                supportingText = {
                    Text(
                        "${body.length}/$BODY_LIMIT"
                    )
                },
                minLines = 5,
                maxLines = 9,
                shape =
                    MaterialTheme.shapes.small,
                modifier =
                    Modifier.fillMaxWidth()
            )

            Spacer(
                Modifier.height(Spacing.sm)
            )

            AnimatedVisibility(
                visible =
                    uiState.error != null,
                enter =
                    fadeIn() +
                        expandVertically()
            ) {
                uiState.error?.let {
                        message ->
                    ErrorBanner(
                        message,
                        modifier =
                            Modifier.padding(
                                bottom =
                                    Spacing.sm
                            )
                    )
                }
            }

            AnimatedVisibility(
                visible =
                    uiState.successMessage != null,
                enter =
                    fadeIn() +
                        expandVertically()
            ) {
                uiState.successMessage?.let {
                        message ->
                    SuccessBanner(
                        message,
                        modifier =
                            Modifier.padding(
                                bottom =
                                    Spacing.sm
                            )
                    )
                }
            }

            DeliveryNote()

            Spacer(
                Modifier.height(Spacing.lg)
            )

            Button(
                onClick = {
                    showConfirmation = true
                },
                enabled = canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape =
                    MaterialTheme.shapes.small
            ) {
                if (
                    uiState.isSubmitting
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color =
                            MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(
                        Modifier.width(Spacing.sm)
                    )

                    Text("Sending…")
                } else {
                    Icon(
                        imageVector =
                            Icons.Filled.Campaign,
                        contentDescription = null
                    )

                    Spacer(
                        Modifier.width(Spacing.sm)
                    )

                    Text("Review announcement")
                }
            }

            Spacer(
                Modifier.height(Spacing.md)
            )
        }
    }

    if (showConfirmation) {
        BroadcastConfirmationDialog(
            title = title.trim(),
            body = body.trim(),
            isSubmitting =
                uiState.isSubmitting,
            onDismiss = {
                if (
                    !uiState.isSubmitting
                ) {
                    showConfirmation =
                        false
                }
            },
            onConfirm = {
                viewModel.send(
                    title.trim(),
                    body.trim()
                )
            }
        )
    }
}

@Composable
private fun AudienceCard() {
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(Spacing.md)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(40.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.People,
                            contentDescription = null,
                            modifier =
                                Modifier.size(20.dp),
                            tint =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(
                    Modifier.width(Spacing.sm)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text =
                            "Guardians in your assigned sections",
                        style =
                            MaterialTheme.typography.titleSmall,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Text(
                        text =
                            "PickupPass determines recipients from your current section assignments.",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(
                Modifier.height(Spacing.md)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                DeliveryChip(
                    icon =
                        Icons.Filled.NotificationsActive,
                    label = "Push notification"
                )

                DeliveryChip(
                    icon =
                        Icons.Filled.Campaign,
                    label = "Notification inbox"
                )
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = CircleShape,
        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 9.dp,
                    vertical = 6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                    Modifier.size(15.dp),
                tint =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(
                Modifier.width(5.dp)
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeliveryNote() {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        color =
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier =
                Modifier.padding(Spacing.md),
            verticalAlignment =
                Alignment.Top
        ) {
            Icon(
                imageVector =
                    Icons.Filled.NotificationsActive,
                contentDescription = null,
                modifier =
                    Modifier.size(19.dp),
                tint =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(
                Modifier.width(Spacing.sm)
            )

            Column {
                Text(
                    text = "Immediate delivery",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    fontWeight =
                        FontWeight.ExtraBold
                )

                Text(
                    text =
                        "After confirmation, PickupPass sends this announcement immediately. Review the title, message, and audience before sending.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BroadcastConfirmationDialog(
    title: String,
    body: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier =
                    Modifier.size(44.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Campaign,
                        contentDescription = null,
                        tint =
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        title = {
            Text(
                text =
                    "Send this announcement?",
                fontWeight =
                    FontWeight.ExtraBold
            )
        },
        text = {
            Column {
                Text(
                    text =
                        "This will be sent immediately to reachable guardians in your assigned sections.",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    Modifier.height(Spacing.md)
                )

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        MaterialTheme.shapes.medium,
                    color =
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier =
                            Modifier.padding(Spacing.md)
                    ) {
                        Text(
                            text = title,
                            maxLines = 2,
                            overflow =
                                TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.titleSmall,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            text = body,
                            maxLines = 6,
                            overflow =
                                TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled =
                    !isSubmitting
            ) {
                Text("Keep editing")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled =
                    !isSubmitting
            ) {
                if (
                    isSubmitting
                ) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color =
                            MaterialTheme.colorScheme.onPrimary
                    )

                    Spacer(
                        Modifier.width(Spacing.sm)
                    )
                }

                Text(
                    if (
                        isSubmitting
                    ) {
                        "Sending…"
                    } else {
                        "Send now"
                    }
                )
            }
        }
    )
}
