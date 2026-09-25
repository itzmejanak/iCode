package dev.icode.mobile.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLanguagesTest {
    @Test fun `react extensions select react grammars`() {
        assertEquals("source.tsx", scopeForFile("App.tsx"))
        assertEquals("source.js.jsx", scopeForFile("View.jsx"))
    }

    @Test fun `unknown and misleading filenames remain plain text`() {
        assertNull(scopeForFile("Makefile"))
        assertNull(scopeForFile("Dockerfile"))
        assertNull(scopeForFile("notes.txt"))
    }

    @Test fun `markdown extension enables source and preview`() {
        assertEquals("text.html.markdown", scopeForFile("README.MD"))
        assertTrue(isMarkdownFile("README.MD"))
    }
}
