package dev.icode.mobile.data.runtime

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The website's manifest overrides the built-in one only when the app can use it (contracts/api-v1.md § Runtime). */
class ManifestSourceTest {
    private val bundled = File("src/main/assets/${RuntimeManifest.ASSET}").readText()

    private fun website(change: JSONObject.() -> Unit): String = JSONObject(bundled).apply(change).toString()

    @Test fun `a valid website manifest overrides the built-in one, with its revision`() {
        val choice = ManifestSource.choose(bundled, website { put("revision", 7); getJSONObject("proot").put("version", "9.9.9") })
        assertTrue(choice.origin.fromWebsite)
        assertEquals(7, choice.origin.revision)
        assertEquals("9.9.9", choice.manifest.prootVersion)
    }

    @Test fun `no website copy means the built-in one`() {
        val choice = ManifestSource.choose(bundled, null)
        assertFalse(choice.origin.fromWebsite)
        assertEquals(RuntimeManifest.parse(bundled).prootVersion, choice.manifest.prootVersion)
    }

    @Test fun `a website manifest that fails the app's checks is ignored`() {
        assertFalse(ManifestSource.choose(bundled, website { put("schemaVersion", 2) }).origin.fromWebsite)
        assertFalse(ManifestSource.choose(bundled, website { getJSONObject("rootfs").remove("sha256") }).origin.fromWebsite)
        assertFalse(ManifestSource.choose(bundled, "not json").origin.fromWebsite)
    }

    @Test fun `a website manifest for another architecture is ignored`() {
        assertFalse(ManifestSource.choose(bundled, website { put("architecture", "x86_64") }).origin.fromWebsite)
    }
}
