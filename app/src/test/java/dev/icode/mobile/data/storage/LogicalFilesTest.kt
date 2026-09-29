package dev.icode.mobile.data.storage

import dev.icode.mobile.domain.LogicalWorkspace
import dev.icode.mobile.domain.StorageClass
import dev.icode.mobile.domain.WorkspaceMount
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogicalFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var user: File
    private lateinit var kept: File
    private lateinit var files: LogicalFiles

    @Before fun setUp() {
        user = temporaryFolder.newFolder("RevPlayer")
        File(user, "src").mkdirs()
        File(user, "src/App.tsx").writeText("app")
        File(user, "package.json").writeText("{}")
        // What the runtime leaves in the user's folder where a mount is placed.
        File(user, "node_modules").mkdirs()
        val private = temporaryFolder.newFolder("private")
        kept = File(private, "linux/node_modules")
        File(kept, "semver").mkdirs()
        File(kept, "semver/index.js").writeText("semver")
        File(kept, ".bin").mkdirs()
        Files.createSymbolicLink(File(kept, ".bin/semver").toPath(), Paths.get("../semver/index.js"))
        files = LogicalFiles(LogicalWorkspace(user.path, private.path, listOf(WorkspaceMount("node_modules", StorageClass.LINUX))))
    }

    @Test fun `a folder lists its own entries and each mount in it, the mount from private storage`() {
        val top = files.list(user.path)
        assertEquals(listOf("node_modules", "package.json", "src"), top.map { it.name })
        val mount = top.first()
        assertEquals("${user.path}/node_modules", mount.location)
        assertEquals(kept, mount.bytes)
        assertEquals(listOf(".bin", "semver"), files.list(mount.location).map { it.name })
        assertEquals("semver", files.bytes("${user.path}/node_modules/semver/index.js").readText())
    }

    @Test fun `a mount shows without a placeholder, and a placeholder never shows in its place`() {
        File(user, "node_modules").delete()
        assertEquals(listOf("node_modules", "package.json", "src"), files.list(user.path).map { it.name })
        kept.deleteRecursively()
        File(user, "node_modules").mkdirs()
        assertEquals(listOf("package.json", "src"), files.list(user.path).map { it.name })
    }

    @Test fun `a link in a mount is listed as the link it is`() {
        val link = files.list("${user.path}/node_modules/.bin").single()
        assertEquals("semver", link.name)
        assertTrue(Files.isSymbolicLink(link.bytes.toPath()))
        assertEquals("semver", link.bytes.readText())
    }

    @Test fun `only the workspace is projected`() {
        assertEquals("", files.relative(user.path))
        assertEquals("src/App.tsx", files.relative("${user.path}/src/App.tsx"))
        assertNull(files.relative("${user.path}x/a"))
        assertEquals(File("/elsewhere/a.txt"), files.bytes("/elsewhere/a.txt"))
        assertTrue(files.isPrivate("${user.path}/node_modules"))
        assertTrue(files.isPrivate("${user.path}/node_modules/semver"))
        assertFalse(files.isPrivate("${user.path}/src"))
        assertFalse(files.isPrivate(user.path))
    }
}
