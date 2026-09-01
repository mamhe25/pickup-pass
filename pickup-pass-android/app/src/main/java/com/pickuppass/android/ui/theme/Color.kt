package com.pickuppass.android.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — indigo primary (trust/security), green for the release-approval
// action, red reserved exclusively for warnings/errors so it never gets diluted,
// amber reserved exclusively for non-blocking caution states (e.g. "account
// created but the invite email failed to send") so it never gets confused
// with a hard error.
val Indigo50 = Color(0xFFECFDF5)
val Indigo100 = Color(0xFFD1FAE5)
val Indigo500 = Color(0xFF10B981)
val Indigo600 = Color(0xFF047857)
val Indigo700 = Color(0xFF065F46)
val Indigo900 = Color(0xFF064E3B)

val Green500 = Color(0xFF2DD4BF)
val Green600 = Color(0xFF0F766E)
val Green700 = Color(0xFF115E59)
val Green900 = Color(0xFF134E4A)

val Red500 = Color(0xFFEF4444)
val Red600 = Color(0xFFDC2626)
val Red900 = Color(0xFF7F1D1D)

val Amber50 = Color(0xFFFFFBEB)
val Amber500 = Color(0xFFF59E0B)
val Amber700 = Color(0xFFB45309)
val Amber900 = Color(0xFF78350F)

// Full gray scale (previously had gaps at 300/600 that individual screens
// were filling with one-off hardcoded hex values instead of a named token).
val Gray50 = Color(0xFFF9FAFB)
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray700 = Color(0xFF374151)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)

val Surface = Color(0xFFFFFFFF)
