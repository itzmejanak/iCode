package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMoreButton
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.rememberShownWhileBusy
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.domain.isBusy

/**
 * One workspace in the home's Recent list, as VS Code's Welcome page lists folders (docs/home-plan.md
 * §1): its name as a link, its `/workspace/` path under it, its branch and when it was last opened,
 * and its actions, from ⋮ or a long press — [onActions] asks the screen to show the workspace's sheet
 * ([WorkspaceSheet]). A tap opens it.
 */
@Composable
fun WorkspaceRow(item: WorkspaceSummary, onActions: () -> Unit, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    // Opening from the tap (docs/loading-plan.md §5): the row says so in place of its ⋮.
    val opening = rememberShownWhileBusy(LocalICodeSnapshot.current.isBusy(BusyAction.OpenWorkspace, item.location))
    IcodeListRow(
        modifier = modifier,
        onClick = { gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) },
        onLongClick = onActions,
        content = {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = IcodeType.Body.copy(color = IcodeColors.Accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                MonoLine(item.path, style = IcodeType.MonoSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                item.branch?.let { Text(it, style = IcodeType.MonoMicro, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(RelativeTime.since(item.lastOpenedAt), style = IcodeType.Caption)
            }
        },
        trailing = {
            if (opening) {
                Box(Modifier.size(IcodeSizes.TouchTarget), contentAlignment = Alignment.Center) {
                    IcodeSpinner(Modifier.semantics { contentDescription = "Opening ${item.name}" })
                }
            } else {
                IcodeMoreButton("More options for ${item.name}", onClick = onActions)
            }
        },
    )
}
