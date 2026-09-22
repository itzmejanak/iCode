package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.ChoiceChip
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DiffBand
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val mergeOptions = listOf("Accept Current", "Accept Incoming", "Accept Both", "Compare")

/** Merge conflict resolution with per-side bands. */
@Composable
fun ConflictResolutionScreen(actions: DemoActions) {
    var choice by rememberSaveable { mutableStateOf(mergeOptions.first()) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("auth.ts", onBack = actions.back, actions = { StatusLabel("Conflict", IcodeColors.Error) })
        Row(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space2),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            mergeOptions.forEach { option ->
                ChoiceChip(
                    text = option,
                    active = choice == option,
                    modifier = Modifier.weight(1f),
                    onClick = { choice = option },
                )
            }
        }
        CodeSurface(
            modifier = Modifier.weight(1f),
            padding = PaddingValues(top = IcodeSpacing.Space3),
        ) {
            MonoLine(
                "export const validateToken = (token: string) => {",
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space3),
                color = IcodeColors.TextMuted,
            )
            DiffBand("<<<<<<< HEAD (Current Change)", DiffKind.ConflictCurrent)
            MonoLine(
                "  const decoded = jwt.verify(token, SECRET);\n  return decoded.sub !== undefined;",
                modifier = Modifier.padding(IcodeSpacing.Space3),
            )
            DiffBand("=======", DiffKind.Separator)
            MonoLine(
                "  const verified = oauth.decode(token);\n  return verified.isValid && verified.scope === 'write';",
                modifier = Modifier.padding(IcodeSpacing.Space3),
            )
            DiffBand(">>>>>>> feature/oauth (Incoming Change)", DiffKind.ConflictIncoming)
            MonoLine("};", modifier = Modifier.padding(horizontal = IcodeSpacing.Space3), color = IcodeColors.TextMuted)
        }
        Text(
            "Conflicted — resolve all conflicts and mark as resolved",
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space3 + IcodeSpacing.Micro),
            style = IcodeType.Caption.copy(color = IcodeColors.Amber),
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            "Mark as Resolved",
            { actions.navigate(DemoScreen.SourceControl) },
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
        )
    }
}
