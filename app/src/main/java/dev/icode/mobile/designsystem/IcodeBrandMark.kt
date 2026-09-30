package dev.icode.mobile.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import dev.icode.mobile.R

/**
 * The in-app brand mark shares the launcher icon's artwork and background.
 * The art ships per density at the largest size drawn (`IcodeSizes.Tile`, 74 dp),
 * from `design/brand/icon-with-background.png`.
 */
@Composable
fun IcodeBrandMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        modifier = modifier
            .clip(IcodeShapes.AppIcon)
            .background(colorResource(R.color.ic_launcher_background)),
    ) {
        Image(
            painter = painterResource(R.drawable.icode_launcher_art),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The logo as a letterpress behind empty space, as VS Code marks an empty editor: the themed icon's
 * silhouette in the text colour, faint enough to read as texture in every palette. Decorative —
 * nothing to tap, nothing for a screen reader. It may be wider than its parent: the canvas around
 * the logo is empty.
 */
@Composable
fun IcodeWatermark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_monochrome),
        contentDescription = null,
        colorFilter = ColorFilter.tint(IcodeColors.TextPrimary.copy(alpha = WATERMARK_ALPHA)),
        modifier = modifier.requiredSize(IcodeSizes.Watermark),
    )
}

private const val WATERMARK_ALPHA = 0.06f
