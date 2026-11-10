package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageServiceRulesTest {
    @Test fun `quoted command becomes arguments without shell interpretation`() {
        assertEquals(
            listOf("/usr/bin/server", "--label", "a b", "--literal=\$HOME"),
            LanguageServiceRules.commandArguments("/usr/bin/server --label 'a b' '--literal=\$HOME'"),
        )
    }

    @Test fun `unfinished quoting is rejected`() {
        val error = runCatching { LanguageServiceRules.commandArguments("server 'unfinished") }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test fun `a blank command, a bad quote or a bad variable cannot be saved`() {
        assertNotNull(LanguageServiceRules.validate(config("a").copy(command = " ")))
        assertNotNull(LanguageServiceRules.validate(config("a").copy(command = "server 'open")))
        assertNotNull(LanguageServiceRules.validate(config("a").copy(environment = "not a variable")))
        assertNull(LanguageServiceRules.validate(config("a").copy(environment = "# a comment\nPYTHONPATH=/workspace/x\n\nEMPTY=")))
    }

    private fun config(id: String, name: String = id) = LanguageServiceConfiguration(id, name, "server", patterns = "*.kt")

    @Test fun `saving one service keeps another server running`() {
        val shown = LanguageServiceRules.rows(listOf(config("a"), config("b")), emptyList(), changed = null)
            .map { if (it.id == "a") it.copy(status = LanguageServiceStatus.Running, processId = 41, capabilities = listOf("Hover")) else it }
        val rows = LanguageServiceRules.rows(listOf(config("a"), config("b", "B edited")), shown, changed = "b")
        assertEquals(LanguageServiceStatus.Running, rows[0].status)
        assertEquals(41, rows[0].processId)
        assertEquals(listOf("Hover"), rows[0].capabilities)
        assertEquals("B edited", rows[1].name)
    }

    @Test fun `the saved service starts from stopped`() {
        val shown = LanguageServiceRules.rows(listOf(config("a")), emptyList(), changed = null)
            .map { it.copy(status = LanguageServiceStatus.Failed, statusMessage = "exited with 1") }
        val row = LanguageServiceRules.rows(listOf(config("a")), shown, changed = "a").single()
        assertEquals(LanguageServiceStatus.Stopped, row.status)
        assertNull(row.statusMessage)
    }

    @Test fun `a new service is added stopped, in configuration order`() {
        val shown = LanguageServiceRules.rows(listOf(config("a")), emptyList(), changed = null)
            .map { it.copy(status = LanguageServiceStatus.Starting) }
        val rows = LanguageServiceRules.rows(listOf(config("a"), config("new")), shown, changed = "new")
        assertEquals(listOf("a", "new"), rows.map { it.id })
        assertEquals(listOf(LanguageServiceStatus.Starting, LanguageServiceStatus.Stopped), rows.map { it.status })
    }

    @Test fun `a service serving nothing says it starts from its page`() {
        val row = LanguageServiceRules.rows(listOf(LanguageServiceConfiguration("x", "rust  analyzer", "ra")), emptyList(), changed = null).single()
        assertEquals("RA", row.initials)
        assertEquals("Started from its page", row.servesLabel)
        assertEquals("python, *.pyi", LanguageServiceRules.row(LanguageServiceConfiguration("p", "P", "pylsp", languages = "python", patterns = "*.pyi")).servesLabel)
    }

    @Test fun `a file is served by its language or a pattern of its name`() {
        val typescript = LanguageServiceConfiguration("t", "T", "tls", languages = "typescript, typescriptreact", patterns = "*.{mts,cts} Dockerfile")
        assertTrue(LanguageServiceRules.serves(typescript, "App.tsx", "typescriptreact"))
        assertTrue(LanguageServiceRules.serves(typescript, "x.mts", "plaintext"))
        assertTrue(LanguageServiceRules.serves(typescript, "Dockerfile", "dockerfile"))
        assertFalse(LanguageServiceRules.serves(typescript, "x.py", "python"))
        assertFalse(LanguageServiceRules.serves(typescript, "x.mtsx", "plaintext"))
        assertFalse(LanguageServiceRules.serves(LanguageServiceConfiguration("n", "N", "n"), "x.ts", "typescript"))
    }

    @Test fun `iCode's file types are LSP's language identifiers, shell scripts named as LSP names them`() {
        assertEquals("shellscript", LanguageServiceRules.languageIdOf(FileTypes.of("build.sh")))
        assertEquals("typescriptreact", LanguageServiceRules.languageIdOf(FileTypes.of("App.tsx")))
        assertEquals("cpp", LanguageServiceRules.languageIdOf(FileTypes.of("x.hpp")))
        assertEquals("plaintext", LanguageServiceRules.languageIdOf(FileTypes.of("notes")))
    }

    @Test fun `completions are asked as a word is typed or on the server's own trigger, never after a space`() {
        val triggers = setOf(".", "\"", "<")
        listOf("a", "Z", "7", "_", "$", ".", "\"").forEach { assertTrue(it, LanguageServiceRules.asksCompletion(it, triggers)) }
        listOf(" ", ";", "(", "\n", "=").forEach { assertFalse(it, LanguageServiceRules.asksCompletion(it, triggers)) }
        assertFalse(LanguageServiceRules.asksCompletion(null, triggers))
        assertFalse(LanguageServiceRules.asksCompletion(".", emptySet()))
    }

    @Test fun `environment lines become variables, and a malformed one is refused`() {
        assertEquals(mapOf("A" to "1", "B" to "x=y"), LanguageServiceRules.environmentOf("A=1\n  B=x=y  \n#C=3"))
        assertThrows(IllegalArgumentException::class.java) { LanguageServiceRules.environmentOf("1A=2") }
    }

    @Test fun `every preset can be saved as it is`() {
        LanguageServicePresets.all.forEach { preset -> assertNull(preset.id, LanguageServiceRules.validate(preset.configuration.copy(id = preset.id))) }
        assertEquals(LanguageServicePresets.all.size, LanguageServicePresets.all.map { it.id }.toSet().size)
    }
}
