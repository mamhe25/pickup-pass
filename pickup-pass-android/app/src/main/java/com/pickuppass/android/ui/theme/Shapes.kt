package com.pickuppass.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for corner radius. Before this existed, screens
 * hand-wrote RoundedCornerShape(...) with five different values (10, 12,
 * 14, 16, 20dp) with no consistent logic for which value went where —
 * every new screen was a fresh guess. Use these tokens instead of a raw
 * RoundedCornerShape(Ndp) anywhere in the app.
 *
 * Guidance on which tier to reach for:
 * - ExtraSmall: chips, badges, small pills
 * - Small: buttons, input fields, dropdowns — MaterialTheme.shapes.small
 * - Medium: standard cards, dialogs — MaterialTheme.shapes.medium
 * - Large: prominent/elevated cards, larger dialogs
 * - ExtraLarge: bottom sheets, full-bleed prominent surfaces
 */
val PickupPassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
