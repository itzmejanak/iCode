package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.assistant.tools.TestRuntime
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFileSourceTest {
    @get:Rule val temp = TemporaryFolder()

    private val runtime by lazy { TestRuntime(temp.root) }
    private val source by lazy { WorkspaceFileSource(runtime.paths) }

    private fun link(name: String, target: String) = Files.createSymbolicLink(File(runtime.root, name).toPath(), File(target).toPath())

    @Test fun `a link is listed while it leads inside its workspace, followed as the runtime follows it`() {
        File(runtime.root, "src/real.kt").apply { parentFile!!.mkdirs(); writeText("") }
        File(runtime.workspaces, "other").mkdirs()
        // Made in the runtime: a runtime path, which the phone itself cannot follow.
        link("alias.kt", "/workspace/app/src/real.kt")
        link("docs", "src")
        link("away", "/workspace/other")
        link("system", "/etc")
        link("lib", "/workspace/app/src")
        val listed = source.listChildren(runtime.root.path)
        assertEquals(listOf("docs", "lib", "src", "alias.kt"), listed.map { it.name })
        // Named where it is; opened as the file it names.
        assertEquals(File(runtime.root, "alias.kt").path, listed.last().location)
        assertEquals(File(runtime.root, "src/real.kt").path, source.readDocument(listed.last().location).location)
        assertEquals(listOf("real.kt"), source.listChildren(File(runtime.root, "lib").path).map { it.name })
    }
}
