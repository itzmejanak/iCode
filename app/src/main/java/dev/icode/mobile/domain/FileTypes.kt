package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ArchiveFormat

/**
 * What a file holds: code, structured data, a document to read, a picture, a video, a sound, an
 * archive, an app package, or other bytes no person reads (a font, a library, a database).
 */
enum class FileFamily { Code, Data, Document, Image, Video, Audio, Archive, App, Binary }

/**
 * A kind of file: the [language] the editor colours it as, by its id in the language catalog
 * ([dev.icode.mobile.domain.syntax.Languages]), if one is shipped, its family, and whether it is
 * [text] — what the editor opens — or bytes of a format of their own. Only [FileTypes] makes them
 * (docs/file-kinds-icons-plan.md D1).
 */
data class FileType(val id: String, val family: FileFamily, val language: String?, val text: Boolean = true)

/**
 * The one answer to "what kind of file is this?" — for the editor's colours, the explorers, their
 * categories, thumbnails, viewers and icons, and the kind other apps are told — by the file's whole
 * name (`Makefile`), then a name pattern (`Dockerfile.dev`), then the longest extension (`.hpp.in`
 * before `.in`); then, where the text is at hand, its first line (`#!/usr/bin/env bash`, `<?xml`);
 * and, where the name cannot say, its first bytes ([of] with a head, [FileSignatures]).
 *
 * The languages' names, extensions, patterns and first-line expressions are each language's own, as
 * its VS Code extension declares them (`contributes.languages`, commit 307b60e7;
 * docs/editor-grammar-provenance.md); the kinds that are not text are the formats' own extensions.
 * Patterns on a path (a `.git` folder's `config`) are not matched: those files are inside folders the
 * Explorer never lists. Anything not listed is plain text.
 */
