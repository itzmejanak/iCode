package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

    /**
     * VS Code's remote button, ＞ above ＜ (docs/home-plan.md §1): the home's status bar draws the
     * runtime with it, where VS Code shows the machine it works on.
     */
    val Runtime: ImageVector by lazy {
        ImageVector.Builder("Runtime", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3.5f, 4.5f)
                lineTo(8.5f, 9.5f)
                lineTo(3.5f, 14.5f)
                moveTo(20.5f, 9.5f)
                lineTo(15.5f, 14.5f)
                lineTo(20.5f, 19.5f)
            }
        }.build()
    }
}
