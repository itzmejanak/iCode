package dev.icode.mobile.capture

import android.annotation.SuppressLint
import dev.icode.mobile.data.git.GitConflictParser
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FolderBrowserEntry
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.FolderBrowserState
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.GitDiffLine
import dev.icode.mobile.domain.GitDiffLineKind
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.RecoverableDraft
import dev.icode.mobile.domain.RuntimeDetails
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.StorageDetails
import dev.icode.mobile.domain.StorageMeasurement
import dev.icode.mobile.domain.StorageSummary
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkspaceSummary

/**
 * The state every captured screen is drawn from: one workspace with Git
 * changes, open files, diagnostics and language services.
 *
 * The capture opens each screen directly, without the taps that would select
 * a diff, a conflict or a change to discard, so those are set here too.
 */
object CaptureFixtures {
    /** Built per capture: the last commit's age is relative to now. */
    fun snapshot() = WorkbenchSnapshot(
        workspaces = listOf(
            WorkspaceSummary("icode-subsystem", ROOT, "main", ago(hours = 2)),
            WorkspaceSummary("external-storage-driver", "~/sdcard/dev/storage-driver", "dev-v2", ago(hours = 5)),
            WorkspaceSummary("legacy-compiler-plugin", "~/storage/shared/legacy-plugin", null, ago(hours = 24), available = false),
        ),
        files = listOf(
            FileEntry("src", "src", FileKind.Folder, expanded = true),
            FileEntry("components", "src/components", FileKind.Folder, depth = 1, gitStatus = GitStatusCodes.ADDED),
            FileEntry("utils", "src/utils", FileKind.Folder, depth = 1, expanded = true),
            FileEntry("helpers.ts", "src/utils/helpers.ts", FileKind.TypeScript, depth = 2, gitStatus = GitStatusCodes.MODIFIED),
            FileEntry("tests", "tests", FileKind.Folder, expanded = false),
            FileEntry("README.md", "README.md", FileKind.Markdown),
            FileEntry("package.json", "package.json", FileKind.Json, gitStatus = "•"),
            FileEntry("index.ts", "index.ts", FileKind.TypeScript),
        ),
        gitChanges = listOf(
            GitChange(GitStatusCodes.ADDED, "auth-guard.tsx", "src/components/auth-guard.tsx", staged = true),
            GitChange(GitStatusCodes.ADDED, "token-utils.ts", "src/utils/token-utils.ts", staged = true),
            GitChange(GitStatusCodes.MODIFIED, "App.tsx", "src/App.tsx", staged = false),
            DELETED_CHANGE,
            GitChange(GitStatusCodes.UNTRACKED, "config-schema.json", "src/config-schema.json", staged = false),
            GitChange(GitStatusCodes.CONFLICTED, "session-store.ts", "src/utils/session-store.ts", staged = false),
        ),
        activeDiff = DIFF,
        activeConflict = CONFLICT,
        pendingDiscard = DELETED_CHANGE,
        gitRepository = GitRepositoryState(
            workTreePath = ROOT,
            branch = BRANCH,
            hasCommits = true,
            remoteName = "origin",
            authorName = null,
            authorEmail = null,
            branches = listOf("main", "develop", BRANCH, "feature/dashboard", "bugfix/login-error"),
            lastCommit = GitCommitSummary(
                id = "a9d2b1f8c7e923",
                message = "feat: implement secure auth flow on Android",
                author = "Dev Android <dev@icode.com>",
                authoredAtEpochMillis = System.currentTimeMillis() - 5 * MINUTE,
            ),
            upstream = "origin/$BRANCH",
            ahead = 2,
            behind = 1,
        ),
        diagnostics = listOf(
            Diagnostic(Severity.Error, "src/App.tsx", "Type error: Property name does not exist on type User", "line 42:15"),
            Diagnostic(Severity.Warning, "src/App.tsx", "Unused variable: tempData", "line 15:8"),
            Diagnostic(Severity.Error, "src/utils/api.ts", "NetworkError: Call does not handle API timeout failures", "line 108:3"),
        ),
        languageServices = listOf(
            LanguageService(
                "TS", "TypeScript Language Server", "*.ts, *.tsx", running = true,
                id = TYPESCRIPT_SERVICE,
                command = "typescript-language-server --stdio",
                rootMarkers = "tsconfig.json, package.json",
                initializationOptions = "{\"preferences\":{\"importModuleSpecifier\":\"non-relative\"}}",
                processId = 24018,
            ),
            LanguageService("PY", "Pylsp (Python)", "*.py", running = false),
            LanguageService("RS", "Rust Analyzer", "*.rs", running = true),
        ),
        selectedLanguageServiceId = TYPESCRIPT_SERVICE,
        activeWorkspace = "icode-subsystem",
        initialExplorerSelection = "src/utils/helpers.ts",
        activePath = ROOT,
        activeWorkspaceLocation = ROOT,
        activeBranch = BRANCH,
        gitIdentityLoaded = true,
        gitAvailable = true,
        runtimeHealthy = true,
        runtime = RuntimeState.Ready("Ubuntu 24.04.5 LTS"),
        runtimeDetails = RuntimeDetails(
            inspected = true,
            release = "Ubuntu 24.04.5 LTS (noble) base",
            architecture = "arm64-v8a",
            rootfsPath = SAMPLE_ROOTFS,
            installedPackages = 247,
            rootfsSize = StorageMeasurement(1_932_735_283L),
            availableBytes = 16_106_127_360L,
            prootVersion = "5.1.107.92",
        ),
        systemAccess = SystemAccess(storage = true, notifications = true, unrestrictedBattery = true),
        activeDocument = document("App.tsx", "src/App.tsx", dirty = true),
        openDocuments = listOf(
            document("App.tsx", "src/App.tsx", dirty = true),
            document("package.json", "package.json"),
            document("README.md", "README.md"),
        ),
        recentDocuments = listOf(
            RecentDocument("auth-guard.tsx", "$ROOT/src/components/auth-guard.tsx", "src/components/auth-guard.tsx", ago(minutes = 10)),
            RecentDocument("token-utils.ts", "$ROOT/src/utils/token-utils.ts", "src/utils/token-utils.ts", ago(hours = 2)),
        ),
        recoverableDrafts = listOf(
            draft("src/components/auth.ts", sizeBytes = 4_301, minutesAgo = 2),
            draft("src/utils/token-utils.ts", sizeBytes = 1_843, minutesAgo = 5),
            draft("package.json", sizeBytes = 12_288, minutesAgo = 15),
        ),
        folderBrowser = FolderBrowserState(
            purpose = FolderBrowserPurpose.OpenWorkspace,
            rootPath = ROOT,
            currentPath = ROOT,
            displayPath = "root / projects / icode-subsystem",
            folders = listOf(
                FolderBrowserEntry("src", "$ROOT/src", directoryCount = 12, fileCount = 4),
                FolderBrowserEntry("public", "$ROOT/public", directoryCount = 2, fileCount = 15),
                FolderBrowserEntry("tests", "$ROOT/tests", directoryCount = 1, fileCount = 6),
            ),
        ),
        createFolderDestination = "$ROOT/src",
        createFolderDestinationDisplay = "$ROOT/src",
        storage = StorageSummary(availableBytes = 13_314_398_617L, totalBytes = 137_438_953_472L),
        storageDetails = StorageDetails(
            device = StorageSummary(availableBytes = (14.2 * GIB).toLong(), totalBytes = 16 * GIB),
            runtime = StorageMeasurement((1.1 * GIB).toLong()),
            cache = StorageMeasurement(220 * MIB),
            workspaceSizes = mapOf(
                ROOT to StorageMeasurement(244 * MIB),
                "~/sdcard/dev/storage-driver" to StorageMeasurement((1.2 * GIB).toLong()),
            ),
        ),
        fileIndex = listOf(
            IndexedFile("App.tsx", "src/App.tsx", FileKind.TypeScript),
            IndexedFile("useAuth.ts", "src/hooks/useAuth.ts", FileKind.TypeScript),
            IndexedFile("package.json", "package.json", FileKind.Json),
        ),
    )

