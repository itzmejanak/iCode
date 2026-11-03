package dev.icode.mobile.domain

import java.net.URI

/** One row of the Assistant page, keyed for its list. */
sealed interface AssistantEntry {
    val key: String

    /** What the user wrote, and the files sent with it. */
    data class Said(override val key: String, val text: String, val attachments: List<AssistantAttachment> = emptyList()) : AssistantEntry

    /** What the assistant answered, Markdown. */
    data class Answer(override val key: String, val text: String) : AssistantEntry

    /** What the model thought before it answered, as its provider sent it; [live] while it is still being written. */
    data class Reasoning(override val key: String, val text: String, val live: Boolean = false) : AssistantEntry

    /** A tool the assistant used, and what came of it. */
    data class Step(
        override val key: String,
        val call: AssistantCall,
        val status: AssistantStepStatus,
        /** What the tool answered; null while it has not. */
        val output: String? = null,
        /** For a change to a file, the lines it added and removed. */
        val changes: LineChanges? = null,
    ) : AssistantEntry

    /** Where the chat was compacted: before it, the model reads only its summary. */
    data class Compacted(override val key: String, val summary: String) : AssistantEntry

    /** Steps one after another, one row of the chat ([AssistantStepRules.rows]); keyed by its first. */
    data class Steps(override val key: String, val steps: List<Step>) : AssistantEntry
}

enum class AssistantStepStatus { Asking, Running, Queued, Done, Failed }

/** What a turn sends of a chat: the summary of what was compacted, and the messages after it. */
data class AssistantHistory(val summary: String?, val messages: List<AssistantMessage>)

object AssistantRules {
    /** What a call left unanswered by a stop, a failure or an ended process is told. */
    const val STOPPED = "Stopped before it ran"

    /** A chat is compacted before a turn once its last request filled this share of the window. */
    const val COMPACT_AT = 0.8f

    /** Files sent with one message, at most (§12 R6). */
    const val MAX_ATTACHMENTS = 5

    /** The page: what was said and done in [state]'s chat. */
    fun entries(state: AssistantState): List<AssistantEntry> = buildList {
        var firstOpen = true
        state.messages.forEachIndexed { index, message ->
            when (message) {
                is AssistantMessage.User -> add(AssistantEntry.Said("m$index", message.text, message.attachments))
                is AssistantMessage.Reply -> {
                    if (message.reasoning.isNotBlank()) add(AssistantEntry.Reasoning("m${index}r", message.reasoning))
                    if (message.text.isNotBlank()) add(AssistantEntry.Answer("m$index", message.text))
                    val results = resultsOf(state.messages, index).associateBy { it.callId }
                    message.calls.forEachIndexed { position, call ->
                        val result = results[call.id]
                        val status = when {
                            result != null -> if (result.failed) AssistantStepStatus.Failed else AssistantStepStatus.Done
                            state.approval?.call?.id == call.id -> AssistantStepStatus.Asking
                            !state.runningHere -> AssistantStepStatus.Failed
                            firstOpen -> AssistantStepStatus.Running
                            else -> AssistantStepStatus.Queued
                        }
                        if (result == null) firstOpen = false
                        add(AssistantEntry.Step("m${index}c$position", call, status, result?.output ?: STOPPED.takeIf { !state.runningHere }, result?.changes))
                    }
                }
                is AssistantMessage.Results -> Unit
                is AssistantMessage.Compacted -> add(AssistantEntry.Compacted("m$index", message.summary))
            }
        }
        // The reply being written, under the rest: replaced by the reply once it is recorded.
        state.turn?.live?.takeIf { state.runningHere }?.let { live ->
            if (live.reasoning.isNotBlank()) add(AssistantEntry.Reasoning(LIVE_REASONING, live.reasoning, live = true))
            if (live.text.isNotBlank()) add(AssistantEntry.Answer(LIVE_ANSWER, live.text))
        }
    }

    /**
     * [messages] with every call answered, in its order: one left open is told it was [STOPPED]. A
     * provider refuses a conversation with a call and no result, so this is what a chat is saved and
     * resumed as. Results go right after their reply; any elsewhere answer nothing and are dropped.
     */
    fun closed(messages: List<AssistantMessage>): List<AssistantMessage> = buildList {
        messages.forEachIndexed { index, message ->
            if (message is AssistantMessage.Results) return@forEachIndexed
            add(message)
            if (message is AssistantMessage.Reply && message.calls.isNotEmpty()) {
                val given = resultsOf(messages, index).associateBy { it.callId }
                add(AssistantMessage.Results(message.calls.map { given[it.id] ?: AssistantResult(it.id, it.tool, STOPPED, failed = true) }))
            }
        }
    }

