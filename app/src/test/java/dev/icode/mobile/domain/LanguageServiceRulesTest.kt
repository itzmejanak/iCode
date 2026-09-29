package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun `blank command is rejected`() {
        assertNotNull(LanguageServiceRules.validate("server", " ", "*.kt", "{}"))
    }

    private fun config(id: String, name: String = id) = LanguageServiceConfiguration(id, name, "server", "*.kt", "", "{}")

    @Test fun `saving one service keeps another server running`() {
        val shown = LanguageServiceRules.rows(listOf(config("a"), config("b")), emptyList(), changed = null)
            .map { if (it.id == "a") it.copy(status = LanguageServiceStatus.Running, processId = 41) else it }
        val rows = LanguageServiceRules.rows(listOf(config("a"), config("b", "B edited")), shown, changed = "b")
        assertEquals(LanguageServiceStatus.Running, rows[0].status)
        assertEquals(41, rows[0].processId)
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

    @Test fun `a row shows initials and all files when no pattern is set`() {
        val row = LanguageServiceRules.rows(
            listOf(LanguageServiceConfiguration("x", "rust  analyzer", "ra", "", "", "{}")), emptyList(), changed = null,
        ).single()
        assertEquals("RA", row.initials)
        assertEquals("All files", row.patterns)
    }
}
