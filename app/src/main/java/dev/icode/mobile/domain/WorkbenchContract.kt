package dev.icode.mobile.domain

import kotlinx.coroutines.flow.StateFlow

data class WorkspaceSummary(
    val name: String,
    val path: String,
    val branch: String?,
    val lastOpened: String,
    val available: Boolean = true,
)

data class FileEntry(
    val name: String,
    val path: String,
    val kind: FileKind,
    val depth: Int = 0,
    val expanded: Boolean = false,
    val gitStatus: String? = null,
)

enum class FileKind { Folder, TypeScript, Json, Markdown }

data class GitChange(
    val status: String,
    val name: String,
    val path: String,
    val staged: Boolean,
)

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val message: String,
    val location: String,
    val source: String = "TS",
)

enum class Severity { Error, Warning, Info }

data class LanguageService(
    val initials: String,
    val name: String,
    val patterns: String,
    val running: Boolean,
)

data class WorkbenchSnapshot(
    val workspaces: List<WorkspaceSummary>,
    val files: List<FileEntry>,
    val gitChanges: List<GitChange>,
    val diagnostics: List<Diagnostic>,
    val languageServices: List<LanguageService>,
    val activeWorkspace: String,
    val activePath: String,
    val activeBranch: String,
    val runtimeHealthy: Boolean,
)

sealed interface WorkbenchIntent {
    data class OpenWorkspace(val path: String) : WorkbenchIntent
    data class OpenFile(val path: String) : WorkbenchIntent
    data class ToggleFolder(val path: String) : WorkbenchIntent
    data class StageFile(val path: String) : WorkbenchIntent
    data class UnstageFile(val path: String) : WorkbenchIntent
    data class SetLanguageServiceRunning(val name: String, val running: Boolean) : WorkbenchIntent
    data object Refresh : WorkbenchIntent
}

/**
 * Stable UI boundary for the later runtime/storage/Git implementation.
 *
 * Production adapters will implement this interface. Compose never shells out or reads files directly.
 */
interface WorkbenchGateway {
    val snapshot: StateFlow<WorkbenchSnapshot>
    fun dispatch(intent: WorkbenchIntent)
}
