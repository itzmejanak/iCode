package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool
import org.junit.Assert.assertEquals
import org.junit.Test

/** §5.4 S4: the six histories, the order and the app's pages survive the process; nothing else is invented. */
class NavigationSavingTest {
    @Test fun `every part survives a process restart`() {
        val at = Navigation.start(Route.LauncherHome).opened("/ws/one")
            .navigate(Route.SourceControl).navigate(Route.DiffView)
            .openPanel(ToolPanelTab.Problems)
            .show(Route.WorkspaceEditor)
        assertEquals(at, NavigationSaving.restored(NavigationSaving.saved(at), Route.Splash))
    }

    @Test fun `Terminal outside the workspace comes back where it was`() {
        val at = Navigation.start(Route.LauncherHome).opened("/ws/one").back().openPanel(ToolPanelTab.Terminal)
        assertEquals(at, NavigationSaving.restored(NavigationSaving.saved(at), Route.Splash))
    }

    @Test fun `a workspace path with the format's own marks in it comes back whole`() {
        val at = Navigation.start(Route.LauncherHome).opened("/ws/a,b;c=d")
        assertEquals("/ws/a,b;c=d", NavigationSaving.restored(NavigationSaving.saved(at), Route.Splash).workspace)
    }

    @Test fun `what cannot be read starts afresh, never somewhere that cannot be`() {
        val fresh = Navigation.start(Route.Splash)
        assertEquals(fresh, NavigationSaving.restored(listOf("older", "x"), Route.Splash))
        assertEquals(fresh, NavigationSaving.restored(emptyList<String>(), Route.Splash))
        // A tool's history that does not start on its own main screen breaks the rules: not taken.
        val saved = NavigationSaving.saved(Navigation.start(Route.LauncherHome).opened("/ws/one"))
        saved[2] = "${WorkspaceTool.Explorer}=${Route.DiffView}"
        assertEquals(fresh, NavigationSaving.restored(saved, Route.Splash))
    }

    @Test fun `a route a later version removed is dropped, and the rest is kept`() {
        val saved = NavigationSaving.saved(Navigation.start(Route.LauncherHome).navigate(Route.SettingsRoot))
        saved[1] = "LauncherHome,GoneInThisVersion,SettingsRoot"
        assertEquals(listOf(Route.LauncherHome, Route.SettingsRoot), NavigationSaving.restored(saved, Route.Splash).app)
    }
}
