package com.mh.videodownloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mh.videodownloader.R

/**
 * Cairo, bundled as a variable font. It is the rare family that draws Arabic and Latin with the
 * same voice, which matters a lot in an app whose primary language is Arabic.
 */
@OptIn(ExperimentalTextApi::class)
private fun cairo(weight: Int) = Font(
    resId = R.font.cairo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val CairoFamily = FontFamily(
    cairo(400),
    cairo(500),
    cairo(600),
    cairo(700),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.withCairo(FontWeight.SemiBold),
    displayMedium = base.displayMedium.withCairo(FontWeight.SemiBold),
    displaySmall = base.displaySmall.withCairo(FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.withCairo(FontWeight.Bold),
    headlineMedium = base.headlineMedium.withCairo(FontWeight.Bold),
    headlineSmall = base.headlineSmall.withCairo(FontWeight.SemiBold),
    titleLarge = base.titleLarge.withCairo(FontWeight.Bold, lineHeight = 32.sp),
    titleMedium = base.titleMedium.withCairo(FontWeight.SemiBold),
    titleSmall = base.titleSmall.withCairo(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.withCairo(FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.withCairo(FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = base.bodySmall.withCairo(FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = base.labelLarge.withCairo(FontWeight.SemiBold),
    labelMedium = base.labelMedium.withCairo(FontWeight.Medium),
    labelSmall = base.labelSmall.withCairo(FontWeight.Medium),
)

private fun TextStyle.withCairo(
    weight: FontWeight,
    lineHeight: androidx.compose.ui.unit.TextUnit = this.lineHeight,
) = copy(
    fontFamily = CairoFamily,
    fontWeight = weight,
    lineHeight = lineHeight,
)
