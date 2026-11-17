package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.io.Sha256
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A place's files kept before each message and put back by Undo (docs/assistant-and-mcp.md). */
class AssistantCheckpointsTest {
    @get:Rule val temp = TemporaryFolder()

    private val runtime by lazy { TestRuntime(temp.newFolder("runtime")) }
    private val place by lazy { runtime.workspace }
    private val root by lazy { runtime.root }
    private val folder by lazy { File(temp.root, "checkpoints") }
    private val checkpoints by lazy { AssistantCheckpoints(folder) }

    private fun write(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    /** Every file of the place, as text, by its path: what Undo must give back exactly. */
    private fun files(): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    @Test fun `undo puts every file back as it was, changed, added and deleted, in new folders too`() {
        write("src/a.kt", "val a = 1")
        write("README.md", "Hello")
        val before = files()
        val id = checkpoints.take(place)!!
        write("src/a.kt", "val a = 2")
        File(root, "README.md").delete()
        write("src/new/b.kt", "val b = 1")
        assertEquals(emptyList<String>(), checkpoints.restore(place, id))
        assertEquals(before, files())
        assertFalse("a folder made since goes", File(root, "src/new").exists())
    }

    @Test fun `what tools make again is neither kept nor touched, and a file that runs runs again`() {
        write("run.sh", "echo hi").setExecutable(true)
        write("node_modules/x/index.js", "1")
        write(".git/HEAD", "ref: main")
        val id = checkpoints.take(place)!!
        File(root, "run.sh").setExecutable(false)
        write("node_modules/x/index.js", "2")
        write("build/out.txt", "made")
        checkpoints.restore(place, id)
        assertTrue(File(root, "run.sh").canExecute())
        assertEquals("2", File(root, "node_modules/x/index.js").readText())
        assertEquals("made", File(root, "build/out.txt").readText())
        assertEquals("ref: main", File(root, ".git/HEAD").readText())
    }

    @Test fun `going back to an earlier message undoes what came after it too`() {
        write("a.txt", "one")
        val first = checkpoints.take(place)!!
        write("a.txt", "two")
        checkpoints.ended(place, first)
        val second = checkpoints.take(place)!!
        write("a.txt", "three")
        write("b.txt", "new")
        checkpoints.ended(place, second)
        checkpoints.restore(place, first)
        assertEquals(mapOf("a.txt" to "one"), files())
    }

    @Test fun `files changed outside the chat's work since a message are found, between its turns and after the last`() {
        write("a.txt", "one")
        val first = checkpoints.take(place)!!
        write("a.txt", "by the assistant")
        checkpoints.ended(place, first)
        // The user, between two messages.
        write("mine.txt", "mine")
        val second = checkpoints.take(place)!!
        write("a.txt", "by the assistant again")
        checkpoints.ended(place, second)
        assertEquals(setOf("mine.txt"), checkpoints.outside(place, listOf(first, second)))
        // And after the last turn.
        write("later.txt", "later")
        assertEquals(setOf("later.txt", "mine.txt"), checkpoints.outside(place, listOf(first, second)))
        assertEquals(setOf("later.txt"), checkpoints.outside(place, listOf(second)))
    }

    @Test fun `forgotten checkpoints take the contents only they kept, and a gone one is said`() {
        write("a.txt", "one")
        val first = checkpoints.take(place)!!
        write("a.txt", "two")
        val second = checkpoints.take(place)!!
        val blobs = File(folder, "${Sha256.hex(place.place!!)}/blobs")
        assertEquals(2, blobs.listFiles()!!.size)
        checkpoints.forget(place.place, listOf(first))
        assertEquals(1, blobs.listFiles()!!.size)
        assertNull(checkpoints.restore(place, first))
        assertEquals(emptyList<String>(), checkpoints.restore(place, second))
    }

    @Test fun `a workspace's checkpoints follow its rename, and go with its delete`() {
        write("a.txt", "one")
        val id = checkpoints.take(place)!!
        val renamed = File(root.parentFile, "renamed")
        root.renameTo(renamed)
        checkpoints.moved(root.path, renamed.path)
        File(renamed, "a.txt").writeText("two")
        val moved = AssistantWorkspace(renamed, runtime.paths)
        assertEquals(emptyList<String>(), checkpoints.restore(moved, id))
        assertEquals("one", File(renamed, "a.txt").readText())
        checkpoints.forgotten(renamed.path)
        assertNull(checkpoints.restore(moved, id))
    }

    @Test fun `in no workspace the home is kept, its hidden folders left out`() {
        val home = AssistantWorkspace(null, runtime.paths)
        File(home.root, "notes.txt").writeText("one")
        File(home.root, ".cache/x").apply { parentFile.mkdirs(); writeText("cache") }
        val id = checkpoints.take(home)!!
        File(home.root, "notes.txt").writeText("two")
        File(home.root, ".cache/x").writeText("changed")
        checkpoints.restore(home, id)
        assertEquals("one", File(home.root, "notes.txt").readText())
        assertEquals("changed", File(home.root, ".cache/x").readText())
    }
}
