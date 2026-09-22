package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PanelTab
import dev.icode.mobile.designsystem.PanelTabStrip
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val demoTerminalLines = listOf(
    "~/my-project $ npm run dev" to IcodeColors.TextPrimary,
    "> my-project@0.1.0 dev" to IcodeColors.TextSecondary,
    "> next dev" to IcodeColors.TextSecondary,
    "- ready started server on 0.0.0.0:3000, url: http://localhost:3000" to IcodeColors.Blue,
    "- event compiled client and server successfully in 312ms (18 modules)" to IcodeColors.Green,
    "Attention: Next.js now collects completely anonymous telemetry." to IcodeColors.TextPrimary,
    "~/my-project $ █" to IcodeColors.TextPrimary,
)

private val demoExtraKeys = listOf("ESC", "TAB", "CTRL", "ALT", "◀", "▲", "▼", "▶")

/** Expanded terminal with session tabs, transcript and extra key row. */
@Composable
fun TerminalScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        IcodeTopBar("my-project", onBack = actions.back)
        PanelTabStrip(
            tabs = listOf(
                PanelTab("● bash", tone = IcodeTone.Success, active = true),
                PanelTab("● build", tone = IcodeTone.Error),
            ),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            trailing = {
                Text(
                    "+",
                    modifier = Modifier.clickable { actions.navigate(DemoScreen.OutputPanel) }.padding(IcodeSpacing.Space4),
                    style = IcodeType.Title,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.padding(IcodeSpacing.Space2 + IcodeSpacing.Micro),
                )
            },
        )
        CodeSurface(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            demoTerminalLines.forEach { (text, color) -> MonoLine(text, color = color) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space2)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            demoExtraKeys.forEach { key -> KeyChip(key) }
        }
    }
}
