package dev.icode.mobile.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.FloatingPill
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.PillPlace
import dev.icode.mobile.domain.ScreenEdge
import dev.icode.mobile.domain.ThemeVariantRules
import dev.icode.mobile.domain.files.ExplorerSettings
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FilesLook
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FilesView
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
import org.junit.Assert.assertTrue
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
            repository.setBorderLines(false)
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
            assertFalse(one.appearance.borderLines)
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

    @Test fun `an accent choice survives a restart, and resetting returns to the theme's`() = runBlocking {
        val file = File(temporaryFolder.root, "accent.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            assertNull(repository.values(null).first().appearance.accentColor)
            repository.setAccentColor(0xFF4FB3FF.toInt())
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            assertEquals(0xFF4FB3FF.toInt(), reopened.values(null).first().appearance.accentColor)
            reopened.setAccentColor(null)
            assertNull(reopened.values(null).first().appearance.accentColor)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `a pill moved survives a restart, and put back it is where it starts`() = runBlocking {
        val file = File(temporaryFolder.root, "pills.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            assertEquals(emptyMap<FloatingPill, PillPlace>(), repository.values(null).first().appearance.pillPlaces)
            repository.setPillPlace(FloatingPill.Dock, PillPlace(ScreenEdge.Start, 0.4f))
            repository.setPillPlace(FloatingPill.Bell, PillPlace(ScreenEdge.End, 0.9f))
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            val expected = mapOf(FloatingPill.Dock to PillPlace(ScreenEdge.Start, 0.4f), FloatingPill.Bell to PillPlace(ScreenEdge.End, 0.9f))
            assertEquals(expected, reopened.values("/root/one").first().appearance.pillPlaces)
            reopened.setPillPlace(FloatingPill.Dock, null)
            assertEquals(expected - FloatingPill.Dock, reopened.values(null).first().appearance.pillPlaces)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `each theme's variant survives a restart on its own, and iCode's own is no key at all`() = runBlocking {
        val file = File(temporaryFolder.root, "variants.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            // Before any choice, every theme is drawn as iCode's own: today's look after the update.
            assertEquals(emptyMap<AppTheme, String>(), repository.values(null).first().appearance.variants)
            repository.setThemeVariant(AppTheme.Dark, "nord")
            repository.setThemeVariant(AppTheme.Amoled, "dracula")
            repository.setThemeVariant(AppTheme.Light, "sakura")
            repository.setThemeVariant(AppTheme.Light, ThemeVariantRules.ICODE)
            assertEquals(true, runCatching { repository.setThemeVariant(AppTheme.Dark, "Not An Id") }.isFailure)
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
            assertNull(store.data.first()[stringPreferencesKey("appearance_light_variant")])
            val reopened = AndroidWorkspacePreferencesRepository(store)
            val appearance = reopened.values("/root/one").first().appearance
            assertEquals(mapOf(AppTheme.Dark to "nord", AppTheme.Amoled to "dracula"), appearance.variants)
            assertEquals(AppTheme.Dark, appearance.theme)
            reopened.setThemeVariant(AppTheme.Dark, null)
            assertEquals(mapOf(AppTheme.Amoled to "dracula"), reopened.values(null).first().appearance.variants)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `Match Phone keeps the dark theme in use for nights, and turned off keeps the theme on screen`() = runBlocking {
        val file = File(temporaryFolder.root, "auto.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            val fresh = repository.values(null).first().appearance
            assertFalse(fresh.followSystem)
            assertEquals(AppTheme.Dark, fresh.nightTheme)
            repository.setTheme(AppTheme.Amoled)
            repository.setFollowSystem(AppTheme.Amoled, true)
            assertEquals(true, runCatching { repository.setNightTheme(AppTheme.Light) }.isFailure)
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )
            val auto = reopened.values(null).first().appearance
            assertTrue(auto.followSystem)
            assertEquals(AppTheme.Amoled, auto.nightTheme)
            // Turned off by day, the Light on screen stays.
            reopened.setFollowSystem(AppTheme.Light, false)
            val off = reopened.values(null).first().appearance
            assertFalse(off.followSystem)
            assertEquals(AppTheme.Light, off.theme)
            assertEquals(AppTheme.Amoled, off.nightTheme)
        } finally {
            secondScope.cancel()
        }
    }

    @Test fun `terminal alerts are on until switched off, and the choice survives a restart`() = runBlocking {
        val file = File(temporaryFolder.root, "alerts.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )
            val fresh = repository.values(null).first().terminal
            assertTrue(fresh.bellAlerts)
            assertTrue(fresh.finishAlerts)
            repository.setTerminalBellAlerts(false)
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AndroidWorkspacePreferencesRepository(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            ).values(null).first()
            assertNull(reopened.loadError)
            assertFalse(reopened.terminal.bellAlerts)
            assertTrue(reopened.terminal.finishAlerts)
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
                preferences[stringPreferencesKey("appearance_dark_variant")] = "Not An Id"
                preferences[stringPreferencesKey("appearance_light_variant")] = "sakura"
                preferences[stringPreferencesKey("appearance_night_theme")] = "Light"
            }
            val current = AndroidWorkspacePreferencesRepository(store).values(null).first()
            assertEquals(2, current.globalIndentSize)
            assertEquals(AppTheme.Dark, current.appearance.theme)
            assertEquals(100, current.appearance.uiTextScalePercent)
            assertEquals(14, current.editor.fontSizeSp)
            assertEquals(AutoSaveMode.AfterOneSecond, current.editor.autoSave)
            assertEquals(mapOf(AppTheme.Light to "sakura"), current.appearance.variants)
            assertEquals(AppTheme.Dark, current.appearance.nightTheme)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `the explorer's settings survive restart, each view's look apart`() = runBlocking {
        val file = File(temporaryFolder.root, "explorer.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = AndroidWorkspacePreferencesRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
            assertEquals(ExplorerSettings(), repository.values(null).first().explorer)
            repository.setShowHidden(false)
            repository.setFilesLook(null, FilesLook(FilesView.Compact, FilesSort.Name, descending = false))
            repository.setFilesLook(ExplorerView.Trash, FilesLook(FilesView.Grid))
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
            // A look that cannot be read is no choice: the view opens its own way.
            store.edit { it[stringPreferencesKey("explorer_look_recent")] = "Tiles,Size,up" }
            val explorer = AndroidWorkspacePreferencesRepository(store).values(null).first().explorer
            assertFalse(explorer.showHidden)
            assertEquals(FilesLook(FilesView.Compact, FilesSort.Name, descending = false), explorer.look(null))
            assertEquals(FilesLook(FilesView.Grid), explorer.look(ExplorerView.Trash))
            assertEquals(ExplorerView.Recent.opensWith, explorer.look(ExplorerView.Recent))
        } finally {
            secondScope.cancel()
        }
    }
}
