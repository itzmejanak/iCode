package dev.icode.mobile.domain

// The assistant (docs/assistant-and-mcp.md): iCode's own chat beside the workspace. These are the
// only assistant types screens see; the engine behind them (Koog) stays inside data/assistant/koog.

/** Where the assistant's model is served from: one of Koog's clients, or any OpenAI-compatible server. */
enum class AssistantProviderKind(
    val label: String,
    /** A request without an API key is refused by this provider. */
    val needsKey: Boolean,
    /** The form asks for the server's address. */
    val asksBaseUrl: Boolean,
    /** What the address field starts with; null where the user must type it. */
    val defaultBaseUrl: String? = null,
) {
    OpenAI("OpenAI", needsKey = true, asksBaseUrl = false),
    Anthropic("Anthropic", needsKey = true, asksBaseUrl = false),
    Google("Google Gemini", needsKey = true, asksBaseUrl = false),
    OpenRouter("OpenRouter", needsKey = true, asksBaseUrl = false),

    /** An Ollama server: in the runtime on this phone by default. */
    Ollama("Ollama", needsKey = false, asksBaseUrl = true, defaultBaseUrl = "http://127.0.0.1:11434"),

    /** DeepSeek, Groq, LM Studio, vLLM…: the address up to and including `/v1`. */
    OpenAICompatible("OpenAI-compatible", needsKey = false, asksBaseUrl = true),
}

/** How much a model reasons before it answers, in the words its provider uses. */
enum class AssistantEffort(val label: String) {
    Minimal("Minimal"),
    Low("Low"),
    Medium("Medium"),
    High("High"),
    Max("Max"),

    /** A model that thinks or does not (Ollama): no level between. */
    On("On"),
}

/** What a turn may do: implement, plan, answer, or orchestrate. */
enum class AssistantMode(val label: String, val description: String) {
    Code("Code", "Reads, changes files and runs commands"),
    Plan("Plan", "Reads and analyzes; plans changes without making them"),
    Ask("Ask", "Reads and answers; changes nothing"),
    Orchestrator("Orchestrator", "Plans, implements and verifies step by step"),
}

/** A model as the assistant knows it: its limits, its efforts, and what it reads besides text. */
data class AssistantModel(
    val id: String,
    /** Tokens it reads at most; null when neither Koog nor the user knows. */
    val contextWindow: Long? = null,
    /** Tokens it writes at most in one reply; null when unknown. */
    val maxOutput: Long? = null,
    /** Empty: no choice to offer. */
    val efforts: List<AssistantEffort> = emptyList(),
    /** It reasons before it answers; it reads images, and documents (PDF), sent with a message. Koog's word, or the user's. */
    val reasons: Boolean = false,
    val images: Boolean = false,
    val documents: Boolean = false,
    /** Koog knows it, so these are Koog's facts; otherwise they are what the user said. */
    val known: Boolean = false,
) {
    /** What a provider keeps of it: its ID, and for a model Koog does not know, what the user said of it. */
    val spec: AssistantModelSpec
        get() = if (known) AssistantModelSpec(id) else AssistantModelSpec(id, contextWindow, maxOutput, images, reasons)
}

/** A model as a provider keeps it: its ID, and what the user says of one Koog does not know. */
data class AssistantModelSpec(
    val id: String,
    val contextWindow: Long? = null,
    val maxOutput: Long? = null,
    val images: Boolean = false,
    /** It reasons before it answers: its provider's efforts are offered. */
    val reasoning: Boolean = false,
)

/** A provider the user added: never its key, only whether one is saved. */
data class AssistantProvider(
    /** iCode's own. */
    val id: String,
    val kind: AssistantProviderKind,
    /** As it is listed: its kind's label unless the user named it ("DeepSeek"). */
    val name: String,
    /** Asked only where [AssistantProviderKind.asksBaseUrl]; null otherwise. */
    val baseUrl: String?,
    val hasKey: Boolean,
    /** The models it offers the chat, in the user's order, as the engine knows them. */
    val models: List<AssistantModel>,
)

