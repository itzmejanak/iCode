package dev.icode.mobile.domain

/** What a file holds, as lists draw it: code, structured data, or a document to read. */
enum class FileFamily { Code, Data, Document }

/**
 * A kind of file: the TextMate grammar the editor colours it with, if one is
 * shipped, and its family. Only [FileTypes] makes them.
 */
data class FileType(val id: String, val family: FileFamily, val grammar: String?)

/**
 * The one answer to "what kind of file is this?" — for the editor's colours,
 * the Explorer and search results — by the file's name, then its extension.
 * Anything not listed is plain text, editable and uncoloured.
 */
object FileTypes {
    val PlainText = FileType("plaintext", FileFamily.Document, null)
    val Markdown = FileType("markdown", FileFamily.Document, "text.html.markdown")

    /** One rule per type; a name or extension belongs to exactly one rule (tested). */
    internal class Rule(val type: FileType, val extensions: Set<String>, val names: Set<String>)

    internal val rules: List<Rule> = listOf(
        code("kotlin", "source.kotlin", "kt kts"),
        code("java", "source.java", "java"),
        code("python", "source.python", "py pyw"),
        code("javascript", "source.js", "js mjs cjs"),
        code("javascriptreact", "source.js.jsx", "jsx"),
        code("typescript", "source.ts", "ts mts cts"),
        code("typescriptreact", "source.tsx", "tsx"),
        code("html", "text.html.basic", "html htm"),
        code("xml", "text.xml", "xml svg"),
        code("css", "source.css", "css"),
        code("shell", "source.shell", "sh bash zsh"),
        code("rust", "source.rust", "rs"),
        code("go", "source.go", "go"),
        code("c", null, "c h"),
        code("cpp", null, "cc cpp hpp"),
        code("csharp", null, "cs"),
        code("php", null, "php"),
        code("ruby", null, "rb"),
        code("swift", null, "swift"),
        data("json", "source.json", "json jsonc"),
        data("json5", null, "json5"),
        data("yaml", "source.yaml", "yaml yml"),
        Rule(Markdown, words("md markdown"), emptySet()),
    )

    private val byName: Map<String, FileType> = rules.flatMap { rule -> rule.names.map { it to rule.type } }.toMap()
    private val byExtension: Map<String, FileType> =
        rules.flatMap { rule -> rule.extensions.map { it to rule.type } }.toMap()

    fun of(name: String): FileType {
        val lower = name.lowercase()
        byName[lower]?.let { return it }
        return byExtension[lower.substringAfterLast('.', missingDelimiterValue = "")] ?: PlainText
    }

    private fun code(id: String, grammar: String?, extensions: String) =
        Rule(FileType(id, FileFamily.Code, grammar), words(extensions), emptySet())

    private fun data(id: String, grammar: String?, extensions: String) =
        Rule(FileType(id, FileFamily.Data, grammar), words(extensions), emptySet())

    private fun words(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
