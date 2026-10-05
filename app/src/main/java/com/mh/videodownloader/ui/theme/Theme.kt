package com.mh.videodownloader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mh.videodownloader.data.prefs.AppFont
import com.mh.videodownloader.data.prefs.ThemeMode

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** True when the app is currently painting its dark palette, whatever decided that. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun VideoDownloaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    font: AppFont = AppFont.CAIRO,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(font) { appTypography(font) },
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The signature gradient: used for the hero header and the primary download button. */
@Composable
fun brandGradient(): Brush {
    val dark = LocalIsDarkTheme.current
    return if (dark) {
        // Keep enough depth for white label text to stay readable on the button.
        Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF4C6BF5), Color(0xFF0093B5)))
    } else {
        Brush.linearGradient(listOf(BrandViolet, BrandIndigo, BrandCyan))
    }
}

@Composable
fun heroGradient(): Brush {
    val dark = LocalIsDarkTheme.current
    return if (dark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF2A1F63),
                Color(0xFF161A2E),
                MaterialTheme.colorScheme.background,
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF6D4AFF),
                Color(0xFF5B3BE8),
                MaterialTheme.colorScheme.background,
            )
        )
    }
}
