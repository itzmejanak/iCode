package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileViewersTest {
    @Test fun `a picture opens in the image viewer`() {
        listOf("photo.jpg", "IMG_0001.HEIC", "icon.webp", "assets/logo.png").forEach { assertEquals(it, FileViewer.Image, FileViewer.of(it)) }
    }

    @Test fun `every other kind opens in the editor, an SVG's source too`() {
        listOf("logo.svg", "Main.kt", "notes.md", "report.pdf", "README").forEach { assertNull(it, FileViewer.of(it)) }
    }
}
