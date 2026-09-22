package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private class PaletteCommand(val group: String?, val command: String, val shortcut: String)

private val demoCommands = listOf(
    PaletteCommand("Git commands", "Git: Push Branch", "Ctrl+Shift+P"),
    PaletteCommand(null, "Git: Pull (from origin)", "Ctrl+Shift+L"),
    PaletteCommand(null, "Git: Commit Staged", "Ctrl+Alt+C"),
    PaletteCommand("File commands", "File: Save Current", "Ctrl+S"),
)

/** Action palette with grouped commands and keyboard shortcuts. */
@Composable
fun CommandPaletteScreen(actions: DemoActions) {
    var query by rememberSaveable { mutableStateOf("Git: push") }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep).padding(top = IcodeSpacing.Space3)) {
        IcodeTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space3),
            leadingIcon = Icons.Outlined.Search,
            borderColor = IcodeColors.Accent,
        )
        demoCommands.forEachIndexed { index, item ->
            if (item.group != null) {
                SectionLabel(
                    item.group,
                    Modifier.padding(start = IcodeSpacing.Space3, top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
                )
            }
            IcodeListRow(
                title = item.command,
                selected = index == selectedIndex,
                titleColor = if (index == selectedIndex) IcodeColors.Accent else IcodeColors.TextPrimary,
                onClick = {
                    selectedIndex = index
                    actions.navigate(if (item.command.contains("Push")) DemoScreen.CommitPush else DemoScreen.WorkspaceEditor)
                },
                leading = {
                    Icon(
                        if (item.command.startsWith("Git")) Icons.Outlined.AccountTree else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = if (index == selectedIndex) IcodeColors.Accent else IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconMd),
                    )
                },
                trailing = { MonoLine(item.shortcut, style = IcodeType.MonoSmall) },
            )
        }
    }
}
