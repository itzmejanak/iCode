package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val demoOutputLogs = listOf(
    "[14:30:11] [Git] Executing: git status --porcelain" to IcodeColors.TextSecondary,
    "[14:30:12] [Git] success: M src/App.tsx, A src/components/auth-guard.tsx" to IcodeColors.Green,
    "[14:30:15] [Git] Executing: git push origin feature/auth" to IcodeColors.TextSecondary,
    "[14:30:16] [Git] error: rejected - non-fast-forward" to IcodeColors.Error,
    "[14:30:16] [Git] hint: pull before pushing again to merge remote changes" to IcodeColors.Amber,
)

/** Output channel panel for Git and runtime logs. */
@Composable
fun OutputPanelScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "App.tsx",
            onBack = actions.back,
            actions = { Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = IcodeColors.TextPrimary) },
        )
        MonoLine(
            text = "import React, { useState } from 'react'; export const App...",
            modifier = Modifier
                .fillMaxWidth()
                .background(IcodeColors.BackgroundDeep)
                .padding(IcodeSpacing.Space4),
            color = IcodeColors.TextMuted,
        )
        PanelTabStrip(
            tabs = listOf(
                PanelTab("Terminal"),
                PanelTab("Output", tone = IcodeTone.Accent, active = true),
            ),
            style = IcodeType.Body,
            activeStyle = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
            activeBackground = Color.Transparent,
            verticalPadding = IcodeSpacing.Space3 + IcodeSpacing.Micro,
            trailing = {
                Spacer(Modifier.weight(1f))
                KeyChip("Channel: Git ⌄")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(IcodeColors.Surface)
                .padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            demoOutputLogs.forEach { (line, color) -> MonoLine(line, color = color, style = IcodeType.MonoSmall) }
        }
    }
}
