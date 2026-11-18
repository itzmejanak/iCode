package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantDraftFile
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantQueued
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineChanges
import java.io.File
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `every message and every output is kept in full, however long`() {
        val long = (1..400).flatMap { listOf(AssistantMessage.User("q$it"), AssistantMessage.Reply("a$it")) }
        val output = AssistantMessage.Results(listOf(AssistantResult("c", "run_command", "x".repeat(90_000), failed = false)))
        val saved = AssistantChat(long + AssistantMessage.Reply("", listOf(call.copy(id = "c"))) + output)
        store.save("/w/app", "s1", saved, now = 1)
        assertEquals(saved, AssistantChatStore(folder).load("/w/app", "s1"))
    }

    @Test fun `a change at the end is appended, and what came before is not written again`() {
        val file = File(folder, Sha256.hex("/w/app") + "/s1.jsonl")
        store.save("/w/app", "s1", chat, now = 1)
        val before = file.readBytes()
        val answered = chat.copy(messages = chat.messages + AssistantMessage.User("Next") + AssistantMessage.Reply("", listOf(call)))
        store.save("/w/app", "s1", answered, now = 2)
        assertTrue(file.readBytes().take(before.size) == before.toList())
        // A result joins its reply's results, the last line written again; a message taken back is cut off.
        val withResult = answered.copy(messages = answered.messages + AssistantMessage.Results(listOf(AssistantResult("call_1", "edit_file", "Edited a.kt", failed = false))))
        store.save("/w/app", "s1", withResult, now = 3)
        store.save("/w/app", "s1", withResult.copy(messages = withResult.messages.dropLast(3)), now = 4)
        assertEquals(chat, AssistantChatStore(folder).load("/w/app", "s1"))
        assertEquals(before.toList(), file.readBytes().toList())
    }

    @Test fun `a line a killed process left half written is dropped, and the next save writes the chat whole again`() {
        val file = File(folder, Sha256.hex("/w/app") + "/s1.jsonl")
        store.save("/w/app", "s1", chat, now = 1)
        file.appendText("""{"role":"reply","text":"half""")
        val fresh = AssistantChatStore(folder)
        assertEquals(chat, fresh.load("/w/app", "s1"))
        fresh.save("/w/app", "s1", chat.copy(messages = chat.messages + AssistantMessage.User("After")), now = 2)
        assertEquals(chat.messages + AssistantMessage.User("After"), AssistantChatStore(folder).load("/w/app", "s1").messages)
        assertTrue(file.readText().endsWith("\n"))
    }

    @Test fun `a chat an earlier build kept whole is read, and kept in lines from its next save`() {
        val workspace = File(folder, Sha256.hex("/w/app")).apply { mkdirs() }
        File(workspace, "s1.json").writeText(
            """{"id":"s1","title":"Old","updatedAt":5,"messages":[{"role":"user","text":"Hi","attachments":[]},{"role":"reply","text":"Hello","calls":[]}]}""",
        )
        assertEquals(listOf(AssistantSession("s1", "Old", 5)), store.sessions("/w/app"))
        val old = store.load("/w/app", "s1")
        assertEquals(listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello")), old.messages)
        store.save("/w/app", "s1", old.copy(messages = old.messages + AssistantMessage.User("Again")), now = 6)
        assertTrue(!File(workspace, "s1.json").exists())
        assertEquals(old.messages + AssistantMessage.User("Again"), AssistantChatStore(folder).load("/w/app", "s1").messages)
        assertEquals("Old", store.sessions("/w/app").single().title)
    }

    @Test fun `who answers a chat, and who wrote each reply, come back with it`() {
        val saved = AssistantChat(
            listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello", by = AssistantChoice("p1", "gpt-5"))),
            answerer = AssistantChoice("p1", "o3"),
        )
        store.save("/w/app", "s1", saved, now = 1)
        assertEquals(saved, AssistantChatStore(folder).load("/w/app", "s1"))
    }

    @Test fun `a chat answered by a coding agent comes back with it, and each reply with what it cost`() {
        val saved = AssistantChat(
            listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello", by = AssistantAgent("codex-acp"), cost = "0.04 USD")),
            answerer = AssistantAgent("codex-acp"),
        )
        store.save("/w/app", "s1", saved, now = 1)
        assertEquals(saved, AssistantChatStore(folder).load("/w/app", "s1"))
    }

    @Test fun `a message keeps its checkpoint, and a chat cut back to before one loses it and all after`() {
        val chats = AssistantChats(store)
        chats.append("/w/app", "s1", AssistantMessage.User("One", checkpoint = "c1"))
        chats.append("/w/app", "s1", AssistantMessage.Reply("Done."))
        chats.append("/w/app", "s1", AssistantMessage.User("Two", checkpoint = "c2"))
        chats.append("/w/app", "s1", AssistantMessage.Reply("Done again."))
        assertEquals("c2", (AssistantChatStore(folder).load("/w/app", "s1").messages[2] as AssistantMessage.User).checkpoint)
        val reloaded = AssistantChats(AssistantChatStore(folder))
        assertEquals(listOf(AssistantMessage.User("Two", checkpoint = "c2"), AssistantMessage.Reply("Done again.")), reloaded.cutBack("/w/app", "s1", 2))
        assertEquals(listOf(AssistantMessage.User("One", checkpoint = "c1"), AssistantMessage.Reply("Done.")), AssistantChatStore(folder).load("/w/app", "s1").messages)
        assertEquals(emptyList<AssistantMessage>(), reloaded.cutBack("/w/app", "s1", 7))
        reloaded.cutBack("/w/app", "s1", 0)
        assertEquals(emptyList<AssistantSession>(), AssistantChatStore(folder).sessions("/w/app"))
    }

    @Test fun `messages waiting in a chat's queue come back with it, in order, with their files, and go with its delete`() {
        val chats = AssistantChats(store)
        chats.append("/w/app", "s1", AssistantMessage.User("Build it"))
        val photo = AssistantDraftFile("content://photos/1", "1.jpg", AssistantAttachmentKind.Image)
        chats.enqueue("/w/app", "s1", "Then the tests", emptyList(), aloud = false)
        chats.enqueue("/w/app", "s1", "And this", listOf(photo), aloud = true)
        val queued = AssistantChatStore(folder).load("/w/app", "s1").queued
        assertEquals(listOf("Then the tests", "And this"), queued.map { it.text })
        assertEquals(listOf(photo), queued[1].files)
        assertTrue(queued[1].aloud)
        // Put first, then taken out: the next to go, then the rest.
        val reloaded = AssistantChats(AssistantChatStore(folder))
        assertTrue(reloaded.toFront("/w/app", "s1", queued[1].id))
        assertFalse(reloaded.toFront("/w/app", "s1", "gone"))
        assertEquals(queued[1], reloaded.unqueue("/w/app", "s1"))
        assertEquals(listOf(queued[0]), AssistantChatStore(folder).load("/w/app", "s1").queued)
        assertEquals(queued[0], reloaded.unqueue("/w/app", "s1", queued[0].id))
        assertNull(reloaded.unqueue("/w/app", "s1"))
        reloaded.enqueue("/w/app", "s1", "Last", emptyList(), aloud = false)
        reloaded.delete("/w/app", "s1")
        assertEquals(emptyList<AssistantQueued>(), AssistantChatStore(folder).load("/w/app", "s1").queued)
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
        // Made again, a chat's time is when its file last changed.
        File(folder, Sha256.hex("/w/app") + "/s1.jsonl").setLastModified(1_000_000)
        File(folder, Sha256.hex("/w/app") + "/s2.jsonl").setLastModified(2_000_000)
        File(folder, Sha256.hex("/w/app") + "/index.json").writeText("[{")
        assertEquals(listOf("s2", "s1"), store.sessions("/w/app").map { it.id })
        assertEquals(2, JSONArray(File(folder, Sha256.hex("/w/app") + "/index.json").readText()).length())
    }

    @Test fun `a chat that cannot be read starts again, and nothing of it reaches the log`() {
        store.save("/w/app", "s1", chat, now = 1)
        File(folder, Sha256.hex("/w/app") + "/s1.jsonl").writeText("""{"id":"s1","title":"t"}""" + "\n" + """{"role":"x","text":"secret-token-123"}""" + "\n")
        assertEquals(emptyList<AssistantMessage>(), AssistantChatStore(folder).load("/w/app", "s1").messages)
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
