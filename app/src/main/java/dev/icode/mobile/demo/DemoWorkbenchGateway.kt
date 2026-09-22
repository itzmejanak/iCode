package dev.icode.mobile.demo

import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkspaceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DemoWorkbenchGateway : WorkbenchGateway {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot())
    override val snapshot: StateFlow<WorkbenchSnapshot> = mutableSnapshot.asStateFlow()

    override fun dispatch(intent: WorkbenchIntent) {
        when (intent) {
            is WorkbenchIntent.ToggleFolder -> mutableSnapshot.update { state ->
                state.copy(files = state.files.map { file ->
                    if (file.path == intent.path) file.copy(expanded = !file.expanded) else file
                })
            }
            is WorkbenchIntent.StageFile -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { change ->
                    if (change.path == intent.path) change.copy(staged = true) else change
                })
            }
            is WorkbenchIntent.UnstageFile -> mutableSnapshot.update { state ->
                state.copy(gitChanges = state.gitChanges.map { change ->
                    if (change.path == intent.path) change.copy(staged = false) else change
                })
            }
            is WorkbenchIntent.SetLanguageServiceRunning -> mutableSnapshot.update { state ->
                state.copy(languageServices = state.languageServices.map { service ->
                    if (service.name == intent.name) service.copy(running = intent.running) else service
                })
            }
            is WorkbenchIntent.OpenWorkspace,
            is WorkbenchIntent.OpenFile,
            WorkbenchIntent.Refresh,
            -> Unit
        }
    }

    private fun initialSnapshot() = WorkbenchSnapshot(
        workspaces = listOf(
            WorkspaceSummary("icode-subsystem", "~/projects/icode-subsystem", "main", "2h ago"),
            WorkspaceSummary("external-storage-driver", "~/sdcard/dev/storage-driver", "dev-v2", "5h ago"),
            WorkspaceSummary("legacy-compiler-plugin", "~/storage/shared/legacy-plugin", null, "1d ago", available = false),
        ),
        files = listOf(
            FileEntry("src", "src", FileKind.Folder, expanded = true),
            FileEntry("components", "src/components", FileKind.Folder, depth = 1, gitStatus = "A"),
            FileEntry("utils", "src/utils", FileKind.Folder, depth = 1, expanded = true),
            FileEntry("helpers.ts", "src/utils/helpers.ts", FileKind.TypeScript, depth = 2, gitStatus = "M"),
            FileEntry("tests", "tests", FileKind.Folder, expanded = false),
            FileEntry("README.md", "README.md", FileKind.Markdown),
            FileEntry("package.json", "package.json", FileKind.Json, gitStatus = "•"),
            FileEntry("index.ts", "index.ts", FileKind.TypeScript),
        ),
        gitChanges = listOf(
            GitChange("A", "auth-guard.tsx", "src/components/auth-guard.tsx", staged = true),
            GitChange("A", "token-utils.ts", "src/utils/token-utils.ts", staged = true),
            GitChange("M", "App.tsx", "src/App.tsx", staged = false),
            GitChange("D", "old-auth-helper.ts", "src/utils/old-auth-helper.ts", staged = false),
            GitChange("?", "config-schema.json", "src/config-schema.json", staged = false),
        ),
        diagnostics = listOf(
            Diagnostic(Severity.Error, "src/App.tsx", "Type error: Property name does not exist on type User", "line 42:15"),
            Diagnostic(Severity.Warning, "src/App.tsx", "Unused variable: tempData", "line 15:8"),
            Diagnostic(Severity.Error, "src/utils/api.ts", "NetworkError: Call does not handle API timeout failures", "line 108:3"),
        ),
        languageServices = listOf(
            LanguageService("TS", "TypeScript Language Server", "*.ts, *.tsx", running = true),
            LanguageService("PY", "Pylsp (Python)", "*.py", running = false),
            LanguageService("RS", "Rust Analyzer", "*.rs", running = true),
        ),
        activeWorkspace = "icode-subsystem",
        activePath = "~/projects/icode-subsystem",
        activeBranch = "feature/auth",
        runtimeHealthy = true,
    )
}
