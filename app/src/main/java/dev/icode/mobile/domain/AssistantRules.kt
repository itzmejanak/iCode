package dev.icode.mobile.domain

/** One row of the Assistant page, keyed for its list. */
sealed interface AssistantEntry {
    val key: String

    /** What the user wrote. */
    data class Said(override val key: String, val text: String) : AssistantEntry

    /** What the assistant answered, Markdown; [streaming] while it is still being written. */
    data class Answer(override val key: String, val text: String, val streaming: Boolean = false) : AssistantEntry

    /** A tool the assistant used, and what came of it. */
    data class Step(
        override val key: String,
        val call: AssistantCall,
        val status: AssistantStepStatus,
        /** What the tool answered; null while it has not. */
        val output: String? = null,
    ) : AssistantEntry
}

enum class AssistantStepStatus { Asking, Running, Queued, Done, Failed }

object AssistantRules {
    /** What a call left unanswered by a stop, a failure or an ended process is told. */
    const val STOPPED = "Stopped before it ran"

    /** The page: what was said and done in [state]'s chat, then what is being written now. */
    fun entries(state: AssistantState): List<AssistantEntry> = buildList {
        val results = state.messages.filterIsInstance<AssistantMessage.Results>()
            .flatMap { it.results }.associateBy { it.callId }
        var firstOpen = true
        state.messages.forEachIndexed { index, message ->
            when (message) {
                is AssistantMessage.User -> add(AssistantEntry.Said("m$index", message.text))
                is AssistantMessage.Reply -> {
                    if (message.text.isNotBlank()) add(AssistantEntry.Answer("m$index", message.text))
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
            }
        }
        if (state.runningHere && state.streaming.isNotEmpty()) {
            add(AssistantEntry.Answer("streaming", state.streaming, streaming = true))
        }
    }

    /**
     * [messages] with every call answered: one left open is told it was [STOPPED]. A provider
     * refuses a conversation with a call and no result, so this is what a chat is saved and resumed as.
     */
    fun closed(messages: List<AssistantMessage>): List<AssistantMessage> {
        val answered = messages.filterIsInstance<AssistantMessage.Results>().flatMap { it.results }.mapTo(HashSet()) { it.callId }
        val out = mutableListOf<AssistantMessage>()
        // What the reply just passed still owes: joined to the results that follow it, or on their own.
        var owed = emptyList<AssistantResult>()
        for (message in messages) {
            if (owed.isNotEmpty()) {
                if (message is AssistantMessage.Results) {
                    out += AssistantMessage.Results(message.results + owed)
                    owed = emptyList()
                    continue
                }
                out += AssistantMessage.Results(owed)
                owed = emptyList()
            }
            out += message
            if (message is AssistantMessage.Reply) {
                owed = message.calls.filter { it.id !in answered }.map { AssistantResult(it.id, it.tool, STOPPED, failed = true) }
            }
        }
        if (owed.isNotEmpty()) out += AssistantMessage.Results(owed)
        return out
    }

    /** What is wrong with the settings form, in the words the form shows; null when it can be saved. */
    fun settingsProblem(kind: AssistantProviderKind, baseUrl: String, model: String, keyTyped: Boolean, keySaved: Boolean): String? = when {
        kind.asksBaseUrl && baseUrl.isBlank() -> "Enter the server's address"
        kind.asksBaseUrl && !HTTP_URL.matches(baseUrl.trim()) -> "Enter an address that starts with https:// or http://"
        model.isBlank() -> "Enter a model"
        model.trim().any(Char::isWhitespace) -> "A model ID has no spaces"
        kind.needsKey && !keyTyped && !keySaved -> "Enter the API key"
        else -> null
    }

    /** The address as stored: trimmed, without a trailing slash; null where the provider's own is used. */
    fun storedBaseUrl(kind: AssistantProviderKind, typed: String): String? =
        if (!kind.asksBaseUrl) null else typed.trim().trimEnd('/').ifEmpty { null }

    /** The last part of a workspace's location: the name it is shown by. */
    fun workspaceName(location: String): String = location.trimEnd('/').substringAfterLast('/')

    private val HTTP_URL = Regex("^https?://[^\\s/?#:]+(:[0-9]{1,5})?(/\\S*)?$")
}
