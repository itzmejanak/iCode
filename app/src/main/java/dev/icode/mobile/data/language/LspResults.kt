package dev.icode.mobile.data.language

import dev.icode.mobile.domain.LanguageAction
import dev.icode.mobile.domain.LanguageCompletionItem
import dev.icode.mobile.domain.LanguagePlace
import dev.icode.mobile.domain.LanguageTextEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A server's answers read into iCode's types (spec 3.17): a value it sends that iCode cannot read is
 * skipped, never thrown on, and an enumeration value it does not know is kept as nothing.
 */
internal class LspResults(private val uris: LspUris, private val workspace: String) {
    /** `Location | Location[] | LocationLink[] | null`, as definition and references answer. */
    fun places(result: JsonElement?): List<LanguagePlace> = when (result) {
        is JsonArray -> result.mapNotNull { place(it) }
        is JsonObject -> listOfNotNull(place(result))
        else -> emptyList()
    }

    /** `DocumentSymbol[]` (a tree) or `SymbolInformation[]` (flat), as a list in reading order. */
    fun symbols(result: JsonElement?, location: String?): List<LanguagePlace> {
        val items = LanguageJson.objects(result)
        if (items.firstOrNull()?.containsKey("location") == true) return items.mapNotNull { symbol ->
            val place = place(symbol["location"]) ?: return@mapNotNull null
            place.copy(preview = symbolLabel(symbol))
        }
        val file = location ?: return emptyList()
        return buildList { items.forEach { flatten(it, file, 0, this) } }
    }

    /** `CompletionItem[] | CompletionList | null`. */
    fun completions(result: JsonElement?, resolvable: Boolean): List<LanguageCompletionItem> {
        val items = when (result) {
            is JsonArray -> result
            is JsonObject -> result["items"] as? JsonArray
            else -> null
        } ?: return emptyList()
        val defaults = (result as? JsonObject)?.get("itemDefaults") as? JsonObject
        return LanguageJson.objects(items).take(MAX_COMPLETIONS).mapNotNull { completion(it, defaults, resolvable) }
    }

    fun completion(item: JsonObject, defaults: JsonObject? = null, resolvable: Boolean = false): LanguageCompletionItem? {
        val label = LanguageJson.string(item, "label") ?: return null
        val textEdit = item["textEdit"] as? JsonObject
        // A plain TextEdit has a range; an InsertReplaceEdit (not advertised) its insert range.
        val range = LspText.rangeFrom(textEdit?.get("range") ?: textEdit?.get("insert") ?: (defaults?.get("editRange") as? JsonObject)?.let { it["insert"] ?: it })
        val newText = LanguageJson.string(textEdit, "newText") ?: LanguageJson.string(item, "textEditText")
        val insert = LanguageJson.string(item, "insertText") ?: label
        val format = (item["insertTextFormat"] as? JsonPrimitive)?.intOrNull ?: (defaults?.get("insertTextFormat") as? JsonPrimitive)?.intOrNull
        return LanguageCompletionItem(
            label = label,
            detail = LanguageJson.string(item, "detail") ?: LanguageJson.string(item["labelDetails"], "description"),
            documentation = LanguageJson.markup(item["documentation"]),
            kind = KINDS.getOrNull(((item["kind"] as? JsonPrimitive)?.intOrNull ?: 0) - 1),
            sortText = LanguageJson.string(item, "sortText"),
            filterText = LanguageJson.string(item, "filterText"),
            edit = range?.let { LanguageTextEdit(it, newText ?: insert) },
            insertText = newText ?: insert,
            snippet = format == 2,
            additionalEdits = edits(item["additionalTextEdits"]),
            deprecated = (item["deprecated"] as? JsonPrimitive)?.booleanOrNull == true || (item["tags"] as? JsonArray)?.any { (it as? JsonPrimitive)?.intOrNull == 1 } == true,
            resolvable = resolvable,
            token = item,
        )
    }

    fun edits(element: JsonElement?): List<LanguageTextEdit> = LanguageJson.objects(element).mapNotNull { edit ->
        val range = LspText.rangeFrom(edit["range"]) ?: return@mapNotNull null
        LanguageTextEdit(range, LanguageJson.string(edit, "newText") ?: return@mapNotNull null)
    }

