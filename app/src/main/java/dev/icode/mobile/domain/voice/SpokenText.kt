package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantState

/** What a change in the assistant asks of the voice: say this, or hush. */
sealed interface VoiceCue {
    data class Say(val text: String) : VoiceCue
    data object Hush : VoiceCue
}

/**
 * What is spoken of the assistant (docs/phone-agent-plan.md §5.8): its answer and its questions, read
 * as a person reads them — no Markdown, no code blocks, no links.
 */
object SpokenText {
    private val FENCE = Regex("(?ms)^\\s*(```|~~~).*?(^\\s*\\1\\s*$|\\z)")
    private val IMAGE = Regex("!\\[[^]]*]\\([^)]*\\)")
    private val LINK = Regex("\\[([^]]*)]\\([^)]*\\)")
    private val URL = Regex("<?\\b(?:https?|ftp)://\\S+>?")
    private val TAG = Regex("</?[A-Za-z][^>]*>")
    private val LINE_MARK = Regex("^\\s*(?:#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+[.)]\\s+)+")
    private val RULE = Regex("^\\s*(?:[-*_]\\s*){3,}$|^\\s*\\|?\\s*:?-{3,}.*$")
    private val EMPHASIS = Regex("\\*{1,3}|~~|`+|(?<![\\p{L}\\p{N}])_{1,3}|_{1,3}(?![\\p{L}\\p{N}])")
    private val SPACE = Regex("[ \\t]+")
    private val STOPPED = Regex("[.!?…:;,。！？]$")
    private val SENTENCE = Regex("(?<=[.!?…。！？])\\s+")

    /** [markdown] as it is read aloud; a line that ends without a stop gets one, so a heading or a list pauses. */
    fun of(markdown: String): String = markdown
        .replace(FENCE, "\n")
        .replace(IMAGE, "")
        .replace(LINK, "$1")
        .replace(URL, "")
        .replace(TAG, "")
        .lines()
        .filterNot { RULE.matches(it) }
        .map { line -> line.replace(LINE_MARK, "").replace('|', ' ').replace(EMPHASIS, "").replace(SPACE, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { if (STOPPED.containsMatchIn(it)) it else "$it." }

    /** The answer of the last turn: the replies after the user's last message, as spoken; null when they say nothing. */
    fun lastAnswer(messages: List<AssistantMessage>): String? {
        val start = messages.indexOfLast { it is AssistantMessage.User } + 1
        return messages.drop(start).filterIsInstance<AssistantMessage.Reply>()
            .map { of(it.text) }.filter { it.isNotEmpty() }
            .joinToString(" ").ifEmpty { null }
    }

    /** [text] in parts of at most [max] characters, cut between sentences, else between words. */
    fun parts(text: String, max: Int): List<String> {
        val parts = mutableListOf<String>()
        var current = ""
        fun add(piece: String) {
            current = when {
                current.isEmpty() -> piece
                current.length + 1 + piece.length <= max -> "$current $piece"
                else -> piece.also { parts += current }
            }
        }
        text.split(SENTENCE).filter { it.isNotBlank() }.forEach { sentence ->
            if (sentence.length <= max) add(sentence) else sentence.split(' ').flatMap { it.chunked(max) }.forEach(::add)
        }
        if (current.isNotEmpty()) parts += current
        return parts
    }

    /**
     * What [after] asks of the voice, from [before]: a new turn hushes it; a turn [spoken] aloud says
     * each question it asks, and its answer once it ends. Stopped part-way or failed, it ends with no
     * answer, and hushes.
     */
    fun cue(before: AssistantState, after: AssistantState, spoken: SpokenReplies): VoiceCue? {
        if (before.turn == null && after.turn != null) return VoiceCue.Hush
        val turn = before.turn ?: after.turn ?: return null
        if (!spoken.speaks(turn.aloud)) return null
        val approval = after.approval
        if (approval != null && approval.call.id != before.approval?.call?.id) {
            return VoiceCue.Say("${of(AssistantPermissions.question(approval, AssistantPlace.name(turn.workspace)))} ${of(approval.call.summary)}")
        }
        val pause = after.pause
        if (pause != null && before.pause == null) return VoiceCue.Say("${of(pause.kind.question)} ${of(pause.detail)}")
        if (after.turn != null || before.turn == null) return null
        // An answer is read only while its chat is shown; one the user left is not read over another.
        if (after.workspace != turn.workspace || after.session != turn.session) return null
        val last = after.messages.lastOrNull()
        val finished = after.failure == null && last is AssistantMessage.Reply && last.calls.isEmpty()
        return (if (finished) lastAnswer(after.messages) else null)?.let { VoiceCue.Say(it) } ?: VoiceCue.Hush
    }
}
