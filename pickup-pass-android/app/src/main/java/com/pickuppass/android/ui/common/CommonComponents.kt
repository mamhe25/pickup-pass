package com.pickuppass.android.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import com.pickuppass.android.ui.theme.Amber500
import com.pickuppass.android.ui.theme.Amber900
import com.pickuppass.android.ui.theme.Spacing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
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
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}

/**
 * Amber, not red or green — for "the thing you asked for mostly happened,
 * but pay attention to this" states, like an account being created
 * successfully while its invite email failed to send. Visually distinct
 * from both a clean success (SuccessBanner) and a real failure
 * (ErrorBanner), since collapsing this into either would either bury a
 * real problem in green or make a successful account creation look like
 * it failed outright.
 */
@Composable
fun WarningBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = Amber500.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Amber900)
            Text(
                text = message,
                color = Amber900,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}

@Composable
fun SuccessBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}

/**
 * Renders either a "data:image/...;base64,..." URI (decoded on-device — this
 * is how avatars/logos are stored now, directly in Firestore, to avoid
 * Cloud Storage's Blaze-plan billing requirement) or a regular http(s) URL
 * via Coil, so the rest of the app doesn't need to care which shape a given
 * photoUrl/logoUrl happens to be.
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
        val bitmap = remember(model) { decodeDataUri(model) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
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
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun GuardianAvatar(
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp
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
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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

@Composable
fun FullScreenLoading() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Screen title with an optional "logo + school name" subtitle underneath —
 * used on both the parent and teacher/guard home screens so it's always
 * obvious which school's account is currently signed in, which matters once
 * a single device might be used across more than one school over time.
 */
@Composable
fun BrandedTitle(
    title: String,
    school: com.pickuppass.android.data.model.SchoolInfo?,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = Color.Unspecified
) {
    Column {
        Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = titleColor)
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
                    color = if (subtitleColor != Color.Unspecified) subtitleColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Generic string-options dropdown filter — originally built for the
 * Dismissal History screen's grade/section/staff filters, promoted here so
 * the grouped/searchable student list can use the exact same component
 * rather than a second copy of the same ~20 lines.
 */
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

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected ?: "All",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
