package dev.icode.mobile.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.IndentType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidWorkspacePreferencesRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `persisted overrides survive repository recreation and reset independently`() = runBlocking {
        val file = File(temporaryFolder.root, "workspace.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            assertEquals(4, first.values("/root/one").first().effectiveIndentSize)
            assertFalse(first.values("/root/one").first().autoFetch)

            first.setGlobalIndentSize(2)
            first.setIndentSizeOverride("/root/one", 6)
            first.setIndentTypeOverride("/root/one", IndentType.Tabs)
            first.setAutoFetch("/root/one", true)
        } finally {
            // DataStore allows one active instance per file; cancel() alone returns before the
            // first one has closed, and the reopened store then fails to read (measured: 17/300
            // under load). Wait for it to close.
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            assertNull(reopened.values("/root/one").first().loadError)
            assertEquals(6, reopened.values("/root/one").first().effectiveIndentSize)
            assertEquals(IndentType.Tabs, reopened.values("/root/one").first().effectiveIndentType)
            assertEquals(true, reopened.values("/root/one").first().autoFetch)
            assertEquals(2, reopened.values("/root/two").first().effectiveIndentSize)
            assertFalse(reopened.values("/root/two").first().autoFetch)

            reopened.setIndentSizeOverride("/root/one", null)
            reopened.setIndentTypeOverride("/root/one", null)
            val reset = reopened.values("/root/one").first()
            assertNull(reset.indentSizeOverride)
            assertEquals(2, reset.effectiveIndentSize)
            assertEquals(IndentType.Spaces, reset.effectiveIndentType)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `invalid size does not persist`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "invalid.preferences_pb") },
            )
            val repository = AndroidWorkspacePreferencesRepository(store)
            assertEquals(true, runCatching { repository.setIndentSizeOverride("/root/one", 0) }.isFailure)
            assertNull(repository.values("/root/one").first().indentSizeOverride)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `appearance and editor settings survive restart without changing workspace overrides`() = runBlocking {
        val file = File(temporaryFolder.root, "appearance.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            repository.setTheme(AppTheme.Amoled)
            repository.setUiTextScalePercent(125)
            repository.setCompactLayout(false)
            repository.setEditorFontSize(16)
            repository.setEditorFontFamily(EditorFontFamily.SystemMono)
            repository.setWordWrap(false)
            repository.setAutoSave(AutoSaveMode.Off)
            repository.setLineNumbers(false)
            repository.setMinimap(true)
            repository.setBracketMatching(false)
            repository.setIndentSizeOverride("/root/one", 6)
        } finally {
            // DataStore allows one active instance per file; cancel() alone returns before the
            // first one has closed, and the reopened store then fails to read (measured: 17/300
            // under load). Wait for it to close.
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            val one = reopened.values("/root/one").first()
            val two = reopened.values("/root/two").first()
            assertEquals(one.appearance, two.appearance)
            assertEquals(one.editor, two.editor)
            assertEquals(AppTheme.Amoled, one.appearance.theme)
            assertEquals(125, one.appearance.uiTextScalePercent)
            assertFalse(one.appearance.compactLayout)
            assertEquals(16, one.editor.fontSizeSp)
            assertEquals(EditorFontFamily.SystemMono, one.editor.fontFamily)
            assertFalse(one.editor.wordWrap)
            assertEquals(AutoSaveMode.Off, one.editor.autoSave)
            assertFalse(one.editor.lineNumbers)
            assertEquals(true, one.editor.minimap)
            assertFalse(one.editor.bracketMatching)
            assertEquals(6, one.effectiveIndentSize)
            assertEquals(4, two.effectiveIndentSize)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `invalid stored appearance and editor values fall back without erasing existing keys`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "invalid-values.preferences_pb") },
            )
            store.edit { preferences ->
                preferences[intPreferencesKey("editor_indent_size")] = 2
                preferences[stringPreferencesKey("appearance_theme")] = "future-theme"
                preferences[intPreferencesKey("appearance_ui_text_scale_percent")] = 42
                preferences[intPreferencesKey("editor_font_size_sp")] = 100
                preferences[stringPreferencesKey("editor_auto_save")] = "future-mode"
            }
            val current = AndroidWorkspacePreferencesRepository(store).values(null).first()
            assertEquals(2, current.globalIndentSize)
            assertEquals(AppTheme.Dark, current.appearance.theme)
            assertEquals(100, current.appearance.uiTextScalePercent)
            assertEquals(14, current.editor.fontSizeSp)
            assertEquals(AutoSaveMode.AfterOneSecond, current.editor.autoSave)
        } finally {
            scope.cancel()
        }
    }
}
