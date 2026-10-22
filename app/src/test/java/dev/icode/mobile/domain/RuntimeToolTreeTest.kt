package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeToolTreeTest {
    private val sections = RuntimeToolTree.sections()
    private val sdk = sections.first().nodes.single { it is RuntimeToolNode.Tool && it.tool == RuntimeTools.AndroidSdk }
    private val ndk = sdk.children.filterIsInstance<RuntimeToolNode.Family>().single()

    private fun RuntimeToolNode.outline(): String =
        if (children.isEmpty()) title else "$title[${children.joinToString { it.outline() }}]"

    private fun RuntimeToolNode.tools(): List<RuntimeTool> =
        (if (this is RuntimeToolNode.Tool) listOf(tool) else emptyList()) + children.flatMap { it.tools() }

    @Test fun `the page lists the Android SDK's add-ons beneath it, and the NDK's revisions beneath the NDK`() {
        assertEquals(listOf("Used by iCode", "More tools"), sections.map { it.title })
        assertEquals(
            listOf("Git", "SSH client", "GnuPG", "ripgrep", "Android SDK[CMake 3.22.1, NDK (Side by side)[28.2.13676358, 27.0.12077973]]"),
            sections[0].nodes.map { it.outline() },
        )
        assertEquals(listOf("curl", "Python 3", "Build tools"), sections[1].nodes.map { it.outline() })
    }

    @Test fun `every tool is one row, in the catalog's order`() {
        assertEquals(RuntimeTools.all, sections.flatMap { it.nodes }.flatMap { it.tools() })
    }

    @Test fun `a row says only what no row above it says`() {
        val cmake = sdk.children.first()
        assertEquals(RuntimeTools.Cmake.usedBy, cmake.usedBy)
        assertEquals(emptyList<RuntimeTool>(), cmake.needs)
        // The family names what its revisions need beyond the SDK it is beneath; the revisions repeat none of it.
        assertEquals(RuntimeTools.Ndk.summary, ndk.summary)
        assertEquals(RuntimeTools.Ndk28.usedBy, ndk.usedBy)
        assertEquals(listOf(RuntimeTools.Cmake), ndk.needs)
        ndk.children.forEach { revision ->
            assertNull(revision.usedBy)
            assertEquals(emptyList<RuntimeTool>(), revision.needs)
        }
        assertEquals("For Android Gradle plugin 9.0–9.4", ndk.children.first().summary)
    }

    @Test fun `a family's tick is partly while some of its versions are ticked`() {
        assertEquals(RuntimeToolTick.Off, RuntimeToolTree.tick(ndk, setOf("android-sdk", "cmake")))
        assertEquals(RuntimeToolTick.Partly, RuntimeToolTree.tick(ndk, setOf("android-ndk-27")))
        assertEquals(RuntimeToolTick.On, RuntimeToolTree.tick(ndk, setOf("android-ndk-28", "android-ndk-27")))
        assertEquals(RuntimeToolTick.On, RuntimeToolTree.tick(sdk, setOf("android-sdk")))
    }

    @Test fun `a family's row ticks its newest version, with what it needs, and unticks them all`() {
        val ticked = RuntimeToolTree.toggle(setOf("git"), ndk)
        assertEquals(setOf("git", "android-ndk-28", "cmake", "android-sdk"), ticked)
        val both = ticked + "android-ndk-27"
        // What the versions needed stays: other tools may need it.
        assertEquals(setOf("git", "cmake", "android-sdk"), RuntimeToolTree.toggle(both, ndk))
        assertEquals(setOf("git", "cmake", "android-sdk"), RuntimeToolTree.toggle(setOf("git", "cmake", "android-sdk", "android-ndk-27"), ndk))
        // A tool's row is the tool's tick, kept whole.
        assertEquals(setOf("git"), RuntimeToolTree.toggle(both, sdk))
    }

    @Test fun `each tool is beneath one it requires, and a family's versions share their place, use and needs`() {
        RuntimeTools.all.forEach { tool ->
            tool.within?.let { assertTrue("${tool.id} is beneath $it, which it does not require", it in tool.requires) }
            assertEquals("${tool.id} has a family and a version, or neither", tool.family == null, tool.version == null)
        }
        RuntimeTools.all.filter { it.family != null }.groupBy { it.family }.values.forEach { versions ->
            assertEquals(1, versions.map { Triple(it.within, it.usedBy, it.requires) }.distinct().size)
        }
    }
}
