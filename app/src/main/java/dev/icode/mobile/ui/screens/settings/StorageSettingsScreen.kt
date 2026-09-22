package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

private val usageLegend = "● Runtime (1.1GB)    ● Workspaces (410MB)    ● Cache (220MB)"

/** Storage usage, workspace paths and cleanup operations. */
@Composable
fun StorageSettingsScreen(actions: DemoActions) {
    val workspaces = LocalWorkbenchSnapshot.current.workspaces
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Storage Settings", subtitle = "Disk usage and workspace folders", onBack = actions.back)
        LazyColumn(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
        ) {
            item { SectionLabel("Storage Usage") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1.8 GB Used", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text("14.2 GB Free of 16 GB", style = IcodeType.Caption)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IcodeSpacing.Space2)
                            .padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro),
                    ) {
                        Box(Modifier.weight(1.1f).height(IcodeSpacing.Space2).background(IcodeColors.Accent))
                        Box(Modifier.weight(0.42f).height(IcodeSpacing.Space2).background(IcodeColors.Blue))
                        Box(Modifier.weight(0.2f).height(IcodeSpacing.Space2).background(IcodeColors.Amber))
                        Box(Modifier.weight(0.08f).height(IcodeSpacing.Space2).background(IcodeColors.SurfaceTrack))
                    }
                    Text(
                        usageLegend,
                        modifier = Modifier.padding(top = IcodeSpacing.Space3),
                        style = IcodeType.Caption,
                    )
                }
            }
            item { SectionLabel("Workspace Paths", Modifier.padding(top = IcodeSpacing.Space2)) }
            items(workspaces) { workspace ->
                val size = if (workspace.name.startsWith("external")) "1.2 GB" else "244 MB"
                IcodeListRow(
                    card = true,
                    content = {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            MonoLine(workspace.path, style = IcodeType.MonoSmall)
                            MonoLine("$size  · ${workspace.lastOpened}", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = IcodeColors.TextSecondary,
                            modifier = Modifier.size(IcodeSizes.IconMd),
                        )
                    },
                )
            }
            item { SectionLabel("Cleanup Operations", Modifier.padding(top = IcodeSpacing.Space2)) }
            item {
                TwoColumnActions(
                    left = { SecondaryButton("Clear Cache", {}, Modifier.fillMaxWidth()) },
                    right = { DangerButton("Remove Stale", {}, Modifier.fillMaxWidth()) },
                )
            }
        }
    }
}
