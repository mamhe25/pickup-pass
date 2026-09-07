package com.pickuppass.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Surface,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,

    secondary = Violet600,
    onSecondary = Surface,
    secondaryContainer = Violet100,
    onSecondaryContainer = Violet900,

    tertiary = Amber500,
    onTertiary = Gray900,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Amber900,

    error = Red600,
    onError = Surface,
    errorContainer = Red50,
    onErrorContainer = Red900,

    background = Color(0xFFF7F8FC),
    onBackground = Gray900,

    surface = Surface,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,

    outline = Gray300,
    outlineVariant = Gray200,
    scrim = Midnight950,
)

private val DarkColors = darkColorScheme(
    primary = Indigo400,
    onPrimary = Midnight950,
    primaryContainer = Indigo800,
    onPrimaryContainer = Indigo100,

    secondary = Violet400,
    onSecondary = Midnight950,
    secondaryContainer = Color(0xFF342D74),
    onSecondaryContainer = Violet100,

    tertiary = Amber500,
    onTertiary = Midnight950,
    tertiaryContainer = Color(0xFF5C3B13),
    onTertiaryContainer = Amber100,

    error = Red500,
    onError = Midnight950,
    errorContainer = Color(0xFF5A1F1B),
    onErrorContainer = Color(0xFFFEE4E2),

    background = Color(0xFF0E1326),
    onBackground = Gray50,

    surface = Color(0xFF151B30),
    onSurface = Gray50,
    surfaceVariant = Color(0xFF202841),
    onSurfaceVariant = Gray300,

    outline = Color(0xFF4A5574),
    outlineVariant = Color(0xFF303A57),
    scrim = Color.Black,
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
            window.navigationBarColor = colorScheme.background.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PickupPassTypography,
        shapes = PickupPassShapes,
        content = content
    )
}
