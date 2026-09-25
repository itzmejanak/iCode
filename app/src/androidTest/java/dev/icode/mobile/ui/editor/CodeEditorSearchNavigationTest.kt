package dev.icode.mobile.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeEditorSearchNavigationTest {
    @Test
    fun searchResultRevealsOneBasedLineAndZeroBasedColumn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session = CodeEditorSession(instrumentation.targetContext)
            try {
                session.revealAt(lineNumber = 3, column = 4)
                session.bind(
                    OpenDocument(
                        name = "example.txt",
                        location = "/example.txt",
                        text = "one\ntwo\n    target\n",
                        encoding = TextEncoding.Utf8,
                        lineEnding = LineEnding.Lf,
                        dirty = false,
                        readOnly = false,
                    ),
                    onChange = {},
                )
                assertEquals(2, session.editor.cursor.leftLine)
                assertEquals(4, session.editor.cursor.leftColumn)
            } finally {
                session.release()
            }
        }
    }
}
