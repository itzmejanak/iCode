package dev.icode.mobile.data.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspacePathsTest {
    private val root = "/data/data/dev.icode.mobile/files/workspaces"
    private val paths = WorkspacePaths(File(root))

    @Test fun `a location and its guest path are one path under two roots`() {
        assertEquals("/workspace", paths.guestOf(root))
        assertEquals("/workspace/RevPlayer", paths.guestOf("$root/RevPlayer"))
        assertEquals("/workspace/RevPlayer/src/App.tsx", paths.guestOf("$root/RevPlayer/src/App.tsx/"))
        assertEquals(root, paths.locationOf("/workspace"))
        assertEquals("$root/RevPlayer/src", paths.locationOf("/workspace/RevPlayer/src"))
    }

    @Test fun `nothing outside the workspaces maps, a sibling sharing a prefix included`() {
        assertNull(paths.guestOf("/storage/emulated/0/Projects/RevPlayer"))
        assertNull(paths.guestOf("${root}2/app"))
        assertNull(paths.locationOf("/root/project"))
        assertNull(paths.locationOf("/workspaces/app"))
    }

    @Test fun `any name survives the round trip, a colon and a space included`() {
        val location = "$root/my app: v2/notes.md"
        assertEquals("/workspace/my app: v2/notes.md", paths.guestOf(location))
        assertEquals(location, paths.locationOf(paths.guestOf(location)!!))
    }

    @Test fun `the workspace holding a location is the folder right under the root`() {
        assertEquals("$root/RevPlayer", paths.workspaceOf("$root/RevPlayer/src/App.tsx"))
        assertEquals("$root/RevPlayer", paths.workspaceOf("$root/RevPlayer"))
        assertNull(paths.workspaceOf(root))
        assertNull(paths.workspaceOf("/storage/emulated/0/RevPlayer/src"))
    }
}
