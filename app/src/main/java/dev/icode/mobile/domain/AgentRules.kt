package dev.icode.mobile.domain

/** One row of the composer's "/" menu: a command the agent offers, or one of its settings. */
sealed interface AgentMenuItem {
    data class Command(val command: AgentCommand) : AgentMenuItem
    data class Option(val option: AgentOption) : AgentMenuItem
}

/** How the page shows what a coding agent offers (docs/assistant-and-mcp.md). */
object AgentRules {
    /** The categories the composer shows as chips, in its order: the agent's mode, model and effort. */
    val CHIPS = listOf("mode", "model", "thought_level")

    /** What follows "/" while the draft is one word starting with it; null otherwise: the menu is closed. */
    fun slashQuery(draft: String): String? =
        draft.takeIf { it.startsWith("/") && it.none(Char::isWhitespace) }?.drop(1)

    /** The settings shown as chips: selects in [CHIPS]' categories, in that order. */
    fun chips(options: List<AgentOption>): List<AgentOption.Select> =
        CHIPS.flatMap { category -> options.filterIsInstance<AgentOption.Select>().filter { it.category == category } }

    /**
     * The "/" menu for [query]: the commands whose names start with it, then those that contain it;
     * then the settings not shown as chips whose names do.
     */
    fun menu(query: String, commands: List<AgentCommand>, options: List<AgentOption>): List<AgentMenuItem> {
        val wanted = query.lowercase()
        val starting = commands.filter { it.name.lowercase().startsWith(wanted) }
        val containing = commands.filter { it !in starting && wanted in it.name.lowercase() }
        val shown = chips(options).toSet()
        val settings = options.filter { it !in shown && wanted in it.name.lowercase() }
        return (starting + containing).map(AgentMenuItem::Command) + settings.map(AgentMenuItem::Option)
    }

    /** What a command typed into the draft reads: "/name " for its words to follow. */
    fun typed(command: AgentCommand): String = "/${command.name} "

    /** "2 of 5 done": the plan's progress, as its row says it. */
    fun planProgress(plan: List<AgentPlanItem>): String =
        "${plan.count { it.status == AgentPlanItem.Status.Completed }} of ${plan.size} done"

    /** The step the plan is on: the first in progress, else the first still to do; null when all are done. */
    fun planStep(plan: List<AgentPlanItem>): AgentPlanItem? =
        plan.firstOrNull { it.status == AgentPlanItem.Status.InProgress } ?: plan.firstOrNull { it.status == AgentPlanItem.Status.Pending }

    /**
     * A tool call's tool and kind, from the kind the agent gives it (`read`, `edit`, `execute`…): its
     * row is a read, a change or a command, as the built-in assistant's are; unknown kinds are reads.
     */
    fun toolOf(kind: String?): Pair<AssistantToolName, AssistantToolKind> = when (kind) {
        "read" -> AssistantToolName.ReadFile to AssistantToolKind.Read
        "search" -> AssistantToolName.SearchText to AssistantToolKind.Read
        "fetch" -> AssistantToolName.Fetch to AssistantToolKind.Read
        "think" -> AssistantToolName.Think to AssistantToolKind.Read
        "switch_mode" -> AssistantToolName.SwitchMode to AssistantToolKind.Read
        "edit" -> AssistantToolName.EditFile to AssistantToolKind.Change
        "delete" -> AssistantToolName.DeleteFile to AssistantToolKind.Change
        "move" -> AssistantToolName.MoveFile to AssistantToolKind.Change
        "execute" -> AssistantToolName.RunCommand to AssistantToolKind.Command
        else -> AssistantToolName.AgentTool to AssistantToolKind.Read
    }

    /** What an agent's approval card asks: its call's title, as the agent wrote it. */
    fun approvalQuestion(call: AssistantCall): String = call.summary.ifBlank { "Allow this step?" }

    /** "Claude Agent · Sonnet": the agent and, where it offers one, its model, as the composer's chip names them. */
    fun label(agent: CodingAgent?, options: List<AgentOption>): String {
        val name = agent?.name ?: return "Choose an agent"
        val model = chips(options).firstOrNull { it.category == "model" }?.currentName
        return if (model == null) name else "$name · $model"
    }

    /** The composer's placeholder in an agent's chat. */
    fun placeholder(agent: CodingAgent?): String = agent?.let { "Message ${it.name}" } ?: "Message the agent"

    /** "Installed", "Not installed", "Installing…", as an agent's row says it. */
    fun installLabel(installed: Boolean, installing: Boolean): String = when {
        installing -> "Installing…"
        installed -> "Installed"
        else -> "Not installed"
    }

    /** The variables an agent reads its keys from, as its key sign-ins name them. */
    fun keyNames(status: AgentStatus?): List<String> =
        status?.signIn.orEmpty().filter { it.kind == AgentSignIn.Kind.Keys }.flatMap(AgentSignIn::variables).distinct()

    /** The sign-in its keys are for, by ID; null when it signs in with none. */
    fun keysMethod(status: AgentStatus?): String? = status?.signIn.orEmpty().firstOrNull { it.kind == AgentSignIn.Kind.Keys }?.id

    /** What an agent's process is doing, as its settings say it. */
    fun statusLabel(status: AgentStatus?): String = when (status?.state) {
        null, AgentStatus.State.Off -> "Not running"
        AgentStatus.State.Starting -> "Starting…"
        AgentStatus.State.Ready -> "Running"
        AgentStatus.State.Failed -> "Failed"
    }
}
