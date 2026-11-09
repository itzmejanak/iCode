package dev.icode.mobile.data.language

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What a server said it can do (`InitializeResult.capabilities`), with what it registered since
 * (`client/registerCapability`). A missing capability is "not supported" (spec 3.17 "Client
 * Capabilities"), so nothing is offered that the server did not report (PR-LANG-03).
 */
internal class LspCapabilities(private val server: JsonObject, private val registered: Map<String, JsonObject> = emptyMap()) {
    enum class Sync { None, Full, Incremental }

    /** How it wants text changes. A number, or `TextDocumentSyncOptions.change`; absent is None. */
    val sync: Sync
        get() = when (val sync = server["textDocumentSync"]) {
            is JsonPrimitive -> syncOf(sync.intOrNull)
            is JsonObject -> syncOf((sync["change"] as? JsonPrimitive)?.intOrNull)
            else -> if ("textDocument/didChange" in registered) syncOf((registered["textDocument/didChange"]?.get("syncKind") as? JsonPrimitive)?.intOrNull) else Sync.None
        }

    /** Whether it wants open and close; a number for textDocumentSync means it does. */
    val openClose: Boolean
        get() = when (val sync = server["textDocumentSync"]) {
            is JsonPrimitive -> (sync.intOrNull ?: 0) > 0
            is JsonObject -> (sync["openClose"] as? JsonPrimitive)?.booleanOrNull == true
            else -> "textDocument/didOpen" in registered
        }

    /** Whether it wants saves, and with the text. */
    val save: Boolean get() = saveOptions != null
    val saveIncludesText: Boolean
        get() = ((saveOptions as? JsonObject)?.get("includeText") as? JsonPrimitive)?.booleanOrNull == true

    private val saveOptions: JsonElement?
        get() = when (val save = (server["textDocumentSync"] as? JsonObject)?.get("save")) {
            null, JsonNull -> registered["textDocument/didSave"]
            is JsonPrimitive -> save.takeIf { it.booleanOrNull == true }
            else -> save
        }

    val completion: Boolean get() = provides("completionProvider", "textDocument/completion")
    val completionResolves: Boolean get() = option("completionProvider", "textDocument/completion", "resolveProvider")
    val completionTriggers: Set<String> get() = strings("completionProvider", "textDocument/completion", "triggerCharacters")
    val hover: Boolean get() = provides("hoverProvider", "textDocument/hover")
    val signatureHelp: Boolean get() = provides("signatureHelpProvider", "textDocument/signatureHelp")
    val definition: Boolean get() = provides("definitionProvider", "textDocument/definition")
    val references: Boolean get() = provides("referencesProvider", "textDocument/references")
    val documentSymbols: Boolean get() = provides("documentSymbolProvider", "textDocument/documentSymbol")
    val workspaceSymbols: Boolean get() = provides("workspaceSymbolProvider", "workspace/symbol")
    val formatting: Boolean get() = provides("documentFormattingProvider", "textDocument/formatting")
    val rename: Boolean get() = provides("renameProvider", "textDocument/rename")
    val renamePrepares: Boolean get() = option("renameProvider", "textDocument/rename", "prepareProvider")
    val codeActions: Boolean get() = provides("codeActionProvider", "textDocument/codeAction")
    val codeActionsResolve: Boolean get() = option("codeActionProvider", "textDocument/codeAction", "resolveProvider")

    /** It answers `textDocument/diagnostic` (3.17 pull diagnostics). */
    val pullDiagnostics: Boolean get() = provides("diagnosticProvider", "textDocument/diagnostic")

    /** What the Languages page lists it can do, in the order an editor offers them. */
    fun labels(): List<String> = listOfNotNull(
        "Completion".takeIf { completion },
        "Hover".takeIf { hover },
        "Signature help".takeIf { signatureHelp },
        "Go to definition".takeIf { definition },
        "Find references".takeIf { references },
        "Document symbols".takeIf { documentSymbols },
        "Workspace symbols".takeIf { workspaceSymbols },
        "Formatting".takeIf { formatting },
        "Rename".takeIf { rename },
        "Code actions".takeIf { codeActions },
        "Diagnostics on request".takeIf { pullDiagnostics },
    )

    fun withRegistration(method: String, options: JsonObject) = LspCapabilities(server, registered + (method to options))

    fun withoutRegistration(method: String) = LspCapabilities(server, registered - method)

    /** `true`, or an options object, statically; or a registration of [method]. */
    private fun provides(key: String, method: String): Boolean = when (val value = server[key]) {
        is JsonPrimitive -> value.booleanOrNull == true
        is JsonObject -> true
        else -> method in registered
    }

    private fun option(key: String, method: String, name: String): Boolean =
        ((optionsOf(key, method)?.get(name)) as? JsonPrimitive)?.booleanOrNull == true

    private fun strings(key: String, method: String, name: String): Set<String> =
        (optionsOf(key, method)?.get(name) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    private fun optionsOf(key: String, method: String): JsonObject? = server[key] as? JsonObject ?: registered[method]

    private fun syncOf(kind: Int?): Sync = when (kind) {
        1 -> Sync.Full
        2 -> Sync.Incremental
        else -> Sync.None
    }
}
