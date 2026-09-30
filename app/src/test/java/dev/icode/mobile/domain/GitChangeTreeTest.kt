package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GitChangeTreeTest {
    private fun change(path: String, status: String = GitStatusCodes.UNTRACKED) =
        GitChange(status, path.trimEnd('/').substringAfterLast('/'), path, staged = false)

    private val readme = change("README.md", GitStatusCodes.MODIFIED)
    private val app = change("src/main/App.kt", GitStatusCodes.MODIFIED)
    private val util = change("src/main/Util.kt")
    private val appTest = change("src/test/AppTest.kt")
    private val red = change("colors/red.txt")
    private val blue = change("colors/Blue.txt")
    private val intro = change("docs/guide/intro.md")
    private val all = listOf(readme, app, util, appTest, red, blue, intro)

    private fun shape(rows: List<GitChangeRow>) = rows.map { row ->
        "  ".repeat(row.depth) + when (row) {
            is GitChangeRow.Folder -> "${if (row.expanded) "v" else ">"} ${row.name} (${row.changes})"
            is GitChangeRow.File -> row.change.name
        }
    }

    @Test fun `changes sit under their folders, folders first, each by name`() {
        assertEquals(
            listOf(
                "v colors (2)",
                "  Blue.txt",
                "  red.txt",
                "v docs/guide (1)",
                "  intro.md",
                "v src (3)",
                "  v main (2)",
                "    App.kt",
                "    Util.kt",
                "  v test (1)",
                "    AppTest.kt",
                "README.md",
            ),
            shape(GitChangeTree.rows(all, collapsed = emptySet())),
        )
    }

    @Test fun `a collapsed folder hides what is in it, and says how many`() {
        val rows = GitChangeTree.rows(all, collapsed = setOf("src", "docs/guide"))
        assertEquals(listOf("v colors (2)", "  Blue.txt", "  red.txt", "> docs/guide (1)", "> src (3)", "README.md"), shape(rows))
        // A folder standing for a chain is collapsed by the path of its last.
        assertEquals("docs/guide", (rows[3] as GitChangeRow.Folder).path)
    }

    @Test fun `a folder Git reports whole is a row of its own`() {
        val nested = change("vendor/lib/")
        assertEquals(listOf("v vendor (1)", "  lib"), shape(GitChangeTree.rows(listOf(nested), collapsed = emptySet())))
    }
}