    /** What the model is sent of [messages]: from the last compaction on, its summary in front, every call answered. */
    fun forModel(messages: List<AssistantMessage>): AssistantHistory {
        val closed = closed(messages)
        val last = closed.indexOfLast { it is AssistantMessage.Compacted }
        if (last < 0) return AssistantHistory(null, closed)
        return AssistantHistory((closed[last] as AssistantMessage.Compacted).summary, closed.drop(last + 1))
    }

    /** How full the context is, 0..1; null when the window is unknown or nothing was sent yet. */
    fun contextShare(tokens: Long?, window: Long?): Float? =
        if (tokens == null || window == null || window <= 0) null else (tokens.toFloat() / window).coerceIn(0f, 1f)

    /**
     * What the meter says (§13), always: "84k / 200k" — the tokens of the chat's last request, of
     * the model's window — or "84k tokens" where the window is unknown; 0 before the first request.
     */
    fun contextLabel(tokens: Long?, window: Long?): String =
        if (window != null) "${tokenCount(tokens ?: 0)} / ${tokenCount(window)}" else "${tokenCount(tokens ?: 0)} tokens"

    /** "200k context · reasons · images · PDFs": what the model pickers say of a model; "" when nothing is known. */
    fun modelSummary(model: AssistantModel): String = listOfNotNull(
        model.contextWindow?.let { "${tokenCount(it)} context" },
        "reasons".takeIf { model.efforts.isNotEmpty() },
        "images".takeIf { model.images },
        "PDFs".takeIf { model.documents },
    ).joinToString(" · ")

    /** The same of a model Koog does not know, from what the user said of it. */
    fun modelSummary(spec: AssistantModelSpec): String = listOfNotNull(
        spec.contextWindow?.let { "${tokenCount(it)} context" },
        "reasons".takeIf { spec.reasoning },
        "images".takeIf { spec.images },
    ).joinToString(" · ")

    /** A chat's title: its first message's first line, cut short. */
    fun sessionTitle(firstMessage: String): String {
        val line = firstMessage.trim().lineSequence().firstOrNull().orEmpty().trim()
        return when {
            line.isEmpty() -> "New chat"
            line.length <= TITLE_LENGTH -> line
            else -> line.take(TITLE_LENGTH).trimEnd() + "…"
        }
    }

    /** [effort] when [model] offers it; otherwise the provider's default (null). */
    fun effortFor(effort: AssistantEffort?, model: AssistantModel?): AssistantEffort? =
        effort?.takeIf { model != null && it in model.efforts }

    /** What is wrong with a provider's form, in the words the form shows; null when it can be saved (§13 P1–P3). */
    fun providerProblem(
        kind: AssistantProviderKind,
        name: String,
        baseUrl: String,
        keyTyped: Boolean,
        keySaved: Boolean,
        models: List<AssistantModelSpec>,
    ): String? = when {
        name.isBlank() -> "Enter a name"
        kind.asksBaseUrl && baseUrl.isBlank() -> "Enter the server's address"
        kind.asksBaseUrl && !HTTP_URL.matches(baseUrl.trim()) -> "Enter an address that starts with https:// or http://"
        kind.needsKey && !keyTyped && !keySaved -> "Enter the API key"
        models.isEmpty() -> "Add at least one model"
        else -> models.firstOrNull { it.id.isBlank() || it.id.trim().any(Char::isWhitespace) }?.let { "A model ID has no spaces" }
            ?: models.groupBy { it.id.trim() }.entries.firstOrNull { it.value.size > 1 }?.let { "${it.key} is listed twice" }
    }

    /**
     * Advice past what blocks saving a provider: OpenRouter's address on an OpenAI-compatible server
     * loses what only the OpenRouter type reads — the models' reasoning, which OpenRouter sends as
     * `reasoning` and Koog's OpenAI client reads as `reasoning_content` (docs/runtime-foundation-plan.md D8).
     */
    fun providerHint(kind: AssistantProviderKind, baseUrl: String): String? {
        if (kind != AssistantProviderKind.OpenAICompatible) return null
        val host = runCatching { URI(baseUrl.trim()).host }.getOrNull()?.lowercase() ?: return null
        return OPENROUTER_HINT.takeIf { host == OPENROUTER_HOST || host.endsWith(".$OPENROUTER_HOST") }
    }

