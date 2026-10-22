package dev.icode.mobile.domain

/** What a file holds, as lists draw it: code, structured data, a document to read, or a picture. */
enum class FileFamily { Code, Data, Document, Image }

/**
 * A kind of file: the TextMate grammar the editor colours it with, if one is
 * shipped, and its family. Only [FileTypes] makes them.
 */
data class FileType(val id: String, val family: FileFamily, val grammar: String?)

/**
 * The one answer to "what kind of file is this?" — for the editor's colours,
 * the Explorer and search results — by the file's whole name (`Makefile`),
 * then a name pattern (`Dockerfile.dev`), then the longest extension
 * (`.hpp.in` before `.in`), then, where the text is at hand, its first line
 * (`#!/usr/bin/env bash`, `<?xml`).
 *
 * Names, extensions, patterns and first-line expressions are each language's
 * own, as its VS Code extension declares them (`contributes.languages`, commit
 * 307b60e7; docs/editor-grammar-provenance.md). Patterns on a path (a `.git`
 * folder's `config`) are not matched: those files are inside folders the
 * Explorer never lists. Anything not listed is plain text.
 */
object FileTypes {
    val PlainText = FileType("plaintext", FileFamily.Document, null)
    val Markdown = FileType("markdown", FileFamily.Document, "text.html.markdown")
    val Image = FileType("image", FileFamily.Image, null)

    /** One rule per type; a name, extension or pattern belongs to exactly one rule (tested). */
    internal class Rule(
        val type: FileType,
        val extensions: Set<String>,
        val names: Set<String>,
        /** A name's beginning, for patterns such as `Dockerfile.*`. */
        val prefixes: Set<String>,
        val firstLine: Regex?,
    )

    internal val rules: List<Rule> = listOf(
        code("kotlin", "source.kotlin", "kt kts"),  // Sora Editor's grammar; VS Code has no Kotlin
        code("java", "source.java", "java jav"),
        code("python", "source.python", "py rpy pyw cpy gyp gypi pyi ipy pyt", names = "sconstruct sconscript", firstLine = """^#!\s*/?.*\bpython[0-9.-]*\b"""),
        // Before JavaScript: its `\bnode` also matches `ts-node`.
        code("typescript", "source.ts", "ts cts mts", firstLine = """^#!.*\b(deno|bun|ts-node)\b"""),
        code("javascript", "source.js", "js es6 mjs cjs pac", names = "jakefile", firstLine = """^#!.*\bnode"""),
        code("javascriptreact", "source.js.jsx", "jsx"),
        code("typescriptreact", "source.tsx", "tsx"),
        code("html", "text.html.basic", "html htm shtml xhtml xht mdoc jsp asp aspx jshtm volt ejs rhtml"),
        code("xml", "text.xml", "xml xsd ascx atom axml axaml bpmn cpt csl csproj csproj.user dita ditamap dtd ent mod dtml fsproj fxml iml isml jmx launch menu mxml nuspec opml owl proj props pt publishsettings pubxml pubxml.user rbxlx rbxmx rdf rng rss shproj slnx storyboard svg targets tld tmx vbproj vbproj.user vcxproj vcxproj.filters wixproj wsdl wxi wxl wxs xaml xbl xib xlf xliff xpdl xul xoml", firstLine = """(\<\?xml.*)|(\<svg)|(\<\!doctype\s+svg)"""),
        code("xsl", "text.xml.xsl", "xsl xslt"),
        code("css", "source.css", "css"),
        code("scss", "source.css.scss", "scss"),
        code("less", "source.css.less", "less"),
        code("shell", "source.shell", "sh bash bashrc bash_aliases bash_profile bash_login ebuild eclass profile bash_logout xprofile xsession xsessionrc zsh zshrc zprofile zlogin zlogout zshenv zsh-theme fish ksh csh cshrc tcshrc yashrc yash_profile", names = "apkbuild pkgbuild .envrc .hushlogin zshrc zshenv zlogin zprofile zlogout bashrc_apple_terminal zshrc_apple_terminal", firstLine = """^#!.*\b(bash|fish|zsh|sh|ksh|dtksh|pdksh|mksh|ash|dash|yash|sh|csh|jcsh|tcsh|itcsh).*|^#\s*-\*-[^*]*mode:\s*shell-script[^*]*-\*-"""),
        code("bat", "source.batchfile", "bat cmd"),
        code("rust", "source.rust", "rs"),
        code("go", "source.go", "go"),
        code("c", "source.c", "c i"),
        code("cpp", "source.cpp", "cpp cppm cc ccm cxx cxxm c++ c++m hpp hh hxx h++ h ii ino inl ipp ixx mpp mxx tpp txx hpp.in h.in"),
        code("csharp", "source.cs", "cs csx cake"),
        code("php", "text.html.php", "php php4 php5 phtml ctp", firstLine = """^#!\s*/.*\bphp\b"""),
        code("ruby", "source.ruby", "rb rbx rjs gemspec rake ru erb podspec rbi", names = "rakefile gemfile guardfile podfile capfile cheffile hobofile vagrantfile appraisals rantfile berksfile berksfile.lock thorfile puppetfile dangerfile brewfile fastfile appfile deliverfile matchfile scanfile snapfile gymfile", firstLine = """^#!\s*/.*\bruby\b"""),
        code("swift", "source.swift", "swift"),
        code("dart", "source.dart", "dart"),
        code("lua", "source.lua", "lua"),
        code("groovy", "source.groovy", "groovy gvy gradle jenkinsfile nf", names = "jenkinsfile", prefixes = "jenkinsfile", firstLine = """^#!.*\bgroovy\b"""),
        code("sql", "source.sql", "sql dsql"),
        code("dockerfile", "source.dockerfile", "dockerfile containerfile", names = "dockerfile containerfile", prefixes = "dockerfile. containerfile."),
        code("makefile", "source.makefile", "mak mk", names = "makefile gnumakefile ocamlmakefile", firstLine = """^#!\s*/usr/bin/make"""),
        data("json", "source.json", "json bowerrc jscsrc webmanifest js.map css.map ts.map har jslintrc jsonld geojson ipynb vuerc jsonc eslintrc eslintrc.json jsfmtrc jshintrc swcrc hintrc babelrc toolset.jsonc code-snippets tsbuildinfo", names = "composer.lock .watchmanconfig babel.config.json bun.lock .babelrc.json .ember-cli typedoc.json tsconfig.json jsconfig.json"),
        data("jsonl", "source.json.lines", "jsonl ndjson"),
        data("json5", null, "json5"),  // no grammar shipped: JSON5's syntax is not JSON's
        data("yaml", "source.yaml", "yaml yml eyaml eyml cff yaml-tmlanguage yaml-tmpreferences yaml-tmtheme winget", firstLine = """^#cloud-config"""),
        data("ini", "source.ini", "ini"),
        data("properties", "source.ini", "conf properties cfg directory gitattributes gitconfig gitmodules editorconfig repo", names = "gitconfig"),
        data("dotenv", "source.dotenv", "env", names = ".env .flaskenv user-dirs.dirs", prefixes = ".env."),
        data("ignore", "source.ignore", "gitignore_global gitignore git-blame-ignore-revs"),
        markdown("md mkd mkdn mdwn mdown markdown markdn mdtxt mdtext litcoffee ron ronn workbook"),
        document("diff", "source.diff", "diff patch rej"),
        document("git-commit", "text.git-commit", "", names = "commit_editmsg merge_msg"),
        document("git-rebase", "text.git-rebase", "", names = "git-rebase-todo"),
        // Pictures Android's own decoder reads (SVG is XML, above: its source is what is edited).
        rule(Image, "png jpg jpeg gif webp bmp heic heif avif ico", "", "", null),
    )

