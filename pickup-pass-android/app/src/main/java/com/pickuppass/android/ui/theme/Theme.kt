package com.pickuppass.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors =
    lightColorScheme(
        primary = Evergreen600,
        onPrimary = Surface,
        primaryContainer =
            Evergreen100,
        onPrimaryContainer =
            Evergreen900,

        secondary = Teal700,
        onSecondary = Surface,
        secondaryContainer =
            Teal100,
        onSecondaryContainer =
            Teal900,

        tertiary = Lime500,
        onTertiary = Gray900,

        error = Red600,
        onError = Surface,

        background = Gray50,
        onBackground = Gray900,

        surface = Surface,
        onSurface = Gray900,

        surfaceVariant = Gray100,
        onSurfaceVariant = Gray700,

        outline = Gray200,
        outlineVariant = Gray200
    )

private val DarkColors =
    darkColorScheme(
        primary = Emerald400,
        onPrimary =
            ColorTokens.DeepEvergreen,
        primaryContainer =
            Evergreen900,
        onPrimaryContainer =
            Evergreen100,

        secondary = Teal400,
        onSecondary =
            ColorTokens.DeepTeal,
        secondaryContainer =
            Teal900,
        onSecondaryContainer =
            Teal100,

        tertiary = Lime500,
        onTertiary = Gray900,

        error = Red500,
        onError = Gray900,

        background =
            ColorTokens.DarkBackground,
        onBackground = Gray50,

        surface =
            ColorTokens.DarkSurface,
        onSurface = Gray50,

        surfaceVariant = Gray800,
        onSurfaceVariant =
            Gray300,

        outline = Gray600,
        outlineVariant = Gray700
    )

private object ColorTokens {
    val DeepEvergreen =
        androidx.compose.ui.graphics.Color(
            0xFF022C22
        )

    val DeepTeal =
        androidx.compose.ui.graphics.Color(
            0xFF042F2E
        )

    val DarkBackground =
        androidx.compose.ui.graphics.Color(
            0xFF0B1412
        )

    val DarkSurface =
        androidx.compose.ui.graphics.Color(
            0xFF111C1A
        )
}

@Composable
fun PickupPassTheme(
    darkTheme: Boolean =
        isSystemInDarkTheme(),
    content:
        @Composable () -> Unit
) {
    val colorScheme =
        if (darkTheme) {
            DarkColors
        } else {
            LightColors
        }

    val view =
        LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window =
                (view.context as Activity)
                    .window

            window.statusBarColor =
                colorScheme.background
                    .toArgb()

            window.navigationBarColor =
                colorScheme.background
                    .toArgb()

            WindowCompat
                .getInsetsController(
                    window,
                    view
                )
                .isAppearanceLightStatusBars =
                !darkTheme

            WindowCompat
                .getInsetsController(
                    window,
                    view
                )
                .isAppearanceLightNavigationBars =
                !darkTheme
        }
    }

    MaterialTheme(
        colorScheme =
            colorScheme,
        typography =
            PickupPassTypography,
        shapes =
            PickupPassShapes,
        content =
            content
    )
}
