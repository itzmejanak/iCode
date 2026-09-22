package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.ChoiceChip
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.navigation.DemoActions

private val demoSearchResults = listOf(
    Triple(
        "src/App.tsx",
        "3 matches",
        listOf(
            "1   import React, { useState } from 'react';",
            "5   const [active, setActive] = useState(true);",
            "6   const [loading, setLoading] = useState(false);",
        ),
    ),
    Triple(
        "src/hooks/useAuth.ts",
        "2 matches",
        listOf(
            "2   import { useState, useEffect } from 'react';",
            "8   const [user, setUser] = useState(null);",
        ),
    ),
)

/** Workspace content search with match filters and grouped results. */
@Composable
fun WorkspaceSearchScreen(actions: DemoActions) {
    var query by rememberSaveable { mutableStateOf("useState") }
    var matchCase by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Search Workspace", onBack = actions.back)
        IcodeTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            leadingIcon = Icons.Outlined.Search,
            trailingText = "⊗",
        )
        Row(
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            ChoiceChip("Match Case", matchCase) { matchCase = !matchCase }
            ChoiceChip("Whole Word", active = false) {}
            ChoiceChip("Regex", active = false) {}
        }
        Text("5 results in 2 files", modifier = Modifier.padding(IcodeSpacing.Space4), style = IcodeType.Caption)
        Column(
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            demoSearchResults.forEach { (file, count, lines) ->
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⌄  $file",
                            style = IcodeType.Mono.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold),
                        )
                        Text("  ($count)", style = IcodeType.MonoSmall)
                    }
                    lines.forEach { line ->
                        MonoLine(
                            text = line,
                            modifier = Modifier.padding(start = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                            style = IcodeType.MonoSmall,
                        )
                    }
                }
            }
        }
    }
}
