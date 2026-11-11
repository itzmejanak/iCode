package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.syntax.Language
import dev.icode.mobile.domain.syntax.Languages
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language catalog (`assets/textmate/catalog.json`, docs/syntax-highlighting-plan.md D3) against
 * what it describes: the assets, the grammars' own scopes, includes and origins, and the provenance doc.
 */
class LanguageCatalogTest {
    private val catalog = ShippedGrammars.catalog

    @Test fun `every language's grammar and configuration are shipped`() {
        val missing = catalog.all.flatMap { listOfNotNull(it.grammar, it.configuration) }.filterNot { ShippedGrammars.asset(it).isFile }
        assertEquals(emptyList<String>(), missing)
        assertTrue(catalog.all.size >= 42)
    }

    @Test fun `every asset folder holds a language's grammar`() {
        val folders = ShippedGrammars.asset("textmate").listFiles().orEmpty().filter(File::isDirectory).map { it.name }.toSet()
        val used = catalog.all.map { it.grammar.split('/')[1] }.toSet()
        assertEquals(emptySet<String>(), folders - used)
    }

    /** A grammar declares its own scope; one that disagrees with the catalog would fail to register in Sora. */
    @Test fun `each grammar's own scope is the catalog's`() {
        val wrong = catalog.all.filter { grammarScope(ShippedGrammars.asset(it.grammar)) != it.scope }.map { it.id }
        assertEquals(emptyList<String>(), wrong)
    }

    /** The includes are what the engine loads with a language (S3); the grammar files are what they must say. */
    @Test fun `each language's includes are what its grammar pulls in`() {
        val pulled = ShippedGrammars.all.associate { it.scope to it.includes - it.scope }
        val wrong = catalog.all.filter { it.includes != pulled[it.scope] }.map { "${it.id}: ${pulled[it.scope].orEmpty().sorted()}" }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun `what a language embeds is named by catalog ids`() {
        val unknown = catalog.all.flatMap { language -> language.embedded.values.filter { catalog.byId(it) == null }.map { "${language.id}: $it" } }
        assertEquals(emptyList<String>(), unknown)
        assertEquals("javascript", catalog.byId("html")?.embedded?.get("source.js"))
    }

    /**
     * A grammar file names the commit it was converted from (`version`); the catalog records the same.
     * Shiki's grammars keep no `version`: Shiki's index names each one's file at its commit, which the
     * catalog records instead (tools/import-grammars.py).
     */
    @Test fun `each language's upstream is the commit its grammar names`() {
        val wrong = catalog.all.filter { language ->
            val file = ShippedGrammars.asset(language.grammar)
            val version = if (file.name.endsWith(".json")) JSONObject(file.readText()).optString("version").ifEmpty { null } else null
            when {
                version != null -> version != language.upstream
                language.source.key == "shiki" -> language.upstream != null && !SHIKI_UPSTREAM.matches(language.upstream!!)
                else -> language.upstream != null
            }
        }.map { it.id }
        assertEquals(emptyList<String>(), wrong)
    }

    /**
     * docs/editor-grammar-provenance.md records every shipped grammar's source, upstream and licence
     * once, in the tables under "Shipped grammars"; they are the catalog's, row for row. On a mismatch the
     * message is the tables as they must read.
     */
    @Test fun `the provenance doc's tables are the catalog's`() {
        val doc = File("../docs/editor-grammar-provenance.md").readLines()
        val start = doc.indexOf(PROVENANCE_HEADING)
        assertTrue("$PROVENANCE_HEADING is missing", start >= 0)
        val section = doc.drop(start + 1).takeWhile { !it.startsWith("## ") }
        val expected = provenanceTables(catalog)
        assertEquals(expected.joinToString("\n"), section.filter { it.startsWith("|") }.joinToString("\n"))
    }

    private fun grammarScope(file: File): String? =
        if (file.name.endsWith(".json")) JSONObject(file.readText()).optString("scopeName")
        else PLIST_SCOPE.find(file.readText())?.groupValues?.get(1)

    private companion object {
        const val PROVENANCE_HEADING = "## Shipped grammars"
        val PLIST_SCOPE = Regex("<key>scopeName</key>\\s*<string>([^<]*)</string>")
        val SHIKI_UPSTREAM = Regex("https://github.com/[^/]+/[^/]+/blob/[0-9a-f]{40}/.+")

        fun provenanceTables(catalog: Languages): List<String> {
            val sources = catalog.all.map { it.source }.distinct()
            return listOf("| Source | Where | Licence |", "|---|---|---|") +
                sources.map { "| ${it.name} | ${it.url} | ${it.licence} |" } +
                listOf("| Language | Scope | Copied from | Upstream | Licence |", "|---|---|---|---|---|") +
                catalog.all.map { "| `${it.id}` | `${it.scope}` | ${it.source.name} `${it.path}` | ${upstream(it)} | ${it.licence} |" }
        }

        /** `owner/repo@commit` as a link, or the source itself when the grammar names no other origin. */
        fun upstream(language: Language): String = language.upstream?.let { url ->
            val match = Regex("https://github.com/([^/]+/[^/]+)/(?:commit|blob)/([0-9a-f]{8})").find(url)
            match?.let { "[${it.groupValues[1]}@${it.groupValues[2]}]($url)" } ?: url
        } ?: "None: ${language.source.name} is the origin"
    }
}
