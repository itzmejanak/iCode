package dev.icode.mobile.domain

// Background work (docs/runtime-and-terminal.md): everything iCode is doing that must outlive
// its screen, as the foreground service keeps it alive and its notifications carry it.

/** Where iCode shows a piece of background work: what a tap on its notification opens. */
sealed interface BackgroundPlace {
    /** The task's own sheet. */
    data class Task(val id: Long) : BackgroundPlace

    /** Clone progress. */
    data object Clone : BackgroundPlace

    /** Fetch, pull or push progress. */
    data object Sync : BackgroundPlace

    /** The runtime install, repair or reset. */
    data object Runtime : BackgroundPlace

    /** The tools' page: a tools install or removal, wherever it was asked for. */
    data object RuntimeTools : BackgroundPlace

    /** The Assistant page: its turn in hand. */
    data object Assistant : BackgroundPlace

    /** The Assistant page, where a workspace's managed processes are listed. */
    data object Processes : BackgroundPlace

    /** gpg's question: its GnuPG sheet shows over whatever screen is open. */
    data object Gpg : BackgroundPlace

    /** As a notification carries it: `task:12`, `clone`, `sync`, `runtime`, `runtime-tools`, `assistant`, `processes`, `gpg`. */
    fun encoded(): String = when (this) {
        is Task -> "$TASK$id"
        Clone -> "clone"
        Sync -> "sync"
        Runtime -> "runtime"
        RuntimeTools -> "runtime-tools"
        Assistant -> "assistant"
        Processes -> "processes"
        Gpg -> "gpg"
    }

    companion object {
        private const val TASK = "task:"

        /** Null for anything [encoded] does not write. */
        fun decode(value: String?): BackgroundPlace? = when {
            value == null -> null
            value.startsWith(TASK) -> value.removePrefix(TASK).toLongOrNull()?.let(::Task)
            value == "clone" -> Clone
            value == "sync" -> Sync
            value == "runtime" -> Runtime
            value == "runtime-tools" -> RuntimeTools
            value == "assistant" -> Assistant
            value == "processes" -> Processes
            value == "gpg" -> Gpg
            else -> null
        }
    }
}

/** One piece of background work, as its notification says it. */
data class BackgroundItem(
    /** Stable while the work lasts: `task:12`, `git`, `runtime`, `runtime-tools`. */
    val key: String,
    /** "Deleting…", "Cloning…", "Installing the Linux runtime". */
    val title: String,
    /** The item or step in hand: "node_modules/is-callable/.editorconfig", "icode · Receiving objects". */
    val detail: String,
    /** "1207 / 11813" when the work counts items. */
    val counts: String? = null,
    /** 0..1 when known; null while counting or when nothing is measured. */
    val fraction: Float? = null,
    /** What Cancel sends; null where stopping would leave things worse than finishing. */
    val cancel: ICodeIntent? = null,
    val place: BackgroundPlace,
    /** What it waits on the user for ("Run `npm test`?"); it pops up while iCode is out of view. */
    val question: String? = null,
    /** False for what runs until it is stopped — a server — which has no progress to show. */
    val progresses: Boolean = true,
    /** What [cancel]'s button says. */
    val cancelLabel: String = "Cancel",
) {
    /** The notification's title: "Deleting… (1207 / 11813)", "Cloning… 45%", or the title alone. */
    val heading: String get() = when {
        counts != null -> "$title ($counts)"
        fraction != null -> "$title ${percentOf(fraction)}"
        else -> title
    }
}

object BackgroundWorkRules {
    /**
     * Everything running in [snapshot]: each task, the Git transfer, the runtime install, the tools
     * apt is installing or removing, the assistant's turn, the managed processes that run, and gpg's
     * question waiting on its sheet. What stopped short is not carried: its page says why, and so
     * does its notice (docs/engineering-rules.md).
     */
    fun of(snapshot: ICodeSnapshot): List<BackgroundItem> = buildList {
        snapshot.tasks.filterNot { it.quiet || it.failure != null }.forEach { add(task(it)) }
        git(snapshot)?.let(::add)
        runtime(snapshot)?.let(::add)
        snapshot.runtimeTools.work?.takeIf { it.failure == null }?.let { add(runtimeTools(it)) }
        snapshot.assistant.turn?.let { add(assistant(it, snapshot.assistant, snapshot.mcp.question)) }
        processes(snapshot.processes.filter { it.running })?.let(::add)
        snapshot.gpgPrompts.firstOrNull()?.let { add(gpg(it)) }
    }

