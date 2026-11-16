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
 * (docs/workspaces-and-files.md).
 */
data class FileType(val id: String, val family: FileFamily, val language: String?, val text: Boolean = true)

/**
 * The one answer to "what kind of file is this?" — for the editor's colours, the explorers, their
 * categories, thumbnails, viewers and icons, and the kind other apps are told — by the file's whole
 * name (`Makefile`), then a name pattern (`Dockerfile.dev`), then the longest extension (`.hpp.in`
 * before `.in`); then, where the text is at hand, its first line (`#!/usr/bin/env bash`, `<?xml`);
 * and, where the name cannot say, its first bytes ([of] with a head, [FileSignatures]).
 *
 * The text types are the language catalog's (assets/textmate/catalog.json, `files`): each language's
 * names, extensions, patterns and first-line expressions as its VS Code extension declares them
 * (`contributes.languages`), else as its grammar's upstream and GitHub Linguist do, written by
 * tools/import-grammars.py (docs/editor-grammar-provenance.md). The kinds that are not text are the
 * formats' own extensions, here. Patterns on a path (a `.git` folder's `config`) are not matched: those
 * files are inside folders the Explorer never lists. Anything not listed is plain text.
 */
object FileTypes {
    val PlainText = FileType("plaintext", FileFamily.Document, null)
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

    /**
     * A file type as the language catalog declares it (`files` in assets/textmate/catalog.json, built into
     * [catalogFileTypes] by app/build.gradle.kts): space-separated extensions, names and name beginnings.
     */
    internal class Declared(
        val id: String,
        val family: FileFamily,
        val language: String,
        val extensions: String,
        val names: String,
        val prefixes: String,
        val firstLine: String?,
    )

    /** The catalog's text types, in its order (first lines are tried in it), then the formats of other bytes. */
    internal val rules: List<Rule> = catalogFileTypes.map { rule(FileType(it.id, it.family, it.language), it.extensions, it.names, it.prefixes, it.firstLine) } + listOf(
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

    val Markdown: FileType = checkNotNull(types["markdown"]) { "The catalog declares no Markdown" }

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
     * Whether [name] says too little for its kind, so its first bytes decide
     * (docs/workspaces-and-files.md): a name no rule knows, or one two formats share. Every other name
     * is never read.
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

    /** A format of its own bytes, not text: no grammar, and the editor does not open it. */
    private fun binary(id: String, family: FileFamily, extensions: String) = rule(FileType(id, family, null, text = false), extensions, "", "", null)

    private fun rule(type: FileType, extensions: String, names: String, prefixes: String, firstLine: String?) =
        Rule(type, words(extensions), words(names), words(prefixes), firstLine?.let(::Regex))

    private fun words(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
