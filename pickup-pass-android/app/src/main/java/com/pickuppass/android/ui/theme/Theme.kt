package com.pickuppass.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Surface,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo700,
    secondary = Green600,
    onSecondary = Surface,
    error = Red600,
    onError = Surface,
    background = Gray50,
    onBackground = Gray900,
    surface = Surface,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    outline = Gray200,
)

// Previously only defined 8 of the ~15 roles LightColors defines — any
// screen referencing the missing ones in dark mode (primaryContainer,
// surfaceVariant, outline, etc.) silently fell back to Material3's
// generic default purple-ish scheme instead of the actual brand, which
// was a real dark-mode visual bug, not just an incompleteness. Container
// roles use deeper, more saturated tones than their light-mode pastel
// counterparts — the standard Material3 dark-scheme convention — rather
// than reusing the light-mode container colors directly.
private val DarkColors = darkColorScheme(
    primary = Indigo500,
    onPrimary = Gray900,
    primaryContainer = Indigo900,
    onPrimaryContainer = Indigo100,
    secondary = Green500,
    onSecondary = Gray900,
    error = Red500,
    onError = Gray900,
    background = Gray900,
    onBackground = Gray50,
    surface = Gray800,
    onSurface = Gray50,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray300,
    outline = Gray600,
)

@Composable
fun PickupPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PickupPassTypography,
        shapes = PickupPassShapes,
        content = content
    )
}
