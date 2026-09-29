package dev.icode.mobile.ui.editor

import java.io.File
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** [EditorColours] on the JVM: joni, so only what does not need Oniguruma (the device test checks the rest). */
class EditorColoursTest {
    @Test fun `every language's tokens take their role's colour on every theme`() {
        val assets = File("src/main/assets")
        val languages = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        val files = (0 until languages.length()).associate { index ->
            val entry = languages.getJSONObject(index)
            entry.getString("scopeName") to File(assets, entry.getString("grammar"))
        }
        assertEquals("", EditorColours.wrong({ scope -> files[scope]?.let(IGrammarSource::fromFile) }, oniguruma = false).joinToString("\n"))
    }
}
