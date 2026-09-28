package dev.icode.mobile.ui.screens.workspace

import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceCreationParentTest {
    private val files = listOf(
        FileEntry("src", "/project/src", FileKind.Folder, depth = 0),
        FileEntry("components", "/project/src/components", FileKind.Folder, depth = 1),
        FileEntry("App.kt", "/project/src/components/App.kt", FileKind.Code, depth = 2),
        FileEntry("README.md", "/project/README.md", FileKind.Markdown, depth = 0),
    )

    @Test fun `selected folder receives new items inside itself`() {
        assertEquals("/project/src/components", creationParentLocation("/project", "/project/src/components", files))
    }

    @Test fun `selected nested file receives siblings`() {
        assertEquals("/project/src/components", creationParentLocation("/project", "/project/src/components/App.kt", files))
    }

    @Test fun `root file or stale selection uses workspace root`() {
        assertEquals("/project", creationParentLocation("/project", "/project/README.md", files))
        assertEquals("/project", creationParentLocation("/project", "/missing", files))
        assertEquals("/project", creationParentLocation("/project", "", files))
    }
}
