package dev.icode.mobile.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.icode.mobile.domain.syntax.Languages
import org.eclipse.tm4e.core.internal.oniguruma.impl.onig.NativeOnigConfig
import org.eclipse.tm4e.core.registry.IGrammarSource
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
        val catalog = Languages.parse(assets.open(Languages.ASSET).reader().use { it.readText() })
        val wrong = EditorColours.wrong(catalog, { scope ->
            catalog.byScope(scope)?.grammar?.let { path -> IGrammarSource.fromInputStream(assets.open(path), path, null) }
        }, oniguruma = true)
        assertEquals("", wrong.joinToString("\n"))
    }
}
