package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.domain.files.StoragePlace
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One Trash per place, a rename on the item's own volume (docs/explorer-gaps-plan.md §3.7). */
class FileTrashTest {
    @get:Rule val temp = TemporaryFolder()

    private var now = TimeUnit.DAYS.toMillis(100)
    private var access = true
    private val workspaces by lazy { temp.newFolder("files", "workspaces") }
    private val phone by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val privateTrash by lazy { File(workspaces.parentFile, "trash") }
    private val sharedTrash by lazy { File(phone, "Android/media/dev.icode.mobile/Trash") }
    private val trash by lazy { FileTrash(privateTrash, sharedTrash, PhoneStorage(phone) { access }) { now } }
    private val src by lazy {
        File(workspaces, "app/src").apply {
            mkdirs()
            File(this, "App.kt").writeText("fun main() {}")
        }
    }

    @Test fun `an item goes to its place's Trash whole, with what it was`() {
        val item = trash.put(src.path, bytes = 13, workspace = false)
        assertFalse(src.exists())
        assertEquals("fun main() {}", File(trash.locationOf(item), "App.kt").readText())
        assertEquals(StoragePlace.Private, item.place)
        assertTrue(trash.locationOf(item).startsWith(privateTrash.path))
        assertEquals(listOf(item), trash.list())
        assertEquals(item, trash.item(item.id))
    }

    @Test fun `on phone storage it stays on phone storage, out of the galleries`() {
        val photo = File(phone, "DCIM/beach.jpg").apply { parentFile!!.mkdirs(); writeText("jpg") }
        val item = trash.put(photo.path, bytes = 3, workspace = false)
        assertEquals(StoragePlace.Shared, item.place)
        assertTrue(trash.locationOf(item).startsWith(sharedTrash.path))
        assertTrue(File(sharedTrash, ".nomedia").isFile)
    }

    @Test fun `restored, it goes back where it was, its folders made again when they went`() {
        val item = trash.put(src.path, bytes = 13, workspace = false)
        File(workspaces, "app").deleteRecursively()
        trash.restore(item, item.from)
        assertEquals("fun main() {}", File(src, "App.kt").readText())
        assertTrue(trash.list().isEmpty())
        assertNull(trash.item(item.id))
    }

    @Test fun `a name taken where it goes is never overwritten`() {
        val item = trash.put(src.path, bytes = 13, workspace = false)
        src.mkdirs()
        assertThrows(IOException::class.java) { trash.restore(item, item.from) }
        assertEquals(listOf(item), trash.list())
    }

    @Test fun `deleted for good, it leaves Trash at once, each entry said`() {
        val item = trash.put(src.path, bytes = 13, workspace = false)
        val said = mutableListOf<String>()
        trash.delete(item) { said += it }
        assertEquals(listOf("src/App.kt", "src"), said)
        assertTrue(trash.list().isEmpty())
        assertTrue(File(privateTrash, "files").list()!!.isEmpty())
    }

    @Test fun `newest first, both places, Shared's only with access`() {
        val first = trash.put(src.path, bytes = null, workspace = false)
        now += 1000
        val photo = File(phone, "beach.jpg").apply { writeText("jpg") }
        val second = trash.put(photo.path, bytes = 3, workspace = false)
        assertEquals(listOf(second, first), trash.list())
        access = false
        assertEquals(listOf(first), trash.list())
    }

    @Test fun `30 days on an item is removed, and at start what has no record goes too`() {
        val old = trash.put(src.path, bytes = 13, workspace = false)
        now += TimeUnit.DAYS.toMillis(29)
        val readme = File(workspaces, "app/README.md").apply { writeText("# app") }
        val kept = trash.put(readme.path, bytes = 5, workspace = true)
        now += TimeUnit.DAYS.toMillis(1)
        val orphan = File(privateTrash, "files/left-behind").apply { mkdirs() }
        trash.purge(orphans = false)
        assertEquals(listOf(kept), trash.list())
        assertTrue(orphan.exists())
        trash.purge(orphans = true)
        assertFalse(orphan.exists())
        assertEquals(listOf(kept), trash.list())
        assertNull(trash.item(old.id))
    }

    @Test fun `a record whose item has gone is let go`() {
        val item = trash.put(src.path, bytes = 13, workspace = false)
        File(trash.locationOf(item)).deleteRecursively()
        assertTrue(trash.list().isEmpty())
        assertFalse(File(privateTrash, "info/${item.id}.json").exists())
    }

    @Test fun `Trash and what holds it are Trash's own`() {
        trash.put(src.path, bytes = 13, workspace = false)
        assertTrue(trash.holds(privateTrash.path))
        assertTrue(trash.holds(File(privateTrash, "files").path))
        assertTrue(trash.holds(File(phone, "Android").path))
        assertFalse(trash.holds(File(phone, "DCIM").path))
        assertFalse(trash.holds(workspaces.path))
    }
}
