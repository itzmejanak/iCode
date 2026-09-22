package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.demo.DemoContent
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private class QuickOpenEntry(val name: String, val path: String, val tone: IcodeTone)

private val quickOpenFiles = listOf(
    QuickOpenEntry(DemoContent.appTsx, "src/components/App.tsx", IcodeTone.Accent),
    QuickOpenEntry("useAuth.ts", "src/hooks/useAuth.ts", IcodeTone.Info),
    QuickOpenEntry(DemoContent.packageJson, "root/package.json", IcodeTone.Warning),
)

/** Quick file picker with a state-driven highlighted result. */
@Composable
fun QuickOpenScreen(actions: DemoActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space3)) {
        IcodeTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Go to File...",
            leadingIcon = Icons.Outlined.Search,
        )
        IcodeCard(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
            padding = PaddingValues(IcodeSpacing.Space1),
        ) {
            quickOpenFiles.forEachIndexed { index, entry ->
                IcodeListRow(
                    title = entry.name,
                    subtitle = entry.path,
                    selected = index == selectedIndex,
                    titleColor = if (index == selectedIndex) IcodeColors.Accent else IcodeColors.TextPrimary,
                    onClick = {
                        selectedIndex = index
                        actions.navigate(DemoScreen.WorkspaceEditor)
                    },
                    leading = {
                        Icon(
                            if (entry.name.endsWith("json")) Icons.Outlined.Description else Icons.Outlined.Code,
                            contentDescription = null,
                            tint = entry.tone.tint(),
                            modifier = Modifier.size(IcodeSizes.IconXl),
                        )
                    },
                    trailing = { Text("◔", color = IcodeColors.TextSecondary) },
                )
            }
        }
    }
}
