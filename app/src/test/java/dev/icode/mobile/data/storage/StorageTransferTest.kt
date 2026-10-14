package dev.icode.mobile.data.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [base] with entries added to a folder's listing: names a folder on disk cannot hold, or a name twice. */
private class ListedAlso(private val base: TransferTree, private val extra: Map<String, List<TransferEntry>>) : TransferTree by base {
    override fun children(folder: TransferEntry): List<TransferEntry> = base.children(folder) + extra[folder.id].orEmpty()
}

class StorageTransferTest {
    @get:Rule val temp = TemporaryFolder()

    private fun source(): File = temp.newFolder("picked").apply {
        File(this, "src/main").mkdirs()
        File(this, "src/main/App.kt").writeText("fun main() {}")
        File(this, "README.md").writeText("# app")
        File(this, ".git").mkdirs()
        File(this, ".git/HEAD").writeText("ref: refs/heads/main\n")
    }

    @Test fun `the plan lists each folder before what it holds, with the bytes to copy`() {
        val plan = StorageTransfer.plan(FileTransferTree(source()))
        val order = plan.entries.map { it.relative }
        assertEquals(setOf(".git", ".git/HEAD", "README.md", "src", "src/main", "src/main/App.kt"), order.toSet())
        order.forEach { path -> path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { assertTrue(order.indexOf(it) < order.indexOf(path)) } }
        assertEquals(("fun main() {}".length + "# app".length + "ref: refs/heads/main\n".length).toLong(), plan.bytes)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test fun `a name listed twice and a name no folder can hold are skipped and counted`() {
        val picked = source()
        fun entry(name: String) = TransferEntry("x-$name", name, folder = false, size = 5, modified = 0)
        val tree = ListedAlso(FileTransferTree(picked), mapOf(picked.path to listOf(entry("README.md"), entry("a/b"), entry(".."))))
        val plan = StorageTransfer.plan(tree)
        assertEquals(listOf("..", "README.md", "a/b"), plan.skipped.sorted())
        assertEquals(1, plan.entries.count { it.relative == "README.md" })
    }

    @Test fun `a link in the source is not followed, and is counted as not copied`() {
        val picked = source()
        val outside = temp.newFolder("outside").apply { File(this, "secret").writeText("not imported") }
        Files.createSymbolicLink(File(picked, "elsewhere").toPath(), outside.toPath())
        Files.createSymbolicLink(File(picked, "readme-link").toPath(), File(picked, "README.md").toPath())
        val plan = StorageTransfer.plan(FileTransferTree(picked))
        assertEquals(listOf("elsewhere", "readme-link"), plan.skipped.sorted())
        assertTrue(plan.entries.none { it.relative.startsWith("elsewhere") })
    }

    @Test fun `the copy is the plan, each time kept, and the source is only read`() {
        val picked = source()
        val stamp = 1_600_000_000_000L
        File(picked, "README.md").setLastModified(stamp)
        File(picked, "src").setLastModified(stamp)
        val tree = FileTransferTree(picked)
        val plan = StorageTransfer.plan(tree)
        val into = File(temp.root, ".import-1")
        val done = mutableListOf<String>()
        StorageTransfer.copy(tree, plan, into) { done += it }

        assertEquals(plan.entries.map { it.relative }, done)
        assertEquals("fun main() {}", File(into, "src/main/App.kt").readText())
        assertEquals(stamp, File(into, "README.md").lastModified())
        assertEquals(stamp, File(into, "src").lastModified())
        assertEquals("# app", File(picked, "README.md").readText())
    }

    @Test fun `a copy stopped from its progress stops there`() {
        val tree = FileTransferTree(source())
        val plan = StorageTransfer.plan(tree)
        val into = File(temp.root, ".import-2")
        var copied = 0
        val stopped = runCatching { StorageTransfer.copy(tree, plan, into) { if (++copied == 2) throw IllegalStateException("Cancelled") } }
        assertTrue(stopped.isFailure)
        assertEquals(2, copied)
    }

    @Test fun `what is already where it would go is never written over, nor removed`() {
        val tree = FileTransferTree(source())
        val into = temp.newFolder(".import-3").apply { File(this, "kept").writeText("theirs") }
        assertFalse(runCatching { StorageTransfer.copy(tree, StorageTransfer.plan(tree), into) {} }.isSuccess)
        assertEquals("theirs", File(into, "kept").readText())
    }

    @Test fun `a copy stopped or failed removes what it made`() {
        val tree = FileTransferTree(source())
        val into = File(temp.root, ".import-4")
        runCatching { StorageTransfer.copy(tree, StorageTransfer.plan(tree), into) { if (it == "src") throw IllegalStateException("Cancelled") } }
        assertFalse(into.exists())
    }

    @Test fun `for phone storage a name that differs from another only in case is skipped`() {
        val picked = source().apply {
            File(this, "readme.md").writeText("lower")
            File(this, "Src").mkdirs()
        }
        val plan = StorageTransfer.plan(FileTransferTree(picked), caseInsensitive = true)
        // In name order the first of each name is kept: "README.md" before "readme.md", "Src" before "src".
        assertEquals(listOf("readme.md", "src"), plan.skipped.sorted())
        assertEquals(listOf("README.md", "Src"), plan.entries.map { it.relative }.filter { '/' !in it && !it.startsWith(".") }.sorted())
        assertTrue(StorageTransfer.plan(FileTransferTree(picked)).skipped.isEmpty())
    }

    @Test fun `a file is copied as itself, its time kept`() {
        val file = File(source(), "README.md").apply { setLastModified(1_600_000_000_000L) }
        val tree = FileTransferTree(file)
        val plan = StorageTransfer.plan(tree)
        assertEquals("# app".length.toLong(), plan.bytes)
        assertTrue(plan.entries.isEmpty())
        val into = File(temp.root, "README copy.md")
        val done = mutableListOf<String>()
        StorageTransfer.copy(tree, plan, into) { done += it }
        assertEquals(listOf(""), done)
        assertEquals("# app", into.readText())
        assertEquals(1_600_000_000_000L, into.lastModified())
    }
}
