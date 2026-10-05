package dev.icode.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.ui.editor.CodeEditorSessions
import dev.icode.mobile.ui.terminal.TerminalSessionSource

val LocalICodeSnapshot = compositionLocalOf<ICodeSnapshot> {
    error("ICodeSnapshot was not provided")
}

val LocalICodeGateway = compositionLocalOf<ICodeGateway> {
    error("ICodeGateway was not provided")
}

val LocalCodeEditorSessions = compositionLocalOf<CodeEditorSessions> {
    error("CodeEditorSessions was not provided")
}

val LocalTerminalSessions = compositionLocalOf<TerminalSessionSource> {
    error("TerminalSessionSource was not provided")
}

val LocalWorkspacePreferencesRepository = compositionLocalOf<WorkspacePreferencesRepository> {
    error("WorkspacePreferencesRepository was not provided")
}

val LocalWorkspacePreferences = compositionLocalOf { WorkspacePreferences() }
