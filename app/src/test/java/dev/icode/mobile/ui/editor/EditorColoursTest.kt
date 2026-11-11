package dev.icode.mobile.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** [EditorColours] on the JVM: joni, so only what does not need Oniguruma (the device test checks the rest). */
class EditorColoursTest {
    @Test fun `every language's tokens take their role's colour on every theme`() {
        assertEquals("", EditorColours.wrong(ShippedGrammars::source, oniguruma = false).joinToString("\n"))
    }
}