    /**
     * gpg-agent asks through the GnuPG sheet, and the command that needed it — a commit, the
     * terminal's — waits: out of view, the question pops up, and Cancel answers it. Its text is
     * gpg's, a paragraph gpg wraps into lines.
     */
    private fun gpg(prompt: GpgPrompt): BackgroundItem = BackgroundItem(
        key = "gpg",
        title = "GnuPG is waiting…",
        detail = prompt.description.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
            .ifEmpty { prompt.prompt.orEmpty() },
        cancel = GpgIntent.Answer(prompt.id, GpgAnswer.Cancel),
        place = BackgroundPlace.Gpg,
        question = if (prompt.asksPassphrase) "Enter your GnuPG passphrase" else "Answer GnuPG",
        progresses = false,
    )

    /**
     * What runs beyond the command that started it — a server, a watcher — kept by the foreground
     * service until it is stopped: no progress, and Stop All. They show on the Assistant page.
     */
    private fun processes(running: List<ManagedProcess>): BackgroundItem? {
        if (running.isEmpty()) return null
        return BackgroundItem(
            key = "processes",
            title = if (running.size == 1) "1 process running" else "${running.size} processes running",
            detail = running.joinToString(" · ") { process -> process.name + process.ports.firstOrNull()?.let { " :$it" }.orEmpty() },
            cancel = ICodeIntent.StopAllProcesses,
            place = BackgroundPlace.Processes,
            progresses = false,
            cancelLabel = "Stop All",
        )
    }

    /**
     * Its progress shows on the Assistant page. A failure is said on the page, where the chat is,
     * so it is not carried here; a question is, as it stops the turn until it is answered. The one
     * notification of a turn, phone control's included: Stop, the assistant's word everywhere.
     */
    private fun assistant(turn: AssistantTurn, state: AssistantState, asked: McpQuestion?) = BackgroundItem(
        key = "assistant",
        title = "Assistant is working…",
        detail = "${turn.workspaceName} · ${turn.step}",
        cancel = ICodeIntent.StopAssistant,
        place = BackgroundPlace.Assistant,
        question = state.approval?.let { "${it.call.summary}?" } ?: state.pause?.kind?.question
            ?: state.clarification?.let { "${AssistantQuestionRules.TITLE}: ${AssistantQuestionRules.asked(it)}" }
            ?: asked?.let { "${it.server} asks: ${it.message.lineSequence().firstOrNull().orEmpty()}" },
        cancelLabel = "Stop",
    )

    /** Its progress shows on the tools' page, and on a button waiting for it. */
    private fun runtimeTools(work: RuntimeToolWork) = BackgroundItem(
        key = "runtime-tools",
        title = work.title,
        detail = listOfNotNull(work.step, work.item).joinToString(" · "),
        counts = work.counts,
        fraction = work.fraction,
        cancel = ICodeIntent.CancelRuntimeTools.takeIf { work.cancellable },
        place = BackgroundPlace.RuntimeTools,
    )

    private fun task(task: WorkTask) = BackgroundItem(
        key = "task:${task.id}",
        title = task.title,
        detail = task.currentItem ?: if (task.total == null) "Counting ${task.subject}" else task.subject,
        counts = task.counts,
        fraction = task.fraction,
        cancel = ICodeIntent.CancelTask(task.id).takeIf { task.cancellable },
        place = BackgroundPlace.Task(task.id),
    )

    private fun git(snapshot: ICodeSnapshot): BackgroundItem? {
        val operation = snapshot.gitOperation as? GitOperationState.Running ?: return null
        val label = operation.label
        // Installing Git is the tools' run, carried as theirs.
        if (label !in GitTransferLabel.ALL) return null
        val clone = label == GitTransferLabel.CLONING
        val subject = if (clone) snapshot.cloneName else snapshot.activeWorkspace
        val progress = operation.progress
        return BackgroundItem(
            key = "git",
            title = "$label…",
            detail = listOfNotNull(subject.ifEmpty { null }, progress?.phase).joinToString(" · ").ifEmpty { label },
            fraction = progress?.fraction,
            cancel = ICodeIntent.CancelGitTransfer,
            place = if (clone) BackgroundPlace.Clone else BackgroundPlace.Sync,
        )
    }

    private fun runtime(snapshot: ICodeSnapshot): BackgroundItem? {
        val title = when (snapshot.runtimeInstallReason) {
            RuntimeInstallReason.Initial -> "Installing the Linux runtime"
            RuntimeInstallReason.Repair -> "Repairing the Linux runtime"
            RuntimeInstallReason.Reset -> "Resetting the Linux runtime"
        }
        return when (val state = snapshot.runtime) {
            is RuntimeState.Downloading -> BackgroundItem(
                "runtime", title, "${state.label} · ${state.transferred}", fraction = state.fraction,
                place = BackgroundPlace.Runtime,
            )
            is RuntimeState.Installing -> BackgroundItem(
                "runtime", title, state.label, counts = state.counts, fraction = state.fraction,
                place = BackgroundPlace.Runtime,
            )
            else -> null
        }
    }
}
