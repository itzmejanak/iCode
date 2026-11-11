package dev.icode.mobile.domain.syntax

import org.json.JSONObject

/** Where a grammar was copied from: a repository at one commit, under that repository's licence. */
data class GrammarSource(val key: String, val name: String, val url: String, val licence: String)

/**
 * A language the editor can colour (docs/syntax-highlighting-plan.md D3): its [id] (VS Code's language id
 * where VS Code has one), its TextMate [scope], its [grammar] and [configuration] as asset paths, the
 * languages [embedded] in it (scope to language id, as Sora reads them), the other grammars it [includes]
 * by scope, and where its grammar comes from: the [source] and the [path] in it, the [upstream] commit the
 * grammar file names as its own origin (null when the source is the origin), and the grammar's [licence].
 */
data class Language(
    val id: String,
    val scope: String,
    val grammar: String,
    val configuration: String?,
    val embedded: Map<String, String>,
    val includes: Set<String>,
    val source: GrammarSource,
    val path: String,
    val upstream: String?,
    val licence: String,
)

/**
 * The one catalog of languages: each declared once, in [ASSET], a data file a tool can write, so the
 * catalog grows to every language without a Kotlin file per entry. `FileTypes` names a language by its
 * id; the editor registers grammars from here; the provenance doc is checked against it (tests).
 */
class Languages internal constructor(val all: List<Language>) {
    private val ids: Map<String, Language> = all.associateBy { it.id }
    private val scopes: Map<String, Language> = all.associateBy { it.scope }

    init {
        require(ids.size == all.size) { "A language id is declared twice" }
        require(scopes.size == all.size) { "A scope is declared twice" }
    }

    fun byId(id: String): Language? = ids[id]

    fun byScope(scope: String): Language? = scopes[scope]

    companion object {
        /** The catalog, as an asset path. */
        const val ASSET = "textmate/catalog.json"

        /** The catalog as [ASSET] holds it. Throws for anything else. */
        fun parse(json: String): Languages {
            val catalog = JSONObject(json)
            val sources = catalog.getJSONObject("sources").let { all ->
                all.keys().asSequence().associateWith { key ->
                    val source = all.getJSONObject(key)
                    GrammarSource(key, source.getString("name"), source.getString("url"), source.getString("licence"))
                }
            }
            val languages = catalog.getJSONArray("languages")
            return Languages(
                (0 until languages.length()).map { index ->
                    val entry = languages.getJSONObject(index)
                    val sourceKey = entry.getString("source")
                    Language(
                        id = entry.getString("id"),
                        scope = entry.getString("scope"),
                        grammar = entry.getString("grammar"),
                        configuration = entry.optString("configuration").ifEmpty { null },
                        embedded = entry.optJSONObject("embedded")?.let { map -> map.keys().asSequence().associateWith(map::getString) }.orEmpty(),
                        includes = entry.optJSONArray("includes")?.let { list -> (0 until list.length()).map(list::getString).toSet() }.orEmpty(),
                        source = checkNotNull(sources[sourceKey]) { "Unknown grammar source $sourceKey" },
                        path = entry.getString("path"),
                        upstream = if (entry.isNull("upstream")) null else entry.getString("upstream"),
                        licence = entry.getString("licence"),
                    )
                },
            )
        }
    }
}
