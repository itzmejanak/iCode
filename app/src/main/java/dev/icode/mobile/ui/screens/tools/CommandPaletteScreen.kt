package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.PaletteCommand
import dev.icode.mobile.domain.PaletteCommandId
import dev.icode.mobile.domain.PaletteCommands
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Searchable action palette; commands use existing guarded workbench intents. */
@Composable
fun CommandPaletteScreen(actions: NavigationActions) {
    val snapshot = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val commands = PaletteCommands.search(snapshot, query)
    val focusRequester = remember { FocusRequester() }

    val run: (PaletteCommand) -> Unit = { command ->
        runPaletteCommand(command, snapshot, gateway::dispatch, actions.replace, actions.back)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep).padding(top = IcodeSpacing.Space3)) {
        IcodeTextField(
            value = query,
            onValueChange = {
                query = it
                selectedIndex = 0
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space3),
            inputModifier = Modifier.focusRequester(focusRequester).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(commands.lastIndex.coerceAtLeast(0))
                        true
                    }
                    Key.DirectionUp -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter -> {
                        commands.getOrNull(selectedIndex.coerceAtMost(commands.lastIndex))?.let(run)
                        true
                    }
                    else -> false
                }
            },
            leadingIcon = Icons.Outlined.Search,
            placeholder = "Search commands",
            borderColor = IcodeColors.Accent,
        )
        if (commands.isEmpty()) {
            MonoLine("No matching commands", modifier = Modifier.padding(IcodeSpacing.Space3), color = IcodeColors.TextMuted)
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(commands, key = { _, command -> command.id }) { index, command ->
                    if (index == 0 || commands[index - 1].group != command.group) {
                        SectionLabel(
                            command.group,
                            Modifier.padding(start = IcodeSpacing.Space3, top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
                        )
                    }
                    IcodeListRow(
                        title = command.title,
                        subtitle = command.unavailableReason,
                        selected = index == selectedIndex,
                        titleColor = when {
                            !command.available -> IcodeColors.TextMuted
                            index == selectedIndex -> IcodeColors.Accent
                            else -> IcodeColors.TextPrimary
                        },
                        onClick = if (command.available) ({ run(command) }) else null,
                        leading = {
                            Icon(
                                if (command.group == "Git commands") Icons.Outlined.AccountTree else Icons.Outlined.Description,
                                contentDescription = null,
                                tint = if (command.available) IcodeColors.Accent else IcodeColors.TextMuted,
                                modifier = Modifier.size(IcodeSizes.IconMd),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Keeps touch and hardware-key activation on the same tested route/intent path. */
internal fun runPaletteCommand(
    command: PaletteCommand,
    snapshot: WorkbenchSnapshot,
    dispatch: (WorkbenchIntent) -> Unit,
    replace: (Route) -> Unit,
    back: () -> Unit,
) {
    if (!command.available) return
    when (command.id) {
        PaletteCommandId.PushBranch -> {
            dispatch(WorkbenchIntent.PushRemote)
            replace(Route.PullFetchProgress)
        }
        PaletteCommandId.PullBranch -> {
            dispatch(WorkbenchIntent.PullRemote)
            replace(Route.PullFetchProgress)
        }
        // A commit requires a message; Source Control owns that form and validation.
        PaletteCommandId.CommitStaged -> replace(Route.SourceControl)
        PaletteCommandId.SaveCurrent -> snapshot.activeDocument?.let {
            dispatch(WorkbenchIntent.SaveDocument(it.location))
            back()
        }
        PaletteCommandId.QuickOpen -> replace(Route.QuickOpen)
        PaletteCommandId.SearchWorkspace -> replace(Route.WorkspaceSearch)
        PaletteCommandId.OpenTerminal -> replace(Route.TerminalExpanded)
        PaletteCommandId.OpenSourceControl -> replace(Route.SourceControl)
        PaletteCommandId.OpenAssistant -> replace(Route.Assistant)
    }
}
