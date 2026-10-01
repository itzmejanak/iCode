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

/** What a turn may do: everything, or only read (§12 R8). */
enum class AssistantMode(val label: String, val description: String) {
    Agent("Agent", "Reads, changes files and runs commands"),
    Ask("Ask", "Reads and answers; changes nothing"),
}

/** A model as the assistant knows it: its limits, and the efforts its provider accepts for it. */
data class AssistantModel(
    val id: String,
    /** Tokens it reads at most; null when neither Koog nor the user knows. */
    val contextWindow: Long? = null,
    /** Tokens it writes at most in one reply; null when unknown. */
    val maxOutput: Long? = null,
    /** Empty: no choice to offer. */
    val efforts: List<AssistantEffort> = emptyList(),
)

/** What the user set for the assistant; never the key itself. */
data class AssistantSettings(
    val kind: AssistantProviderKind,
    /** Asked only where [AssistantProviderKind.asksBaseUrl]; null otherwise. */
    val baseUrl: String?,
    val model: String,
    val hasKey: Boolean,
    /** Changes and commands wait for an answer (on by default). */
    val askBeforeChanges: Boolean = true,
    /** Null: the provider's default ("Auto"). */
    val effort: AssistantEffort? = null,
    val mode: AssistantMode = AssistantMode.Agent,
    /** The limits the user set for [model], for one Koog's catalogue does not know. */
    val contextWindow: Long? = null,
    val maxOutput: Long? = null,
) {
    /** "Anthropic · claude-sonnet-4-5". */
    val summary: String get() = "${kind.label} · $model"
}

/** What a tool does to the workspace: reads never ask; changes and commands do. */
enum class AssistantToolKind { Read, Change, Command }

/** One request for a tool, as the model made it. */
data class AssistantCall(
    /** The provider's ID for it, or iCode's for a call that came without one: its result names it. */
    val id: String,
    val tool: String,
    /** Its arguments as the model wrote them: JSON text. */
    val arguments: String,
    /** "Read src/App.kt": what its step says, written when the call arrived. */
    val summary: String,
    val kind: AssistantToolKind,
)

/** A tool's answer, as the model reads it. */
data class AssistantResult(val callId: String, val tool: String, val output: String, val failed: Boolean)

/** A file sent with a message: its path in the workspace and its text as it was then (§12 R6). */
data class AssistantAttachment(val path: String, val content: String)

/** The conversation as the model sees it; the page is drawn from it ([AssistantRules.entries]). */
sealed interface AssistantMessage {
    data class User(val text: String, val attachments: List<AssistantAttachment> = emptyList()) : AssistantMessage

    /** What the model said, and the tools it asked for. */
    data class Reply(val text: String, val calls: List<AssistantCall> = emptyList()) : AssistantMessage

    /** The answers to the calls of the reply right before it. */
    data class Results(val results: List<AssistantResult>) : AssistantMessage

    /**
     * The chat before this point, summarized by the model to fit its context (§12 R4): later turns
     * send this summary in place of what it covers; the page still shows everything.
     */
    data class Compacted(val summary: String) : AssistantMessage
}

/** A change or a command waiting for the user. */
data class AssistantApproval(
    val call: AssistantCall,
    /** What it will do, in full enough to judge: the command, or the file and the text it replaces. */
    val detail: String,
)

enum class AssistantAnswer { Allow, AllowAll, Deny }

/** The turn in hand: one at a time, in the chat it started in. */
data class AssistantTurn(
    val workspace: String,
    val workspaceName: String,
    val session: String,
    /** "Thinking…", "Read src/App.kt", "Waiting for your answer", "Compacting the chat…". */
    val step: String,
)

/** One chat of a workspace, as its history lists it (§12 R5). */
data class AssistantSession(val id: String, val title: String, val updatedAt: Long)

/** The assistant as the screens show it. */
data class AssistantState(
    /** Null until a provider is set: the page explains and offers its settings. */
    val settings: AssistantSettings? = null,
    /** Koog's catalogue per provider: the models that chat and call tools, with their limits. */
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>> = emptyMap(),
    /** The chosen model as known now: its limits and efforts, the user's limits applied. */
    val model: AssistantModel? = null,
    /** Model IDs chosen before with this provider, newest first: the picker lists them first. */
    val recentModels: List<String> = emptyList(),
    /** The workspace whose chats are shown; null until the page opens on one. */
    val workspace: String? = null,
    /** That workspace's chats, newest first. */
    val sessions: List<AssistantSession> = emptyList(),
    /** The chat shown; null for a new one, not yet written to. */
    val session: String? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /** Tokens the last request of this chat used; with [AssistantModel.contextWindow], how full it is. */
    val contextTokens: Long? = null,
    val turn: AssistantTurn? = null,
    val approval: AssistantApproval? = null,
    /** "Allow All in This Chat" was answered: this chat's changes and commands no longer ask. */
    val allowAll: Boolean = false,
    /** Why the last turn stopped short; the next one clears it. */
    val failure: String? = null,
) {
    val running: Boolean get() = turn != null

    /** The turn in hand belongs to the chat shown, not to another one. */
    val runningHere: Boolean get() = turn != null && turn.workspace == workspace && turn.session == session
}
