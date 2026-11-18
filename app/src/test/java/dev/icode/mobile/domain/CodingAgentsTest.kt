package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CodingAgentsTest {
    private fun agent(id: String, version: String, source: AgentSource? = AgentSource.Npm(id, version, emptyList(), emptyMap())) =
        CodingAgent(id, id, version, "", null, "MIT", "https://example.com", source)

    private fun installed(version: String) = InstalledAgent(version, listOf("/root/x"), emptyMap(), 3 * 1024 * 1024)

    @Test fun `an update is an installed agent the registry has another release of`() {
        val catalogue = listOf(agent("a", "2.0.0"), agent("b", "1.0.0"), agent("c", "1.0.0"))
        val updates = CodingAgents.updates(catalogue, mapOf("a" to installed("1.0.0"), "b" to installed("1.0.0")))
        assertEquals(listOf("a"), updates.map { it.id })
    }

    @Test fun `a row says the release, what it takes, and what an install downloads where that is known`() {
        assertEquals("Not installed · 2.0.0 · 48 MB download", CodingAgents.status(agent("a", "2.0.0"), null, 48L * 1024 * 1024))
        assertEquals("Not installed · 2.0.0", CodingAgents.status(agent("a", "2.0.0"), null, null))
        assertEquals("Installed · 2.0.0 · 3.0 MB", CodingAgents.status(agent("a", "2.0.0"), installed("2.0.0"), null).replace(',', '.'))
        assertEquals("1.0.0 · update to 2.0.0", CodingAgents.status(agent("a", "2.0.0"), installed("1.0.0"), null))
        assertEquals("No build for this phone", CodingAgents.status(agent("a", "2.0.0", source = null), null, null))
    }

    @Test fun `every offered agent names its own folders, and none is verified yet`() {
        assertEquals(CodingAgents.OFFERED.toSet(), CodingAgents.OWN.keys)
        assertEquals(emptySet<String>(), CodingAgents.VERIFIED)
    }
}
