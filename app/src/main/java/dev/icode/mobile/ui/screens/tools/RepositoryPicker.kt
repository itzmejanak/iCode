package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.countOf

/**
 * Which repository Source Control shows, when the open workspace holds more than one
 * (docs/workspace-destinations-plan.md §2.6): each named by its folder in the workspace, the one
 * Git works in ticked. Everything below — changes, commit, branch, pull and push — is that one's.
 */
@Composable
internal fun RepositoryPicker(repositories: List<String>, chosen: String?, workspace: String?, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun label(location: String) = if (location == workspace) location.substringAfterLast('/') else location.removePrefix("$workspace/")
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = IcodeSpacing.Space4, vertical = IcodeSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            Column(Modifier.weight(1f)) {
                Text(chosen?.let(::label) ?: "Choose a repository", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                Text("${countOf(repositories.size, "repository", "repositories")} in this workspace", style = IcodeType.Caption)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose a repository", tint = IcodeColors.TextSecondary)
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = repositories.map { location ->
                IcodeMenuAction(label(location), if (location == chosen) Icons.Outlined.Check else Icons.Outlined.AccountTree, { onChoose(location) })
            },
        )
    }
    Divider()
}
