package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** iCode's own icons, drawn as Material's are (a 24-unit square, tinted where used), for what Material has none of. */
object IcodeIcons {
    /** Two corners pulled apart, ┌ above ┘ (owner's picture, 2026-10-22): opens the whole of what it stands beside. */
    val Expand: ImageVector by lazy {
        ImageVector.Builder("Expand", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 5f)
                horizontalLineTo(13f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                close()
                moveTo(19f, 19f)
                horizontalLineTo(11f)
                verticalLineTo(17f)
                horizontalLineTo(17f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                close()
            }
        }.build()
    }
}
