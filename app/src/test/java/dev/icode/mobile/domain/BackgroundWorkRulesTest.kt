package dev.icode.mobile.domain

import dev.icode.mobile.data.workspace.ICodeStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorkRulesTest {
    private val empty = ICodeStateHolder.emptySnapshot()

    private fun task(id: Long, done: Long = 0, total: Long? = null, item: String? = null, cancellable: Boolean = false, hidden: Boolean = false, failure: String? = null) =
        WorkTask(id, "Deleting…", "node_modules", item, done, total, cancellable, hidden, failure)

    @Test fun `nothing running is nothing to carry`() {
        assertTrue(BackgroundWorkRules.of(empty).isEmpty())
    }

    @Test fun `a task says its counts, its item, and the Cancel it allows`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(12, 1207, 11813, "node_modules/a/b.js", cancellable = true)))).single()
        assertEquals("task:12", item.key)
        assertEquals("Deleting… (1207 / 11813)", item.heading)
        assertEquals("node_modules/a/b.js", item.detail)
        assertEquals(ICodeIntent.CancelTask(12), item.cancel)
        assertEquals(BackgroundPlace.Task(12), item.place)
    }

    @Test fun `while counting it says so, and a delete has no Cancel, even hidden`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(3, hidden = true)))).single()
        assertEquals("Deleting…", item.heading)
        assertEquals("Counting node_modules", item.detail)
        assertNull(item.fraction)
        assertNull(item.cancel)
    }

    @Test fun `a failed task is not carried, its sheet and its notice saying why`() {
        assertTrue(BackgroundWorkRules.of(empty.copy(tasks = listOf(task(4, failure = "Permission denied")))).isEmpty())
    }

    @Test fun `a clone is carried with Git's phase and share, and opens its own page`() {
        val snapshot = empty.copy(
            cloneName = "icode",
            gitOperation = GitOperationState.Running(GitTransferLabel.CLONING, GitProgress("Receiving objects", 0.45f)),
        )
        val item = BackgroundWorkRules.of(snapshot).single()
        assertEquals("Cloning… 45%", item.heading)
        assertEquals("icode · Receiving objects", item.detail)
        assertEquals(ICodeIntent.CancelGitTransfer, item.cancel)
        assertEquals(BackgroundPlace.Clone, item.place)
    }

    @Test fun `a pull opens the sync page, and a finished transfer is not carried`() {
        val pulling = empty.copy(activeWorkspace = "app", gitOperation = GitOperationState.Running(GitTransferLabel.PULLING))
        assertEquals(BackgroundPlace.Sync, BackgroundWorkRules.of(pulling).single().place)
        val done = empty.copy(gitOperation = GitOperationState.Succeeded(GitTransferLabel.PULLING))
        assertTrue(BackgroundWorkRules.of(done).isEmpty())
        val failed = empty.copy(gitOperation = GitOperationState.Failed(GitTransferLabel.PULLING, "rejected"))
        assertTrue("a transfer that stopped short is told, not carried", BackgroundWorkRules.of(failed).isEmpty())
        val installingGit = empty.copy(gitOperation = GitOperationState.Running("Installing Git"))
        assertTrue("installing Git is the tools' run, carried as theirs", BackgroundWorkRules.of(installingGit).isEmpty())
    }

    @Test fun `the runtime install is carried, not cancellable`() {
        val snapshot = empty.copy(
            runtime = RuntimeState.Downloading(50, 100, "Ubuntu base"),
            runtimeInstallReason = RuntimeInstallReason.Repair,
        )
        val item = BackgroundWorkRules.of(snapshot).single()
        assertEquals("runtime", item.key)
        assertEquals("Repairing the Linux runtime 50%", item.heading)
        assertEquals(BackgroundPlace.Runtime, item.place)
        assertNull(item.cancel)

        val extracting = snapshot.copy(runtime = RuntimeState.Installing("Extracting Ubuntu base", RuntimeInstallStep.Extracting, 1024, 4096))
        val counted = BackgroundWorkRules.of(extracting).single()
        assertEquals("Repairing the Linux runtime (1024 / 4096)", counted.heading)
        assertEquals(0.25f, counted.fraction)
        val configuring = snapshot.copy(runtime = RuntimeState.Installing("Configuring the runtime", RuntimeInstallStep.Configuring))
        assertEquals("Repairing the Linux runtime", BackgroundWorkRules.of(configuring).single().heading)
        assertTrue(BackgroundWorkRules.of(snapshot.copy(runtime = RuntimeState.Failed("No space left"))).isEmpty())
    }

    @Test fun `a tools install is carried with apt's step and count, cancellable only while downloading`() {
        val downloading = RuntimeToolWork(
            RuntimeToolAction.Install,
            listOf(RuntimeTools.SshClient),
            AptProgress(phase = AptPhase.Downloading, total = 6, downloadSize = "1238 kB", item = "libcbor0.10"),
        )
        val item = BackgroundWorkRules.of(empty.copy(runtimeTools = RuntimeToolsState(work = downloading))).single()
        assertEquals("runtime-tools", item.key)
        assertEquals("Installing SSH client", item.heading)
        assertEquals("Downloading 1238 kB · libcbor0.10", item.detail)
        assertEquals(ICodeIntent.CancelRuntimeTools, item.cancel)
        assertEquals(BackgroundPlace.RuntimeTools, item.place)

        val installing = downloading.copy(progress = AptProgress(phase = AptPhase.Installing, total = 6, done = 3, item = "libfido2-1"))
        val counted = BackgroundWorkRules.of(empty.copy(runtimeTools = RuntimeToolsState(work = installing))).single()
        assertEquals("Installing SSH client (3 / 6)", counted.heading)
        assertEquals(0.5f, counted.fraction)
        assertEquals(null, counted.cancel)

        val failed = installing.copy(failure = "Could not install SSH client: E: Unable to fetch")
        assertTrue(BackgroundWorkRules.of(empty.copy(runtimeTools = RuntimeToolsState(work = failed))).isEmpty())
    }

    @Test fun `a place survives the notification that carries it`() {
        listOf(
            BackgroundPlace.Task(7), BackgroundPlace.Clone, BackgroundPlace.Sync, BackgroundPlace.Runtime, BackgroundPlace.RuntimeTools,
            BackgroundPlace.Assistant, BackgroundPlace.AssistantChat("/w/app@2", "s1"), BackgroundPlace.AssistantChat(null, "s2"),
            BackgroundPlace.Processes, BackgroundPlace.Gpg,
        ).forEach {
            assertEquals(it, BackgroundPlace.decode(it.encoded()))
        }
        assertNull(BackgroundPlace.decode("task:x"))
        assertNull(BackgroundPlace.decode("chat:"))
        assertNull(BackgroundPlace.decode(null))
    }

    @Test fun `each chat at work says its step, stops on its own Stop, opens its chat, and carries its own question`() {
        val turn = AssistantTurn("/w/app", "app", "s1", "Run `npm test`")
        val working = empty.copy(assistant = AssistantState(turns = listOf(turn)))
        val item = BackgroundWorkRules.of(working).single()
        assertEquals("assistant:s1", item.key)
        assertEquals("Assistant is working…", item.heading)
        assertEquals("app · Run `npm test`", item.detail)
        assertEquals(ICodeIntent.StopAssistant("/w/app", "s1"), item.cancel)
        assertEquals("Stop", item.cancelLabel)
        assertEquals(BackgroundPlace.AssistantChat("/w/app", "s1"), item.place)
        assertNull(item.question)

        val call = AssistantCall("c1", "run_command", """{"command":"npm test"}""", "Run `npm test`", AssistantToolKind.Command)
        val home = AssistantTurn(null, "Home", "s2", "Thinking…")
        val asking = empty.copy(assistant = AssistantState(turns = listOf(turn.copy(approval = AssistantApproval(call, "npm test")), home)))
        val items = BackgroundWorkRules.of(asking)
        assertEquals(listOf("assistant:s1", "assistant:s2"), items.map { it.key })
        assertEquals(listOf("Run `npm test`?", null), items.map { it.question })
        assertEquals(ICodeIntent.StopAssistant(null, "s2"), items[1].cancel)
        assertTrue(BackgroundWorkRules.of(empty.copy(assistant = AssistantState())).isEmpty())
    }

    @Test fun `running processes are one item that keeps them, with no progress, whose Stop All stops them all`() {
        fun process(id: String, name: String, state: ManagedProcessState, ports: List<Int> = emptyList()) =
            ManagedProcess(id, "/w/app", name, "$name serve", "/workspace/app", 1L, state, ports)
        val ended = process("p1", "pytest", ManagedProcessState.Ended(0, 2L, stopped = false))
        assertTrue(BackgroundWorkRules.of(empty.copy(processes = listOf(ended))).isEmpty())
        val running = empty.copy(processes = listOf(ended, process("p2", "uvicorn", ManagedProcessState.Running, listOf(8000)), process("p3", "vite", ManagedProcessState.Running)))
        val item = BackgroundWorkRules.of(running).single()
        assertEquals("2 processes running", item.title)
        assertEquals("uvicorn :8000 · vite", item.detail)
        assertEquals(ICodeIntent.StopAllProcesses, item.cancel)
        assertEquals("Stop All", item.cancelLabel)
        assertFalse("a server runs until it is stopped: no bar", item.progresses)
        assertEquals(BackgroundPlace.Processes, item.place)
        assertEquals(BackgroundPlace.Processes, BackgroundPlace.decode(item.place.encoded()))
    }

    @Test fun `gpg's question is carried in its own words, and Cancel answers it`() {
        // What gpg-agent 2.4.4 asked to unlock a key, as the pinentry received it.
        val prompt = GpgPrompt(
            id = "941",
            asksPassphrase = true,
            title = null,
            description = "Please enter the passphrase to unlock the OpenPGP secret key:\n\"Ada Lovelace <ada@example.com>\"\n" +
                "255-bit EDDSA key, ID BFDDD2AF39D6382D,\ncreated 2026-10-18.\n",
            prompt = "Passphrase:",
            error = null,
            ok = null,
            cancel = null,
        )
        val item = BackgroundWorkRules.of(empty.copy(gpgPrompts = listOf(prompt))).single()
        assertEquals("gpg", item.key)
        assertEquals("GnuPG is waiting…", item.heading)
        assertEquals(
            "Please enter the passphrase to unlock the OpenPGP secret key: \"Ada Lovelace <ada@example.com>\" " +
                "255-bit EDDSA key, ID BFDDD2AF39D6382D, created 2026-10-18.",
            item.detail,
        )
        assertEquals("Enter your GnuPG passphrase", item.question)
        assertEquals(GpgIntent.Answer("941", GpgAnswer.Cancel), item.cancel)
        assertFalse(item.progresses)
        assertEquals(BackgroundPlace.Gpg, item.place)
        val confirm = prompt.copy(asksPassphrase = false)
        assertEquals("Answer GnuPG", BackgroundWorkRules.of(empty.copy(gpgPrompts = listOf(confirm))).single().question)
    }
}
