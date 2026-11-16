package dev.icode.mobile.domain.markdown

import dev.icode.mobile.domain.WorkspaceRuntimePath
import java.net.URLDecoder
import java.util.Base64

/**
 * Where a rendered document's relative links start (docs/editor-and-language-services.md). A file's
 * links resolve against its [folder], and `/…` against the workspace's [root], as GitHub reads a
 * repository's. An answer's resolve against the workspace. In both, paths under [runtimeRoot] — the
 * workspace as the runtime names it, `/workspace/app` — are the workspace's own.
 */
data class MarkdownPlace(
    val folder: String,
    val root: String,
    val runtimeRoot: String,
    /** `/docs/a.md` is the workspace's (a file's link); otherwise a path outside it, not followed. */
    val slashFromRoot: Boolean,
) {
    companion object {
        /** A file's, at [location] in the workspace at [root]; null for a file outside it. */
        fun ofFile(location: String, root: String?): MarkdownPlace? =
            root?.takeIf { location.startsWith("$it/") }?.let { MarkdownPlace(location.substringBeforeLast('/'), it, runtimeOf(it), slashFromRoot = true) }

        /** An assistant's answer in the workspace at [root]: its paths are the workspace's, `/workspace/<name>/…` among them. */
        fun ofAnswer(root: String?): MarkdownPlace? = root?.let { MarkdownPlace(it, it, runtimeOf(it), slashFromRoot = false) }

        private fun runtimeOf(root: String) = WorkspaceRuntimePath.of(root.substringAfterLast('/'))
    }
}

/** Where a tap on a link goes. */
sealed interface MarkdownTarget {
    /** The user's browser or mail app. */
    data class Web(val url: String) : MarkdownTarget

    /** A workspace file, at [line] when the link names one (`#L10`). */
    data class File(val path: String, val line: Int?) : MarkdownTarget

    /** A heading of the same document, by its slug. */
    data class Anchor(val slug: String) : MarkdownTarget
}

/** Where an image's bytes are. */
sealed interface MarkdownImageSource {
    data class Local(val path: String) : MarkdownImageSource

    /** `data:image/…;base64,…`. */
    data class Inline(val bytes: ByteArray) : MarkdownImageSource {
        override fun equals(other: Any?) = other is Inline && other.bytes.contentEquals(bytes)
        override fun hashCode() = bytes.contentHashCode()
    }

    /** `https://`, which only a file's preview loads. */
    data class Remote(val url: String) : MarkdownImageSource
}

object MarkdownLinks {
    /** A reference label as CommonMark matches them: case folded, its spaces collapsed. */
    fun label(text: String): String = text.trim().replace(WHITESPACE, " ").lowercase()

    /**
     * [text]'s anchor, as GitHub makes it: lower case; letters, marks, digits, `_` and `-` kept,
     * other characters dropped; spaces to `-`. A repeat gets `-1`, `-2`… ([unique]).
     */
    fun slug(text: String): String = buildString {
        text.lowercase().forEach { c ->
            when {
                c == ' ' -> append('-')
                c == '-' || c == '_' || Character.isLetterOrDigit(c) -> append(c)
                Character.getType(c) in MARKS -> append(c)
            }
        }
    }

    /** [slug] made unique against [taken] (counts by slug, updated): `a`, then `a-1`, `a-2`. */
    fun unique(slug: String, taken: MutableMap<String, Int>): String {
        val seen = taken[slug]
        taken[slug] = (seen ?: -1) + 1
        return if (seen == null) slug else "$slug-${seen + 1}"
    }

    /** Where [href] leads from [place]; null when it is not followed (`javascript:`, `file:`, out of the workspace). */
    fun target(href: String, place: MarkdownPlace?): MarkdownTarget? {
        val link = href.trim()
        if (link.isEmpty()) return null
        if (link.startsWith("#")) return MarkdownTarget.Anchor(decode(link.drop(1)))
        SCHEME.find(link)?.let { scheme ->
            return if (scheme.groupValues[1].lowercase() in WEB_SCHEMES) MarkdownTarget.Web(link) else null
        }
        val path = link.substringBefore('#').substringBefore('?')
        val line = LINE.matchEntire(link.substringAfter('#', ""))?.groupValues?.get(1)?.toIntOrNull()
        return file(decode(path), place ?: return null)?.let { MarkdownTarget.File(it, line) }
    }

    /** Where [src]'s bytes are from [place]; null when it is not drawn: `http:`, SVG, out of the workspace. */
    fun image(src: String, place: MarkdownPlace?): MarkdownImageSource? {
        val link = src.trim()
        if (link.startsWith("data:", ignoreCase = true)) return inline(link)
        val scheme = SCHEME.find(link)?.groupValues?.get(1)?.lowercase()
        if (link.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)) return null
        return when (scheme) {
            null -> place?.let { file(decode(link.substringBefore('#').substringBefore('?')), it) }?.let(MarkdownImageSource::Local)
            "https" -> MarkdownImageSource.Remote(link)
            else -> null
        }
    }

    /** [path] from [place] as a file of the workspace, `..` resolved; null when that leads out of it. */
    private fun file(path: String, place: MarkdownPlace): String? {
        if (path.isEmpty()) return null
        val runtime = place.runtimeRoot
        val absolute = when {
            path == runtime || path.startsWith("$runtime/") -> place.root + path.removePrefix(runtime)
            path.startsWith("/") -> if (place.slashFromRoot) place.root + path else return null
            else -> "${place.folder}/$path"
        }
        val parts = ArrayDeque<String>()
        absolute.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull() ?: return null
                else -> parts.addLast(part)
            }
        }
        val resolved = parts.joinToString("/", prefix = "/")
        return resolved.takeIf { it == place.root || it.startsWith("${place.root}/") }
    }

    private fun inline(link: String): MarkdownImageSource? {
        val header = link.substringBefore(',', "")
        val type = header.removePrefix("data:").removePrefix("DATA:").substringBefore(';').lowercase()
        if (!type.startsWith("image/") || type == "image/svg+xml" || !header.endsWith(";base64", ignoreCase = true)) return null
        return runCatching { Base64.getMimeDecoder().decode(link.substringAfter(',')) }.getOrNull()?.let(MarkdownImageSource::Inline)
    }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private val WHITESPACE = Regex("\\s+")
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val WEB_SCHEMES = setOf("http", "https", "mailto")
    private val LINE = Regex("L(\\d+)(?:-L?\\d+)?")
    private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK).map(Byte::toInt).toSet()
}