    /**
     * What Git says changed in the file at [location], as Source Control opens it: the change it can
     * show as a diff — unstaged before staged — or null for a file Git does not see changed, one it
     * tracks no history of (new, not yet added), one deleted, or one in conflict.
     */
    fun gitChangeOf(location: String, repository: GitRepositoryState?, changes: List<GitChange>): GitChange? {
        val root = repository?.workTreePath?.trimEnd('/') ?: return null
        val path = location.takeIf { it.startsWith("$root/") }?.removePrefix("$root/") ?: return null
        return changes.filter { it.path == path && it.status !in UNSHOWN_CHANGES }.minByOrNull { it.staged }
    }

    /** What is wrong with a model's options as typed; null when they can be kept. */
    fun modelOptionsProblem(contextWindow: String, maxOutput: String): String? = when {
        contextWindow.isNotBlank() && tokens(contextWindow) == null -> "The context window is a number of tokens"
        maxOutput.isNotBlank() && tokens(maxOutput) == null -> "The output limit is a number of tokens"
        else -> null
    }

    /** What a file is to the model, by its type, or its name's extension when the type is unknown. */
    fun attachmentKind(name: String, mimeType: String?): AssistantAttachmentKind {
        val type = mimeType?.lowercase().orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            // Sent as JPEG whatever it was, so any image Android decodes — a camera's HEIC too.
            type.startsWith("image/") || (type.isEmpty() && FileTypes.of(name).family == FileFamily.Image) -> AssistantAttachmentKind.Image
            type == PDF || (type.isEmpty() && extension == "pdf") -> AssistantAttachmentKind.Document
            else -> AssistantAttachmentKind.Text
        }
    }

    /** Why [model] cannot take files of these [kinds], in the words the composer shows; null when it can. */
    fun attachmentProblem(kinds: Collection<AssistantAttachmentKind>, model: AssistantModel?): String? = when {
        model == null -> null
        AssistantAttachmentKind.Image in kinds && !model.images -> "${model.id} does not read images — choose a model that does"
        AssistantAttachmentKind.Document in kinds && !model.documents -> "${model.id} does not read PDFs — choose a model that does"
        else -> null
    }

    /** A token count as typed — "200000", "200k", "1m" — or null when it is not one. */
    fun tokens(typed: String): Long? {
        val text = typed.trim().lowercase().replace(",", "").replace("_", "")
        val (digits, factor) = when {
            text.endsWith("k") -> text.dropLast(1) to 1_000L
            text.endsWith("m") -> text.dropLast(1) to 1_000_000L
            else -> text to 1L
        }
        val value = digits.toDoubleOrNull() ?: return null
        return (value * factor).toLong().takeIf { value > 0 && it in 1..MAX_TOKENS }
    }

    /** "200k", "1.05m", "8192": a token count as people read it. */
    fun tokenCount(tokens: Long): String = when {
        tokens >= 1_000_000 -> trimmed(tokens / 1_000_000.0) + "m"
        tokens >= 10_000 -> "${tokens / 1_000}k"
        tokens >= 1_000 -> trimmed(tokens / 1_000.0) + "k"
        else -> tokens.toString()
    }

    /** The address as stored: trimmed, without a trailing slash; null where the provider's own is used. */
    fun storedBaseUrl(kind: AssistantProviderKind, typed: String): String? =
        if (!kind.asksBaseUrl) null else typed.trim().trimEnd('/').ifEmpty { null }

    /**
     * The results of the reply at [index]: those right after it. Matched there, not across the chat,
     * since a provider may give the same ID to calls of different replies (Ollama's are a hash).
     */
    private fun resultsOf(messages: List<AssistantMessage>, index: Int): List<AssistantResult> =
        (messages.getOrNull(index + 1) as? AssistantMessage.Results)?.results.orEmpty()

    private fun trimmed(value: Double): String =
        ((value * 10).toLong() / 10.0).toString().removeSuffix(".0")

    private const val PDF = "application/pdf"
    private val UNSHOWN_CHANGES = setOf(GitStatusCodes.UNTRACKED, GitStatusCodes.DELETED, GitStatusCodes.CONFLICTED)
    private const val LIVE_REASONING = "live-reasoning"
    private const val LIVE_ANSWER = "live-answer"
    private const val OPENROUTER_HOST = "openrouter.ai"
    private const val OPENROUTER_HINT = "Choose the OpenRouter type for OpenRouter: only it shows the models' reasoning"

    private const val TITLE_LENGTH = 60
    private const val MAX_TOKENS = 100_000_000L
    private val HTTP_URL = Regex("^https?://[^\\s/?#:]+(:[0-9]{1,5})?(/\\S*)?$")
}
