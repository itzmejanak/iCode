package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.LanguageServiceRules
import org.json.JSONObject

/**
 * Which icon a file or a folder draws with, as VS Code reads an icon theme (docs/file-kinds-icons-plan.md
 * D5) — here Material Icon Theme, shipped as its own data (`assets/file-icons/theme.json`, written by
 * `tools/import-file-icons.py`). A file: its path in its folder (`.config/graphqlrc`), its whole name,
 * its extensions from the longest (`d.ts` before `ts`), then its language as [FileTypes] reads it — a name
 * no language claims has none — then the default file. A folder: its path, its name, then the default folder; open, its open icon. On a
 * light theme the theme's own light icons come first at each step. Icons are named by the theme's ids.
 */
class FileIconTheme internal constructor(
    val file: String,
    val folder: String,
    /** Each folder icon's open one. */
    private val opens: Map<String, String>,
    private val dark: Names,
    private val light: Names,
) {
    internal class Names(
        val fileExtensions: Map<String, String>,
        val fileNames: Map<String, String>,
        val folderNames: Map<String, String>,
        val languageIds: Map<String, String>,
    )

    /**
     * The icon of a file named [name], in a folder named [parent] when known. [type], when given, is
     * what its bytes said it is: where that is not what its name says — a `.ts` that is a video — the
     * icon is its kind's, not its name's.
     */
    fun fileIcon(name: String, parent: String? = null, type: FileType? = null, light: Boolean = false): String {
        val lower = name.lowercase()
        val named = FileTypes.of(lower)
        if (type != null && type != named) {
            val stand = FileTypes.extensionOf(type)
            return first(light) { names -> names.languageIds[type.id] ?: stand?.let(names.fileExtensions::get) } ?: file
        }
        return first(light) { names -> parent?.let { names.fileNames["${it.lowercase()}/$lower"] } }
            ?: first(light) { names -> names.fileNames[lower] }
            ?: FileTypes.extensionsOf(lower).firstNotNullOfOrNull { extension -> first(light) { names -> names.fileExtensions[extension] } }
            // As VS Code: a name no language claims has none, so the default file, not plain text's icon.
            ?: named.takeIf { it != FileTypes.PlainText }?.let { type -> first(light) { names -> names.languageIds[LanguageServiceRules.languageIdOf(type)] } }
            ?: file
    }

    /** The icon of a folder named [name], in one named [parent] when known (`.github/workflows`); [open] while it is expanded. */
    fun folderIcon(name: String, parent: String? = null, open: Boolean = false, light: Boolean = false): String {
        val lower = name.lowercase()
        val icon = first(light) { names -> parent?.let { names.folderNames["${it.lowercase()}/$lower"] } }
            ?: first(light) { names -> names.folderNames[lower] }
            ?: folder
        return if (open) opens[icon] ?: icon else icon
    }

    /** Every icon the theme names: what must be shipped. */
    val icons: Set<String>
        get() = buildSet {
            add(file)
            addAll(opens.keys)
            addAll(opens.values)
            listOf(dark, light).forEach { names ->
                addAll(names.fileExtensions.values)
                addAll(names.fileNames.values)
                addAll(names.folderNames.values)
                addAll(names.languageIds.values)
            }
        }

    /** At one step of the order, the light theme's icon before the dark one's. */
    private inline fun first(light: Boolean, at: (Names) -> String?): String? = (if (light) at(this.light) else null) ?: at(dark)

    companion object {
        /** The theme as `tools/import-file-icons.py` writes it. Throws for anything else. */
        fun parse(json: String): FileIconTheme {
            val theme = JSONObject(json)
            fun names(section: JSONObject?) = Names(
                fileExtensions = section.strings("fileExtensions"),
                fileNames = section.strings("fileNames"),
                folderNames = section.strings("folderNames"),
                languageIds = section.strings("languageIds"),
            )
            return FileIconTheme(
                file = theme.getString("file"),
                folder = theme.getString("folder"),
                opens = theme.strings("open"),
                dark = names(theme),
                light = names(theme.optJSONObject("light")),
            )
        }

        private fun JSONObject?.strings(key: String): Map<String, String> {
            val section = this?.optJSONObject(key) ?: return emptyMap()
            return section.keys().asSequence().associateWith { section.getString(it) }
        }
    }
}
