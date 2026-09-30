package dev.icode.mobile.data.workspace

import android.content.SharedPreferences
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.EarlierWorkspace
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceStorageMigrationTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val values = mutableMapOf<String, Any?>()
    private val notices = mutableListOf<AppNotice>()
    private val forgotten = mutableListOf<String>()
    private val store by lazy { FakeStore() }

    private val root by lazy { File(temp.root, "files/workspaces").apply { mkdirs() } }
    private val rootfs by lazy { File(temp.root, "files/runtime/rootfs").apply { mkdirs() } }
    private val migration by lazy {
        WorkspaceStorageMigration(preferences(), WorkspacePaths(root), rootfs, store, notices::add) { forgotten += it }
    }

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    /** Preferences kept in [values]: all the marker needs. */
    private fun preferences(): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putInt" -> editor.also { values[args[0] as String] = args[1] }
                "commit" -> true
                else -> error("unexpected ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getInt" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("unexpected ${method.name}")
            }
        } as SharedPreferences
    }

    private inner class FakeStore : WorkspaceStore {
        var kept = listOf("${root.path}/app", "/storage/emulated/0/Projects/site")
        var earlier = listOf(EarlierRecent("site", "/storage/emulated/0/Projects/site", "/storage/emulated/0/Projects/site"))
        var failure: Exception? = null
        override fun lastOpened() = emptyMap<String, Long>()
        override fun touch(location: String, at: Long) = Unit
        override fun relocate(from: String, to: String) = Unit
        override fun forget(location: String) = Unit
        override fun forgetUnless(keep: (workspaceLocation: String) -> Boolean) {
            failure?.let { throw it }
            kept = kept.filter(keep)
        }
        override fun loadRecentDocuments(workspaceLocation: String) = emptyList<StoredRecentDocument>()
        override fun touchRecentDocument(document: StoredRecentDocument) = Unit
        override fun updateRecentDocuments(change: (List<StoredRecentDocument>) -> List<StoredRecentDocument>) = Unit
        override fun loadSession(): StoredSession? = null
        override fun saveSession(session: StoredSession?) = Unit
        override fun earlierRecents() = earlier
        override fun clearEarlierRecents() {
            earlier = emptyList()
        }
    }

    @Test fun `the first start clears the earlier design's leftovers and names the earlier workspaces once`() = runBlocking {
        val workspace = File(root, "app").apply { mkdirs() }
        File(workspace, "notes.md").writeText("kept")
        File(root, "a".repeat(64)).mkdirs()
        File(rootfs, "workspace/old-mount").mkdirs()
        File(rootfs, "workspace/users-own/file").apply { parentFile.mkdirs(); writeText("kept") }
        File(root, ".clone-12").mkdirs()

        migration.run()

        assertEquals(listOf("app"), root.list()!!.toList())
        assertEquals("kept", File(workspace, "notes.md").readText())
        assertEquals(listOf("users-own"), File(rootfs, "workspace").list()!!.toList())
        assertEquals(listOf("${root.path}/app"), store.kept)
        assertEquals(listOf("/storage/emulated/0/Projects/site"), forgotten)
        val notice = notices.single() as AppNotice.WorkspacesMovedIn
        assertEquals(listOf(EarlierWorkspace("site", "/storage/emulated/0/Projects/site")), notice.earlier)
        assertFalse(notice.read)
        assertTrue(store.earlier.isEmpty())
    }

    @Test fun `later starts only clear what an ended clone or import left`() = runBlocking {
        migration.run()
        val lookalike = File(root, "b".repeat(64)).apply { mkdirs() }
        File(root, ".import-3").mkdirs()
        store.earlier = listOf(EarlierRecent("late", "/storage/emulated/0/late", "/storage/emulated/0/late"))

        migration.run()

        assertEquals(listOf(lookalike.name), root.list()!!.toList())
        assertEquals(1, notices.size)
    }

    @Test fun `without earlier workspaces there is nothing to say`() = runBlocking {
        store.earlier = emptyList()
        migration.run()
        assertTrue(notices.isEmpty())
    }

    @Test fun `a failure is tried again at the next start`() = runBlocking {
        store.failure = IllegalStateException("Unable to save")
        migration.run()
        assertTrue(notices.isEmpty())

        store.failure = null
        migration.run()
        assertEquals(1, notices.size)
    }
}
