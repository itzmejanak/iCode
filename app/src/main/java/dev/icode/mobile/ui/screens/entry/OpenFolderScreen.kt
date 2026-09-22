package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val demoFolders = listOf(
    "src" to "12 directories, 4 files",
    "public" to "2 directories, 15 files",
    "tests" to "1 directory, 6 files",
)

/** Directory browser used when opening an existing workspace folder. */
@Composable
fun OpenFolderScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Open Folder", onBack = actions.back)
        MonoLine(
            text = "root / projects / icode-subsystem",
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space3),
            color = IcodeColors.Accent,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            item {
                Text(
                    "↰  ..",
                    style = IcodeType.Mono.copy(color = IcodeColors.Accent),
                    modifier = Modifier.padding(IcodeSpacing.Space2),
                )
            }
            items(demoFolders) { (name, meta) ->
                IcodeListRow(
                    card = true,
                    title = name,
                    subtitle = meta,
                    titleStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    leading = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = IcodeColors.Accent) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IcodeColors.TextSecondary,
                        )
                    },
                )
            }
        }
        PrimaryButton(
            "Open This Folder",
            { actions.navigate(DemoScreen.EditorEmpty) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
        )
    }
}