/** What the user set for the assistant; never a key. */
data class AssistantSettings(
    val providers: List<AssistantProvider> = emptyList(),
    /** Null until a provider has a model. */
    val choice: AssistantChoice? = null,
    /** What runs without asking, every chat's to start with (docs/assistant-and-mcp.md). */
    val permission: AssistantPermission = AssistantPermission.AskFirst,
    /** Null: the provider's default ("Auto"). */
    val effort: AssistantEffort? = null,
    val mode: AssistantMode = AssistantMode.Code,
    /** The model that writes commit messages (Settings › Source Control); null: the chat's. */
    val commitChoice: AssistantChoice? = null,
) {
    val provider: AssistantProvider? get() = providerOf(choice)
    val model: AssistantModel? get() = modelOf(choice)

    fun providerOf(choice: AssistantChoice?): AssistantProvider? = providers.firstOrNull { it.id == choice?.provider }

    /** [choice]'s model, while its provider still offers it. */
    fun modelOf(choice: AssistantChoice?): AssistantModel? = providerOf(choice)?.models?.firstOrNull { it.id == choice?.model }

    /** What writes commit messages: [commitChoice] while its provider lists its model, else the chat's model. */
    val commitWriter: Pair<AssistantProvider, AssistantModel>?
        get() = chosen(commitChoice) ?: chosen(choice)

    private fun chosen(choice: AssistantChoice?): Pair<AssistantProvider, AssistantModel>? {
        val provider = providerOf(choice) ?: return null
        return modelOf(choice)?.let { provider to it }
    }

    /** A model is chosen: the chat can be used. */
    val ready: Boolean get() = model != null
}

/** A provider's models as its server lists them, for its settings page. */
data class AssistantAvailable(
    /** The provider being edited: its ID, or "" for one being added. */
    val provider: String,
    val loading: Boolean = true,
    val models: List<AssistantModel> = emptyList(),
    /** Why the server could not be asked, in the words the page shows. */
    val failure: String? = null,
)

/** What a file sent with a message is to the model. */
enum class AssistantAttachmentKind { Text, Image, Document }

/** A file sent with a message, as it was then. */
data class AssistantAttachment(
    /** "src/App.kt" for a workspace's file; its own name for one from the phone. */
    val name: String,
    val kind: AssistantAttachmentKind = AssistantAttachmentKind.Text,
    /** A text file's text, bounded. */
    val text: String = "",
    /** An image's or a document's bytes, kept in its chat's folder; null for text. */
    val file: String? = null,
    val mimeType: String = "text/plain",
    /** A file of the chat's workspace, [name] its path there: a tap on it opens it in the editor. */
    val inWorkspace: Boolean = false,
)

/** The conversation as the model sees it; the page is drawn from it ([AssistantRules.entries]). */
sealed interface AssistantMessage {
    data class User(
        val text: String,
        val attachments: List<AssistantAttachment> = emptyList(),
        /** The checkpoint of its place's files before it was answered, which Undo goes back to; null where none was kept. */
        val checkpoint: String? = null,
    ) : AssistantMessage

    /**
     * What the model said, and the tools it asked for. [reasoning] is what it thought first, where
     * its provider sends that: shown on the page, never sent back to the model. [by] is who wrote
     * it; null in a chat saved before replies kept that.
     */
    data class Reply(
        val text: String,
        val calls: List<AssistantCall> = emptyList(),
        val reasoning: String = "",
        val by: AssistantAnswerer? = null,
        /** What an agent said the reply cost, as it said it: "0.04 USD". */
        val cost: String? = null,
    ) : AssistantMessage

    /** The answers to the calls of the reply right before it. */
    data class Results(val results: List<AssistantResult>) : AssistantMessage

    /**
     * The chat before this point, summarized by the model to fit its context: later turns
     * send this summary in place of what it covers; the page still shows everything.
     */
    data class Compacted(val summary: String) : AssistantMessage
}

/** One chat of a workspace, as its history lists it. */
data class AssistantSession(val id: String, val title: String, val updatedAt: Long)

