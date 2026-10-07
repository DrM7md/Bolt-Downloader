package com.mh.videodownloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val base = Typography()

/** The system face throughout, with the app's own weights and line heights on top. */
val AppTypography = Typography(
    displayLarge = base.displayLarge.with(FontWeight.SemiBold),
    displayMedium = base.displayMedium.with(FontWeight.SemiBold),
    displaySmall = base.displaySmall.with(FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.with(FontWeight.Bold),
    headlineMedium = base.headlineMedium.with(FontWeight.Bold),
    headlineSmall = base.headlineSmall.with(FontWeight.SemiBold),
    titleLarge = base.titleLarge.with(FontWeight.Bold, lineHeight = 32.sp),
    titleMedium = base.titleMedium.with(FontWeight.SemiBold),
    titleSmall = base.titleSmall.with(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.with(FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.with(FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = base.bodySmall.with(FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = base.labelLarge.with(FontWeight.SemiBold),
    labelMedium = base.labelMedium.with(FontWeight.Medium),
    labelSmall = base.labelSmall.with(FontWeight.Medium),
)

/**
 * For text the user did not write in the app's language — video titles, channel and folder
 * names. Compose lays text out in the SCREEN's direction unless told otherwise, which turned
 * "2014 — Year in Search" into "Year in Search — 2014" in the Arabic UI. UI labels keep the
 * layout direction on purpose: "MP3 للمحدد" must stay RTL even though it starts with Latin.
 */
fun TextStyle.forContent(): TextStyle = copy(textDirection = TextDirection.Content)

private fun TextStyle.with(weight: FontWeight, lineHeight: TextUnit = this.lineHeight) =
    copy(fontWeight = weight, lineHeight = lineHeight)