    private val byName: Map<String, FileType> = rules.flatMap { rule -> rule.names.map { it to rule.type } }.toMap()
    private val byPrefix: List<Pair<String, FileType>> =
        rules.flatMap { rule -> rule.prefixes.map { it to rule.type } }.sortedByDescending { it.first.length }
    private val byExtension: Map<String, FileType> =
        rules.flatMap { rule -> rule.extensions.map { it to rule.type } }.toMap()

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

    /**
     * A Markdown code fence's language — ```` ```kotlin ````, ```` ```kt ````, ```` ```py ```` — as a
     * type: by its id, else as an extension, as GitHub reads fences by language name and extension.
     */
    fun ofLanguage(language: String): FileType {
        val lower = language.lowercase()
        return rules.firstOrNull { it.type.id == lower }?.type ?: byExtension[lower] ?: PlainText
    }

    /** As [of], and when the name says nothing — a script without an extension — its first line. */
    fun of(name: String, firstLine: String): FileType =
        of(name).takeIf { it != PlainText } ?: rules.firstOrNull { it.firstLine?.containsMatchIn(firstLine) == true }?.type ?: PlainText

    private fun code(
        id: String,
        grammar: String?,
        extensions: String,
        names: String = "",
        prefixes: String = "",
        firstLine: String? = null,
    ) = rule(FileType(id, FileFamily.Code, grammar), extensions, names, prefixes, firstLine)

    private fun data(id: String, grammar: String?, extensions: String, names: String = "", prefixes: String = "", firstLine: String? = null) =
        rule(FileType(id, FileFamily.Data, grammar), extensions, names, prefixes, firstLine)

    private fun document(id: String, grammar: String?, extensions: String, names: String = "") =
        rule(FileType(id, FileFamily.Document, grammar), extensions, names, "", null)

    private fun markdown(extensions: String) = rule(Markdown, extensions, "", "", null)

    private fun rule(type: FileType, extensions: String, names: String, prefixes: String, firstLine: String?) =
        Rule(type, words(extensions), words(names), words(prefixes), firstLine?.let(::Regex))

    private fun words(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
