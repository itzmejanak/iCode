package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitTreeStatusTest {

    private val root = "/storage/emulated/0/project"
    private fun folder(path: String) = FileEntry(path.substringAfterLast('/'), "$root/$path", FileKind.Folder)
    private fun file(path: String) = FileEntry(path.substringAfterLast('/'), "$root/$path", FileKind.File)
    private fun decorate(entries: List<FileEntry>, changes: Map<String, String>) =
        GitTreeStatus.decorate(entries, changes.mapKeys { "$root/${it.key}" }).associate { it.path.removePrefix("$root/") to it.gitStatus }

    @Test
    fun `a change deep inside marks every folder above it`() {
        val statuses = decorate(
            listOf(folder("src"), folder("src/utils"), file("src/utils/helpers.ts"), folder("docs")),
            mapOf("src/utils/helpers.ts" to GitStatusCodes.MODIFIED),
        )
        assertEquals(GitStatusCodes.MODIFIED, statuses["src"])
        assertEquals(GitStatusCodes.MODIFIED, statuses["src/utils"])
        assertEquals(GitStatusCodes.MODIFIED, statuses["src/utils/helpers.ts"])
        assertNull(statuses["docs"])
    }

    @Test
    fun `a folder shows the strongest change beneath it`() {
        val statuses = decorate(
            listOf(folder("src")),
            mapOf(
                "src/a.ts" to GitStatusCodes.ADDED,
                "src/b.ts" to GitStatusCodes.MODIFIED,
                "src/c.ts" to GitStatusCodes.CONFLICTED,
                "src/d.ts" to GitStatusCodes.UNTRACKED,
            ),
        )
        assertEquals(GitStatusCodes.CONFLICTED, statuses["src"])
    }

    @Test
    fun `a deleted file still marks its folder`() {
        val statuses = decorate(listOf(folder("src")), mapOf("src/gone.ts" to GitStatusCodes.DELETED))
        assertEquals(GitStatusCodes.DELETED, statuses["src"])
    }

    @Test
    fun `everything inside an untracked folder is untracked`() {
        // Git reports "?? newdir/" for a new folder, not each file in it.
        val statuses = decorate(
            listOf(folder("newdir"), file("newdir/a.ts"), folder("newdir/sub")),
            mapOf("newdir" to GitStatusCodes.UNTRACKED),
        )
        assertEquals(GitStatusCodes.UNTRACKED, statuses["newdir"])
        assertEquals(GitStatusCodes.UNTRACKED, statuses["newdir/a.ts"])
        assertEquals(GitStatusCodes.UNTRACKED, statuses["newdir/sub"])
    }

    @Test
    fun `a sibling with a longer name is not beneath the folder`() {
        val statuses = decorate(listOf(folder("src")), mapOf("src-old/a.ts" to GitStatusCodes.MODIFIED))
        assertNull(statuses["src"])
    }
}
