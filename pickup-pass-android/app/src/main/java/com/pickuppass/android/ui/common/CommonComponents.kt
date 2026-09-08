package com.pickuppass.android.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pickuppass.android.R
import com.pickuppass.android.ui.theme.Amber500
import com.pickuppass.android.ui.theme.Amber700
import com.pickuppass.android.ui.theme.Spacing
import com.pickuppass.android.ui.theme.Success500
import com.pickuppass.android.ui.theme.Success600
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SMART_IMAGE_MAX_DIMENSION_PX = 1024

@Composable
fun PickupPassBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.pickuppass_logo_full),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
fun PickupPassWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    textAlign: TextAlign? = null,
) {
    val dark = isSystemInDarkTheme()
    val pickupColor = if (dark) Color(0xFFF7F8FF) else Color(0xFF243A9A)
    val passColor = if (dark) Color(0xFFA89CFF) else Color(0xFF6D5DFB)

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = pickupColor)) { append("Pickup") }
            withStyle(SpanStyle(color = passColor)) { append("Pass") }
        },
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.ExtraBold,
        textAlign = textAlign,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .height(50.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(text, style = MaterialTheme.typography.labelLarge)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun NotificationActionButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayCount =
        when {
            unreadCount > 99 -> "99+"
            unreadCount > 0 -> unreadCount.toString()
            else -> ""
        }

    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription =
                    if (unreadCount > 0) {
                        "Notifications, $unreadCount unread"
                    } else {
                        "Notifications"
                    },
                modifier = Modifier.size(26.dp),
            )
        }

        if (displayCount.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 1.dp)
                    .height(20.dp)
                    .widthIn(min = 20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shadowElevation = 1.dp,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayCount,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTopAppBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            },
            actions = actions,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        )
    }
}

enum class FeedbackTone {
    Success,
    Error,
    Warning,
    Info,
}

/**
 * Canonical PickupPass action-feedback surface.
 *
 * Terminal action outcomes are presented as a centered modal so they never
 * appear underneath the field, list, or card that triggered the action. The
 * dialog deliberately stays visible until the user dismisses it, keeping
 * important school, guardian, security, and administrative feedback readable.
 */
@Composable
fun FeedbackCard(
    message: String,
    tone: FeedbackTone,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (message.isBlank()) return

    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val accent: Color
    val icon: ImageVector
    val defaultTitle: String
    val kicker: String
    val actionLabel: String
    val liveRegionMode: LiveRegionMode

    when (tone) {
        FeedbackTone.Success -> {
            accent = if (isDark) Success500 else Success600
            icon = Icons.Filled.CheckCircle
            defaultTitle = "Success"
            kicker = "Action completed"
            actionLabel = "Done"
            liveRegionMode = LiveRegionMode.Polite
        }
        FeedbackTone.Error -> {
            accent = scheme.error
            icon = Icons.Filled.Error
            defaultTitle = "Something went wrong"
            kicker = "Action not completed"
            actionLabel = "Close"
            liveRegionMode = LiveRegionMode.Assertive
        }
        FeedbackTone.Warning -> {
            accent = if (isDark) Amber500 else Amber700
            icon = Icons.Filled.Warning
            defaultTitle = "Please review"
            kicker = "Review required"
            actionLabel = "Got it"
            liveRegionMode = LiveRegionMode.Polite
        }
        FeedbackTone.Info -> {
            accent = scheme.primary
            icon = Icons.Filled.Info
            defaultTitle = "Information"
            kicker = "PickupPass update"
            actionLabel = "Okay"
            liveRegionMode = LiveRegionMode.Polite
        }
    }

    var visible by remember(message, tone) { mutableStateOf(true) }
    if (!visible) return

    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            color = scheme.surface,
            contentColor = scheme.onSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
            shadowElevation = 28.dp,
            tonalElevation = 1.dp,
            modifier = modifier
                .padding(horizontal = Spacing.md)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .semantics { liveRegion = liveRegionMode },
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.md,
                            top = Spacing.md,
                            end = Spacing.sm,
                            bottom = Spacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PickupPassBrandMark(size = 32.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        PickupPassWordmark(
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "SECURE ACTION FEEDBACK",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { visible = false },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close message",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = accent.copy(alpha = if (isDark) 0.20f else 0.10f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = kicker.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                        )
                        Text(
                            text = title ?: defaultTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    color = scheme.surfaceVariant.copy(alpha = 0.54f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { visible = false },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                            ),
                            modifier = Modifier.widthIn(min = 96.dp),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    icon: ImageVector = Icons.Filled.Warning,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (destructive) scheme.error else scheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = Spacing.md)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.surface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
            shadowElevation = 24.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = accent.copy(alpha = 0.10f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = if (destructive) "CONFIRM IMPORTANT ACTION" else "CONFIRM ACTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                        ),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumHeroCard(
    eyebrow: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary,
                            scheme.secondary,
                        ),
                    ),
                )
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.onPrimary.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onPrimary.copy(alpha = 0.80f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onPrimary,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimary.copy(alpha = 0.82f),
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    FeedbackCard(
        message = message,
        tone = FeedbackTone.Error,
        modifier = modifier
    )
}

/**
 * Amber, rather than danger/success, for partial-success and caution states.
 */
@Composable
fun WarningBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    FeedbackCard(
        message = message,
        tone = FeedbackTone.Warning,
        modifier = modifier
    )
}

