package dev.icode.mobile.domain

/** One row of the Assistant page, keyed for its list. */
sealed interface AssistantEntry {
    val key: String

    /** What the user wrote, and the files sent with it. */
    data class Said(override val key: String, val text: String, val attachments: List<String> = emptyList()) : AssistantEntry

    /** What the assistant answered, Markdown. */
    data class Answer(override val key: String, val text: String) : AssistantEntry

    /** A tool the assistant used, and what came of it. */
    data class Step(
        override val key: String,
        val call: AssistantCall,
        val status: AssistantStepStatus,
        /** What the tool answered; null while it has not. */
        val output: String? = null,
    ) : AssistantEntry

    /** Where the chat was compacted: before it, the model reads only its summary. */
    data class Compacted(override val key: String, val summary: String) : AssistantEntry
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
                is AssistantMessage.User -> add(AssistantEntry.Said("m$index", message.text, message.attachments.map { it.path }))
                is AssistantMessage.Reply -> {
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
                        add(AssistantEntry.Step("m${index}c$position", call, status, result?.output ?: STOPPED.takeIf { !state.runningHere }))
                    }
                }
                is AssistantMessage.Results -> Unit
                is AssistantMessage.Compacted -> add(AssistantEntry.Compacted("m$index", message.summary))
            }
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

    /** What is wrong with the settings form, in the words the form shows; null when it can be saved. */
    fun settingsProblem(
        kind: AssistantProviderKind,
        baseUrl: String,
        model: String,
        keyTyped: Boolean,
        keySaved: Boolean,
        contextWindow: String = "",
        maxOutput: String = "",
    ): String? = when {
        kind.asksBaseUrl && baseUrl.isBlank() -> "Enter the server's address"
        kind.asksBaseUrl && !HTTP_URL.matches(baseUrl.trim()) -> "Enter an address that starts with https:// or http://"
        model.isBlank() -> "Enter a model"
        model.trim().any(Char::isWhitespace) -> "A model ID has no spaces"
        kind.needsKey && !keyTyped && !keySaved -> "Enter the API key"
        contextWindow.isNotBlank() && tokens(contextWindow) == null -> "The context window is a number of tokens"
        maxOutput.isNotBlank() && tokens(maxOutput) == null -> "The output limit is a number of tokens"
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

    /** The last part of a workspace's location: the name it is shown by. */
    fun workspaceName(location: String): String = location.trimEnd('/').substringAfterLast('/')

    /**
     * The results of the reply at [index]: those right after it. Matched there, not across the chat,
     * since a provider may give the same ID to calls of different replies (Ollama's are a hash).
     */
    private fun resultsOf(messages: List<AssistantMessage>, index: Int): List<AssistantResult> =
        (messages.getOrNull(index + 1) as? AssistantMessage.Results)?.results.orEmpty()

    private fun trimmed(value: Double): String =
        ((value * 10).toLong() / 10.0).toString().removeSuffix(".0")

    private const val TITLE_LENGTH = 60
    private const val MAX_TOKENS = 100_000_000L
    private val HTTP_URL = Regex("^https?://[^\\s/?#:]+(:[0-9]{1,5})?(/\\S*)?$")
}
