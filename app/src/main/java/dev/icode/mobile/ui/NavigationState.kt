package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.navigation.panelTabOrNull

/**
 * The back stack and the tools panel's open tab, for [IcodeApp].
 *
 * Keeps navigation concerns out of the composition root and survives
 * configuration changes through [Saver].
 */
@Stable
class NavigationState private constructor(
    initialHistory: List<String>,
    initialPanelTab: ToolPanelTab,
    private val captureMode: Boolean,
) {
    var historyNames by mutableStateOf(initialHistory)
        private set

    var panelTab by mutableStateOf(initialPanelTab)
        private set

    val current: Route get() = Route.valueOf(historyNames.last())

    /** Each entry of the history by its place and route: the key of what its screen keeps. */
    val entryKeys: List<String> get() = historyNames.mapIndexed { place, name -> "$place:$name" }

    val canGoBack: Boolean get() = historyNames.size > 1

    val actions: NavigationActions get() = NavigationActions(
        navigate = { destination ->
            val route = panelRoute(destination)
            when {
                route == current -> Unit
                route == Route.TerminalExpanded && current.panelTabOrNull() != null ->
                    historyNames = historyNames.dropLast(1) + route.name
                else -> historyNames = historyNames + route.name
            }
        },
        replace = { destination ->
            val route = panelRoute(destination)
            if (route != current) historyNames = historyNames.dropLast(1) + route.name
        },
        returnTo = { destination ->
            val route = panelRoute(destination)
            when {
                route == current -> Unit
                historyNames.getOrNull(historyNames.lastIndex - 1) == route.name -> historyNames = historyNames.dropLast(1)
                else -> historyNames = historyNames.dropLast(1) + route.name
            }
        },
        openPanel = { tab ->
            panelTab = tab
            when {
                current.panelTabOrNull() == null -> historyNames = historyNames + Route.TerminalExpanded.name
                !captureMode && current != Route.TerminalExpanded ->
                    historyNames = historyNames.dropLast(1) + Route.TerminalExpanded.name
            }
        },
        panelTab = panelTab,
        back = { if (canGoBack) historyNames = historyNames.dropLast(1) },
        canGoBack = canGoBack,
        captureMode = captureMode,
    )

    private fun panelRoute(destination: Route): Route {
        destination.panelTabOrNull()?.let { panelTab = it }
        return if (destination.panelTabOrNull() != null) Route.TerminalExpanded else destination
    }

    companion object {
        fun create(initialScreen: Route, captureMode: Boolean): NavigationState =
            NavigationState(
                initialHistory = listOf(initialScreen.name),
                initialPanelTab = initialScreen.panelTabOrNull() ?: ToolPanelTab.Terminal,
                captureMode = captureMode,
            )

        fun saver(initialScreen: Route, captureMode: Boolean): Saver<NavigationState, Any> =
            listSaver(
                save = { state -> listOf(ArrayList(state.historyNames), state.panelTab.name) },
                restore = { saved ->
                    val restoredHistory = (saved.firstOrNull() as? ArrayList<*>)
                        ?.filterIsInstance<String>().orEmpty().ifEmpty { listOf(initialScreen.name) }
                    val restoredTab = (saved.getOrNull(1) as? String)
                        ?.let { name -> ToolPanelTab.entries.firstOrNull { it.name == name } }
                        ?: restoredHistory.lastOrNull()?.let { name ->
                            Route.entries.firstOrNull { it.name == name }?.panelTabOrNull()
                        }
                        ?: ToolPanelTab.Terminal
                    NavigationState(
                        initialHistory = restoredHistory,
                        initialPanelTab = restoredTab,
                        captureMode = captureMode,
                    )
                },
            )
    }
}

@Composable
fun rememberNavigationState(initialScreen: Route, captureMode: Boolean): NavigationState =
    rememberSaveable(initialScreen, saver = NavigationState.saver(initialScreen, captureMode)) {
        NavigationState.create(initialScreen, captureMode)
    }