    private fun ago(minutes: Int = 0, hours: Int = 0): Long =
        System.currentTimeMillis() - (minutes + hours * 60) * MINUTE

    private fun draft(path: String, sizeBytes: Long, minutesAgo: Int) = RecoverableDraft(
        location = "$ROOT/$path",
        name = path.substringAfterLast('/'),
        sizeBytes = sizeBytes,
        savedAtEpochMillis = ago(minutes = minutesAgo),
    )

    private fun document(name: String, location: String, dirty: Boolean = false) = OpenDocument(
        name = name,
        location = location,
        text = when {
            name.endsWith(".json") -> """{ "name": "icode-subsystem", "version": "0.1.0" }"""
            name.endsWith(".md") -> "# iCode\n\nA mobile workspace with **real files** and a Linux terminal.\n"
            else -> SOURCE_TEXT
        },
        encoding = TextEncoding.Utf8,
        lineEnding = LineEnding.Lf,
        dirty = dirty,
        readOnly = false,
    )

    private const val ROOT = "~/projects/icode-subsystem"

    /** Drawn as text on the runtime screen; nothing opens it. */
    @SuppressLint("SdCardPath")
    private const val SAMPLE_ROOTFS = "/data/user/0/dev.icode.mobile/files/runtime/rootfs"
    private const val BRANCH = "feature/auth"
    private const val TYPESCRIPT_SERVICE = "typescript"
    private const val MINUTE = 60_000L
    private const val MIB = 1024L * 1024
    private const val GIB = 1024L * MIB

    private val DELETED_CHANGE =
        GitChange(GitStatusCodes.DELETED, "old-auth-helper.ts", "src/utils/old-auth-helper.ts", staged = false)

    /** Markers as Git writes them; the conflict screen adds its own labels. */
    private val CONFLICT = GitConflictParser.parse(
        "src/utils/session-store.ts",
        listOf(
            "export const validateToken = (token: string) => {",
            "<<<<<<< HEAD",
            "  const decoded = jwt.verify(token, SECRET);",
            "  return decoded.sub !== undefined;",
            "=======",
            "  const verified = oauth.decode(token);",
            "  return verified.isValid && verified.scope === 'write';",
            ">>>>>>> feature/oauth",
            "};",
        ).joinToString("\n"),
    )

    private val DIFF = GitFileDiff(
        path = "src/App.tsx",
        hunks = listOf(
            GitDiffHunk(
                oldStart = 15, oldCount = 8, newStart = 15, newCount = 12,
                header = "@@ -15,8 +15,12 @@",
                lines = listOf(
                    GitDiffLine(GitDiffLineKind.Context, 15, 15, "const config = {"),
                    GitDiffLine(GitDiffLineKind.Removed, 16, null, "  secure: false,"),
                    GitDiffLine(GitDiffLineKind.Added, null, 16, "  secure: true,"),
                    GitDiffLine(GitDiffLineKind.Added, null, 17, "  algorithm: 'RS256',"),
                    GitDiffLine(GitDiffLineKind.Context, 17, 18, "subscribe();"),
                ),
            ),
        ),
    )

    private val SOURCE_TEXT = """
        import React from 'components';
        const ActiveComponent = () => {
          return <div>Active Workspace</div>;
        };
        export default ActiveComponent;
    """.trimIndent()
}
