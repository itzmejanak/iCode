package dev.icode.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    /**
     * Design frames that are a state of another frame rather than a place.
     *
     * A frame earns a route only when it has its own back-stack identity: the
     * user navigates to it deliberately and Back means something. These do not
     * — they are reached because an operation reached that state, and the
     * action that resolves them belongs on the surface doing the work.
     *
     * Listing them keeps the catalog honest: every design frame is still
     * accounted for, and dropping one silently still fails this test.
     */
    private val framesRenderedAsStates = mapOf(
        "3:55" to "Runtime error — the Failed state of Runtime installing (3:27)",
        "2003:77" to "Create folder — the Workspaces page's New Folder dialog (workspace-destinations-plan.md §2.5)",
        "2003:731" to "Delete confirmation — the discard dialog over what asked for it (workspace-destinations-plan.md G14)",
    )

    /** Pages the design does not have, each added at the owner's request. */
    private val routesWithoutFrame = setOf(
        Route.Notifications, // 2026-09-25: notices off the launcher
        Route.RuntimeTools, // 2026-09-30: choose the runtime's tools, as Android Studio's setup (runtime-tools-plan.md §3.5)
        Route.ChooseDestination, // 2026-10-01: the file explorer choosing where a project goes (workspace-destinations-plan.md)
        Route.RepositorySelector, // 2026-10-01: the repository chosen as the branch is (workspace-destinations-plan.md D7)
        Route.GitHistory, // 2026-10-22: HEAD's history from Last Commit's corner (git-history-plan.md)
        Route.Assistant, // 2026-10-01: iCode's own assistant, on Koog (assistant-koog-plan.md §3)
        Route.AssistantSettings, // 2026-10-01: the assistant's providers (assistant-koog-plan.md §13 P3)
        Route.AssistantProvider, // 2026-10-01: one provider — its key, address and models (assistant-koog-plan.md §13 P3)
        Route.SourceControlSettings, // 2026-10-08: Git's credentials and the commit messages' model (commit-message-generator-plan.md M8)
        Route.DeviceInfoConsent, // 2026-10-29: onboarding's optional device-info question (device-telemetry-plan.md §4)
        Route.AssistantProviders, // 2026-11-03: the providers on their own page, from Settings › Assistant
        Route.VoiceSettings, // 2026-11-03: speech in and out (phone-agent-plan.md §5.8)
        Route.PhoneControl, // 2026-11-03: phone control, off by default (phone-agent-plan.md §5.9)
        Route.ShizukuSetup, // 2026-11-03: Shizuku's one-page state and setup (phone-agent-plan.md §5.11)
        Route.McpServers, // 2026-11-05: MCP servers for the assistant's chats (mcp-plan.md §4.2)
        Route.McpServer, // 2026-11-05: one MCP server — its fields, tools, log (mcp-plan.md §4.2)
        Route.ManageStorage, // 2026-10-27: what storage holds, kind by kind, and the explorer's views of it (explorer-gaps-plan.md §3.10)
    )

    private val framed = Route.entries.mapNotNull { it.figmaNode }

    @Test
    fun `a route without a design frame is one the owner asked for`() {
        assertEquals(routesWithoutFrame, Route.entries.filter { it.figmaNode == null }.toSet())
    }

    @Test
    fun `every design frame is either a route or a documented state`() {
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
    fun `every node id is a design node reference`() {
        val nodes = framed + framesRenderedAsStates.keys
        assertTrue(nodes.all { it.matches(Regex("""\d+:\d+""")) })
    }

    private companion object {
        /** The design hands over 40 frames; that total never changes silently. */
        const val TOTAL_FIGMA_FRAMES = 40
    }
}
