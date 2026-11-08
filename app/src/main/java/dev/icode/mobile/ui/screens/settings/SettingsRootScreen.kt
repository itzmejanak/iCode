package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * The root list in three groups: how iCode looks and edits, what it thinks and codes with, and what
 * it runs on. Searching answers with the pages themselves, across every group.
 */
private val settingsGroups = listOf(
    "Appearance & editing" to listOf(Route.AppearanceSettings, Route.SettingsEditor, Route.TerminalSettings),
    "Assistant & code" to listOf(Route.AssistantSettings, Route.SourceControlSettings, Route.SettingsLanguages),
    "System" to listOf(Route.SettingsRuntime, Route.StorageSettings, Route.About),
)

/** Settings root with the application settings categories. */
@Composable
fun SettingsRootScreen(actions: NavigationActions) {
    val hasWorkspace = LocalICodeSnapshot.current.activeWorkspaceLocation != null
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchVisible) { if (searchVisible) searchFocus.requestFocus() }
    val matches = searchSettingsCategories(searchQuery)
    val groups: List<Pair<String?, List<SettingsCategory>>> = if (searchQuery.isNotBlank()) {
        listOf(null to matches)
    } else {
        settingsGroups.map { (label, routes) ->
            label to routes.mapNotNull { route -> matches.firstOrNull { it.destination == route } }
        }
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Settings",
            onBack = actions.back,
            actions = {
                IconButton(onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search settings", tint = IcodeColors.TextPrimary)
                }
            },
        )
        if (searchVisible) {
            IcodeTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search settings",
                leadingIcon = Icons.Outlined.Search,
                modifier = Modifier.fillMaxWidth().padding(start = IcodeSpacing.Gutter, top = IcodeSpacing.Gutter, end = IcodeSpacing.Gutter),
                inputModifier = Modifier.focusRequester(searchFocus),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            // Under the search field, its results follow it as one group.
            contentPadding = PaddingValues(
                start = IcodeSpacing.Gutter,
                top = if (searchVisible) IcodeSpacing.ItemGap else IcodeSpacing.Gutter,
                end = IcodeSpacing.Gutter,
                bottom = IcodeSpacing.Gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap),
        ) {
            if (matches.isEmpty()) item(key = "none") {
                Text("No matching settings", style = IcodeType.Body, color = IcodeColors.TextSecondary)
            }
            groups.forEach { (label, categories) ->
                label?.let { title ->
                    item(key = title) {
                        SectionLabel(title, Modifier.padding(top = IcodeSpacing.SectionBreak))
                    }
                }
                items(categories, key = { it.destination.toString() }) { category ->
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
        SettingsScopeBar(workspace = false, actions, hasWorkspace)
    }
}

/**
 * Which settings: the application's or the open workspace's — the bottom bar,
 * as the editor's (owner's design). Switching replaces the page, as a tab
 * does, so Back still returns to wherever settings were opened from.
 */
@Composable
internal fun SettingsScopeBar(workspace: Boolean, actions: NavigationActions, hasWorkspace: Boolean = true) {
    IcodeBottomBar(
        items = listOf(
            BottomBarItem("Application", onClick = { actions.replace(Route.SettingsRoot) }.takeIf { workspace }, active = !workspace),
            if (hasWorkspace) {
                BottomBarItem("Workspace", onClick = { actions.replace(Route.WorkspaceSettings) }.takeIf { !workspace }, active = workspace)
            } else {
                BottomBarItem("Open a Workspace", onClick = { actions.navigate(Route.LauncherHome) })
            },
        ),
    )
}
