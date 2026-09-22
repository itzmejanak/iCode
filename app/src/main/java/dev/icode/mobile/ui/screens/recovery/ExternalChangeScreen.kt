package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.DialogScaffold
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Conflict dialog shown when a file changed on disk while editing. */
@Composable
fun ExternalChangeScreen(actions: DemoActions) {
    DialogScaffold(title = "token-utils.ts", subtitle = "Modified state", onBack = actions.back) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = IcodeColors.Amber, modifier = Modifier.size(IcodeSizes.IconXl))
                Text("File Changed Externally", style = IcodeType.Title.copy(color = IcodeColors.Amber))
            }
            Text(
                "token-utils.ts was modified outside the editor while you have unsaved changes inside the IDE.",
                style = IcodeType.Body,
            )
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                DetailRow("Disk Version", "14:31:05 (Newer)", valueColor = IcodeColors.Amber, valueStyle = IcodeType.MonoSmall)
                DetailRow("IDE Cache", "14:28:10 (Modified)", valueColor = IcodeColors.TextSecondary, valueStyle = IcodeType.MonoSmall)
            }
            PrimaryButton("Reload From Disk", { actions.navigate(DemoScreen.WorkspaceEditor) }, Modifier.fillMaxWidth())
            SecondaryButton("Compare Versions Side-by-Side", { actions.navigate(DemoScreen.DiffView) }, Modifier.fillMaxWidth())
            SecondaryButton("Overwrites Disk with My Changes", { actions.navigate(DemoScreen.WorkspaceEditor) }, Modifier.fillMaxWidth())
            Text(
                "Ignore & Keep Editing",
                modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space2),
                style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}
