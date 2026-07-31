package com.pickuppass.android.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing, on a 4dp grid (Material's standard
 * baseline). Before this existed, screens hand-wrote padding(Ndp) with
 * seven different values (10, 12, 14, 16, 20, 24, 32dp) with no shared
 * logic for which value applied where.
 *
 * Guidance:
 * - xs (4dp): tight internal spacing — icon-to-text gaps, chip padding
 * - sm (8dp): spacing between closely related elements (label + field)
 * - md (16dp): the default — screen edge padding, spacing between cards
 * - lg (24dp): spacing between distinct sections on a screen
 * - xl (32dp): generous breathing room — empty states, onboarding-style screens
 * - xxl (48dp): rare — large vertical rhythm on sparse screens (splash, empty states)
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}
