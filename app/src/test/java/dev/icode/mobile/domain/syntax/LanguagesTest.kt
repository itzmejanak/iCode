package dev.icode.mobile.domain.syntax

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LanguagesTest {
    private val catalog = Languages.parse(File("src/main/assets", Languages.ASSET).readText())

    @Test fun `a language by its id or its scope`() {
        val shell = checkNotNull(catalog.byId("shellscript"))
        assertEquals("source.shell", shell.scope)
        assertEquals(shell, catalog.byScope("source.shell"))
        assertEquals("textmate/shellscript/syntaxes/shell-unix-bash.tmLanguage.json", shell.grammar)
        assertEquals("VS Code 307b60e7", shell.source.name)
        assertEquals("extensions/shellscript", shell.path)
        assertNull(catalog.byId("source.shell"))
        assertNull(catalog.byScope("shellscript"))
    }

    @Test fun `optional fields read as absent`() {
        val sassdoc = checkNotNull(catalog.byId("sassdoc"))
        assertNull(sassdoc.configuration)
        assertEquals(emptyMap<String, String>(), sassdoc.embedded)
        assertNull(checkNotNull(catalog.byId("kotlin")).upstream)
        assertEquals(setOf("source.css", "source.js"), catalog.byId("html")?.includes)
    }

    @Test fun `a language or a scope is declared once`() {
        val source = """"sources": {"s": {"name": "S", "url": "u", "licence": "MIT"}}"""
        fun entry(id: String, scope: String) =
            """{"id": "$id", "scope": "$scope", "grammar": "g", "source": "s", "path": "p", "upstream": null, "licence": "MIT"}"""
        assertThrows(IllegalArgumentException::class.java) { Languages.parse("""{$source, "languages": [${entry("a", "x")}, ${entry("a", "y")}]}""") }
        assertThrows(IllegalArgumentException::class.java) { Languages.parse("""{$source, "languages": [${entry("a", "x")}, ${entry("b", "x")}]}""") }
        assertEquals(2, Languages.parse("""{$source, "languages": [${entry("a", "x")}, ${entry("b", "y")}]}""").all.size)
    }
}
