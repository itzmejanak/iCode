package dev.icode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.FolderCrumb

/**
 * Where a folder browser is — Import's on sdcard, the workspaces' file explorer — the root first:
 * the way up, each folder above a tap back to it, the one here in the accent. Deep down it scrolls
 * sideways, starting at its end.
 */
@Composable
fun FolderBreadcrumb(crumbs: List<FolderCrumb>, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .padding(horizontal = IcodeSpacing.Space4, vertical = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val here = index == crumbs.lastIndex
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IcodeColors.TextMuted,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
            }
            Text(
                crumb.name,
                style = IcodeType.Mono.copy(color = if (here) IcodeColors.Accent else IcodeColors.TextSecondary),
                modifier = Modifier
                    .clip(IcodeShapes.Small)
                    .then(if (here) Modifier else Modifier.clickable(role = Role.Button) { onOpen(crumb.path) })
                    .padding(horizontal = IcodeSpacing.Space1, vertical = IcodeSpacing.Space2),
            )
        }
    }
}
