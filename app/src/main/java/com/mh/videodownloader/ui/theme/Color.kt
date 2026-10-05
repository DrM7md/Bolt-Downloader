package com.mh.videodownloader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A hand-tuned violet/cyan identity rather than a generated palette: the app has one job, and the
 * gradient it uses for the hero and the primary action is the thing you remember it by.
 */
internal val BrandViolet = Color(0xFF6D4AFF)
internal val BrandIndigo = Color(0xFF5B3BE8)
internal val BrandCyan = Color(0xFF00B4D8)
internal val BrandPink = Color(0xFFE5476E)

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B3BE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1B0066),
    inversePrimary = Color(0xFFC7BFFF),

    secondary = Color(0xFF00849E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8ECFB),
    onSecondaryContainer = Color(0xFF002F3A),

    tertiary = Color(0xFFC0325A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF40001A),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceTint = Color(0xFF5B3BE8),
    inverseSurface = Color(0xFF303038),
    inverseOnSurface = Color(0xFFF3EFF7),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FC),
    surfaceContainer = Color(0xFFEFECF7),
    surfaceContainerHigh = Color(0xFFE9E6F2),
    surfaceContainerHighest = Color(0xFFE3E0EC),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDCD8E4),

    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC9C5D0),
    scrim = Color(0xFF000000),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2E1A87),
    primaryContainer = Color(0xFF46309F),
    onPrimaryContainer = Color(0xFFE5DEFF),
    inversePrimary = Color(0xFF5B3BE8),

    secondary = Color(0xFF62D4F0),
    onSecondary = Color(0xFF003641),
    secondaryContainer = Color(0xFF004E5D),
    onSecondaryContainer = Color(0xFFB8ECFB),

    tertiary = Color(0xFFFFB1C2),
    onTertiary = Color(0xFF66002F),
    tertiaryContainer = Color(0xFF8F1145),
    onTertiaryContainer = Color(0xFFFFD9E1),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E1018),
    onBackground = Color(0xFFE5E1EA),
    surface = Color(0xFF0E1018),
    onSurface = Color(0xFFE5E1EA),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    surfaceTint = Color(0xFFC7BFFF),
    inverseSurface = Color(0xFFE5E1EA),
    inverseOnSurface = Color(0xFF303038),

    surfaceContainerLowest = Color(0xFF090A10),
    surfaceContainerLow = Color(0xFF161822),
    surfaceContainer = Color(0xFF1A1C27),
    surfaceContainerHigh = Color(0xFF242632),
    surfaceContainerHighest = Color(0xFF2F313E),
    surfaceBright = Color(0xFF34363F),
    surfaceDim = Color(0xFF0E1018),

    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    scrim = Color(0xFF000000),
)

