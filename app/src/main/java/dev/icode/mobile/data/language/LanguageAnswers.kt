package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.LanguageAction
import dev.icode.mobile.domain.LanguageFeatureIntent
import dev.icode.mobile.domain.LanguagePlace
import dev.icode.mobile.domain.LanguageQuery
import dev.icode.mobile.domain.LanguageResult
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The Command Palette's questions to the servers, answered in a sheet over the editor (docs/lsp-plan.md
 * §7): one answer at a time, a new question replacing the last; formatting and renames are said as
 * notices once applied.
 */
internal class LanguageAnswers(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val requests: LanguageRequests,
) {
    private var job: Job? = null
    @Volatile private var actions: List<Triple<LanguageServerSession, LanguageAction, JsonObject>> = emptyList()

    fun handle(intent: LanguageFeatureIntent) {
        when (intent) {
            LanguageFeatureIntent.CloseResult -> show(null).also { job?.cancel() }
            is LanguageFeatureIntent.RunAction -> run(intent.index)
            is LanguageFeatureIntent.Ask -> ask(intent)
        }
    }

    private fun ask(intent: LanguageFeatureIntent.Ask) {
        job?.cancel()
        val title = intent.query.title
        val location = intent.location
        val at = intent.position
        if (intent.query == LanguageQuery.Rename && intent.newName == null) {
            val text = stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text.orEmpty()
            return show(LanguageResult.Rename(title, location, at, wordAt(text, LspText.offsetOf(text, at))))
        }
        if (intent.query != LanguageQuery.Format && intent.query != LanguageQuery.Rename) show(LanguageResult.Asking(title))
        job = scope.launch {
            val answer = runCatching {
                when (intent.query) {
                    LanguageQuery.Definition -> places(title, requests.definition(location, at), "No definition found here")
                    LanguageQuery.References -> places(title, requests.references(location, at), "No references found")
                    LanguageQuery.Symbols -> places(title, requests.documentSymbols(location), "The server lists no symbols in this file")
                    LanguageQuery.Hover -> requests.hover(location, at)?.let { LanguageResult.Text(title, it) } ?: LanguageResult.Failed(title, "Nothing to show here")
                    LanguageQuery.Signature -> requests.signature(location, at)?.let { LanguageResult.Text(title, it) } ?: LanguageResult.Failed(title, "No signature here")
                    LanguageQuery.CodeActions -> {
                        val found = requests.codeActions(location, TextRange(at, at))
                        actions = found
                        if (found.isEmpty()) LanguageResult.Failed(title, "No code actions here") else LanguageResult.Actions(title, location, found.map { it.second })
                    }
                    LanguageQuery.Format -> {
                        requests.format(location).getOrThrow()
                        stateHolder.notify(NoticeTone.Done, "Formatted")
                        null
                    }
                    LanguageQuery.Rename -> {
                        val files = requests.rename(location, at, intent.newName?.trim().orEmpty().ifEmpty { throw IllegalArgumentException("Enter the new name") }).getOrThrow()
                        stateHolder.notify(NoticeTone.Done, "Renamed", if (files == 1) "in 1 file" else "in $files files")
                        null
                    }
                }
            }.getOrElse { failure -> LanguageResult.Failed(title, LanguageFailure.wordsOf(failure)) }
            show(answer)
        }
    }

    private fun run(index: Int) {
        val (session, _, action) = actions.getOrNull(index) ?: return
        show(null)
        scope.launch {
            requests.run(session, action).onFailure { stateHolder.notify(NoticeTone.HeadsUp, "Not applied", LanguageFailure.wordsOf(it)) }
        }
    }

    private fun places(title: String, places: List<LanguagePlace>, none: String): LanguageResult =
        if (places.isEmpty()) LanguageResult.Failed(title, none) else LanguageResult.Places(title, places)

    /** The identifier around [offset] in [text]. */
    private fun wordAt(text: String, offset: Int): String {
        fun part(char: Char) = char.isLetterOrDigit() || char == '_' || char == '$'
        var start = offset.coerceIn(0, text.length)
        var end = start
        while (start > 0 && part(text[start - 1])) start--
        while (end < text.length && part(text[end])) end++
        return text.substring(start, end)
    }

    private fun show(result: LanguageResult?) = stateHolder.update { it.copy(languageResult = result) }
}
