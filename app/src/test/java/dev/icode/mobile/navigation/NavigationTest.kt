package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/design-and-navigation.md: the owner's journeys, Back, and no duplicates or loops. */
class NavigationTest {
    /** The Launcher, then a workspace opened on its Explorer, as a user arrives. */
    private val opened = Navigation.start(Route.LauncherHome).opened("/ws/one")

    /** Back, step by step, until only Android's own Back is left: each screen shown on the way. */
    private fun Navigation.backs(): List<Route> {
        val seen = mutableListOf<Route>()
        var at = this
        while (at.canGoBack) {
            at = at.back()
            seen += at.current
        }
        return seen
    }

    @Test fun `J1 Editor, the dock to the Assistant, Back is the same file's Editor`() {
        val editing = opened.show(Route.WorkspaceEditor)
        val assistant = editing.switchTo(WorkspaceTool.Assistant)
        assertEquals(Route.Assistant, assistant.current)
        val back = assistant.back()
        assertEquals(Route.WorkspaceEditor, back.current)
        // The same entry, so the same screen state: its key never changed.
        assertEquals(editing.shown.last().key, back.shown.last().key)
    }

    @Test fun `J2 Editor and Terminal take turns however often, and the history never grows`() {
        var at = opened.show(Route.WorkspaceEditor)
        val editorKey = at.shown.last().key
        repeat(3) {
            at = at.openPanel(ToolPanelTab.Terminal)
            assertEquals(Route.TerminalExpanded, at.current)
            at = at.navigate(Route.WorkspaceEditor)
            assertEquals(editorKey, at.shown.last().key)
        }
        at = at.openPanel(ToolPanelTab.Terminal)
        assertEquals(listOf(WorkspaceTool.Explorer, WorkspaceTool.Editor, WorkspaceTool.Terminal), at.order)
        assertEquals(listOf(Route.WorkspaceEditor, Route.WorkspaceExplorer, Route.LauncherHome), at.backs())
    }

    @Test fun `J3 a file opened from the Assistant, and Back is the Assistant as it was`() {
        val assistant = opened.navigate(Route.Assistant)
        val key = assistant.shown.last().key
        val file = assistant.show(Route.WorkspaceEditor)
        assertEquals(Route.WorkspaceEditor, file.current)
        val back = file.back()
        assertEquals(Route.Assistant, back.current)
        assertEquals(key, back.shown.last().key)
    }

    @Test fun `Back closes this tool's pages, then goes to the tools used before, then the Launcher`() {
        val at = opened.show(Route.WorkspaceEditor)
            .switchTo(WorkspaceTool.Assistant)
            .switchTo(WorkspaceTool.Terminal)
        assertEquals(listOf(Route.Assistant, Route.WorkspaceEditor, Route.WorkspaceExplorer, Route.LauncherHome), at.backs())
        // Source Control's diff is its own: the dock away and back, Back closes the diff first.
        val diff = opened.navigate(Route.SourceControl).navigate(Route.DiffView).switchTo(WorkspaceTool.Assistant)
        assertEquals(listOf(Route.DiffView, Route.SourceControl, Route.WorkspaceExplorer, Route.LauncherHome), diff.backs())
    }

    @Test fun `a tool Back has left is out of Back's way, but the dock still finds it as it was`() {
        val diff = opened.navigate(Route.SourceControl).navigate(Route.DiffView)
        val left = diff.back().back()
        assertEquals(Route.WorkspaceExplorer, left.current)
        assertFalse(WorkspaceTool.SourceControl in left.order)
        assertEquals(Route.SourceControl, left.switchTo(WorkspaceTool.SourceControl).current)
        val kept = diff.switchTo(WorkspaceTool.Explorer).switchTo(WorkspaceTool.SourceControl)
        assertEquals(Route.DiffView, kept.current)
    }

    @Test fun `today's three loops are gone`() {
        // The Assistant's Workspace switched to the Explorer instead of stacking a second one.
        val workspace = opened.navigate(Route.Assistant).navigate(Route.WorkspaceExplorer)
        assertEquals(listOf(Route.Assistant, Route.LauncherHome), workspace.backs())
        // The Launcher from inside the workspace is the one Launcher, never a second.
        val launcher = opened.show(Route.WorkspaceEditor).navigate(Route.LauncherHome)
        assertEquals(listOf(Route.LauncherHome), launcher.app)
        assertFalse(launcher.canGoBack)
        // Settings' Open a Workspace goes back to the Launcher below it.
        val settings = Navigation.start(Route.LauncherHome).navigate(Route.SettingsRoot).navigate(Route.LauncherHome)
        assertEquals(listOf(Route.LauncherHome), settings.app)
    }

    @Test fun `a page already open is gone back to, never stacked twice`() {
        val at = opened.navigate(Route.SourceControl).navigate(Route.CommitPush).navigate(Route.GitAuth).navigate(Route.CommitPush)
        assertEquals(Route.CommitPush, at.current)
        assertEquals(listOf(Route.SourceControl, Route.CommitPush), at.shown.map { it.route })
    }

