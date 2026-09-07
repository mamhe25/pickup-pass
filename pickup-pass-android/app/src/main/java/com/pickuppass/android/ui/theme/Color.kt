package com.pickuppass.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PickupPass premium color system.
 *
 * Brand identity: midnight indigo + blue-violet.
 * Semantic colors are intentionally separate from the brand:
 * - green = success / verified / safely completed
 * - amber = warning / attention
 * - red = error / destructive
 * - blue = informational
 */
val Indigo50 = Color(0xFFF4F5FF)
val Indigo100 = Color(0xFFE9EDFF)
val Indigo200 = Color(0xFFD7DCFF)
val Indigo400 = Color(0xFF8188F5)
val Indigo500 = Color(0xFF5B5CE2)
val Indigo600 = Color(0xFF4652C7)
val Indigo700 = Color(0xFF35409C)
val Indigo800 = Color(0xFF293477)
val Indigo900 = Color(0xFF1E275C)
val Midnight950 = Color(0xFF12182F)

val Violet50 = Color(0xFFF6F3FF)
val Violet100 = Color(0xFFEDE9FE)
val Violet400 = Color(0xFF8B7CF6)
val Violet500 = Color(0xFF6D5DFB)
val Violet600 = Color(0xFF5E4EE8)
val Violet900 = Color(0xFF30266D)

val Blue50 = Color(0xFFEFF6FF)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val Blue900 = Color(0xFF1E3A8A)

val Success50 = Color(0xFFECFDF3)
val Success100 = Color(0xFFD1FADF)
val Success500 = Color(0xFF12B76A)
val Success600 = Color(0xFF168A5B)
val Success700 = Color(0xFF107B4E)
val Success900 = Color(0xFF054F31)

val Red50 = Color(0xFFFEF3F2)
val Red500 = Color(0xFFF04438)
val Red600 = Color(0xFFD92D20)
val Red900 = Color(0xFF7A271A)

val Amber50 = Color(0xFFFFFAEB)
val Amber100 = Color(0xFFFEF0C7)
val Amber500 = Color(0xFFF4A524)
val Amber700 = Color(0xFFB96B0B)
val Amber900 = Color(0xFF713B12)

val Gray50 = Color(0xFFF8F9FC)
val Gray100 = Color(0xFFF1F3F9)
val Gray200 = Color(0xFFE1E5EE)
val Gray300 = Color(0xFFD0D5DD)
val Gray400 = Color(0xFF98A2B3)
val Gray500 = Color(0xFF667085)
val Gray600 = Color(0xFF475467)
val Gray700 = Color(0xFF344054)
val Gray800 = Color(0xFF1D2939)
val Gray900 = Color(0xFF151B2E)

val Surface = Color(0xFFFFFFFF)

/*
 * Compatibility aliases for screens not yet migrated to semantic tokens.
 * Former PickupPass "evergreen" brand imports now resolve to indigo, so the
 * old green brand cannot leak back into legacy screens during the makeover.
 */
val Evergreen50 = Indigo50
val Evergreen100 = Indigo100
val Evergreen600 = Indigo600
val Evergreen700 = Indigo700
val Evergreen900 = Indigo900

// Older emerald/teal/green imports remain success semantics.
val Emerald400 = Success500
val Emerald500 = Success600
val Teal50 = Success50
val Teal100 = Success100
val Teal400 = Success500
val Teal600 = Success600
val Teal700 = Success700
val Teal900 = Success900
val Green500 = Success500
val Green600 = Success600
val Green700 = Success700
val Green900 = Success900

// Legacy tertiary token. Warm amber is now the controlled accent.
val Lime500 = Amber500
