package dev.icode.mobile.domain

/**
 * Finds http(s) URLs in terminal and output text so they can be activated.
 *
 * Trailing punctuation is trimmed because a URL printed at the end of a
 * sentence would otherwise carry the full stop into the browser.
 */
object UrlDetector {
    private val pattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    private val trailing = charArrayOf('.', ',', ')', ']', '"', '\'', ';', ':')

    fun find(text: String): List<String> =
        pattern.findAll(text)
            .map { it.value.trimEnd(*trailing) }
            .filter { it.length > "https://".length }
            .distinct()
            .toList()

    /** The URL covering character [index] of [line], if any: what a long press on it opens. */
    fun at(line: String, index: Int): String? =
        pattern.findAll(line)
            .firstOrNull { index in it.range }
            ?.value
            ?.trimEnd(*trailing)
            ?.takeIf { it.length > "https://".length }
}
