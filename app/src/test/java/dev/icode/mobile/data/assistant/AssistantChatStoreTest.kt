package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantChatStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val logged = mutableListOf<String>()
    private val folder by lazy { File(temp.root, "chats") }
    private val store by lazy { AssistantChatStore(folder) }

    @Before fun captureLog() {
        BackendLog.writer = { _, _, message -> logged += message }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private val call = AssistantCall("call_1", "edit_file", """{"path":"a.kt","old_text":"\"x\"\n","new_text":"y"}""", "Edit a.kt", AssistantToolKind.Change)
    private val chat = listOf(
        AssistantMessage.User("Rename x — “quotes” and\nlines"),
        AssistantMessage.Reply("On it.", listOf(call)),
        AssistantMessage.Results(listOf(AssistantResult("call_1", "edit_file", "Edited a.kt", failed = false))),
        AssistantMessage.Reply("Done."),
    )

    @Test fun `a chat comes back as it was saved, per workspace`() {
        store.save("/w/app", chat)
        assertEquals(chat, store.load("/w/app"))
        assertEquals(emptyList<AssistantMessage>(), store.load("/w/other"))
        assertTrue(folder.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `a chat saved mid-turn comes back with its open call answered as stopped`() {
        store.save("/w/app", chat.take(2))
        val loaded = store.load("/w/app")
        assertEquals(AssistantRules.closed(chat.take(2)), loaded)
        assertEquals(AssistantRules.STOPPED, (loaded[2] as AssistantMessage.Results).results.single().output)
    }

    @Test fun `it keeps its newest messages, starting at something the user said, each output bounded`() {
        val long = (1..300).flatMap { listOf(AssistantMessage.User("q$it"), AssistantMessage.Reply("a$it")) }
        store.save("/w/app", long + AssistantMessage.Results(listOf(AssistantResult("c", "run_command", "x".repeat(40_000), failed = false))))
        val loaded = store.load("/w/app")
        assertTrue(loaded.size <= 400)
        assertTrue(loaded.first() is AssistantMessage.User)
        assertTrue((loaded.last() as AssistantMessage.Results).results.single().output.length < 17_000)
    }

    @Test fun `a chat follows its workspace's rename, and goes with its delete`() {
        store.save("/w/app", chat)
        store.move("/w/app", "/w/renamed")
        assertEquals(chat, store.load("/w/renamed"))
        assertEquals(emptyList<AssistantMessage>(), store.load("/w/app"))
        store.forget("/w/renamed")
        assertEquals(emptyList<AssistantMessage>(), store.load("/w/renamed"))
    }

    @Test fun `a chat that cannot be read starts again, and nothing of it reaches the log`() {
        store.save("/w/app", chat)
        folder.listFiles()!!.single().writeText("""{"messages":[{"role":"user","text":"secret-token-123"}, {"role":"x"""")
        assertEquals(emptyList<AssistantMessage>(), store.load("/w/app"))
        assertTrue(logged.single().startsWith("Unable to read a chat"))
        assertTrue(logged.none { "secret" in it })
    }
}
