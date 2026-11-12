package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool

/**
 * Where iCode is (docs/dock-navigation-plan.md §2–§3): the app's own pages, and the open workspace's
 * six tools, each with a history of its own, in the order they were used. Every navigation decision
 * is made here; screens ask for routes ([NavigationActions]) and the composition root holds this
 * ([dev.icode.mobile.ui.NavigationState]). Immutable: each step gives the next.
 *
 * - A route that is a tool's main screen ([Route.tool]) switches to that tool, as it was left.
 * - Any other route is a page, pushed on what is on screen: the tool's history, or the app's pages.
 *   A page already there is gone back to, never stacked twice: screens take no arguments, so the
 *   same route is the same screen.
 * - Back closes a tool's page, then goes to the tool used before, then to the app's pages (the
 *   Launcher), then leaves iCode ([back]). A tool Back has left drops out of the order, so Back never
 *   comes back to it and cannot go round.
 * - Terminal asked for from the app's pages opens there, as a page outside the workspace: its tabs
 *   start where a login starts, `$HOME` (docs/home-plan.md §2).
 */
data class Navigation(
    /** The app's pages outside the workspace, the first at the bottom: onboarding, the Launcher, Settings. */
    val app: List<Route> = emptyList(),
    /** Each tool's history, its main screen first; a tool not opened yet has none. */
    val tools: Map<WorkspaceTool, List<Route>> = emptyMap(),
    /** The tools Back returns through, the one on screen last; each at most once. */
    val order: List<WorkspaceTool> = emptyList(),
    /** The workspace's tools are on screen, not the app's pages. */
    val inWorkspace: Boolean = false,
    /** The Terminal tool's open tab. */
    val panelTab: ToolPanelTab = ToolPanelTab.Terminal,
    /** The workspace the tools' histories belong to. */
    val workspace: String? = null,
    /** One more each time the tools start anew: what their screens keep is their own ([keys]). */
    val generation: Int = 0,
) {
    /** The tool on screen; null while the app's pages are. */
    val tool: WorkspaceTool? get() = if (inWorkspace) order.lastOrNull() else null

    /** What is on screen: the top of the tool's history, or of the app's pages. */
    val current: Route get() = shown.last().route

    /**
     * A tool's main screen is on screen: where the dock shows (D6). A sheet route over it (the Branch
     * selector) is drawn over the main screen, not in its place, so the main screen is still on screen.
     */
    val onMainScreen: Boolean get() = tool?.let { historyOf(it).dropLastWhile(Route::sheet).size == 1 } ?: false

    /** Terminal's tab on screen: the tool's own, and outside the workspace its only one, Terminal. */
    val shownTab: ToolPanelTab get() = if (inWorkspace) panelTab else ToolPanelTab.Terminal

    /** The tool Back and the quick switch go to (D4, D9). */
    val previousTool: WorkspaceTool? get() = if (inWorkspace) order.getOrNull(order.lastIndex - 1) else null

    /** Back has somewhere to go inside iCode; otherwise Android's own Back leaves it. */
    val canGoBack: Boolean get() = inWorkspace || app.size > 1

    /** The history on screen, each entry with the key its screen's kept state goes by. */
    val shown: List<Entry> get() = tool?.let { entriesOf(it) } ?: appEntries

    /** Every entry any history holds: what their screens keep lives exactly as long (D5). */
    val keys: List<String> get() = appEntries.map { it.key } + tools.keys.flatMap { tool -> entriesOf(tool).map { it.key } }

    /** One screen in a history, and the key of what it keeps. */
    data class Entry(val key: String, val route: Route)

    fun navigate(route: Route): Navigation = when {
        route == Route.TerminalExpanded && !inWorkspace -> push(route)
        route.tool != null -> switchTo(route.tool, face = route)
        route.leavesWorkspace && inWorkspace -> copy(inWorkspace = false).push(route)
        else -> push(route)
    }

    /** This page closes, and [route] is gone to: a main screen is never replaced, only left. */
    fun replace(route: Route): Navigation = closingPage().navigate(route)

    /** Terminal on [tab]; its own tab asked for from the app's pages opens there, outside the workspace. */
    fun openPanel(tab: ToolPanelTab): Navigation =
        if (tab == ToolPanelTab.Terminal && !inWorkspace) push(Route.TerminalExpanded) else copy(panelTab = tab).switchTo(WorkspaceTool.Terminal)

    /** A tab in no workspace shown for the user, from wherever iCode is: Terminal outside the workspace. */
    fun showTerminalOutside(): Navigation = copy(inWorkspace = false).openPanel(ToolPanelTab.Terminal)

    /**
     * [tool], as it was left — or, asked for by one of its main screens ([face]) other than the one it
     * shows, started on that face: Quick Open asked for while Search finds text, for one.
     */
    fun switchTo(tool: WorkspaceTool, face: Route? = null): Navigation {
        val wanted = face?.let(::faceOf)
        val history = tools[tool]
        val next = when {
            history == null -> listOf(wanted ?: tool.home)
            wanted != null && history.first() != wanted -> listOf(wanted)
            else -> history
        }
        return inside().copy(
            tools = tools + (tool to next),
            order = order - tool + tool,
            panelTab = face?.panelTabOrNull() ?: panelTab,
        )
    }

    /**
     * What was opened for the user (D3): a file, a terminal, a repository just made, a notification's
     * place. A tool shows its main screen, whatever page was left over it; a page is gone to.
     */
    fun show(route: Route): Navigation {
        val tool = route.tool ?: return navigate(route)
        val switched = switchTo(tool, face = route)
        return switched.copy(tools = switched.tools + (tool to switched.historyOf(tool).take(1)))
    }

    /** Back (D4); this, unchanged, when only Android's own Back is left. */
    fun back(): Navigation {
        val tool = tool ?: return if (app.size > 1) copy(app = app.dropLast(1)) else this
        val history = historyOf(tool)
        return when {
            history.size > 1 -> copy(tools = tools + (tool to history.dropLast(1)))
            order.size > 1 -> copy(order = order.dropLast(1))
            // The last tool keeps its place in the order: opened again, the workspace is as it was left.
            else -> copy(inWorkspace = false, app = app.ifEmpty { listOf(Route.LauncherHome) })
        }
    }

    /**
     * A workspace was opened at [location] (null: the one open), on [tool] when something in it was
     * asked for (a terminal), else as D3 says: a new one on its Explorer, the open one again where it
     * was left from the Launcher, or on its Explorer from inside it. The page that made it gives way.
     */
    fun opened(location: String?, tool: WorkspaceTool? = null): Navigation {
        val made = if (!inWorkspace && app.lastOrNull()?.makesWorkspace == true) copy(app = app.dropLast(1)) else this
        val fresh = location != null && location != workspace
        val start = if (fresh) made.startedAt(location) else made
        return when {
            tool != null -> start.show(tool.home)
            fresh -> start.inside()
            !inWorkspace -> if (start.order.isEmpty()) start.switchTo(WorkspaceTool.Explorer) else start.inside()
            else -> start.show(WorkspaceTool.Explorer.home)
        }
    }

    /**
     * The open workspace is [location], however it came to be: opened for a file in it, renamed. The
     * tools of another start anew, on their Explorer, with the tool on screen kept on screen.
     */
    fun workspaceIs(location: String?): Navigation {
        if (location == null || location == workspace) return this
        val onScreen = tool
        val started = startedAt(location)
        return if (onScreen == null || onScreen == WorkspaceTool.Explorer) started else started.show(onScreen.home)
    }

    /**
     * The workspace's tools on screen. Terminal outside the workspace closes as they open: Back from
     * them never shows Terminal again, now with no tool to switch to.
     */
    private fun inside() = copy(inWorkspace = true, app = app - Route.TerminalExpanded)

    private fun startedAt(location: String) = copy(
        tools = mapOf(WorkspaceTool.Explorer to listOf(Route.WorkspaceExplorer)),
        order = listOf(WorkspaceTool.Explorer),
        workspace = location,
        generation = generation + 1,
        panelTab = ToolPanelTab.Terminal,
    )

    private fun push(route: Route): Navigation {
        val tool = tool ?: return copy(app = app.pushed(route))
        return copy(tools = tools + (tool to historyOf(tool).pushed(route)))
    }

    private fun closingPage(): Navigation {
        val tool = tool ?: return copy(app = app.dropLast(1))
        val history = historyOf(tool)
        return if (history.size > 1) copy(tools = tools + (tool to history.dropLast(1))) else this
    }

    private fun historyOf(tool: WorkspaceTool): List<Route> = tools.getValue(tool)

    private fun entriesOf(tool: WorkspaceTool): List<Entry> = historyOf(tool).mapIndexed { place, route -> Entry("$generation/$tool/$place:$route", route) }

    private val appEntries: List<Entry> get() = app.mapIndexed { place, route -> Entry("app/$place:$route", route) }

    companion object {
        /** iCode as it starts on [route]: a tool's main screen opens that tool, any other route the app's pages. */
        fun start(route: Route): Navigation =
            route.tool?.let { Navigation().switchTo(it, face = route) } ?: Navigation(app = listOf(route))

        /** The route a tool shows for [face]: Terminal's tab frames are its one screen, on that tab. */
        internal fun faceOf(face: Route): Route = if (face.panelTabOrNull() != null) Route.TerminalExpanded else face
    }
}

/** [route] on top, or this history back to it where it already is. */
private fun List<Route>.pushed(route: Route): List<Route> = indexOf(route).let { at -> if (at >= 0) take(at + 1) else this + route }
