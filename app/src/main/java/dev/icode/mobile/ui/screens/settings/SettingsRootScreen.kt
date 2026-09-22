package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Settings root with the application settings categories. */
@Composable
fun SettingsRootScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Settings",
            onBack = actions.back,
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search settings", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Application Settings", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            Text(
                "Switch to Workspace  ↻",
                style = IcodeType.Caption,
                modifier = Modifier.clickable { actions.navigate(DemoScreen.WorkspaceSettings) },
            )
        }
        LazyColumn(
            modifier = Modifier.padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            items(settingsCategories) { category ->
                SettingRow(
                    title = category.title,
                    subtitle = category.subtitle,
                    leadingIcon = category.icon,
                    onClick = { actions.navigate(category.destination) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                }
            }
        }
    }
}