    /** `(Command | CodeAction)[]`, as titles to choose from. */
    fun actions(result: JsonElement?): List<Pair<LanguageAction, JsonObject>> = LanguageJson.objects(result).mapNotNull { action ->
        val title = LanguageJson.string(action, "title") ?: return@mapNotNull null
        LanguageAction(
            title = title,
            kind = LanguageJson.string(action, "kind"),
            preferred = (action["isPreferred"] as? JsonPrimitive)?.booleanOrNull == true,
            disabled = LanguageJson.string(action["disabled"], "reason"),
        ) to action
    }

    /** `SignatureHelp`: the active signature, its active parameter marked, and its documentation. */
    fun signature(result: JsonElement?): String? {
        val help = result as? JsonObject ?: return null
        val signatures = LanguageJson.objects(help["signatures"])
        val active = ((help["activeSignature"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, (signatures.size - 1).coerceAtLeast(0))
        val signature = signatures.getOrNull(active) ?: return null
        val signatureLabel = LanguageJson.string(signature, "label") ?: return null
        val parameter = ((signature["activeParameter"] ?: help["activeParameter"]) as? JsonPrimitive)?.intOrNull
        val name = parameter?.let { LanguageJson.objects(signature["parameters"]).getOrNull(it) }?.let { param ->
            when (val label = param["label"]) {
                is JsonPrimitive -> label.content
                // [from, to) offsets into the signature's label.
                is JsonArray -> {
                    val from = (label.getOrNull(0) as? JsonPrimitive)?.intOrNull
                    val to = (label.getOrNull(1) as? JsonPrimitive)?.intOrNull
                    if (from != null && to != null && from in 0..to && to <= signatureLabel.length) signatureLabel.substring(from, to) else null
                }
                else -> null
            }
        }
        return listOfNotNull("```\n$signatureLabel\n```", name?.let { "Now: `$it`" }, LanguageJson.markup(signature["documentation"])).joinToString("\n\n")
    }

    private fun place(element: JsonElement?): LanguagePlace? {
        val item = element as? JsonObject ?: return null
        val uri = LanguageJson.string(item, "uri") ?: LanguageJson.string(item, "targetUri") ?: return null
        val range = LspText.rangeFrom(item["targetSelectionRange"] ?: item["range"] ?: item["targetRange"]) ?: return null
        val location = uris.locationOf(uri)
        val file = location?.let { uris.relative(workspace, it) } ?: uris.guestOf(uri) ?: uri
        return LanguagePlace(location ?: "", file, range, location?.let { lineOf(it, range.start.line) }.orEmpty())
    }

    private fun flatten(symbol: JsonObject, location: String, depth: Int, into: MutableList<LanguagePlace>) {
        val range = LspText.rangeFrom(symbol["selectionRange"] ?: symbol["range"]) ?: return
        into += LanguagePlace(location, uris.relative(workspace, location) ?: location, range, symbolLabel(symbol), depth)
        LanguageJson.objects(symbol["children"]).forEach { flatten(it, location, depth + 1, into) }
    }

    private fun symbolLabel(symbol: JsonObject): String {
        val kind = SYMBOL_KINDS.getOrNull(((symbol["kind"] as? JsonPrimitive)?.intOrNull ?: 0) - 1)
        return listOfNotNull(LanguageJson.string(symbol, "name"), kind?.lowercase(), LanguageJson.string(symbol, "detail")).joinToString(" · ")
    }

    /** The text of [line] in the file at [location], for a reference's preview. */
    private fun lineOf(location: String, line: Int): String = runCatching {
        java.io.File(location).useLines { lines -> lines.drop(line).firstOrNull()?.trim()?.take(PREVIEW_CHARS) }
    }.getOrNull().orEmpty()

    companion object {
        const val MAX_COMPLETIONS = 500
        private const val PREVIEW_CHARS = 160

        /** CompletionItemKind, 1-based (spec 3.17). */
        val KINDS = listOf(
            "Text", "Method", "Function", "Constructor", "Field", "Variable", "Class", "Interface", "Module", "Property", "Unit", "Value",
            "Enum", "Keyword", "Snippet", "Color", "File", "Reference", "Folder", "EnumMember", "Constant", "Struct", "Event", "Operator", "TypeParameter",
        )

        /** SymbolKind, 1-based (spec 3.17). */
        val SYMBOL_KINDS = listOf(
            "File", "Module", "Namespace", "Package", "Class", "Method", "Property", "Field", "Constructor", "Enum", "Interface", "Function", "Variable",
            "Constant", "String", "Number", "Boolean", "Array", "Object", "Key", "Null", "EnumMember", "Struct", "Event", "Operator", "TypeParameter",
        )
    }
}
