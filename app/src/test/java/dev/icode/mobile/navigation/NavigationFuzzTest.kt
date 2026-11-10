package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5.3 N4: whatever is done, in whatever order, the model keeps its rules, Back always reaches
 * Android's own within a bound, never shows one screen twice in a row, and every state survives
 * being saved and read back.
 */
class NavigationFuzzTest {
    private val workspaces = listOf(null, "/ws/one", "/ws/two", "/ws/three")

    private fun Random.step(at: Navigation): Navigation = when (nextInt(9)) {
        0, 1 -> at.navigate(Route.entries.random(this))
        2 -> at.replace(Route.entries.random(this))
        3 -> at.openPanel(ToolPanelTab.entries.random(this))
        4 -> at.switchTo(WorkspaceTool.entries.random(this))
        5 -> at.show(Route.entries.random(this))
        6 -> at.opened(workspaces.random(this), if (nextBoolean()) WorkspaceTool.entries.random(this) else null)
        7 -> at.workspaceIs(workspaces.random(this))
        else -> at.back()
    }

    @Test fun `10 000 random sequences of 60 steps keep every rule, and Back always gets out`() {
        val random = Random(20261110)
        repeat(10_000) { run ->
            var at = Navigation.start(listOf(Route.Splash, Route.LauncherHome, Route.Assistant, Route.ProblemsPanel).random(random))
            repeat(60) { step ->
                at = random.step(at)
                assertTrue("run $run step $step: $at", NavigationSaving.wellFormed(at))
            }
            assertEquals("run $run", at, NavigationSaving.restored(NavigationSaving.saved(at), Route.Splash))
            // Back: within every page, every tool and the app's pages, and never the same screen twice running.
            val bound = at.tools.values.sumOf { it.size } + at.order.size + at.app.size + 1
            var backs = 0
            while (at.canGoBack) {
                val before = at.current
                at = at.back()
                backs++
                assertTrue("run $run: Back went round", backs <= bound)
                assertNotEquals("run $run: $before twice", before, at.current)
                assertTrue("run $run", NavigationSaving.wellFormed(at))
            }
            assertEquals("run $run: at the end Back changes nothing", at, at.back())
        }
    }
}