@Composable
fun SuccessBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    FeedbackCard(
        message = message,
        tone = FeedbackTone.Success,
        modifier = modifier
    )
}

@Composable
fun InfoBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    FeedbackCard(
        message = message,
        tone = FeedbackTone.Info,
        modifier = modifier
    )
}

/**
 * Renders either a Firestore data:image/...;base64 URI or a regular HTTP(S)
 * URL. Base64 decoding is kept off the main thread so loading school logos
 * and guardian photos cannot stall Compose frames during startup or scrolling.
 */
@Composable
fun SmartImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (model.isNullOrBlank()) return

    if (model.startsWith("data:")) {
        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = model
        ) {
            value = withContext(Dispatchers.Default) {
                decodeDataUri(model)
            }
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

private fun decodeDataUri(dataUri: String): Bitmap? {
    return try {
        val base64Part = dataUri.substringAfter(",", "")
        if (base64Part.isEmpty()) return null

        val bytes = Base64.decode(base64Part, Base64.DEFAULT)

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = SMART_IMAGE_MAX_DIMENSION_PX
            )
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    }
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int
): Int {
    if (width <= 0 || height <= 0) return 1

    var sampleSize = 1
    while (
        width / sampleSize > maxDimension ||
        height / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

@Composable
fun GuardianAvatar(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            SmartImage(
                model = photoUrl,
                contentDescription = "Guardian photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Shared full-screen loading state. This deliberately fills the available
 * content area; fillMaxWidth() left the spinner visually stuck at the top of
 * several Scaffold bodies.
 */
@Composable
fun FullScreenLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Screen title with optional school identity.
 */
@Composable
fun BrandedTitle(
    title: String,
    school: com.pickuppass.android.data.model.SchoolInfo?,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = Color.Unspecified
) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )

        if (school != null && !school.schoolName.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!school.logoUrl.isNullOrBlank()) {
                    SmartImage(
                        model = school.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 2.dp, end = 4.dp)
                            .size(16.dp)
                    )
                }

                Text(
                    school.schoolName,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (subtitleColor != Color.Unspecified) subtitleColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected ?: "All",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )

            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Calm confirmation moment after important successful actions.
 */
@Composable
fun SuccessConfirmation(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successCheckScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isSystemInDarkTheme()) Success500 else Success600).copy(alpha = 0.14f),
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (isSystemInDarkTheme()) Success500 else Success600,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
