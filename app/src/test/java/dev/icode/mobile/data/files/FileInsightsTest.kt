package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceLifecycleCoordinator.Reopen
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FactKind
import dev.icode.mobile.domain.files.FileFacts
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.TreeFacts
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Properties and Manage storage, as the explorers ask for them (docs/explorer-gaps-plan.md §3.9, §3.10). */
class FileInsightsTest {
    @get:Rule val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private lateinit var workspaces: File
    private lateinit var insights: FileInsightsCoordinator

    private object Follower : FileFollower {
        lateinit var workspaces: File
        override fun isWorkspace(location: String) = File(location).parentFile == workspaces
        override suspend fun leaving(location: String): Reopen? = null
        override suspend fun stayed(location: String, reopen: Reopen?) = Unit
        override suspend fun moved(from: String, to: String, reopen: Reopen?) = Unit
        override suspend fun removed(location: String) = Unit
        override suspend fun refresh(touched: Collection<String>) = Unit
    }

    @Before fun start() {
        workspaces = temp.newFolder("files", "workspaces").canonicalFile
        Follower.workspaces = workspaces
        val phone = temp.newFolder("emulated", "0").canonicalFile
        val storage = PhoneStorage(phone) { true }
        val places = FilePlaces(workspaces, storage)
        val trash = FileTrash(File(workspaces.parentFile, "trash"), File(phone, "Android/media/dev.icode.mobile/Trash"), storage)
        insights = FileInsightsCoordinator(stateHolder, scope, places, Follower, StorageScan(places, trash))
        insights.start()
        File(workspaces, "app/src").mkdirs()
        File(workspaces, "app/src/Main.kt").writeText("fun main() {}")
        File(workspaces, "app/run.sh").apply { writeText("#!/bin/sh") }.setExecutable(true, false)
        Files.createSymbolicLink(File(workspaces, "app/latest").toPath(), File("run.sh").toPath())
    }

    @After fun stop() {
        scope.cancel()
    }

    private fun <T : Any> await(read: () -> T?): T = runBlocking {
        withTimeout(5_000) {
            var value = read()
            while (value == null) {
                delay(5)
                value = read()
            }
            value
        }
    }

    private fun facts(vararg locations: String): FileFacts {
        val asked = locations.map { File(workspaces, it).path }
        insights.handle(FilesIntent.Properties(asked))
        return await { stateHolder.current.explorer.facts?.takeIf { it.locations == asked && it.measured != null } }
    }

    @Test fun `a file is known at once, a link says where it points, a workspace is one`() {
        val script = facts("app/run.sh")
        assertEquals(FactKind.File, script.kind)
        assertEquals(true, script.executable)
        assertEquals(TreeFacts(9, 0, 0), script.measured)
        assertEquals("run.sh", facts("app/latest").linkTarget)
        assertEquals(FactKind.Workspace, facts("app").kind)
    }

    @Test fun `a folder is measured, and many are counted and measured together`() {
        assertEquals(TreeFacts(13, 1, 0), facts("app/src").measured)
        val many = facts("app/src", "app/run.sh")
        assertNull(many.kind)
        assertEquals(1, many.folders)
        assertEquals(22L, many.measured!!.bytes)
    }

    @Test fun `what moves under open Properties is read again where it went`() {
        facts("app/src")
        File(workspaces, "app/src").renameTo(File(workspaces, "app/lib"))
        insights.follow(listOf(FileStep.Moved(File(workspaces, "app/src").path, File(workspaces, "app/lib").path)))
        await { stateHolder.current.explorer.facts?.takeIf { it.locations == listOf(File(workspaces, "app/lib").path) && it.measured != null } }
        insights.handle(FilesIntent.CloseProperties)
        await { (stateHolder.current.explorer.facts == null).takeIf { it } }
    }

    @Test fun `Manage storage says it scans, then what it found`() {
        insights.handle(FilesIntent.Scan(ScanScope.Private))
        val overview = await { stateHolder.current.explorer.storage?.takeIf { it.scanning == null && it.scannedAt != null } }
        assertEquals(2, overview.files)
    }
}
