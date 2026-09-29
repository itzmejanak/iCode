package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.domain.FileFamily
import dev.icode.mobile.domain.FileType

/** The icon a file's type draws with, in the Explorer and in search results alike. */
internal fun fileTypeIcon(type: FileType): ImageVector = when (type.family) {
    FileFamily.Code -> Icons.Outlined.Code
    FileFamily.Data, FileFamily.Document -> Icons.Outlined.Description
}

/** That icon's colour. */
@Composable
internal fun fileTypeTint(type: FileType): Color = when (type.family) {
    FileFamily.Code -> IcodeColors.Accent
    FileFamily.Data -> IcodeColors.Amber
    FileFamily.Document -> IcodeColors.TextSecondary
}
