package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val demoFindResults = listOf(
    "1   import React from 'components';",
    "2   const ActiveComponent = () => {",
    "3     return <div>Active Workspace</div>;",
    "4   };",
)

/** Editor find-and-replace bar with match toggles. */
@Composable
fun EditorFindScreen(actions: DemoActions) {
    var query by rememberSaveable { mutableStateOf("component") }
    var replacement by rememberSaveable { mutableStateOf("element") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("App.tsx", onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IcodeTextField(query, { query = it }, Modifier.weight(1f), trailingText = "3 of 7")
                KeyChip("⌃")
                KeyChip("⌄")
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = actions.back),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IcodeTextField(replacement, { replacement = it }, Modifier.weight(1f))
                KeyChip("Replace")
                KeyChip("All")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                KeyChip("Aa")
                KeyChip("Ab*")
                KeyChip(".*")
            }
        }
        CodeSurface(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
        ) {
            demoFindResults.forEach { line -> MonoLine(line) }
        }
    }
}
