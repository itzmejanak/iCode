package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.RuntimeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileFoldersTest {
    private val root = "/data/data/dev.icode.mobile.debug/files/workspaces/iCode"

    @Test fun `a file's folder is said in its workspace, never the phone's folders`() {
        assertEquals(".github/ISSUE_TEMPLATE", FileFolders.of("$root/.github/ISSUE_TEMPLATE/bug_report.yml", root, "iCode"))
        assertEquals("docs", FileFolders.of("$root/docs/plan.md", root, "iCode"))
    }

    @Test fun `a file at the workspace's top is said by the workspace's name`() {
        assertEquals("iCode", FileFolders.of("$root/CHANGELOG.md", root, "iCode"))
    }

    @Test fun `a file outside the workspace keeps its own folder`() {
        assertEquals("/storage/emulated/0/Download", FileFolders.of("/storage/emulated/0/Download/a.txt", root, "iCode"))
        assertNull(FileFolders.relative("${root}x/a.txt", root))
        assertEquals("docs/plan.md", FileFolders.relative("$root/docs/plan.md", "$root/"))
    }

    @Test fun `a release is said by its name, and its codename apart`() {
        val details = RuntimeDetails(release = "Ubuntu 24.04.5 LTS (noble) base")
        assertEquals("Ubuntu 24.04.5 LTS", details.releaseName)
        assertEquals("noble", details.codename)
        assertEquals("Debian 12" to null, RuntimeDetails(release = "Debian 12").let { it.releaseName to it.codename })
        assertNull(RuntimeDetails().releaseName)
    }
}
