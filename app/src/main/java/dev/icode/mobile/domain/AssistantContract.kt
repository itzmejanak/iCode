package dev.icode.mobile.domain

// The assistant (docs/assistant-koog-plan.md): iCode's own chat beside the workspace. These are the
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

/** How much a model reasons before it answers, in the words its provider uses (§12 R3). */
enum class AssistantEffort(val label: String) {
    Minimal("Minimal"),
    Low("Low"),
    Medium("Medium"),
    High("High"),
    Max("Max"),

    /** A model that thinks or does not (Ollama): no level between. */
    On("On"),
}

/** What a turn may do (§12 R8): implement, plan, answer, or orchestrate. */
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
    /** Koog knows it, so these are Koog's facts; otherwise they are what the user said (§13 P4). */
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

/** A provider the user added (§13 P1): never its key, only whether one is saved. */
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

/** The chat's model: one of a provider's. */
data class AssistantChoice(val provider: String, val model: String)

/** What the user set for the assistant; never a key. */
data class AssistantSettings(
    val providers: List<AssistantProvider> = emptyList(),
    /** Null until a provider has a model. */
    val choice: AssistantChoice? = null,
    /** What runs without asking, every chat's to start with (docs/assistant-permissions-plan.md §4.1). */
    val permission: AssistantPermission = AssistantPermission.AskFirst,
    /** Null: the provider's default ("Auto"). */
    val effort: AssistantEffort? = null,
    val mode: AssistantMode = AssistantMode.Code,
    /** The model that writes commit messages (Settings › Source Control); null: the chat's. */
    val commitChoice: AssistantChoice? = null,
) {
    val provider: AssistantProvider? get() = providers.firstOrNull { it.id == choice?.provider }
    val model: AssistantModel? get() = provider?.models?.firstOrNull { it.id == choice?.model }

    /** What writes commit messages: [commitChoice] while its provider lists its model, else the chat's model. */
    val commitWriter: Pair<AssistantProvider, AssistantModel>?
        get() = chosen(commitChoice) ?: chosen(choice)

    private fun chosen(choice: AssistantChoice?): Pair<AssistantProvider, AssistantModel>? {
        val provider = providers.firstOrNull { it.id == choice?.provider } ?: return null
        return provider.models.firstOrNull { it.id == choice?.model }?.let { provider to it }
    }

    /** A model is chosen: the chat can be used. */
    val ready: Boolean get() = model != null
}

/** A provider's models as its server lists them (§13 P2, P5), for its settings page. */
data class AssistantAvailable(
    /** The provider being edited: its ID, or "" for one being added. */
    val provider: String,
    val loading: Boolean = true,
    val models: List<AssistantModel> = emptyList(),
    /** Why the server could not be asked, in the words the page shows. */
    val failure: String? = null,
)

/** What a file sent with a message is to the model (§13 P7). */
enum class AssistantAttachmentKind { Text, Image, Document }

/** A file sent with a message, as it was then (§12 R6, §13 P7). */
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
    data class User(val text: String, val attachments: List<AssistantAttachment> = emptyList()) : AssistantMessage

    /**
     * What the model said, and the tools it asked for. [reasoning] is what it thought first, where
     * its provider sends that: shown on the page, never sent back to the model.
     */
    data class Reply(val text: String, val calls: List<AssistantCall> = emptyList(), val reasoning: String = "") : AssistantMessage

    /** The answers to the calls of the reply right before it. */
    data class Results(val results: List<AssistantResult>) : AssistantMessage

    /**
     * The chat before this point, summarized by the model to fit its context (§12 R4): later turns
     * send this summary in place of what it covers; the page still shows everything.
     */
    data class Compacted(val summary: String) : AssistantMessage
}

/** A call waiting for the user: a change, a command, or a reach outside the workspace. */
data class AssistantApproval(
    val call: AssistantCall,
    /** What it will do, in full enough to judge: the command, or the file and the text it replaces. */
    val detail: String,
    /** What it reaches outside the chat's workspace; null when it asks as a change or a command only. */
    val reach: AssistantReach? = null,
    /** Asked each time, in every mode (docs/agent-control-plan.md §3 A9): no "Allow in". */
    val always: Boolean = false,
)

/**
 * A question a turn asks between its steps, about no call (docs/phone-agent-plan.md L5, §5.5): whether
 * it goes on past its step limit, or, on the phone, a step only the user can take. Continue goes on;
 * Stop ends the turn.
 */
data class AssistantPause(val kind: Kind, /** What the card says under its question. */ val detail: String) {
    enum class Kind(val question: String, val proceed: String) {
        StepLimit("Keep going?", "Continue"),
        HandOver("Your turn", "Done"),
    }
}

