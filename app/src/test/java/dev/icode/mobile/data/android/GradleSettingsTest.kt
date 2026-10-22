package dev.icode.mobile.data.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleSettingsTest {
    private val block = GradleSettings.block(1024)

    @Test
    fun `the heap is a quarter of the phone's memory, between 768 MB and 2 GB`() {
        val gib = 1024L * 1024L * 1024L
        assertEquals(768, GradleSettings.heapMegabytes(2 * gib))
        assertEquals(1536, GradleSettings.heapMegabytes(6 * gib))
        assertEquals(2048, GradleSettings.heapMegabytes(12 * gib))
    }

    @Test
    fun `the block points aapt2 at the ARM64 build and keeps Gradle to few processes`() {
        val lines = block.lines()
        assertEquals(GradleSettings.BEGIN, lines.first())
        assertEquals(GradleSettings.END, lines.last())
        assertTrue("android.aapt2FromMavenOverride=/root/Android/Sdk/build-tools/36.0.0/aapt2" in lines)
        assertTrue("org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" in lines)
        assertTrue("org.gradle.workers.max=2" in lines)
        assertTrue("org.gradle.daemon=false" in lines)
    }

    @Test
    fun `the block is added after the user's own lines, replaced in place of the old one, and taken out alone`() {
        val users = "org.gradle.caching=true\n"
        val added = GradleSettings.withBlock(users, block)
        assertEquals("org.gradle.caching=true\n\n$block\n", added)
        val replaced = GradleSettings.withBlock(added, GradleSettings.block(2048))
        assertEquals("org.gradle.caching=true\n\n${GradleSettings.block(2048)}\n", replaced)
        assertEquals(users, GradleSettings.withBlock(replaced, null))
        assertEquals("", GradleSettings.withBlock(GradleSettings.withBlock("", block), null))
    }

    @Test
    fun `the init script sets the default build tools and CMake of every kind of Android module`() {
        val script = GradleSettings.INIT_SCRIPT
        listOf("com.android.application", "com.android.library", "com.android.test", "com.android.dynamic-feature")
            .forEach { assertTrue(it, "'$it'" in script) }
        assertTrue("buildToolsVersion = '36.0.0'" in script)
        assertTrue("def icodeCmake = '3.22.1'" in script)
        assertTrue("android.externalNativeBuild.cmake.version = icodeCmake" in script)
    }

    @Test
    fun `the init script checks a module's NDK and CMake against what Runtime Tools installs, before the Android plugin`() {
        val script = GradleSettings.INIT_SCRIPT
        assertTrue("def icodeSdk = new File('/root/Android/Sdk')" in script)
        assertTrue("def icodeNdks = ['28.2.13676358', '27.0.12077973']" in script)
        // Each NDK's Android Gradle plugins, from the catalog that names its row.
        assertTrue("def icodeDefaults = [['9.0', '9.4', '28.2.13676358'], ['8.7', '8.13', '27.0.12077973']]" in script)
        assertTrue("def icodeWhere = 'in Settings › Runtime Environment › Runtime Tools'" in script)
        // Registered for every project before its build script runs: ahead of the plugin's own afterEvaluate.
        assertTrue("project.afterEvaluate { if (project.state.failure == null) icodeCheck(project) }" in script)
        // Set aside before it stops, so the plugin's afterEvaluate, which still runs, resolves no NDK or CMake.
        assertTrue("nativeBuild.cmake.path = null" in script && "nativeBuild.ndkBuild.path = null" in script)
        assertTrue("(head[18] & 0xff) == 183" in script)
        assertTrue('$' !in script)
    }
}
