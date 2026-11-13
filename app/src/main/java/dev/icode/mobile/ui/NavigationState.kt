package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.domain.WorkspaceTool
import dev.icode.mobile.navigation.Navigation
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.NavigationSaving
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab

/**
 * [Navigation] held for Compose by the composition root: what is on screen follows it, and it
 * survives the process being stopped ([NavigationSaving]). It decides nothing itself.
 */
@Stable
class NavigationState internal constructor(initial: Navigation, private val captureMode: Boolean) {
    var model by mutableStateOf(initial)
        private set

    val current: Route get() = model.current
    val panelTab: ToolPanelTab get() = model.shownTab
    val canGoBack: Boolean get() = model.canGoBack

    val actions: NavigationActions get() = NavigationActions(
        navigate = { model = model.navigate(it) },
        show = ::show,
        outside = ::showOutside,
        replace = { model = model.replace(it) },
        openPanel = { model = model.openPanel(it) },
        panelTab = model.shownTab,
        inWorkspace = model.inWorkspace,
        back = ::back,
        canGoBack = model.canGoBack,
        captureMode = captureMode,
    )

    fun back() {
        model = model.back()
    }

    /** The dock's switch (D6, D9): [tool] as it was left. */
    fun switchTo(tool: WorkspaceTool) {
        model = model.switchTo(tool)
    }

    /** What was opened for the user: a file, a repository just made, a notification's place (D3). */
    fun show(route: Route) {
        model = model.show(route)
    }

    /** A workspace opened, on [tool] when something in it was asked for. */
    fun opened(location: String?, tool: WorkspaceTool? = null) {
        model = model.opened(location, tool)
    }

    /** A tab or a chat in no workspace shown for the user: Terminal or the Assistant outside the workspace. */
    fun showOutside(route: Route) {
        model = model.showOutside(route)
    }

    /** The open workspace, however it came to be: another's tools start anew. */
    fun workspaceIs(location: String?) {
        model = model.workspaceIs(location)
    }

    companion object {
        fun create(initialScreen: Route, captureMode: Boolean): NavigationState =
            NavigationState(Navigation.start(initialScreen), captureMode)

        fun saver(initialScreen: Route, captureMode: Boolean): Saver<NavigationState, Any> = Saver(
            save = { NavigationSaving.saved(it.model) },
            restore = { saved -> NavigationState(NavigationSaving.restored(saved as List<*>, initialScreen), captureMode) },
        )
    }
}

@Composable
fun rememberNavigationState(initialScreen: Route, captureMode: Boolean): NavigationState =
    rememberSaveable(initialScreen, saver = NavigationState.saver(initialScreen, captureMode)) {
        NavigationState.create(initialScreen, captureMode)
    }