/**
 * The user's answer to an approval. [AllowInChat] opens, for this chat until iCode closes, the
 * [AssistantReach.roots] of an outside call, or the chat's workspace (Auto in workspace) for a
 * change or a command (docs/assistant-permissions-plan.md §4.3).
 */
enum class AssistantAnswer { Allow, AllowInChat, Deny }

/** The turn in hand: one at a time, in the chat it started in. */
data class AssistantTurn(
    /** Its workspace's location. */
    val workspace: String,
    val workspaceName: String,
    val session: String,
    /** "Thinking…", "Read src/App.kt", "Waiting for your answer", "Compacting the chat…". */
    val step: String,
    /** The reply being written, as far as it has come; null between replies, and where replies come whole. */
    val live: AssistantLive? = null,
    /** It has acted on the phone: the screen's edges glow while it works (docs/phone-agent-plan.md §5.6). */
    val onPhone: Boolean = false,
    /** Read aloud, as Spoken replies said when it was sent (§5.8): its instructions ask for a short spoken answer. */
    val aloud: Boolean = false,
    /** The terminal tab the running call's command runs in: its step shows it live while it runs (docs/agent-control-plan.md §6). */
    val terminal: AssistantTerminal? = null,
    /** One of its tools has changed a file: said when it ends ([AssistantTurnEnd]). */
    val changedFiles: Boolean = false,
)

/** A streamed reply as far as it has come: what the model thought, and its answer so far. */
data class AssistantLive(val reasoning: String, val text: String)

/** A turn that ended (docs/dock-navigation-plan.md D8): its workspace, how many have ended so far, whether its tools changed a file. */
data class AssistantTurnEnd(val workspace: String, val number: Int, val changedFiles: Boolean)

/** One chat of a workspace, as its history lists it (§12 R5). */
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
    /** The place whose chats are shown — a workspace's location; null until the page opens. */
    val workspace: String? = null,
    /** That place's chats, newest first. */
    val sessions: List<AssistantSession> = emptyList(),
    /** The chat shown; null for a new one, not yet written to. */
    val session: String? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /** Tokens the last request of this chat used; with [AssistantModel.contextWindow], how full it is. */
    val contextTokens: Long? = null,
    val turn: AssistantTurn? = null,
    /** The last turn to end, in any chat; null until one has. */
    val ended: AssistantTurnEnd? = null,
    val approval: AssistantApproval? = null,
    /** The turn waits between steps for the user to go on (docs/phone-agent-plan.md L5). */
    val pause: AssistantPause? = null,
    /** The model's questions, waiting for the user's reply (docs/assistant-questions-plan.md). */
    val clarification: AssistantClarification? = null,
    /** The chat shown answered "Allow in" its workspace: it runs as Auto in workspace until iCode closes. */
    val allowedInWorkspace: Boolean = false,
    /** The chat shown's own terminal tab, once the menu's Terminal opened it. */
    val tab: String? = null,
    /** Why the last turn stopped short; the next one clears it. */
    val failure: String? = null,
    /** A commit message being written, or written and not yet in the commit field. */
    val commitMessage: AssistantCommitMessage? = null,
) {
    val running: Boolean get() = turn != null

    /** The turn in hand has ended: what it waited on goes with it, and its end is said in [ended]. */
    fun turnEnded(): AssistantState = copy(
        turn = null, approval = null, pause = null, clarification = null,
        ended = turn?.let { AssistantTurnEnd(it.workspace, (ended?.number ?: 0) + 1, it.changedFiles) } ?: ended,
    )

    /** The turn waits for the user: an approval, a pause, or the model's questions. */
    val asking: Boolean get() = turn != null && (approval != null || pause != null || clarification != null)

    /** The chat shown's mode: the setting, raised by its own answers. */
    val permission: AssistantPermission get() = AssistantPermissions.effective(settings.permission, allowedInWorkspace)

    /** The turn in hand belongs to the chat shown, not to another one. */
    val runningHere: Boolean get() = turn != null && turn.workspace == workspace && turn.session == session

    /** The turn of the chat shown waits on its questions: a message sent now is the reply (docs/assistant-questions-plan.md D5). */
    val replying: Boolean get() = runningHere && clarification != null
}

/**
 * A commit message the model writes from [workspace]'s staged changes
 * (docs/commit-message-generator-plan.md M6): being written, written, or why it could not be.
 */
data class AssistantCommitMessage(
    val workspace: String,
    val message: String? = null,
    val failure: String? = null,
) {
    val writing: Boolean get() = message == null && failure == null
}
