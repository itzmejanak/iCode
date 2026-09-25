package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
