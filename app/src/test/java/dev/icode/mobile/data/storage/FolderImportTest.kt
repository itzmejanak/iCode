package dev.icode.mobile.data.storage

import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A folder on disk read as a provider lists it; [extra] adds entries a provider may list and disk cannot hold. */
internal class DiskImportTree(private val root: File, private val extra: Map<String, List<ImportEntry>> = emptyMap()) : ImportTree {
    override val top = entryOf(root)

    override fun children(folder: ImportEntry): List<ImportEntry> =
        File(folder.id).listFiles().orEmpty().map(::entryOf) + extra[folder.id].orEmpty()

    override fun open(file: ImportEntry): InputStream = File(file.id).inputStream()

    private fun entryOf(file: File) = ImportEntry(
        id = file.path,
        name = file.name,
        folder = file.isDirectory,
        size = if (file.isFile) file.length() else 0,
        modified = file.lastModified(),
        virtual = false,
    )
}

class FolderImportTest {
    @get:Rule val temp = TemporaryFolder()

    private fun source(): File = temp.newFolder("picked").apply {
        File(this, "src/main").mkdirs()
        File(this, "src/main/App.kt").writeText("fun main() {}")
        File(this, "README.md").writeText("# app")
        File(this, ".git").mkdirs()
        File(this, ".git/HEAD").writeText("ref: refs/heads/main\n")
    }

    @Test fun `the plan lists each folder before what it holds, with the bytes to copy`() {
        val plan = FolderImport.plan(DiskImportTree(source()))
        val order = plan.entries.map { it.relative }
        assertEquals(setOf(".git", ".git/HEAD", "README.md", "src", "src/main", "src/main/App.kt"), order.toSet())
        order.forEach { path -> path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { assertTrue(order.indexOf(it) < order.indexOf(path)) } }
        assertEquals(("fun main() {}".length + "# app".length + "ref: refs/heads/main\n".length).toLong(), plan.bytes)
        assertTrue(plan.skipped.isEmpty())
    }

    @Test fun `a cloud file, a name listed twice and a name no folder can hold are skipped and counted`() {
        val picked = source()
        fun entry(name: String, virtual: Boolean = false) = ImportEntry("x-$name", name, folder = false, size = 5, modified = 0, virtual = virtual)
        val tree = DiskImportTree(picked, mapOf(picked.path to listOf(entry("cloud.pdf", virtual = true), entry("README.md"), entry("a/b"), entry(".."))))
        val plan = FolderImport.plan(tree)
        assertEquals(listOf("..", "README.md", "a/b", "cloud.pdf"), plan.skipped.sorted())
        assertEquals(1, plan.entries.count { it.relative == "README.md" })
    }

    @Test fun `the copy is the plan, each time kept, and the source is only read`() {
        val picked = source()
        val stamp = 1_600_000_000_000L
        File(picked, "README.md").setLastModified(stamp)
        File(picked, "src").setLastModified(stamp)
        val tree = DiskImportTree(picked)
        val plan = FolderImport.plan(tree)
        val into = File(temp.root, ".import-1")
        val done = mutableListOf<String>()
        FolderImport.copy(tree, plan, into) { done += it }

        assertEquals(plan.entries.map { it.relative }, done)
        assertEquals("fun main() {}", File(into, "src/main/App.kt").readText())
        assertEquals(stamp, File(into, "README.md").lastModified())
        assertEquals(stamp, File(into, "src").lastModified())
        assertEquals("# app", File(picked, "README.md").readText())
    }

    @Test fun `a copy stopped from its progress stops there`() {
        val tree = DiskImportTree(source())
        val plan = FolderImport.plan(tree)
        val into = File(temp.root, ".import-2")
        var copied = 0
        val stopped = runCatching { FolderImport.copy(tree, plan, into) { if (++copied == 2) throw IllegalStateException("Cancelled") } }
        assertTrue(stopped.isFailure)
        assertEquals(2, copied)
    }

    @Test fun `the staging folder is never an existing one`() {
        val tree = DiskImportTree(source())
        val into = temp.newFolder(".import-3")
        assertFalse(runCatching { FolderImport.copy(tree, FolderImport.plan(tree), into) {} }.isSuccess)
    }
}