/** The assistant as the screens show it. */
data class AssistantState(
    val settings: AssistantSettings = AssistantSettings(),
    /** Koog's catalogue per provider: the models that chat and call tools, with their limits. */
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>> = emptyMap(),
    /** The provider its settings page shows: its ID, "" for one being added; null when none is. */
    val editing: String? = null,
    /** The kind chosen for a provider being added, from the sheet of kinds. */
    val adding: AssistantProviderKind? = null,
    /** The models its server lists, once asked. */
    val available: AssistantAvailable? = null,
    /** The page has opened [workspace]'s chats, newest first in [sessions]: a workspace's location, or null for the chats in none. */
    val opened: Boolean = false,
    val workspace: String? = null,
    val sessions: List<AssistantSession> = emptyList(),
    /** The chat shown; null for a new one, not yet written to. */
    val session: String? = null,
    /** Who answers the chat shown, as it last chose; null for a new one, which starts with [AssistantSettings.choice]. */
    val answerer: AssistantAnswerer? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /** The chat shown's waiting messages, oldest first (docs/assistant-and-mcp.md). */
    val queued: List<AssistantQueued> = emptyList(),
    /** Tokens the last request of this chat used; with [AssistantModel.contextWindow], how full it is. */
    val contextTokens: Long? = null,
    /** Every chat's turn in hand, in the order they began: chats work at once (docs/assistant-and-mcp.md). */
    val turns: List<AssistantTurn> = emptyList(),
    /** The last turn to end, in any chat; null until one has. */
    val ended: AssistantTurnEnd? = null,
    /** The chat shown answered "Allow in" its workspace: it runs as Auto in workspace until iCode closes. */
    val allowedInWorkspace: Boolean = false,
    /** The chat shown's own terminal tab, once the menu's Terminal opened it. */
    val tab: String? = null,
    /** Why the last turn stopped short; the next one clears it. */
    val failure: String? = null,
    /** An Undo waiting for the user: files changed outside the chat's work since, to be undone too. */
    val undo: AssistantUndo? = null,
    /** The message an Undo of the chat took out, for the composer to take into its draft. */
    val takenBack: String? = null,
    /** The agents that ask to be signed in before a chat answered by one can send, with how (docs/assistant-and-mcp.md). */
    val signIns: Map<String, List<AgentSignIn>> = emptyMap(),
    /** Untested agents already said so in a chat: each is said once (owner's choice, 2026-11-18). */
    val untestedSaid: Set<String> = emptySet(),
    /** The coding agent chosen last, which new chats start with; null once a model is. */
    val agent: String? = null,
    /** iCode was closed while the chat shown worked: it says so, and offers to continue (docs/assistant-and-mcp.md). */
    val interrupted: Boolean = false,
    /** What the agent answering the chat shown offers it — settings and commands — once it has started for it. */
    val agentChat: AgentChat? = null,
    /** The agents' conversations last looked for, in the chats' list until the place shown changes. */
    val agentFinds: AgentFinds? = null,
    /** A commit message being written, or written and not yet in the commit field. */
    val commitMessage: AssistantCommitMessage? = null,
) {
    /** The chat shown's turn, while it works; what it waits on is [approval], [pause] and [clarification]. */
    val turn: AssistantTurn? get() = turnOf(workspace, session)
    val approval: AssistantApproval? get() = turn?.approval
    val pause: AssistantPause? get() = turn?.pause
    val clarification: AssistantClarification? get() = turn?.clarification

    /** Some chat works. */
    val running: Boolean get() = turns.isNotEmpty()

    /**
     * The turn the bubble follows, and stops: one waiting for the user first, then the one on the
     * phone, then the chat shown's, then the last to begin.
     */
    val focus: AssistantTurn? get() = turns.firstOrNull { it.asking } ?: turns.firstOrNull { it.onPhone } ?: turn ?: turns.lastOrNull()

    /** The model the chat shown answers with: its own while its provider still offers it, else what a new chat starts with. */
    val choice: AssistantChoice? get() = (answerer as? AssistantChoice)?.takeIf { settings.modelOf(it) != null } ?: settings.choice

    /** Who answers the chat shown: its own, or for a new one the agent chosen last, else the model. */
    val answering: AssistantAnswerer? get() = answerer ?: agent?.let(::AssistantAgent) ?: choice
    val model: AssistantModel? get() = settings.modelOf(choice)

    /** [session]'s turn in [workspace]; null while that chat does not work. */
    fun turnOf(workspace: String?, session: String?): AssistantTurn? = turns.firstOrNull { it.of(workspace, session) }

    /** [session]'s turn in [workspace], changed; the state as it is when that chat does not work. */
    fun changeTurn(workspace: String?, session: String, change: (AssistantTurn) -> AssistantTurn): AssistantState =
        copy(turns = turns.map { if (it.of(workspace, session)) change(it) else it })

    /** [session]'s turn in [workspace] ended: what it waited on goes with it, and its end is said in [ended]. */
    fun turnEnded(workspace: String?, session: String): AssistantState {
        val gone = turnOf(workspace, session) ?: return this
        return copy(turns = turns.filterNot { it === gone }, ended = AssistantTurnEnd(gone.workspace, (ended?.number ?: 0) + 1, gone.changedFiles))
    }

    /** Some chat's turn waits for the user: an approval, a pause, or the model's questions. */
    val asking: Boolean get() = turns.any { it.asking }

    /** The chat shown's mode: the setting, raised by its own answers. */
    val permission: AssistantPermission get() = AssistantPermissions.effective(settings.permission, allowedInWorkspace)

    /** The chat shown is working. */
    val runningHere: Boolean get() = turn != null

    /** The turn of the chat shown waits on its questions: a message sent now is the reply (docs/assistant-and-mcp.md). */
    val replying: Boolean get() = clarification != null
}
