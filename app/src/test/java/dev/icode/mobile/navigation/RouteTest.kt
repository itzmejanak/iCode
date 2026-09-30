package dev.icode.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    /**
     * Figma frames that are a state of another frame rather than a place.
     *
     * A frame earns a route only when it has its own back-stack identity: the
     * user navigates to it deliberately and Back means something. These do not
     * — they are reached because an operation reached that state, and the
     * action that resolves them belongs on the surface doing the work.
     *
     * Listing them keeps the catalog honest: every Figma frame is still
     * accounted for, and dropping one silently still fails this test.
     */
    private val framesRenderedAsStates = mapOf(
        "3:55" to "Runtime error — the Failed state of Runtime installing (3:27)",
        "2003:77" to "Create folder — the Workspaces page's New Folder dialog (workspace-destinations-plan.md §2.5)",
    )

    /** Pages the design does not have, each added at the owner's request. */
    private val routesWithoutFrame = setOf(
        Route.Notifications, // 2026-09-25: notices off the launcher
        Route.ImportWorkspace, // 2026-09-30: Import names the chosen folder, built from New Folder's parts (workspace-storage-plan.md §4.4)
        Route.RuntimeComponents, // 2026-09-30: choose the runtime's tools, as Android Studio's setup (runtime-components-plan.md §3.5)
        Route.Workspaces, // 2026-10-01: every workspace managed in one place (workspaces-page-plan.md)
        Route.ChooseDestination, // 2026-10-01: the file explorer choosing where a project goes (workspace-destinations-plan.md)
    )

    private val framed = Route.entries.mapNotNull { it.figmaNode }

    @Test
    fun `a route without a Figma frame is one the owner asked for`() {
        assertEquals(routesWithoutFrame, Route.entries.filter { it.figmaNode == null }.toSet())
    }

    @Test
    fun `every Figma frame is either a route or a documented state`() {
        assertEquals(TOTAL_FIGMA_FRAMES, framed.size + framesRenderedAsStates.size)
    }

    @Test
    fun `no frame is both a route and a state`() {
        framesRenderedAsStates.keys.forEach { node ->
            assertFalse("$node is listed as a state but still has a route", node in framed)
        }
    }

    @Test
    fun `no frame is routed twice`() {
        assertEquals(framed.size, framed.distinct().size)
    }

    @Test
    fun `every node id is a Figma node reference`() {
        val nodes = framed + framesRenderedAsStates.keys
        assertTrue(nodes.all { it.matches(Regex("""\d+:\d+""")) })
    }

    private companion object {
        /** The design hands over 40 frames; that total never changes silently. */
        const val TOTAL_FIGMA_FRAMES = 40
    }
}
