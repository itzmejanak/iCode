package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LogicalWorkspaceTest {
    private val user = "/storage/emulated/0/Projects/RevPlayer"
    private val private = "/data/user/0/app/files/workspaces/abc"

    private fun workspace(vararg mounts: WorkspaceMount) = LogicalWorkspace(user, private, mounts.toList())

    @Test fun `every path is the user's unless a mount holds it`() {
        val workspace = workspace(WorkspaceMount("node_modules", StorageClass.LINUX))
        assertEquals(PhysicalLocation(StorageClass.USER, "$user/src/App.tsx"), workspace.resolve("src/App.tsx"))
        assertEquals(PhysicalLocation(StorageClass.USER, user), workspace.resolve(""))
        assertEquals(PhysicalLocation(StorageClass.LINUX, "$private/linux/node_modules"), workspace.resolve("node_modules"))
        assertEquals(
            PhysicalLocation(StorageClass.LINUX, "$private/linux/node_modules/semver/package.json"),
            workspace.resolve("node_modules/semver/package.json"),
        )
    }

    @Test fun `a sibling that only shares a prefix stays the user's`() {
        val workspace = workspace(WorkspaceMount("build", StorageClass.LINUX))
        assertEquals(StorageClass.USER, workspace.resolve("build.gradle").storage)
        assertEquals(StorageClass.USER, workspace.resolve("buildSrc/a.kt").storage)
    }

    @Test fun `the deepest mount wins, and is bound after the one around it`() {
        val workspace = workspace(
            WorkspaceMount(".next/cache", StorageClass.CACHE),
            WorkspaceMount(".next", StorageClass.LINUX),
        )
        assertEquals(PhysicalLocation(StorageClass.CACHE, "$private/cache/.next/cache/x"), workspace.resolve(".next/cache/x"))
        assertEquals(PhysicalLocation(StorageClass.LINUX, "$private/linux/.next/server"), workspace.resolve(".next/server"))
        assertEquals(
            listOf(
                ProjectionBind(user, "/workspace/RevPlayer"),
                ProjectionBind("$private/linux/.next", "/workspace/RevPlayer/.next"),
                ProjectionBind("$private/cache/.next/cache", "/workspace/RevPlayer/.next/cache"),
            ),
            workspace.binds("/workspace/RevPlayer"),
        )
    }

    @Test fun `a folder shows the mounts directly in it, and a move of it would leave those at or under it`() {
        val workspace = workspace(
            WorkspaceMount("node_modules", StorageClass.LINUX),
            WorkspaceMount("packages/app/node_modules", StorageClass.LINUX),
            WorkspaceMount(".next/cache", StorageClass.CACHE),
        )
        assertEquals(listOf("node_modules"), workspace.mountsIn("").map { it.path })
        assertEquals(listOf("packages/app/node_modules"), workspace.mountsIn("packages/app/").map { it.path })
        assertEquals(emptyList<WorkspaceMount>(), workspace.mountsIn("packages"))
        assertEquals(listOf("packages/app/node_modules"), workspace.mountsAtOrUnder("packages").map { it.path })
        assertEquals(listOf("node_modules"), workspace.mountsAtOrUnder("node_modules").map { it.path })
        assertEquals(emptyList<WorkspaceMount>(), workspace.mountsAtOrUnder("node_modules/semver"))
        assertEquals(emptyList<WorkspaceMount>(), workspace.mountsAtOrUnder("pack"))
    }

    @Test fun `a mount whose folder is gone is not bound, nor any mount inside it`() {
        val workspace = workspace(
            WorkspaceMount("node_modules", StorageClass.LINUX),
            WorkspaceMount("packages/app/node_modules", StorageClass.LINUX),
            WorkspaceMount("packages/app/node_modules/.cache", StorageClass.CACHE),
        )
        // packages/app was deleted; the private folder behind its mount is still there.
        val folders = setOf(user, "$private/linux/packages/app/node_modules")
        assertEquals(
            listOf(
                ProjectionBind(user, "/workspace/RevPlayer"),
                ProjectionBind("$private/linux/node_modules", "/workspace/RevPlayer/node_modules"),
            ),
            workspace.binds("/workspace/RevPlayer") { it in folders },
        )
    }

    @Test fun `no mount is the user's class, Git's folder, or anything but a plain relative path`() {
        listOf(
            WorkspaceMount("src", StorageClass.USER),
            WorkspaceMount("", StorageClass.LINUX),
            WorkspaceMount("/node_modules", StorageClass.LINUX),
            WorkspaceMount("node_modules/", StorageClass.LINUX),
            WorkspaceMount("a/../b", StorageClass.LINUX),
            WorkspaceMount("a//b", StorageClass.LINUX),
            WorkspaceMount(".git", StorageClass.CACHE),
            WorkspaceMount(".git/hooks", StorageClass.CACHE),
        ).forEach { mount ->
            assertThrows(mount.toString(), IllegalArgumentException::class.java) { workspace(mount) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            workspace(WorkspaceMount("node_modules", StorageClass.LINUX), WorkspaceMount("node_modules", StorageClass.CACHE))
        }
        assertNull(WorkspaceMounts.problem(WorkspaceMount(".venv", StorageClass.LINUX)))
    }
}
