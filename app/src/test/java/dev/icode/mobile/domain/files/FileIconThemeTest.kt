package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileTypes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The file and folder icons, as the shipped Material Icon Theme gives them (docs/workspaces-and-files.md). */
class FileIconThemeTest {
    private val folder = File("src/main/assets/file-icons")
    private val theme = FileIconTheme.parse(File(folder, "theme.json").readText())

    @Test fun `every icon the theme names is shipped, and nothing else is`() {
        val shipped = File(folder, "icons").list()!!.map { it.removeSuffix(".svg") }.toSet()
        assertEquals(emptySet<String>(), theme.icons - shipped)
        assertEquals(emptySet<String>(), shipped - theme.icons)
        assertTrue(theme.icons.size > 1000)
    }

    @Test fun `a file by its path, its name, its longest extension, its language, then the default`() {
        mapOf(
            "lib.dom.d.ts" to "typescript-def",
            "thrir-client.ts" to "typescript",
            "index.mts" to "typescript",
            "App.tsx" to "react_ts",
            // The theme's own file names before the extension: a Next.js route is a route.
            "route.js" to "routing",
            "server.js" to "javascript",
            "package.json" to "nodejs",
            "tsconfig.json" to "tsconfig",
            "Dockerfile" to "docker",
            ".gitignore" to "git",
            "README.md" to "readme",
            "notes.md" to "markdown",
            "Main.kt" to "kotlin",
            "LICENSE" to "license",
            "logo.svg" to "svg",
            "clip.mp4" to "video",
            "paper.pdf" to "pdf",
        ).forEach { (name, icon) -> assertEquals(name, icon, theme.fileIcon(name)) }
        // No name, extension or language the theme knows: the default file, never the plain-text language's icon.
        assertEquals(theme.file, theme.fileIcon("data.xyz"))
    }

    @Test fun `a file whose bytes said what its name could not takes its kind's icon`() {
        assertEquals("typescript", theme.fileIcon("news.ts"))
        assertEquals("video", theme.fileIcon("news.ts", type = FileTypes.Video))
        assertEquals("image", theme.fileIcon("download", type = FileTypes.Image))
        // The kind its name gives is no override.
        assertEquals("typescript", theme.fileIcon("news.ts", type = FileTypes.of("news.ts")))
    }

    @Test fun `a folder by its path and its name, and its open icon while expanded`() {
        assertEquals("folder-src", theme.folderIcon("src"))
        assertEquals("folder-src-open", theme.folderIcon("src", open = true))
        assertEquals("folder-node", theme.folderIcon("node_modules"))
        assertEquals("folder-git", theme.folderIcon(".git"))
        assertEquals("folder-github", theme.folderIcon(".github"))
        assertEquals("folder-components", theme.folderIcon("Components"))
        assertEquals(theme.folder, theme.folderIcon("holiday photos"))
        assertEquals("folder-open", theme.folderIcon("holiday photos", open = true))
        // A folder's own icon in the folder it belongs in, as VS Code reads `.github/workflows`.
        assertTrue(theme.folderIcon("workflows", parent = ".github") != theme.folderIcon("workflows"))
    }

    @Test fun `on a light theme the theme's light icons come first`() {
        val lit = theme.icons.filter { it.endsWith("_light") }
        assertTrue(lit.isNotEmpty())
        val name = listOf("cursor", "jinja", "intellij").first { theme.folderIcon(it, light = true).endsWith("_light") }
        assertTrue(theme.folderIcon(name, light = true) != theme.folderIcon(name))
        assertTrue(theme.folderIcon(name, open = true, light = true).endsWith("_light"))
    }
}
