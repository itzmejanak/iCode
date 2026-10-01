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

/** What the user set for the assistant; never the key itself. */
data class AssistantSettings(
    val kind: AssistantProviderKind,
    /** Asked only where [AssistantProviderKind.asksBaseUrl]; null otherwise. */
    val baseUrl: String?,
    val model: String,
    val hasKey: Boolean,
    /** Changes and commands wait for an answer (on by default). */
    val askBeforeChanges: Boolean = true,
) {
    /** "Anthropic · claude-sonnet-4-5". */
    val summary: String get() = "${kind.label} · $model"
}

/** What a tool does to the workspace: reads never ask; changes and commands do. */
enum class AssistantToolKind { Read, Change, Command }

/** One request for a tool, as the model made it. */
data class AssistantCall(
    /** The provider's ID for it: its result is matched to it by this, exactly. */
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

/** The conversation as the model sees it; the page is drawn from it ([AssistantRules.entries]). */
sealed interface AssistantMessage {
    data class User(val text: String) : AssistantMessage

    /** What the model said, and the tools it asked for. */
    data class Reply(val text: String, val calls: List<AssistantCall> = emptyList()) : AssistantMessage

    /** The answers to the calls of the reply before it. */
    data class Results(val results: List<AssistantResult>) : AssistantMessage
}

/** A change or a command waiting for the user. */
data class AssistantApproval(
    val call: AssistantCall,
    /** What it will do, in full enough to judge: the command, or the file and the text it replaces. */
    val detail: String,
)

enum class AssistantAnswer { Allow, AllowAll, Deny }

/** The turn in hand: one at a time, in the workspace it started in. */
data class AssistantTurn(
    val workspace: String,
    val workspaceName: String,
    /** "Thinking…", "Read src/App.kt", "Waiting for your answer". */
    val step: String,
)

/** The assistant as the screens show it. */
data class AssistantState(
    /** Null until a provider is set: the page explains and offers its settings. */
    val settings: AssistantSettings? = null,
    /** Koog's catalogue per provider: suggestions for the model field, never a limit. */
    val suggestions: Map<AssistantProviderKind, List<String>> = emptyMap(),
    /** The workspace whose chat is shown; null until the page opens on one. */
    val workspace: String? = null,
    val messages: List<AssistantMessage> = emptyList(),
    /** The reply being written, as streamed so far. */
    val streaming: String = "",
    val turn: AssistantTurn? = null,
    val approval: AssistantApproval? = null,
    /** "Allow All in This Chat" was answered: this chat's changes and commands no longer ask. */
    val allowAll: Boolean = false,
    /** Why the last turn stopped short; the next one clears it. */
    val failure: String? = null,
) {
    val running: Boolean get() = turn != null

    /** The turn shown here belongs to this chat's workspace, not to another one's. */
    val runningHere: Boolean get() = turn != null && turn.workspace == workspace
}
