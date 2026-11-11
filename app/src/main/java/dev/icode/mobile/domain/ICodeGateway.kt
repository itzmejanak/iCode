package dev.icode.mobile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Stable UI boundary for the later runtime/storage/Git implementation.
 *
 * Production adapters will implement this interface. Compose never shells out or reads files directly.
 */
interface ICodeGateway {
    val snapshot: StateFlow<ICodeSnapshot>
    val effects: Flow<ICodeEffect>
        get() = emptyFlow()

    /** The language servers' completions, asked from the editor's completion thread (docs/lsp-plan.md §7). */
    val languageCompletions: LanguageCompletions
        get() = LanguageCompletions.None

    /** The language servers' semantic tokens, asked by the editor (docs/syntax-highlighting-plan.md D8). */
    val languageHighlights: LanguageHighlights
        get() = LanguageHighlights.None

    fun dispatch(intent: ICodeIntent)
}
