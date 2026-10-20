package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.io.File
import org.json.JSONArray
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
    private val chat = AssistantChat(
        messages = listOf(
            AssistantMessage.User("Rename x — “quotes” and\nlines", listOf(AssistantAttachment("src/a.kt", text = "val x = 1\n", inWorkspace = true))),
            AssistantMessage.Reply("On it.", listOf(call)),
            AssistantMessage.Results(listOf(AssistantResult("call_1", "edit_file", "Edited a.kt", failed = false))),
            AssistantMessage.Compacted("The user renamed x."),
            AssistantMessage.Reply("Done.", reasoning = "The rename touched one file."),
        ),
        contextTokens = 12_345,
    )

    @Test fun `a chat comes back as it was saved, and is listed with its title and time`() {
        store.save("/w/app", "s1", chat, now = 1_000)
        assertEquals(chat, store.load("/w/app", "s1"))
        assertEquals(listOf(AssistantSession("s1", "Rename x — “quotes” and", 1_000)), store.sessions("/w/app"))
        assertEquals(AssistantChat(), store.load("/w/other", "s1"))
        assertEquals(emptyList<AssistantSession>(), store.sessions("/w/other"))
        assertTrue(folder.walk().none { it.name.endsWith(".tmp") })
    }

    @Test fun `chats are listed newest first, and a title once given stays`() {
        store.save("/w/app", "s1", chat, now = 1_000)
        store.save("/w/app", "s2", AssistantChat(listOf(AssistantMessage.User("Second"))), now = 2_000)
        assertEquals(listOf("s2", "s1"), store.sessions("/w/app").map { it.id })
        store.save("/w/app", "s1", chat.copy(messages = chat.messages.drop(1) + AssistantMessage.User("Later")), now = 3_000)
        assertEquals(listOf("s1", "s2"), store.sessions("/w/app").map { it.id })
        assertEquals("Rename x — “quotes” and", store.sessions("/w/app").first().title)
    }

    @Test fun `a chat saved mid-turn comes back with its open call answered as stopped`() {
        store.save("/w/app", "s1", AssistantChat(chat.messages.take(2)), now = 1)
        val loaded = store.load("/w/app", "s1").messages
        assertEquals(AssistantRules.closed(chat.messages.take(2)), loaded)
        assertEquals(AssistantRules.STOPPED, (loaded[2] as AssistantMessage.Results).results.single().output)
    }

    @Test fun `it keeps its newest messages, starting at something the user said, each output bounded`() {
        val long = (1..300).flatMap { listOf(AssistantMessage.User("q$it"), AssistantMessage.Reply("a$it")) }
        // A command's output as its tool bounds it — 32 000 characters and its stderr — is kept whole.
        val whole = "x".repeat(41_000)
        val output = AssistantMessage.Results(listOf(AssistantResult("c", "run_command", whole, failed = false), AssistantResult("d", "read_file", "y".repeat(90_000), failed = false)))
        val calls = listOf(call.copy(id = "c"), call.copy(id = "d"))
        store.save("/w/app", "s1", AssistantChat(long + AssistantMessage.Reply("", calls) + output), now = 1)
        val loaded = store.load("/w/app", "s1").messages
        assertTrue(loaded.size <= 400)
        assertTrue(loaded.first() is AssistantMessage.User)
        val results = (loaded.last() as AssistantMessage.Results).results
        assertEquals(whole, results[0].output)
        assertTrue(results[1].output.length < 49_000)
    }

    @Test fun `a call keeps the file and the command it names, and a change by how many lines`() {
        val edit = AssistantCall("e1", "edit_file", """{"path":"src/a.kt"}""", "Edit src/a.kt", AssistantToolKind.Change, file = "src/a.kt")
        val run = AssistantCall("r1", "run_command", """{"command":"npm test"}""", "Run `npm test`", AssistantToolKind.Command, command = "npm test")
        val saved = AssistantChat(
            listOf(
                AssistantMessage.User("Fix it"),
                AssistantMessage.Reply("", listOf(edit, run)),
                AssistantMessage.Results(
                    listOf(
                        AssistantResult("e1", "edit_file", "Edited src/a.kt: +2 −1 lines", failed = false, changes = LineChanges(2, 1)),
                        AssistantResult("r1", "run_command", "Exit code 0 (succeeded)", failed = false),
                    ),
                ),
            ),
        )
        store.save("/w/app", "s1", saved, now = 1)
        assertEquals(saved, store.load("/w/app", "s1"))
    }

    @Test fun `deleting a chat takes it off the list`() {
        store.save("/w/app", "s1", chat, now = 1)
        store.save("/w/app", "s2", chat, now = 2)
        store.delete("/w/app", "s1")
        assertEquals(listOf("s2"), store.sessions("/w/app").map { it.id })
        assertEquals(AssistantChat(), store.load("/w/app", "s1"))
    }

    @Test fun `chats follow their workspace's rename, and go with its delete`() {
        store.save("/w/app", "s1", chat, now = 1)
        store.move("/w/app", "/w/renamed")
        assertEquals(chat, store.load("/w/renamed", "s1"))
        assertEquals(emptyList<AssistantSession>(), store.sessions("/w/app"))
        store.forget("/w/renamed")
        assertEquals(emptyList<AssistantSession>(), store.sessions("/w/renamed"))
        assertEquals(AssistantChat(), store.load("/w/renamed", "s1"))
    }

    @Test fun `a list that cannot be read is made again from the chats`() {
        store.save("/w/app", "s1", chat, now = 1_000)
        store.save("/w/app", "s2", chat, now = 2_000)
        File(folder, Sha256.hex("/w/app") + "/index.json").writeText("[{")
        assertEquals(listOf("s2", "s1"), store.sessions("/w/app").map { it.id })
        assertEquals(2, JSONArray(File(folder, Sha256.hex("/w/app") + "/index.json").readText()).length())
    }

    @Test fun `a chat that cannot be read starts again, and nothing of it reaches the log`() {
        store.save("/w/app", "s1", chat, now = 1)
        File(folder, Sha256.hex("/w/app") + "/s1.json").writeText("""{"messages":[{"role":"user","text":"secret-token-123"}, {"role":"x""")
        assertEquals(AssistantChat(), store.load("/w/app", "s1"))
        assertTrue(logged.single().startsWith("Unable to read a chat"))
        assertTrue(logged.none { "secret" in it })
    }

    @Test fun `a chat ID is never a path`() {
        val refused = runCatching { store.load("/w/app", "../../escape") }.exceptionOrNull()
        assertTrue("$refused", refused is IllegalArgumentException)
    }

    @Test fun `an image kept with a message goes with its chat, back with it, moved with its workspace, deleted with it`() {
        val kept = store.keep("/w/app", "s1", byteArrayOf(1, 2, 3), "jpg")
        assertEquals(kept, store.keep("/w/app", "s1", byteArrayOf(1, 2, 3), "jpg"))
        val photo = AssistantAttachment("IMG_1.jpg", AssistantAttachmentKind.Image, file = kept, mimeType = "image/jpeg")
        val withPhoto = AssistantChat(listOf(AssistantMessage.User("What is this?", listOf(photo))))
        store.save("/w/app", "s1", withPhoto, now = 1)
        assertEquals(withPhoto, store.load("/w/app", "s1"))

        store.move("/w/app", "/w/renamed")
        val moved = (store.load("/w/renamed", "s1").messages.single() as AssistantMessage.User).attachments.single()
        assertTrue(moved.file!!.startsWith(File(folder, Sha256.hex("/w/renamed")).path))
        assertEquals(listOf<Byte>(1, 2, 3), File(moved.file!!).readBytes().toList())

        store.delete("/w/renamed", "s1")
        assertTrue(!File(moved.file!!).exists())
    }
}
