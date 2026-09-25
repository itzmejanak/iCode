package dev.icode.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.ui.editor.CodeEditorSessions
import dev.icode.mobile.ui.terminal.TerminalSessionSource

val LocalWorkbenchSnapshot = compositionLocalOf<WorkbenchSnapshot> {
    error("WorkbenchSnapshot was not provided")
}

val LocalWorkbenchGateway = compositionLocalOf<WorkbenchGateway> {
    error("WorkbenchGateway was not provided")
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
