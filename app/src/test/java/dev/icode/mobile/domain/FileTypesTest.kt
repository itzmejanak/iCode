package dev.icode.mobile.domain

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {
    @Test fun `react extensions select react grammars`() {
        assertEquals("source.tsx", FileTypes.of("App.tsx").grammar)
        assertEquals("source.js.jsx", FileTypes.of("View.jsx").grammar)
    }

    @Test fun `unknown and misleading names are plain text`() {
        listOf("Makefile", "Dockerfile", "notes.txt", "LICENSE", "archive.").forEach { name ->
            assertEquals(name, FileTypes.PlainText, FileTypes.of(name))
        }
    }

    @Test fun `case does not matter`() {
        assertEquals(FileTypes.Markdown, FileTypes.of("README.MD"))
        assertEquals("source.kotlin", FileTypes.of("Build.KTS").grammar)
    }

    @Test fun `each type has a family for the lists`() {
        assertEquals(FileFamily.Code, FileTypes.of("Main.kt").family)
        assertEquals(FileFamily.Code, FileTypes.of("server.mjs").family)
        assertEquals(FileFamily.Data, FileTypes.of("package.json").family)
        assertEquals(FileFamily.Data, FileTypes.of("config.yml").family)
        assertEquals(FileFamily.Document, FileTypes.of("notes.md").family)
    }

    @Test fun `a name or an extension belongs to one type only`() {
        val extensions = FileTypes.rules.flatMap { it.extensions }
        val names = FileTypes.rules.flatMap { it.names }
        assertEquals(extensions.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(names.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
        assertEquals(FileTypes.rules.map { it.type.id }.size, FileTypes.rules.map { it.type.id }.toSet().size)
    }

    @Test fun `every shipped grammar is reached, and every grammar named is shipped`() {
        val registry = JSONObject(File("src/main/assets/textmate/languages.json").readText()).getJSONArray("languages")
        val shipped = (0 until registry.length()).map { registry.getJSONObject(it).getString("scopeName") }.toSet()
        val named = FileTypes.rules.mapNotNull { it.type.grammar }.toSet()
        assertEquals(shipped, named)
        assertTrue(shipped.isNotEmpty())
    }
}
