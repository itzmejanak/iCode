package dev.icode.mobile.domain.voice

/**
 * What is spoken of the assistant (docs/phone-agent-plan.md §5.8): its replies and its questions, read
 * as a person reads them — no Markdown, no code blocks, no links — sentence by sentence as they are
 * written ([ready]).
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
    private val FENCE_LINE = Regex("(?m)^\\s*(```|~~~)")

    /** Where a sentence may end while text still comes: a stop after a word (not "1." of a list, nor "3.5"), or a line's end. */
    private val BOUNDARY = Regex("(?<=[^\\s\\d][.!?…])\\s|(?<=[。！？])|\\n")

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
     * What of [raw] — a reply as far as it has been written — can be said now, and how much of it that
     * takes: its whole sentences, never a code block not yet closed nor a sentence cut in a block; all of
     * it once [final].
     */
    fun ready(raw: String, final: Boolean): Ready {
        val fences = FENCE_LINE.findAll(raw).map { it.range.first }.toList()
        fun outsideCode(at: Int) = fences.count { it < at } % 2 == 0
        val end = if (final) raw.length else BOUNDARY.findAll(raw).map { it.range.last + 1 }.lastOrNull(::outsideCode) ?: 0
        if (end == 0) return Ready(emptyList(), 0)
        return Ready(of(raw.substring(0, end)).split(SENTENCE).filter { it.isNotBlank() }, end)
    }

    /** [sentences] to say, taken from the first [taken] characters of a reply. */
    data class Ready(val sentences: List<String>, val taken: Int)
}
