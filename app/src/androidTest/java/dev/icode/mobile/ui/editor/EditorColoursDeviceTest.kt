package dev.icode.mobile.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.eclipse.tm4e.core.internal.oniguruma.impl.onig.NativeOnigConfig
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [EditorColours] on the phone, with the native Oniguruma the editor uses: every expectation.
 * Run it only with the owner's OK: an instrumented run installs over the app on their phone
 * (gradle.properties keeps it from uninstalling iCode, which would delete its runtime and data).
 */
@RunWith(AndroidJUnit4::class)
class EditorColoursDeviceTest {
    @Test fun everyLanguagesTokensTakeTheirRolesColourOnEveryTheme() {
        assertTrue("native Oniguruma is what the editor runs on", NativeOnigConfig.isAvailable())
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val languages = JSONObject(assets.open("textmate/languages.json").reader().readText()).getJSONArray("languages")
        val paths = (0 until languages.length()).associate { index ->
            val entry = languages.getJSONObject(index)
            entry.getString("scopeName") to entry.getString("grammar")
        }
        val wrong = EditorColours.wrong({ scope ->
            paths[scope]?.let { path -> IGrammarSource.fromInputStream(assets.open(path), path, null) }
        }, oniguruma = true)
        assertEquals("", wrong.joinToString("\n"))
    }
}