    @Test fun `replace closes this page and goes on, and never replaces a main screen`() {
        val palette = opened.show(Route.WorkspaceEditor).navigate(Route.CommandPalette)
        val source = palette.replace(Route.SourceControl)
        assertEquals(Route.SourceControl, source.current)
        assertEquals(listOf(Route.WorkspaceEditor), source.tools.getValue(WorkspaceTool.Editor))
        // On a main screen, there is no page to close: the page opens over it.
        val settings = opened.replace(Route.WorkspaceSettings)
        assertEquals(listOf(Route.WorkspaceExplorer, Route.WorkspaceSettings), settings.shown.map { it.route })
    }

    @Test fun `Terminal's tabs are one screen, and the design's tab frames open it on that tab`() {
        val problems = opened.openPanel(ToolPanelTab.Problems)
        assertEquals(Route.TerminalExpanded, problems.current)
        assertEquals(ToolPanelTab.Problems, problems.panelTab)
        val output = problems.navigate(Route.OutputPanel)
        assertEquals(Route.TerminalExpanded, output.current)
        assertEquals(ToolPanelTab.Output, output.panelTab)
        assertEquals(problems.shown.last().key, output.shown.last().key)
        assertEquals(ToolPanelTab.Problems, Navigation.start(Route.ProblemsPanel).panelTab)
    }

    @Test fun `a file opened for the user shows the Editor's main screen, closing a page left over it`() {
        val find = opened.show(Route.WorkspaceEditor).navigate(Route.EditorFind).switchTo(WorkspaceTool.Assistant)
        assertEquals(Route.EditorFind, find.switchTo(WorkspaceTool.Editor).current)
        assertEquals(Route.WorkspaceEditor, find.show(Route.WorkspaceEditor).current)
    }

    @Test fun `Search started on the face asked for, and the dock shows it as left`() {
        val quick = opened.navigate(Route.QuickOpen)
        assertEquals(Route.QuickOpen, quick.current)
        assertEquals(Route.WorkspaceSearch, quick.navigate(Route.WorkspaceSearch).current)
        assertEquals(Route.QuickOpen, quick.switchTo(WorkspaceTool.Explorer).switchTo(WorkspaceTool.Search).current)
    }

    @Test fun `a workspace opened again from the Launcher is as it was left, and another starts anew`() {
        val left = opened.show(Route.WorkspaceEditor).navigate(Route.LauncherHome)
        val again = left.opened("/ws/one")
        assertEquals(Route.WorkspaceEditor, again.current)
        assertEquals(left.generation, again.generation)
        val other = left.opened("/ws/two")
        assertEquals(Route.WorkspaceExplorer, other.current)
        assertEquals(listOf(WorkspaceTool.Explorer), other.order)
        assertEquals(left.generation + 1, other.generation)
        // Every key of the old tools is gone, so nothing they kept is shown in the new workspace.
        assertTrue(left.keys.filter { !it.startsWith("app/") }.none { it in other.keys })
    }

    @Test fun `opened for a file in another workspace, the Editor stays on screen over that workspace's Explorer`() {
        val file = opened.navigate(Route.SourceControl).show(Route.WorkspaceEditor).workspaceIs("/ws/two")
        assertEquals(Route.WorkspaceEditor, file.current)
        assertEquals(listOf(WorkspaceTool.Explorer, WorkspaceTool.Editor), file.order)
        assertSame(file, file.workspaceIs("/ws/two"))
        assertSame(file, file.workspaceIs(null))
    }

    @Test fun `the page that made the workspace gives way to it`() {
        val cloning = Navigation.start(Route.LauncherHome).navigate(Route.CloneRepository).navigate(Route.CloneProgress)
        val made = cloning.opened("/ws/clone")
        assertEquals(Route.WorkspaceExplorer, made.current)
        // Back from its Explorer is the form it was cloned from, as it was before the tools had histories.
        assertEquals(listOf(Route.CloneRepository, Route.LauncherHome), made.backs())
    }

    @Test fun `a terminal opened with its workspace shows Terminal, and Back is that workspace's Explorer`() {
        val terminal = Navigation.start(Route.LauncherHome).opened("/ws/one", WorkspaceTool.Terminal)
        assertEquals(Route.TerminalExpanded, terminal.current)
        assertEquals(ToolPanelTab.Terminal, terminal.panelTab)
        assertEquals(listOf(Route.WorkspaceExplorer, Route.LauncherHome), terminal.backs())
    }

    @Test fun `Terminal asked for from the Launcher opens there, outside the workspace, and Back is the Launcher`() {
        listOf(Navigation.start(Route.LauncherHome), opened.back()).forEach { home ->
            val terminal = home.openPanel(ToolPanelTab.Terminal)
            assertEquals(Route.TerminalExpanded, terminal.current)
            assertFalse(terminal.inWorkspace)
            assertFalse(terminal.onMainScreen)
            assertEquals(ToolPanelTab.Terminal, terminal.shownTab)
            assertEquals(home, terminal.back())
            assertEquals(terminal, home.navigate(Route.TerminalExpanded))
        }
    }

