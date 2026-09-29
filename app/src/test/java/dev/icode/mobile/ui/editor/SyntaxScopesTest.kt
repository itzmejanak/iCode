package dev.icode.mobile.ui.editor

import dev.icode.mobile.designsystem.SyntaxRole
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxScopesTest {
    private val selectors = syntaxScopes.flatMap { (role, list) -> list.map { it to role } }

    @Test fun `a selector belongs to one role, and every role is used`() {
        val repeated = selectors.groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet<String>(), repeated)
        assertEquals(SyntaxRole.entries.toSet(), syntaxScopes.map { it.first }.toSet())
    }

    /**
     * Nothing left over: every scope any shipped grammar can give a token is
     * either placed in a role or, on purpose, left to what encloses it. A new
     * grammar with a scope nobody decided about fails here, naming it.
     */
    @Test fun `every scope of every shipped grammar is placed or deliberately inherited`() {
        val simple = selectors.filter { ' ' !in it.first }.toMap()
        val undecided = grammarScopes().filter { scope ->
            prefixes(scope).none { it in simple } && inheritedScopes.none { scope == it || scope.startsWith("$it.") }
        }
        assertEquals(emptyList<String>(), undecided.sorted())
    }

    @Test fun `the grammars were read`() {
        val scopes = grammarScopes()
        assertTrue(scopes.size > 2000)
        assertTrue("keyword.control.kotlin" in scopes)
    }

    private fun prefixes(scope: String): List<String> {
        val parts = scope.split('.')
        return (parts.size downTo 1).map { parts.take(it).joinToString(".") }
    }

    private fun grammarScopes(): Set<String> {
        val assets = File("src/main/assets")
        val languages = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        return (0 until languages.length()).flatMap { index ->
            val file = File(assets, languages.getJSONObject(index).getString("grammar"))
            val names = if (file.name.endsWith(".json")) {
                val grammar = JSONObject(file.readText())
                grammar.keys().asSequence().filter { it != "name" }.toList().flatMap { jsonNames(grammar.get(it)) }
            } else {
                // A plist: the grammar's own title is the first <key>name</key>, not a scope.
                PLIST_NAME.findAll(file.readText()).map { it.groupValues[2] }.drop(1).toList()
            }
            names.flatMap { it.split(' ').filter(String::isNotEmpty) }
        }.toSet()
    }

    private fun jsonNames(value: Any?): List<String> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().flatMap { key: String ->
            val child = value.get(key)
            if ((key == "name" || key == "contentName") && child is String) listOf(child) else jsonNames(child)
        }
        is JSONArray -> (0 until value.length()).flatMap { jsonNames(value.get(it)) }
        else -> emptyList()
    }

    private companion object {
        val PLIST_NAME = Regex("<key>(name|contentName)</key>\\s*<string>([^<]*)</string>")
    }
}
