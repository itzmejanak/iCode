package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Creates a folder in the active workspace with a storage check. */
@Composable
fun CreateFolderScreen(actions: DemoActions) {
    var name by rememberSaveable { mutableStateOf("auth-v2_") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Create Folder", onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            LabeledField("Destination Path", "~/projects/icode-subsystem/src") {
                Text("Change", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            }
            LabeledTextField(label = "Folder Name", value = name, onValueChange = { name = it })
            StatusLabel("Path is valid and available", IcodeColors.Green)
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = IcodeColors.Blue)
                    Column {
                        Text("Device Available Storage", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine("12.4 GB free of 128 GB (Ext4 partition)", style = IcodeType.MonoSmall)
                    }
                }
            }
        }
        PrimaryButton(
            "Create Folder",
            { actions.navigate(DemoScreen.WorkspaceExplorer) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
        )
    }
}
