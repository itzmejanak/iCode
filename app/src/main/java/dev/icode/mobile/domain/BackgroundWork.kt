package dev.icode.mobile.domain

// Background work (docs/long-operations-plan.md §6): everything iCode is doing that must outlive
// its screen, as the foreground service keeps it alive and its notifications carry it.

/** Where iCode shows a piece of background work: what a tap on its notification opens. */
sealed interface BackgroundPlace {
    /** The task's own dialog. */
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

    /** gpg's question: its GnuPG dialog shows over whatever screen is open. */
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
    /**
     * Whether iCode shows its progress while iCode is on screen: a task's dialog not hidden, or a
     * page of its own. A failure the user was not watching pops up.
     */
    val watched: Boolean,
    /** Why it stopped short; null while it runs. */
    val failure: String? = null,
    /** What it waits on the user for ("Run `npm test`?"); it pops up while iCode is out of view. */
    val question: String? = null,
    /** False for what runs until it is stopped — a server — which has no progress to show. */
    val progresses: Boolean = true,
    /** What [cancel]'s button says. */
    val cancelLabel: String = "Cancel",
) {
    val running: Boolean get() = failure == null

    /**
     * Stable and small, for what the system keys by number (a notification's id): a task's id below
     * 900 — ids count up in a process, and 900 tasks never run at once — then 900 for the Git
     * transfer, 901 for the runtime, 902 for the runtime tools' apt run, 903 for the assistant's turn,
     * 904 for the managed processes, 905 for gpg's question.
     */
    val number: Int get() = when (place) {
        is BackgroundPlace.Task -> (place.id % 900).toInt()
        BackgroundPlace.Clone, BackgroundPlace.Sync -> 900
        BackgroundPlace.Runtime -> 901
        BackgroundPlace.RuntimeTools -> 902
        BackgroundPlace.Assistant -> 903
        BackgroundPlace.Processes -> 904
        BackgroundPlace.Gpg -> 905
    }

    /** The notification's title: "Deleting… (1207 / 11813)", "Cloning… 45%", or the title alone. */
    val heading: String get() = when {
        counts != null -> "$title ($counts)"
        fraction != null -> "$title ${percentOf(fraction)}"
        else -> title
    }
}

object BackgroundWorkRules {
    /**
     * Everything running in [snapshot], and what stopped short: each task, the Git transfer, the
     * runtime install, the tools apt is installing or removing, the assistant's turn, the
     * managed processes that run, and gpg's question waiting on its dialog.
     */
    fun of(snapshot: ICodeSnapshot): List<BackgroundItem> = buildList {
        snapshot.tasks.filterNot { it.quiet }.forEach { add(task(it)) }
        git(snapshot)?.let(::add)
        runtime(snapshot)?.let(::add)
        snapshot.runtimeTools.work?.let { add(runtimeTools(it)) }
        snapshot.assistant.turn?.let { add(assistant(it, snapshot.assistant.approval)) }
        processes(snapshot.processes.filter { it.running })?.let(::add)
        snapshot.gpgPrompts.firstOrNull()?.let { add(gpg(it)) }
    }

    /**
     * gpg-agent asks through the GnuPG dialog, and the command that needed it — a commit, the
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
        watched = true,
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
            watched = true,
            progresses = false,
            cancelLabel = "Stop All",
        )
    }

    /**
     * Its progress shows on the Assistant page. A failure is said on the page, where the chat is,
     * so it is not carried here; a question is, as it stops the turn until it is answered.
     */
    private fun assistant(turn: AssistantTurn, approval: AssistantApproval?) = BackgroundItem(
        key = "assistant",
        title = "Assistant is working…",
        detail = "${turn.workspaceName} · ${turn.step}",
        cancel = ICodeIntent.StopAssistant,
        place = BackgroundPlace.Assistant,
        watched = true,
        question = approval?.let { "${it.call.summary}?" },
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
        watched = true,
        failure = work.failure,
    )

    private fun task(task: WorkTask) = BackgroundItem(
        key = "task:${task.id}",
        title = task.title,
        detail = task.currentItem ?: if (task.total == null) "Counting ${task.subject}" else task.subject,
        counts = task.counts,
        fraction = task.fraction,
        cancel = ICodeIntent.CancelTask(task.id).takeIf { task.cancellable },
        place = BackgroundPlace.Task(task.id),
        watched = !task.hidden,
        failure = task.failure,
    )

    private fun git(snapshot: ICodeSnapshot): BackgroundItem? {
        val (label, failure) = when (val operation = snapshot.gitOperation) {
            is GitOperationState.Running -> operation.label to null
            is GitOperationState.Failed -> operation.label to operation.reason
            else -> return null
        }
        // Installing Git is the tools' run, carried as theirs.
        if (label !in GitTransferLabel.ALL) return null
        val clone = label == GitTransferLabel.CLONING
        val subject = if (clone) snapshot.cloneName else snapshot.activeWorkspace
        val progress = (snapshot.gitOperation as? GitOperationState.Running)?.progress
        return BackgroundItem(
            key = "git",
            title = "$label…",
            detail = listOfNotNull(subject.ifEmpty { null }, progress?.phase).joinToString(" · ").ifEmpty { label },
            fraction = progress?.fraction,
            cancel = ICodeIntent.CancelGitTransfer,
            place = if (clone) BackgroundPlace.Clone else BackgroundPlace.Sync,
            watched = true,
            failure = failure,
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
                place = BackgroundPlace.Runtime, watched = true,
            )
            is RuntimeState.Installing -> BackgroundItem(
                "runtime", title, state.label, counts = state.counts, fraction = state.fraction,
                place = BackgroundPlace.Runtime, watched = true,
            )
            is RuntimeState.Failed -> BackgroundItem(
                "runtime", title, state.reason, place = BackgroundPlace.Runtime, watched = true, failure = state.reason,
            )
            else -> null
        }
    }
}
