package dev.icode.mobile.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
