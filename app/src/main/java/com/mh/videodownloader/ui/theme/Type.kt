package com.mh.videodownloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mh.videodownloader.R
import com.mh.videodownloader.data.prefs.AppFont

/**
 * Every bundled family draws Arabic and Latin in the same voice — the app is Arabic first, so a
 * Latin-only font would fall back to the system face for most of the UI and look like two apps.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val WEIGHTS = listOf(400, 500, 600, 700)

private val CairoFamily = FontFamily(WEIGHTS.map { variable(R.font.cairo, it) })
private val NotoKufiFamily = FontFamily(WEIGHTS.map { variable(R.font.noto_kufi_arabic, it) })
private val ReadexFamily = FontFamily(WEIGHTS.map { variable(R.font.readex_pro, it) })

private val TajawalFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

private val AlmaraiFamily = FontFamily(
    Font(R.font.almarai_regular, FontWeight.Normal),
    Font(R.font.almarai_bold, FontWeight.Bold),
)

fun fontFamilyOf(font: AppFont): FontFamily = when (font) {
    AppFont.CAIRO -> CairoFamily
    AppFont.TAJAWAL -> TajawalFamily
    AppFont.READEX -> ReadexFamily
    AppFont.NOTO_KUFI -> NotoKufiFamily
    AppFont.ALMARAI -> AlmaraiFamily
    AppFont.SYSTEM -> FontFamily.Default
}

private val base = Typography()

fun appTypography(font: AppFont): Typography {
    val family = fontFamilyOf(font)
    return Typography(
        displayLarge = base.displayLarge.with(family, FontWeight.SemiBold),
        displayMedium = base.displayMedium.with(family, FontWeight.SemiBold),
        displaySmall = base.displaySmall.with(family, FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.with(family, FontWeight.Bold),
        headlineMedium = base.headlineMedium.with(family, FontWeight.Bold),
        headlineSmall = base.headlineSmall.with(family, FontWeight.SemiBold),
        titleLarge = base.titleLarge.with(family, FontWeight.Bold, lineHeight = 32.sp),
        titleMedium = base.titleMedium.with(family, FontWeight.SemiBold),
        titleSmall = base.titleSmall.with(family, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.with(family, FontWeight.Normal, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.with(family, FontWeight.Normal, lineHeight = 22.sp),
        bodySmall = base.bodySmall.with(family, FontWeight.Normal, lineHeight = 18.sp),
        labelLarge = base.labelLarge.with(family, FontWeight.SemiBold),
        labelMedium = base.labelMedium.with(family, FontWeight.Medium),
        labelSmall = base.labelSmall.with(family, FontWeight.Medium),
    )
}

private fun TextStyle.with(
    family: FontFamily,
    weight: FontWeight,
    lineHeight: TextUnit = this.lineHeight,
) = copy(
    fontFamily = family,
    fontWeight = weight,
    lineHeight = lineHeight,
)
