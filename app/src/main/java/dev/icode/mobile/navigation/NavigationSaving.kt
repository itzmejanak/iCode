package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool

/**
 * [Navigation] as strings for Android's saved state (D10): the app's pages, each tool's history, the
 * order, the tab and the workspace survive the process being stopped. Read back, only what is still
 * a route or a tool, and only histories that keep the model's rules, are taken; anything else starts
 * iCode afresh rather than somewhere that cannot be.
 */
object NavigationSaving {
    fun saved(navigation: Navigation): ArrayList<String> = with(navigation) {
        arrayListOf(
            VERSION,
            app.joinToString(LIST) { it.name },
            tools.entries.joinToString(TOOLS) { (tool, history) -> tool.name + TOOL + history.joinToString(LIST) { it.name } },
            order.joinToString(LIST) { it.name },
            if (inWorkspace) "1" else "0",
            panelTab.name,
            workspace.orEmpty(),
            generation.toString(),
        )
    }

    fun restored(saved: List<*>, fallback: Route): Navigation {
        val fields = saved.map { it as? String }
        if (fields.size != FIELDS || fields[0] != VERSION || fields.any { it == null }) return Navigation.start(fallback)
        val app = routes(fields[1]!!)
        val tools = fields[2]!!.split(TOOLS).filter { it.isNotEmpty() }.mapNotNull { pair ->
            val tool = WorkspaceTool.entries.firstOrNull { it.name == pair.substringBefore(TOOL) } ?: return@mapNotNull null
            tool to routes(pair.substringAfter(TOOL))
        }.toMap()
        val restored = Navigation(
            app = app,
            tools = tools,
            order = fields[3]!!.split(LIST).mapNotNull { name -> WorkspaceTool.entries.firstOrNull { it.name == name } },
            inWorkspace = fields[4] == "1",
            panelTab = ToolPanelTab.entries.firstOrNull { it.name == fields[5] } ?: ToolPanelTab.Terminal,
            workspace = fields[6]!!.ifEmpty { null },
            generation = fields[7]!!.toIntOrNull() ?: 0,
        )
        return restored.takeIf(::wellFormed) ?: Navigation.start(fallback)
    }

    /**
     * The model's rules (§5.3), which every step keeps: no history holds a route twice; each tool's
     * starts on one of its own main screens and holds only pages after it; the app's pages are pages,
     * or Terminal and the Assistant outside the workspace while the app's pages are on screen
     * ([opensOutside], docs/home-plan.md §2);
     * the order names each opened tool at most once; and what is on screen exists.
     */
    fun wellFormed(navigation: Navigation): Boolean = with(navigation) {
        val histories = tools.all { (tool, history) ->
            history.isNotEmpty() && history.distinct() == history &&
                history.first().tool == tool && history.first() == Navigation.faceOf(history.first()) &&
                history.drop(1).all { it.tool == null }
        }
        histories && app.distinct() == app && app.all { it.tool == null || (it.opensOutside && !inWorkspace) } &&
            order.distinct() == order && order.all { it in tools } &&
            if (inWorkspace) order.isNotEmpty() else app.isNotEmpty()
    }

    private fun routes(names: String): List<Route> =
        names.split(LIST).filter { it.isNotEmpty() }.mapNotNull { name -> Route.entries.firstOrNull { it.name == name } }

    private const val VERSION = "dock-1"
    private const val FIELDS = 8
    private const val LIST = ","
    private const val TOOLS = ";"
    private const val TOOL = "="
}
