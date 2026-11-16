package dev.icode.mobile.ui.syntax

import dev.icode.mobile.domain.syntax.Language
import dev.icode.mobile.domain.syntax.Languages
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads one language's grammar with what it includes, on demand (docs/editor-and-language-services.md).
 * Each [load] fills a new registry ([newRegistry]), since a tm4e grammar must not be shared between
 * threads: each editor and the read-only thread get their own. A grammar that fails to [add]
 * is that language's failure alone. It is reported once ([onFailure]) and never tried again; a language
 * that includes it still loads, with those rules left plain, as for a scope the catalog does not ship.
 *
 * [load] runs on one thread only (the engine's loader); [failure] may be read from any thread.
 */
internal class GrammarLoader<R>(
    val catalog: Languages,
    private val newRegistry: () -> R,
    private val add: R.(Language) -> Unit,
    private val onFailure: (Language, Throwable) -> Unit,
) {
    private val failures = ConcurrentHashMap<String, Throwable>()

    /** Why language [id]'s grammar did not load, or null if it did or has not been asked for. */
    fun failure(id: String): Throwable? = failures[id]

    /**
     * [language] and every language of the catalog its grammar includes, directly or through another,
     * [language] first. An included scope the catalog does not list is left out: tm4e leaves it plain.
     */
    fun closure(language: Language): List<Language> {
        val found = linkedMapOf(language.scope to language)
        val next = ArrayDeque(listOf(language))
        while (next.isNotEmpty()) {
            next.removeFirst().includes.mapNotNull(catalog::byScope).filter { it.scope !in found }.forEach {
                found[it.scope] = it
                next.addLast(it)
            }
        }
        return found.values.toList()
    }

    /** A new registry holding language [id]'s grammar and every grammar of its [closure] that loads. Throws when [id]'s own grammar fails. */
    fun load(id: String): R {
        val language = checkNotNull(catalog.byId(id)) { "No language $id in the catalog" }
        failures[id]?.let { throw it }
        val registry = newRegistry()
        closure(language).filterNot { failures.containsKey(it.id) }.forEach { member ->
            runCatching { registry.add(member) }.onFailure { error ->
                failures[member.id] = error
                onFailure(member, error)
                if (member === language) throw error
            }
        }
        return registry
    }
}
