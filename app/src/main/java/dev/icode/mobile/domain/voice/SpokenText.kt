package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantMessage

/** When the assistant's answers are read aloud (docs/phone-agent-plan.md §5.8). */
enum class SpokenReplies(val label: String, val description: String) {
    Off("Off", "Answers are only written"),
    InBubble("In the bubble", "A turn started from the bubble is answered aloud"),
    Always("Always", "Every answer is read aloud"),
    ;

    /** Whether the answer of a turn is spoken; [fromBubble] when the bubble sent its message. */
    fun speaks(fromBubble: Boolean): Boolean = when (this) {
        Off -> false
        InBubble -> fromBubble
        Always -> true
    }

    companion object {
        val DEFAULT = InBubble

        fun of(name: String?): SpokenReplies = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What is spoken of an answer: its words, without Markdown, code blocks or links. */
object SpokenText {
    private val FENCE = Regex("```[\\s\\S]*?(```|$)")
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
    private val QUOTE = Regex("(?m)^\\s*>\\s?")
    private val BULLET = Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+")
    private val RULE = Regex("(?m)^\\s*([-*_]\\s*){3,}$")
    private val TABLE_PIPE = Regex("\\|")
    private val EMPHASIS = Regex("(\\*\\*|__|~~|\\*)")
    private val SPACE = Regex("\\s+")

    fun of(markdown: String): String {
        var text = markdown
        text = FENCE.replace(text, " ")
        text = IMAGE.replace(text, " ")
        text = LINK.replace(text) { it.groupValues[1] }
        text = URL.replace(text, " ")
        text = INLINE_CODE.replace(text) { it.groupValues[1] }
        text = RULE.replace(text, " ")
        text = HEADING.replace(text, "")
        text = QUOTE.replace(text, "")
        text = BULLET.replace(text, "")
        text = TABLE_PIPE.replace(text, " ")
        text = EMPHASIS.replace(text, "")
        return SPACE.replace(text, " ").trim()
    }

    /** The answer of the last turn: the replies after the last message of the user, as spoken; null when there is none. */
    fun lastAnswer(messages: List<AssistantMessage>): String? {
        val start = messages.indexOfLast { it is AssistantMessage.User } + 1
        return messages.drop(start)
            .filterIsInstance<AssistantMessage.Reply>()
            .map { of(it.text) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
    }
}