    @Test fun `inside the workspace Terminal is its tool, and its other tabs always are`() {
        assertTrue(opened.openPanel(ToolPanelTab.Terminal).inWorkspace)
        val problems = Navigation.start(Route.LauncherHome).openPanel(ToolPanelTab.Problems)
        assertTrue(problems.inWorkspace)
        assertEquals(ToolPanelTab.Problems, problems.shownTab)
    }

    @Test fun `outside the workspace Terminal shows its own tab, whatever the tool's was`() {
        val home = opened.openPanel(ToolPanelTab.Output).back().back()
        assertEquals(ToolPanelTab.Output, home.panelTab)
        assertEquals(ToolPanelTab.Terminal, home.openPanel(ToolPanelTab.Terminal).shownTab)
    }

    @Test fun `a tab in no workspace is shown outside it, from inside it too`() {
        val shown = opened.showOutside(Route.TerminalExpanded)
        assertEquals(Route.TerminalExpanded, shown.current)
        assertFalse(shown.inWorkspace)
        assertEquals(listOf(Route.LauncherHome), shown.backs())
    }

    @Test fun `entering the workspace closes Terminal outside it, so Back never shows Terminal twice`() {
        val inside = Navigation.start(Route.LauncherHome).openPanel(ToolPanelTab.Terminal).openPanel(ToolPanelTab.Problems)
        assertEquals(listOf(Route.LauncherHome), inside.app)
        assertEquals(listOf(Route.LauncherHome), inside.backs())
        assertEquals(listOf(Route.LauncherHome), Navigation.start(Route.LauncherHome).openPanel(ToolPanelTab.Terminal).opened("/ws/one").app)
    }

    @Test fun `the Assistant asked for from the Launcher opens there, outside the workspace, and shown inside closes it`() {
        val home = Navigation.start(Route.LauncherHome)
        val assistant = home.navigate(Route.Assistant)
        assertEquals(Route.Assistant, assistant.current)
        assertFalse(assistant.inWorkspace)
        assertFalse(assistant.onMainScreen)
        assertEquals(home, assistant.back())
        assertTrue(opened.navigate(Route.Assistant).inWorkspace)
        val inside = assistant.show(Route.Assistant)
        assertTrue(inside.inWorkspace)
        assertEquals(listOf(Route.LauncherHome), inside.app)
        val outside = opened.showOutside(Route.Assistant)
        assertFalse(outside.inWorkspace)
        assertEquals(listOf(Route.LauncherHome), outside.backs())
    }

    @Test fun `the dock shows on a tool's main screen only`() {
        assertTrue(opened.onMainScreen)
        assertFalse(opened.navigate(Route.WorkspaceSettings).onMainScreen)
        assertFalse(Navigation.start(Route.LauncherHome).onMainScreen)
        assertFalse(opened.navigate(Route.Notifications).onMainScreen)
    }

    @Test fun `a sheet route over a tool's main screen keeps the dock, one over a page does not`() {
        val sourceControl = opened.navigate(Route.SourceControl)
        assertTrue(sourceControl.navigate(Route.BranchSelector).onMainScreen)
        assertTrue(sourceControl.navigate(Route.RepositorySelector).onMainScreen)
        assertTrue(sourceControl.navigate(Route.GitHistory).onMainScreen)
        assertTrue(sourceControl.navigate(Route.BranchSelector).back().onMainScreen)
        assertFalse(sourceControl.navigate(Route.DiffView).navigate(Route.BranchSelector).onMainScreen)
    }

    @Test fun `the quick switch goes to the tool used before`() {
        val at = opened.show(Route.WorkspaceEditor).switchTo(WorkspaceTool.Terminal)
        assertEquals(WorkspaceTool.Editor, at.previousTool)
        assertEquals(WorkspaceTool.Terminal, at.switchTo(WorkspaceTool.Editor).previousTool)
        assertEquals(null, Navigation.start(Route.LauncherHome).previousTool)
    }

    @Test fun `started on a tool, as a capture is, Back is the Launcher`() {
        val at = Navigation.start(Route.Assistant)
        assertEquals(Route.Assistant, at.current)
        assertEquals(listOf(Route.LauncherHome), at.backs())
    }

    @Test fun `each page's kept state is its own, gone once closed, and opened again it starts afresh`() {
        val commit = opened.navigate(Route.SourceControl).navigate(Route.CommitPush)
        val key = commit.shown.last().key
        assertTrue(key in commit.keys)
        val closed = commit.back()
        assertFalse(key in closed.keys)
        assertNotEquals(commit.navigate(Route.BranchSelector).shown.last().key, key)
    }
}
