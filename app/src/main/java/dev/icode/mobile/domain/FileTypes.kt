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
 * the Explorer and search results — by the file's whole name (`Makefile`),
 * then a name pattern (`Dockerfile.dev`), then the longest extension
 * (`.hpp.in` before `.in`), then, where the text is at hand, the program its
 * `#!` line runs. Anything not listed is plain text, editable and uncoloured.
 *
 * The names, extensions and patterns are each language's own, as its VS Code
 * extension declares them (`contributes.languages`, commit 307b60e7); see
 * docs/editor-grammar-provenance.md.
 */
object FileTypes {
    val PlainText = FileType("plaintext", FileFamily.Document, null)
    val Markdown = FileType("markdown", FileFamily.Document, "text.html.markdown")

    /** One rule per type; a name or extension belongs to exactly one rule (tested). */
    internal class Rule(
        val type: FileType,
        val extensions: Set<String>,
        val names: Set<String>,
        /** A name's beginning, for patterns such as `Dockerfile.*`. */
        val prefixes: Set<String>,
        val interpreters: Set<String>,
    )

    internal val rules: List<Rule> = listOf(
        code("kotlin", "source.kotlin", "kt kts"),
        code("java", "source.java", "java"),
        code("python", "source.python", "py pyw", interpreters = "python pypy"),
        code("javascript", "source.js", "js mjs cjs", interpreters = "node nodejs"),
        code("javascriptreact", "source.js.jsx", "jsx"),
        code("typescript", "source.ts", "ts mts cts"),
        code("typescriptreact", "source.tsx", "tsx"),
        code("html", "text.html.basic", "html htm"),
        code("xml", "text.xml", "xml svg"),
        code("css", "source.css", "css"),
        code("shell", "source.shell", "sh bash zsh", interpreters = "sh bash zsh dash ksh ash mksh"),
        code("rust", "source.rust", "rs"),
        code("go", "source.go", "go"),
        code("bat", "source.batchfile", "bat cmd"),
        code("c", "source.c", "c i"),
        code("cpp", "source.cpp", "cpp cppm cc ccm cxx cxxm c++ c++m hpp hh hxx h++ h ii ino inl ipp ixx mpp mxx tpp txx hpp.in h.in"),
        code("csharp", "source.cs", "cs csx cake"),
        code("dart", "source.dart", "dart"),
        document("diff", "source.diff", "diff patch rej"),
        code("dockerfile", "source.dockerfile", "dockerfile containerfile", names = "dockerfile containerfile", prefixes = "dockerfile. containerfile."),
        data("dotenv", "source.dotenv", "env", names = ".env .flaskenv user-dirs.dirs", prefixes = ".env."),
        document("git-commit", "text.git-commit", "", names = "commit_editmsg merge_msg"),
        document("git-rebase", "text.git-rebase", "", names = "git-rebase-todo"),  // not by path: **/rebase-merge/done
        data("ignore", "source.ignore", "gitignore_global gitignore git-blame-ignore-revs"),
        code("groovy", "source.groovy", "groovy gvy gradle jenkinsfile nf", names = "jenkinsfile", prefixes = "jenkinsfile", interpreters = "groovy"),
        data("ini", "source.ini", "ini"),
        data("properties", "source.ini", "conf properties cfg directory gitattributes gitconfig gitmodules editorconfig repo", names = "gitconfig"),  // not by path: **/.config/git/config, **/.git/config
        code("less", "source.css.less", "less"),
        code("lua", "source.lua", "lua"),
        code("makefile", "source.makefile", "mak mk", names = "makefile gnumakefile ocamlmakefile", interpreters = "make"),
        code("php", "text.html.php", "php php4 php5 phtml ctp", interpreters = "php"),
        code("ruby", "source.ruby", "rb rbx rjs gemspec rake ru erb podspec rbi", names = "rakefile gemfile guardfile podfile capfile cheffile hobofile vagrantfile appraisals rantfile berksfile berksfile.lock thorfile puppetfile dangerfile brewfile fastfile appfile deliverfile matchfile scanfile snapfile gymfile", interpreters = "ruby"),
        code("scss", "source.css.scss", "scss"),
        code("sql", "source.sql", "sql dsql"),
        code("swift", "source.swift", "swift"),
        data("json", "source.json", "json jsonc"),
        data("json5", null, "json5"),
        data("yaml", "source.yaml", "yaml yml"),
        Rule(Markdown, words("md markdown"), emptySet(), emptySet(), emptySet()),
    )

    private val byName: Map<String, FileType> = rules.flatMap { rule -> rule.names.map { it to rule.type } }.toMap()
    private val byPrefix: List<Pair<String, FileType>> =
        rules.flatMap { rule -> rule.prefixes.map { it to rule.type } }.sortedByDescending { it.first.length }
    private val byExtension: Map<String, FileType> =
        rules.flatMap { rule -> rule.extensions.map { it to rule.type } }.toMap()

    private val byInterpreter: Map<String, FileType> =
        rules.flatMap { rule -> rule.interpreters.map { it to rule.type } }.toMap()

    fun of(name: String): FileType {
        val lower = name.lowercase()
        byName[lower]?.let { return it }
        byPrefix.firstOrNull { lower.startsWith(it.first) }?.let { return it.second }
        // From the first dot on, so the longest extension wins: `x.hpp.in` is C++, not "in".
        var dot = lower.indexOf('.')
        while (dot >= 0) {
            byExtension[lower.substring(dot + 1)]?.let { return it }
            dot = lower.indexOf('.', dot + 1)
        }
        return PlainText
    }

    /** As [of], and when the name says nothing — a script without an extension — its `#!` line. */
    fun of(name: String, firstLine: String): FileType =
        of(name).takeIf { it != PlainText } ?: interpreterOf(firstLine)?.let(byInterpreter::get) ?: PlainText

    /**
     * The program a `#!` line runs, without its version: `#!/bin/bash -e` → bash,
     * `#!/usr/bin/env -S python3.12 -u` → python. With `env`, the program is the
     * first word that is neither an option nor a `NAME=value` setting.
     */
    internal fun interpreterOf(firstLine: String): String? {
        if (!firstLine.startsWith("#!")) return null
        val words = firstLine.removePrefix("#!").trim().split(Regex("\\s+"))
        val program = words.first().substringAfterLast('/').let { first ->
            if (first != "env") first else words.drop(1).firstOrNull { !it.startsWith("-") && '=' !in it }
        } ?: return null
        return program.substringAfterLast('/').trimEnd { it.isDigit() || it == '.' }.ifEmpty { null }
    }

    private fun code(id: String, grammar: String?, extensions: String, names: String = "", prefixes: String = "", interpreters: String = "") =
        rule(FileType(id, FileFamily.Code, grammar), extensions, names, prefixes, interpreters)

    private fun data(id: String, grammar: String?, extensions: String, names: String = "", prefixes: String = "") =
        rule(FileType(id, FileFamily.Data, grammar), extensions, names, prefixes, "")

    private fun document(id: String, grammar: String?, extensions: String, names: String = "") =
        rule(FileType(id, FileFamily.Document, grammar), extensions, names, "", "")

    private fun rule(type: FileType, extensions: String, names: String, prefixes: String, interpreters: String) =
        Rule(type, words(extensions), words(names), words(prefixes), words(interpreters))

    private fun words(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
