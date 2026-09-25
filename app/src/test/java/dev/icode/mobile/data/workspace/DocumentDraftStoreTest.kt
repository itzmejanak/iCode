package dev.icode.mobile.data.workspace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentDraftStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(): DocumentDraftStore = DocumentDraftStore(File(temporaryFolder.root, "drafts"))

    @Test
    fun `a draft is read back exactly as written`() {
        val store = store()
        store.write("/sdcard/p/a.kt", "a.kt", "fun main() {}\n", "abc123")
        val draft = store.read("/sdcard/p/a.kt")
        assertEquals("fun main() {}\n", draft?.text)
        assertEquals("a.kt", draft?.name)
        assertEquals("abc123", draft?.sourceFingerprint)
    }

    @Test
    fun `text containing newlines survives, since every source file has them`() {
        val store = store()
        val text = "line one\nline two\n\nline four"
        store.write("/p/f.txt", "f.txt", text, "f")
        assertEquals(text, store.read("/p/f.txt")?.text)
    }

    @Test
    fun `a path with a newline or colon cannot corrupt the record`() {
        val store = store()
        val awkward = "/sdcard/we:ird\nname/f.txt"
        store.write(awkward, "f.txt", "body", "f")
        assertEquals(awkward, store.read(awkward)?.location)
        assertEquals("body", store.read(awkward)?.text)
    }

    @Test
    fun `an empty draft is still a draft, because the user may have deleted everything`() {
        val store = store()
        store.write("/p/f.txt", "f.txt", "", "f")
        assertEquals("", store.read("/p/f.txt")?.text)
    }

    @Test
    fun `writing again replaces the previous draft rather than adding one`() {
        val store = store()
        store.write("/p/f.txt", "f.txt", "first", "f")
        store.write("/p/f.txt", "f.txt", "second", "f")
        assertEquals("second", store.read("/p/f.txt")?.text)
        assertEquals(1, store.list().size)
    }

    @Test
    fun `deleting removes only that document's draft`() {
        val store = store()
        store.write("/p/a.txt", "a.txt", "a", "f")
        store.write("/p/b.txt", "b.txt", "b", "f")
        store.delete("/p/a.txt")
        assertNull(store.read("/p/a.txt"))
        assertEquals("b", store.read("/p/b.txt")?.text)
    }

    @Test
    fun `listing returns the newest draft first`() {
        val store = store()
        store.write("/p/old.txt", "old.txt", "old", "f")
        Thread.sleep(5)
        store.write("/p/new.txt", "new.txt", "new", "f")
        assertEquals(listOf("new.txt", "old.txt"), store.list().map { it.name })
    }

    @Test
    fun `reading a document with no draft reports none rather than failing`() {
        assertNull(store().read("/p/never-edited.txt"))
    }

    @Test
    fun `listing an empty store is empty, not an error`() {
        assertTrue(store().list().isEmpty())
    }

    @Test
    fun `clear discards every draft`() {
        val store = store()
        store.write("/p/a.txt", "a.txt", "a", "f")
        store.write("/p/b.txt", "b.txt", "b", "f")
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `a corrupt draft file is ignored rather than crashing recovery`() {
        val store = store()
        store.write("/p/good.txt", "good.txt", "good", "f")
        File(temporaryFolder.root, "drafts").listFiles()!!.first().writeText("garbage")
        assertTrue(store.list().size <= 1)
    }

    @Test
    fun `size reflects the draft's own bytes`() {
        val store = store()
        store.write("/p/f.txt", "f.txt", "12345", "f")
        assertEquals(5, store.read("/p/f.txt")?.sizeBytes)
    }
}
