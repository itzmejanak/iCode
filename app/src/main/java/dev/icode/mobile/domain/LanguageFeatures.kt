package dev.icode.mobile.domain

// What the editor and the pages ask the language servers (docs/lsp-plan.md §5.5, §7): each answer in
// iCode's own types, never the protocol's JSON.

/** A replacement of [range] by [newText], in the file's places as LSP counts them. */
data class LanguageTextEdit(val range: TextRange, val newText: String)

/** One of a server's completions (`CompletionItem`), as the editor's list shows and inserts it. */
data class LanguageCompletionItem(
    val label: String,
    val detail: String? = null,
    val documentation: String? = null,
    /** `Function`, `Variable`…: LSP's CompletionItemKind by name. */
    val kind: String? = null,
    val sortText: String? = null,
    val filterText: String? = null,
    /** Where it goes; null: [insertText] replaces the word before the caret. */
    val edit: LanguageTextEdit? = null,
    val insertText: String = label,
    /** [insertText] (or [edit]'s text) is a snippet, with `$1` tab stops. */
    val snippet: Boolean = false,
    /** Elsewhere in the file: an import it needs. */
    val additionalEdits: List<LanguageTextEdit> = emptyList(),
    val deprecated: Boolean = false,
    /** The server fills in [documentation] and [additionalEdits] when asked ([LanguageCompletions.resolve]). */
    val resolvable: Boolean = false,
    /** The server's item, for resolving it; opaque outside the data layer. */
    val token: Any? = null,
)

/** Completions for the editor, asked from the thread Sora completes on. */
interface LanguageCompletions {
    /** What the servers serving [location] would insert at [position]; empty when none offers completion. */
    suspend fun complete(location: String, position: TextPosition): List<LanguageCompletionItem>

    /** [item] as its server fills it in; [item] itself when it cannot. */
    suspend fun resolve(location: String, item: LanguageCompletionItem): LanguageCompletionItem

    companion object {
        val None = object : LanguageCompletions {
            override suspend fun complete(location: String, position: TextPosition) = emptyList<LanguageCompletionItem>()
            override suspend fun resolve(location: String, item: LanguageCompletionItem) = item
        }
    }
}

/** What can be asked about a place in a file, from the Command Palette. */
enum class LanguageQuery(val title: String, val capability: String) {
    Definition("Go to Definition", "Go to definition"),
    References("Find References", "Find references"),
    Symbols("Go to Symbol in File", "Document symbols"),
    Hover("Show Hover", "Hover"),
    Signature("Show Signature Help", "Signature help"),
    CodeActions("Code Actions", "Code actions"),
    Format("Format Document", "Formatting"),
    Rename("Rename Symbol", "Rename"),
}

/** A place an answer points to: a definition, a reference, a symbol. */
data class LanguagePlace(
    /** The host location, to open. */
    val location: String,
    /** As shown: relative to the workspace, or the path in the runtime outside it. */
    val file: String,
    val range: TextRange,
    /** Its line's text, or the symbol's name and kind. */
    val preview: String,
    val depth: Int = 0,
)

/** A code action's title, by its index in the answer. */
data class LanguageAction(val title: String, val kind: String?, val preferred: Boolean, val disabled: String?)

/** An answer shown in a sheet over the editor; one at a time. */
sealed interface LanguageResult {
    val title: String

    data class Asking(override val title: String) : LanguageResult
    data class Places(override val title: String, val places: List<LanguagePlace>) : LanguageResult
    data class Text(override val title: String, val text: String) : LanguageResult
    data class Actions(override val title: String, val location: String, val actions: List<LanguageAction>) : LanguageResult
    data class Failed(override val title: String, val words: String) : LanguageResult

    /** A rename's new name, asked for: [current] is the name there now. */
    data class Rename(override val title: String, val location: String, val position: TextPosition, val current: String) : LanguageResult
}

/** What the language services' pages and sheets ask, beyond configuration ([LanguageIntent]). */
sealed interface LanguageFeatureIntent : LanguageIntent {
    /** [query] at [position] in the open file at [location]; [newName] for a rename. */
    data class Ask(val query: LanguageQuery, val location: String, val position: TextPosition, val newName: String? = null) : LanguageFeatureIntent

    /** The [index]th action of the last code actions answer is applied. */
    data class RunAction(val index: Int) : LanguageFeatureIntent

    data object CloseResult : LanguageFeatureIntent
}

object LanguageFeatureRules {
    /** What the running servers serving [location] said they offer: capability labels. */
    fun offered(snapshot: ICodeSnapshot, location: String?): Set<String> {
        val document = snapshot.openDocuments.firstOrNull { it.location == location } ?: return emptySet()
        val languageId = LanguageServiceRules.languageIdOf(FileTypes.of(document.name, document.text.take(256).substringBefore('\n')))
        return snapshot.languageServices
            .filter { it.status == LanguageServiceStatus.Running && LanguageServiceRules.serves(LanguageServiceRules.configurationOf(it), document.name, languageId) }
            .flatMap { it.capabilities }.toSet()
    }
}