object FileTypes {
    val PlainText = FileType("plaintext", FileFamily.Document, null)
    val Markdown = FileType("markdown", FileFamily.Document, "markdown")
    val Image = FileType("image", FileFamily.Image, null, text = false)
    val Video = FileType("video", FileFamily.Video, null, text = false)
    val Audio = FileType("audio", FileFamily.Audio, null, text = false)
    val Pdf = FileType("pdf", FileFamily.Document, null, text = false)
    val Archive = FileType("archive", FileFamily.Archive, null, text = false)
    val AndroidPackage = FileType("android-package", FileFamily.App, null, text = false)
    val Font = FileType("font", FileFamily.Binary, null, text = false)
    val Executable = FileType("executable", FileFamily.Binary, null, text = false)
    val Database = FileType("database", FileFamily.Binary, null, text = false)

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
        code("kotlin", "kotlin", "kt kts"),  // Sora Editor's grammar; VS Code has no Kotlin
        code("java", "java", "java jav"),
        code("python", "python", "py rpy pyw cpy gyp gypi pyi ipy pyt", names = "sconstruct sconscript", firstLine = """^#!\s*/?.*\bpython[0-9.-]*\b"""),
        // Before JavaScript: its `\bnode` also matches `ts-node`.
        code("typescript", "typescript", "ts cts mts", firstLine = """^#!.*\b(deno|bun|ts-node)\b"""),
        code("javascript", "javascript", "js es6 mjs cjs pac", names = "jakefile", firstLine = """^#!.*\bnode"""),
        code("javascriptreact", "javascriptreact", "jsx"),
        code("typescriptreact", "typescriptreact", "tsx"),
        code("html", "html", "html htm shtml xhtml xht mdoc jsp asp aspx jshtm volt ejs rhtml"),
        code("xml", "xml", "xml xsd ascx atom axml axaml bpmn cpt csl csproj csproj.user dita ditamap dtd ent mod dtml fsproj fxml iml isml jmx launch menu mxml nuspec opml owl proj props pt publishsettings pubxml pubxml.user rbxlx rbxmx rdf rng rss shproj slnx storyboard svg targets tld tmx vbproj vbproj.user vcxproj vcxproj.filters wixproj wsdl wxi wxl wxs xaml xbl xib xlf xliff xpdl xul xoml", firstLine = """(\<\?xml.*)|(\<svg)|(\<\!doctype\s+svg)"""),
        code("xsl", "xsl", "xsl xslt"),
        code("css", "css", "css"),
        code("scss", "scss", "scss"),
        code("less", "less", "less"),
        code("shell", "shellscript", "sh bash bashrc bash_aliases bash_profile bash_login ebuild eclass profile bash_logout xprofile xsession xsessionrc zsh zshrc zprofile zlogin zlogout zshenv zsh-theme fish ksh csh cshrc tcshrc yashrc yash_profile", names = "apkbuild pkgbuild .envrc .hushlogin zshrc zshenv zlogin zprofile zlogout bashrc_apple_terminal zshrc_apple_terminal", firstLine = """^#!.*\b(bash|fish|zsh|sh|ksh|dtksh|pdksh|mksh|ash|dash|yash|sh|csh|jcsh|tcsh|itcsh).*|^#\s*-\*-[^*]*mode:\s*shell-script[^*]*-\*-"""),
        code("bat", "bat", "bat cmd"),
        code("rust", "rust", "rs"),
        code("go", "go", "go"),
        code("c", "c", "c i"),
        code("cpp", "cpp", "cpp cppm cc ccm cxx cxxm c++ c++m hpp hh hxx h++ h ii ino inl ipp ixx mpp mxx tpp txx hpp.in h.in"),
        code("csharp", "csharp", "cs csx cake"),
        code("php", "php", "php php4 php5 phtml ctp", firstLine = """^#!\s*/.*\bphp\b"""),
        code("ruby", "ruby", "rb rbx rjs gemspec rake ru erb podspec rbi", names = "rakefile gemfile guardfile podfile capfile cheffile hobofile vagrantfile appraisals rantfile berksfile berksfile.lock thorfile puppetfile dangerfile brewfile fastfile appfile deliverfile matchfile scanfile snapfile gymfile", firstLine = """^#!\s*/.*\bruby\b"""),
        code("swift", "swift", "swift"),
        code("dart", "dart", "dart"),
        code("lua", "lua", "lua"),
        code("groovy", "groovy", "groovy gvy gradle jenkinsfile nf", names = "jenkinsfile", prefixes = "jenkinsfile", firstLine = """^#!.*\bgroovy\b"""),
        code("sql", "sql", "sql dsql"),
        code("dockerfile", "dockerfile", "dockerfile containerfile", names = "dockerfile containerfile", prefixes = "dockerfile. containerfile."),
        code("makefile", "makefile", "mak mk", names = "makefile gnumakefile ocamlmakefile", firstLine = """^#!\s*/usr/bin/make"""),
        data("json", "json", "json bowerrc jscsrc webmanifest js.map css.map ts.map har jslintrc jsonld geojson ipynb vuerc jsonc eslintrc eslintrc.json jsfmtrc jshintrc swcrc hintrc babelrc toolset.jsonc code-snippets tsbuildinfo", names = "composer.lock .watchmanconfig babel.config.json bun.lock .babelrc.json .ember-cli typedoc.json tsconfig.json jsconfig.json"),
        data("jsonl", "jsonl", "jsonl ndjson"),
        data("json5", null, "json5"),  // no grammar shipped: JSON5's syntax is not JSON's
        data("yaml", "yaml", "yaml yml eyaml eyml cff yaml-tmlanguage yaml-tmpreferences yaml-tmtheme winget", firstLine = """^#cloud-config"""),
        data("ini", "ini", "ini"),
        data("properties", "ini", "conf properties cfg directory gitattributes gitconfig gitmodules editorconfig repo", names = "gitconfig"),
        data("dotenv", "dotenv", "env", names = ".env .flaskenv user-dirs.dirs", prefixes = ".env."),
        data("ignore", "ignore", "gitignore_global gitignore git-blame-ignore-revs"),
        markdown("md mkd mkdn mdwn mdown markdown markdn mdtxt mdtext litcoffee ron ronn workbook"),
        document("diff", "diff", "diff patch rej"),
        document("git-commit", "git-commit", "", names = "commit_editmsg merge_msg"),
        document("git-rebase", "git-rebase", "", names = "git-rebase-todo"),
        // Comma- and tab-separated values: a table to read, text the editor opens.
        document("csv", null, "csv tsv"),
        // Pictures Android's own decoder reads (SVG is XML, above: its source is what is edited).
        rule(Image, "png jpg jpeg jfif gif webp bmp heic heif avif ico dng", "", "", null),
        // `ts` and `mts` are also transport streams (TV recorders, AVCHD cameras): TypeScript's above, by
        // the name; their bytes say when they are not ([SHARED]).
        rule(Video, "mp4 m4v mkv webm mov qt avi 3gp 3g2 flv f4v wmv asf mpg mpeg mpe m2v m2ts m2t ogv vob rm rmvb divx mxf", "", "", null),
        rule(Audio, "mp3 m4a m4b aac wav flac ogg oga opus spx amr awb mid midi wma aif aiff aifc mka weba ape wv caf ac3", "", "", null),
        rule(Pdf, "pdf", "", "", null),
        binary("word", FileFamily.Document, "doc docx odt rtf pages wpd"),
        binary("spreadsheet", FileFamily.Document, "xls xlsx xlsm ods numbers"),
        binary("presentation", FileFamily.Document, "ppt pptx odp"),
        binary("ebook", FileFamily.Document, "epub mobi azw azw3 fb2 djvu"),
        rule(AndroidPackage, APPS, "", "", null),
        // What Extract reads, by its own endings: one list, never two. An app package is a zip, but an app first.
        rule(Archive, (ArchiveFormat.entries.flatMap { it.suffixes }.map { it.removePrefix(".") } - words(APPS)).joinToString(" "), "", "", null),
        rule(Font, "ttf otf woff woff2 eot", "", "", null),
        rule(Executable, "so dll exe dylib o a lib class dex wasm node", "", "", null),
        rule(Database, "db db3 sqlite sqlite3", "", "", null),
    )

    private val byName: Map<String, FileType> = rules.flatMap { rule -> rule.names.map { it to rule.type } }.toMap()
    private val byPrefix: List<Pair<String, FileType>> =
        rules.flatMap { rule -> rule.prefixes.map { it to rule.type } }.sortedByDescending { it.first.length }
    private val byExtension: Map<String, FileType> =
        rules.flatMap { rule -> rule.extensions.map { it to rule.type } }.toMap()
    private val types: Map<String, FileType> = (rules.map { it.type } + PlainText).associateBy { it.id }

    fun of(name: String): FileType {
        val lower = name.lowercase()
        byName[lower]?.let { return it }
        byPrefix.firstOrNull { lower.startsWith(it.first) }?.let { return it.second }
        return extensionsOf(lower).firstNotNullOfOrNull { byExtension[it] } ?: PlainText
    }

    /**
     * [lowerName]'s extensions, the longest first — `lib.dom.d.ts` is `dom.d.ts`, `d.ts`, `ts` — so the
     * longest known wins: `x.hpp.in` is C++, not "in". The icon theme reads names the same way.
     */
    fun extensionsOf(lowerName: String): Sequence<String> =
        generateSequence(lowerName.indexOf('.').takeIf { it >= 0 }) { at -> lowerName.indexOf('.', at + 1).takeIf { it >= 0 } }
            .map { lowerName.substring(it + 1) }
            .filter { it.isNotEmpty() }

    /** The type whose id is [id] — `typescript`, `video` — or null. */
    fun byId(id: String): FileType? = types[id]

    /** [type]'s first extension, as its rule lists it: what a type stands for where a name is needed (`video`, `mp4`). */
    fun extensionOf(type: FileType): String? = rules.firstOrNull { it.type == type }?.extensions?.firstOrNull()

    /**
     * Whether [name] says too little for its kind, so its first bytes decide (docs/file-kinds-icons-plan.md
     * D2): a name no rule knows, or one two formats share. Every other name is never read.
     */
    fun needsContent(name: String): Boolean {
        val lower = name.lowercase()
        val type = of(lower)
        return type == PlainText || type.text && lower.substringAfterLast('.') in SHARED && DECLARATIONS.none { lower.endsWith(it) }
    }

    /**
     * As [of], and where the name says too little ([needsContent]), what its first bytes — [head], up
     * to [HEAD_BYTES] of them — say it is: a transport stream named `.ts` is a video, a picture with no
     * extension is a picture. Bytes no signature knows leave the name's answer.
     */
    fun of(name: String, head: ByteArray): FileType {
        val named = of(name)
        if (!needsContent(name)) return named
        return FileSignatures.of(head) ?: named
    }

    /** How many first bytes [of] reads to tell what a file is. */
    const val HEAD_BYTES = 512

    /** Android app packages: an app before they are the zips they are. */
    private const val APPS = "apk apks xapk apkm aab"

    /** Extensions a text type shares with a format of other bytes: TypeScript's, with the MPEG transport stream's. */
    private val SHARED = setOf("ts", "mts")

    /** TypeScript's declaration files: never a transport stream, so never read for one. */
    private val DECLARATIONS = listOf(".d.ts", ".d.mts")

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
        language: String?,
        extensions: String,
        names: String = "",
        prefixes: String = "",
        firstLine: String? = null,
    ) = rule(FileType(id, FileFamily.Code, language), extensions, names, prefixes, firstLine)

    private fun data(id: String, language: String?, extensions: String, names: String = "", prefixes: String = "", firstLine: String? = null) =
        rule(FileType(id, FileFamily.Data, language), extensions, names, prefixes, firstLine)

    private fun document(id: String, language: String?, extensions: String, names: String = "") =
        rule(FileType(id, FileFamily.Document, language), extensions, names, "", null)

    private fun markdown(extensions: String) = rule(Markdown, extensions, "", "", null)

    /** A format of its own bytes, not text: no grammar, and the editor does not open it. */
    private fun binary(id: String, family: FileFamily, extensions: String) = rule(FileType(id, family, null, text = false), extensions, "", "", null)

    private fun rule(type: FileType, extensions: String, names: String, prefixes: String, firstLine: String?) =
        Rule(type, words(extensions), words(names), words(prefixes), firstLine?.let(::Regex))

    private fun words(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
