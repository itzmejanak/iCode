package dev.icode.mobile.data.language

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What iCode tells a server about itself (docs/lsp-plan.md §5.5): only what it has built. */
internal object LspMessages {
    fun initialize(
        rootGuest: String,
        rootUri: String,
        folders: List<Pair<String, String>>,
        options: JsonObject?,
        version: String,
        locale: String,
    ): JsonObject = buildJsonObject {
        // iCode's own process is not one the runtime can see; the spec allows null.
        put("processId", JsonNull)
        putJsonObject("clientInfo") { put("name", "iCode"); put("version", version) }
        put("locale", locale)
        put("rootPath", rootGuest)
        put("rootUri", rootUri)
        put("capabilities", clientCapabilities())
        options?.let { put("initializationOptions", it) }
        put("trace", "off")
        putJsonArray("workspaceFolders") { folders.forEach { (uri, name) -> add(buildJsonObject { put("uri", uri); put("name", name) }) } }
    }

    fun clientCapabilities(): JsonObject = buildJsonObject {
        putJsonObject("general") {
            putJsonArray("positionEncodings") { add("utf-16") }
        }
        putJsonObject("workspace") {
            put("applyEdit", true)
            putJsonObject("workspaceEdit") {
                put("documentChanges", true)
                putJsonArray("resourceOperations") { add("create"); add("rename"); add("delete") }
                put("failureHandling", "abort")
            }
            put("configuration", true)
            put("workspaceFolders", true)
            putJsonObject("didChangeConfiguration") { put("dynamicRegistration", false) }
            putJsonObject("symbol") { put("dynamicRegistration", true) }
            putJsonObject("diagnostics") { put("refreshSupport", true) }
        }
        putJsonObject("textDocument") {
            putJsonObject("synchronization") { put("dynamicRegistration", true); put("didSave", true); put("willSave", false) }
            putJsonObject("publishDiagnostics") { put("versionSupport", true); put("relatedInformation", false) }
            putJsonObject("diagnostic") { put("dynamicRegistration", true); put("relatedDocumentSupport", false) }
            putJsonObject("completion") {
                put("dynamicRegistration", true)
                putJsonObject("completionItem") {
                    put("snippetSupport", true)
                    put("insertReplaceSupport", false)
                    put("deprecatedSupport", true)
                    putJsonArray("documentationFormat") { add("plaintext"); add("markdown") }
                    putJsonObject("resolveSupport") { putJsonArray("properties") { add("documentation"); add("detail"); add("additionalTextEdits") } }
                }
                put("contextSupport", true)
            }
            putJsonObject("hover") { put("dynamicRegistration", true); putJsonArray("contentFormat") { add("markdown"); add("plaintext") } }
            putJsonObject("signatureHelp") {
                put("dynamicRegistration", true)
                putJsonObject("signatureInformation") { putJsonArray("documentationFormat") { add("markdown"); add("plaintext") } }
            }
            putJsonObject("definition") { put("dynamicRegistration", true); put("linkSupport", true) }
            putJsonObject("references") { put("dynamicRegistration", true) }
            putJsonObject("documentSymbol") { put("dynamicRegistration", true); put("hierarchicalDocumentSymbolSupport", true) }
            putJsonObject("formatting") { put("dynamicRegistration", true) }
            putJsonObject("rename") { put("dynamicRegistration", true); put("prepareSupport", true) }
            putJsonObject("codeAction") {
                put("dynamicRegistration", true)
                put("isPreferredSupport", true)
                put("disabledSupport", true)
                putJsonObject("resolveSupport") { putJsonArray("properties") { add("edit") } }
                putJsonObject("codeActionLiteralSupport") {
                    putJsonObject("codeActionKind") {
                        putJsonArray("valueSet") { listOf("", "quickfix", "refactor", "refactor.extract", "refactor.inline", "refactor.rewrite", "source", "source.organizeImports", "source.fixAll").forEach { add(it) } }
                    }
                }
            }
        }
        putJsonObject("window") { put("workDoneProgress", true) }
    }

    /** The [section] of [settings] (`python.analysis`), as `workspace/configuration` asks; the whole for none. */
    fun section(settings: JsonObject, section: String?): JsonElement {
        if (section.isNullOrEmpty()) return settings
        var at: JsonElement = settings
        for (key in section.split('.')) at = (at as? JsonObject)?.get(key) ?: return JsonNull
        return at
    }

    /** `workspace/configuration`'s answer: one value per item asked. */
    fun configuration(settings: JsonObject, params: JsonElement?): JsonArray = buildJsonArray {
        val items = (params as? JsonObject)?.get("items") as? JsonArray ?: return@buildJsonArray
        items.forEach { item -> add(section(settings, ((item as? JsonObject)?.get("section") as? JsonPrimitive)?.content)) }
    }
}
