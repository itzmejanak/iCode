package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceListRulesTest {
    private fun workspace(name: String, openedAt: Long) =
        WorkspaceSummary(name, "/workspace/$name", branch = null, lastOpenedAt = openedAt, location = "/data/w/$name")

    private val revPlayer = workspace("RevPlayer", 50)
    private val bigBoy = workspace("bigBoy", 90)
    private val notes = workspace("notes", 10)
    private val all = listOf(notes, revPlayer, bigBoy)

    @Test fun `the home screen shows the five most recently opened`() {
        val many = (1..8).map { workspace("w$it", it.toLong()) }
        assertEquals(listOf("w8", "w7", "w6", "w5", "w4"), WorkspaceListRules.recent(many).map { it.name })
        assertEquals(listOf(bigBoy, revPlayer, notes), WorkspaceListRules.recent(all))
    }

    @Test fun `the summary totals only what is measured whole`() {
        val measured = mapOf(
            notes.location to StorageMeasurement(1_000_000),
            revPlayer.location to StorageMeasurement(2_000_000),
            bigBoy.location to StorageMeasurement(3_000_000),
        )
        assertEquals("3 workspaces · ${ByteSize.format(6_000_000)}", WorkspaceListRules.summary(all, measured))
        assertEquals("3 workspaces", WorkspaceListRules.summary(all, measured - bigBoy.location))
        assertEquals("3 workspaces", WorkspaceListRules.summary(all, measured + (bigBoy.location to StorageMeasurement(9, truncated = true))))
        assertEquals("1 workspace", WorkspaceListRules.summary(listOf(notes), emptyMap()))
        assertEquals("0 workspaces", WorkspaceListRules.summary(emptyList(), emptyMap()))
    }

    @Test fun `a size is said the same way everywhere`() {
        assertEquals("Measuring…", storageSizeLabel(null))
        assertEquals("Size exceeds scan limit", storageSizeLabel(StorageMeasurement(5, truncated = true)))
        assertEquals("Size unavailable", storageSizeLabel(StorageMeasurement(null)))
        assertEquals(ByteSize.format(2048), storageSizeLabel(StorageMeasurement(2048)))
    }
}
