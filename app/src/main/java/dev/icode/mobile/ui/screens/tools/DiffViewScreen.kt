package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.DiffRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.navigation.DemoActions

private val demoDiffLines = listOf(
    Triple("15 15", "const config = {", DiffKind.Context),
    Triple("16  -", "secure: false,", DiffKind.Removed),
    Triple("16  +", "secure: true,", DiffKind.Added),
    Triple("17  +", "algorithm: 'RS256',", DiffKind.Added),
    Triple("17 18", "subscribe();", DiffKind.Context),
)

/** Inline diff for the modified document. */
@Composable
fun DiffViewScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("App.tsx", onBack = actions.back, actions = { StatusLabel("MODIFIED", IcodeColors.Amber) })
        MonoLine(
            text = "@@ -15,8 +15,12 @@",
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space3 + IcodeSpacing.Micro),
            color = IcodeColors.TextSecondary,
        )
        MonoLine(
            text = "... 24 lines hidden ...",
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space2 + IcodeSpacing.Micro),
            color = IcodeColors.TextMuted,
            style = IcodeType.MonoSmall,
        )
        demoDiffLines.forEach { (numbers, content, kind) ->
            DiffRow(numbers = numbers, content = content, kind = kind)
        }
        Spacer(Modifier.weight(1f))
        TwoColumnActions(
            left = { SecondaryButton("⌃ Prev Change", {}, Modifier.fillMaxWidth()) },
            right = { SecondaryButton("Next Change ⌄", {}, Modifier.fillMaxWidth()) },
            modifier = Modifier.padding(IcodeSpacing.Space4),
        )
    }
}
