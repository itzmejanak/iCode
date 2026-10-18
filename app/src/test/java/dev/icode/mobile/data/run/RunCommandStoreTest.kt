package dev.icode.mobile.data.run

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.RunConfiguration
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunCommandStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val file by lazy { File(temporaryFolder.root, "files/run-commands.json") }
    private val store by lazy { RunCommandStore(file) }
    private val originalWriter = BackendLog.writer
    private val logged = mutableListOf<String>()

    @Before fun captureLog() {
        BackendLog.writer = { _, _, message -> logged += message }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    @Test fun `commands are kept per workspace, in the order saved, and read again from the file`() {
        val dev = store.save("/w/a", null, " dev ", " npm run dev ")
        val test = store.save("/w/a", null, "", "npm test -- --watch")
        store.save("/w/b", null, "serve", "python3 -m http.server")
        assertEquals(RunConfiguration.Command(dev.id, "dev", "npm run dev"), dev)
        // A blank name is the command's first word, as a process is named.
        assertEquals("npm", test.name)
        assertEquals(listOf(dev, test), RunCommandStore(file).of("/w/a"))
        assertEquals(listOf("serve"), RunCommandStore(file).of("/w/b").map { it.name })
        assertEquals(emptyList<RunConfiguration.Command>(), store.of("/w/c"))
    }

    @Test fun `a command changed keeps its place and id, and one deleted is gone`() {
        val dev = store.save("/w/a", null, "dev", "npm run dev")
        val test = store.save("/w/a", null, "test", "npm test")
        store.save("/w/a", dev.id, "dev", "npm run dev -- --host")
        assertEquals(listOf("npm run dev -- --host", "npm test"), store.of("/w/a").map { it.command })
        store.delete("/w/a", dev.id)
        assertEquals(listOf(test), store.of("/w/a"))
    }

    @Test fun `a workspace renamed keeps its commands, and one deleted forgets them`() {
        store.save("/w/a", null, "dev", "npm run dev")
        store.save("/w/b", null, "serve", "python3 -m http.server")
        store.move("/w/a", "/w/renamed")
        assertEquals(emptyList<RunConfiguration.Command>(), store.of("/w/a"))
        assertEquals(listOf("dev"), store.of("/w/renamed").map { it.name })
        store.forget("/w/b")
        assertEquals(emptyList<RunConfiguration.Command>(), RunCommandStore(file).of("/w/b"))
    }

    @Test fun `a file that cannot be read holds nothing, and the next save writes it whole again`() {
        file.parentFile?.mkdirs()
        file.writeText("{not json")
        assertEquals(emptyList<RunConfiguration.Command>(), store.of("/w/a"))
        // The error's type only: a command line may hold a secret.
        assertEquals(listOf("The saved run commands could not be read (JSONException)"), logged)
        store.save("/w/a", null, "dev", "npm run dev")
        assertEquals(listOf("dev"), RunCommandStore(file).of("/w/a").map { it.name })
        assertEquals(listOf("run-commands.json"), file.parentFile?.list()?.toList())
    }
}
