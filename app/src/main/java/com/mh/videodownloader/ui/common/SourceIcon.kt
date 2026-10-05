package com.mh.videodownloader.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mh.videodownloader.R
import com.mh.videodownloader.domain.Source

/**
 * A site's own mark in place of its name. [tint] null means "ink colour": TikTok and X are
 * black marks, which would vanish on the dark theme if drawn in their brand colour.
 */
enum class Brand(@param:DrawableRes val icon: Int, val tint: Color?, val label: String) {
    YOUTUBE(R.drawable.ic_brand_youtube, Color(0xFFFF0033), "YouTube"),
    INSTAGRAM(R.drawable.ic_brand_instagram, Color(0xFFE1306C), "Instagram"),
    TIKTOK(R.drawable.ic_brand_tiktok, null, "TikTok"),
    X(R.drawable.ic_brand_x, null, "X"),
    FACEBOOK(R.drawable.ic_brand_facebook, Color(0xFF1877F2), "Facebook");

    companion object {
        fun of(source: Source): Brand? = when (source) {
            Source.YOUTUBE -> YOUTUBE
            Source.INSTAGRAM -> INSTAGRAM
            Source.TIKTOK -> TIKTOK
            Source.TWITTER -> X
            Source.FACEBOOK -> FACEBOOK
            else -> null
        }

        /** History rows store the label they were saved with; map it back to a mark. */
        fun of(label: String): Brand? = Source.entries
            .firstOrNull { it.brandName == label }
            ?.let { of(it) }
    }
}

/** The mark alone, for inline use next to other text. */
@Composable
fun SourceIcon(brand: Brand?, size: Dp, modifier: Modifier = Modifier) {
    if (brand == null) {
        Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size),
        )
    } else {
        Icon(
            painter = painterResource(brand.icon),
            contentDescription = brand.label,
            tint = brand.tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = modifier.size(size),
        )
    }
}

/** Pill-shaped mark that lines up with the size/duration chips beside it. */
@Composable
fun SourceChip(brand: Brand?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        SourceIcon(brand, size = 17.dp)
    }
}

/** A brand mark on a white disc — legible on the coloured hero whatever the brand colour. */
@Composable
fun BrandDisc(brand: Brand, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(brand.icon),
            contentDescription = brand.label,
            tint = brand.tint ?: Color.Black,
            modifier = Modifier.size(16.dp),
        )
    }
}
