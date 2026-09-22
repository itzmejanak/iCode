package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.demo.DemoContent
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Commit surface with branch state, last commit and fetch/pull actions. */
@Composable
fun CommitPushScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Source Control",
            onBack = actions.back,
            actions = { Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = IcodeColors.TextPrimary) },
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = IcodeColors.Green)
                    Column(Modifier.weight(1f).padding(start = IcodeSpacing.Space2 + IcodeSpacing.Micro)) {
                        Text(DemoContent.activeBranch, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        MonoLine("1 commit ahead, 0 behind", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                    }
                    PrimaryButton("Push (1)", { actions.navigate(DemoScreen.PullFetchProgress) })
                }
            }
            NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                StatusLabel("Working tree clean. No unstaged changes.", IcodeColors.Green)
            }
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
                    SectionLabel("Last Commit")
                    MonoLine("commit a9d2b1f8c7e923", color = IcodeColors.Accent)
                    Text("feat: implement secure auth flow on Android", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                    DetailRow("Author", "Dev Android <dev@icode.com>", valueStyle = IcodeType.MonoSmall)
                    DetailRow("Time", "5 mins ago", valueStyle = IcodeType.MonoSmall, valueColor = IcodeColors.TextSecondary)
                }
            }
            TwoColumnActions(
                left = { SecondaryButton("FETCH", { actions.navigate(DemoScreen.PullFetchProgress) }, Modifier.fillMaxWidth()) },
                right = { SecondaryButton("PULL", { actions.navigate(DemoScreen.PullFetchProgress) }, Modifier.fillMaxWidth()) },
            )
        }
    }
}
