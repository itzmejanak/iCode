# iCode architecture

## Purpose and scope

This document describes the architecture implemented by the `iCode` Android repository. It is a current-state document. Product goals belong in [product.md](product.md); unfinished implementation, verification, and external work belongs in [roadmap.md](roadmap.md).

## System shape

The repository builds one native Android application. `settings.gradle.kts` includes only the `:app` Gradle module. Domain, data, UI, design-system, and navigation boundaries are Kotlin package boundaries inside that module, not separately compiled Gradle modules.

```text
Android process
├── Compose shell and native views
├── process-scoped backend and immutable application snapshot
├── app-private workspaces and persistence
├── Ubuntu ARM64 root filesystem under PRoot
├── terminal, managed, Git, search, LSP, and local MCP child processes
└── Android integrations: Files, notifications, package install, voice,
    AccessibilityService, Shizuku, browser intents, and optional website calls
```

The public website is not part of the editing or execution path. Android-side website integration is isolated under `app/src/main/java/dev/icode/mobile/data/web/` and is disabled when `BuildConfig.ICODE_WEB_URL` is empty.

## Source boundaries

| Boundary | Current ownership |
|---|---|
| Domain | `app/src/main/java/dev/icode/mobile/domain/` contains immutable product state, intents, effects, validation, and capability contracts. It does not own Compose, Android storage APIs, PRoot command construction, or external protocol clients. |
| Data | `app/src/main/java/dev/icode/mobile/data/` contains Android, filesystem, runtime, Git, search, LSP, assistant, MCP, voice, phone, persistence, task, notification, and website implementations. |
| UI | `app/src/main/java/dev/icode/mobile/ui/` contains the Compose shell, screens, native editor/terminal hosting, Markdown rendering, dock, phone overlays, sharing, and Android presentation adapters. |
| Design system | `app/src/main/java/dev/icode/mobile/designsystem/` contains semantic colors, typography, spacing, components, sheets, rows, bars, and palettes. It is a package in `:app`, not a Gradle module. |
| Navigation | `app/src/main/java/dev/icode/mobile/navigation/Route.kt` defines route identity and groups. `ui/NavigationState.kt` owns saveable histories and `ui/IcodeApp.kt` maps route groups to screen hosts. |
| Composition | `app/src/main/java/dev/icode/mobile/data/workspace/AndroidICodeGateway.kt` constructs infrastructure and coordinators and routes `ICodeIntent`. `ui/IcodeApp.kt` connects the gateway to Compose and native views. |
| Native/runtime assets | `app/src/main/cpp/CMakeLists.txt`, `runtime/`, and `third_party/` provide PRoot, loaders, JNI bridges, terminal sources, RaTeX binaries, and patches consumed by `:app`. |

[Engineering Rules](engineering-rules.md#ownership-and-dependency-direction) defines the repository's package discipline. The current source remains authoritative about the boundaries that physically exist.

## Process lifetime and composition

### Android startup

`ICodeApplication.kt` performs process-level initialization that must exist even when Android starts a service without an activity: notification channels, the phone-overlay factory, and file-icon warming.

`MainActivity.kt` owns the system splash, edge-to-edge window, external arrivals, notification/deep-link routing, and the Compose entry point. It does not construct feature implementations directly.

`ui/IcodeApp.kt` owns the visible application shell. It collects the immutable gateway snapshot, handles effects and Android prompts, holds saveable navigation state, hosts retained editor views, and delegates to the route-group screen hosts.

### Process-scoped backend

`data/workspace/ICodeProcess.kt` keeps one `AndroidICodeGateway` for the Android process. UI view models attach and detach screens; detaching the last screen calls `AndroidICodeGateway.screensGone()` so screen-bound observers such as workspace watchers and language sessions can stop without terminating user-started terminal or managed work.

`data/workspace/AndroidICodeGateway.kt` is the main composition root. It constructs:

- one `ICodeStateHolder` and a process-lifetime coroutine scope;
- workspace, document, file, search, Git, runtime, terminal, language, run, notice, web, assistant, MCP, voice, and phone owners;
- persistence adapters and Android system adapters;
- callbacks that invalidate Git, language, search, explorer, and document state after a shared resource changes.

This is manual constructor composition; the project does not use a dependency-injection framework.

### State and intent flow

`data/workspace/ICodeStateHolder.kt` owns the current `StateFlow<ICodeSnapshot>`, effects, and the workspace mutation mutex. Screens read immutable state through `ICodeGateway` and dispatch typed `ICodeIntent` values. Coordinators perform I/O and publish replacement state.

```text
user action
  → ICodeIntent
  → AndroidICodeGateway
  → owning coordinator or adapter
  → filesystem/process/protocol result
  → ICodeStateHolder snapshot or ICodeEffect
  → Compose/native view
```

Slow work is not serialized behind one global queue. Search, Git transfers, tasks, assistant turns, runtime tools, and managed processes own cancellable jobs appropriate to their lifecycle. Workspace mutations use the state holder's mutex where ordering matters.

## Storage and path model

### Managed workspaces

`data/storage/WorkspacePaths.kt` is the host/guest translator for managed workspaces:

- host root: `<filesDir>/workspaces`;
- guest root: `/workspace`;
- workspace identity: one directory directly below the host root;
- persisted identity: the canonical host path, translated for display and guest execution.

The workspace folders are the registry. `data/workspace/WorkspaceLibraryCoordinator.kt` lists those directories and combines them with metadata from `WorkspaceStore`; there is no database row whose absence hides a workspace directory.

All managed workspaces are app-private. Android **Clear storage** removes them together with the runtime, settings, drafts, chats, and other app data. `android:hasFragileUserData="true"` in `app/src/main/AndroidManifest.xml` allows Android to ask whether data should be kept during uninstall; it does not protect app data from Clear storage. `android:allowBackup="false"`, `android:fullBackupContent="false"`, and `app/src/main/res/xml/data_extraction_rules.xml` disable Android cloud backup and device transfer for this data.

### Runtime paths

`data/runtime/GuestPaths.kt` maps runtime-visible paths to host files while following symlinks as the PRoot guest sees them. It understands:

- the app-private root filesystem;
- every workspace under `/workspace`;
- Android host binds such as `/dev`, `/proc`, `/sys`, `/system`, `/apex`, `/vendor`, and `/product`;
- shared phone storage at `/storage/emulated/0`, with `/sdcard` naming it when available.

The translator follows absolute and relative guest symlinks and PRoot's hard-link stand-ins, with the Linux 40-link loop limit. `WorkspacePaths` remains the narrower workspace translator; `GuestPaths` is used where a runtime-wide path is valid, including assistant tools.

### Shared phone storage

`data/storage/PhoneStorage.kt` owns canonical traversal of `/storage/emulated/0`. `data/system/AndroidSystemAccess.kt` reports Android's All Files Access state, and `ui/SystemPrompts.kt` opens Android's per-app settings page.

`data/runtime/RuntimeLayout.kt` includes the shared-storage path in every PRoot launch when that Android directory exists and creates `/sdcard` as its guest alias. This mount is not a permission bypass. Guest processes can use shared-storage contents only after Android grants the app `MANAGE_EXTERNAL_STORAGE`; before that, Android's app-UID and storage policy deny the usable access. Revoking the permission applies to the same UID and therefore affects guest processes as well as native file code.

Shared storage is not a managed workspace. Import and file transfer copy or move selected content between Shared and app-private Private storage under `data/files/` and `data/workspace/`; the two places retain different Android persistence and permission behavior.

### File and document ownership

`data/workspace/WorkspaceFileSource.kt` is the guarded text-file source used by the editor and assistant file tools. It:

- resolves guest symlinks through `GuestPaths`;
- enforces workspace boundaries for workspace tree operations;
- rejects unreadable, binary/undecodable, and text files larger than 5 MiB;
- preserves supported encoding, byte-order mark, and line ending through `DocumentCodec.kt`;
- fingerprints the saved bytes;
- writes a temporary sibling, flushes and syncs it, then atomically replaces the target;
- refuses to overwrite when the on-disk fingerprint changed.

`data/workspace/DocumentSessionCoordinator.kt` owns open-document state and save/reload decisions. `DocumentRecoveryCoordinator.kt` and `DocumentDraftStore.kt` keep unsaved text outside the workspace so a draft cannot be consumed by Git, search, or a build.

`data/files/FileOperationsCoordinator.kt` owns cross-place file operations, task progress, conflict questions, trash/history, and refresh callbacks. `WorkspaceItemsCoordinator.kt` owns workspace-tree item actions. External changes flow through `FileChangeFollower.kt` into documents, the tree, Git, and search.

### Android DocumentsProvider

`data/storage/WorkspacesProvider.kt` is registered as `${applicationId}.workspaces` in `app/src/main/AndroidManifest.xml`. It exposes one local “iCode / Workspaces” root through Android's Storage Access Framework.

Document IDs are guest paths such as `/workspace/example/src/Main.kt`. The provider validates segments, canonical targets, and symlink destinations so a granted URI cannot escape the workspace root into the runtime or other app-private data. Other apps receive access only through Android's picker grants. They may read/write files and create children. The provider allows creation of a top-level workspace folder, but whole-workspace rename, move, and delete remain iCode-owned so session and settings state can move with it.

A separate non-exported AndroidX `FileProvider` grants individual URIs for Open with, Share, and camera output.

## Runtime and process architecture

### Installation and launch

`app/src/main/assets/runtime/manifest.json` pins the Ubuntu 24.04.5 ARM64 base, PRoot version, tool archives, checksums, sizes, and package sizes. `data/runtime/RuntimeInstaller.kt` validates and extracts the root filesystem into `<filesDir>/runtime/rootfs`; `RuntimeCoordinator.kt` owns health, setup, Repair, Reset, and runtime state.

Executable PRoot and loader carriers are packaged under Android's extracted native-library directory. `app/build.gradle.kts` enables legacy JNI extraction because Android does not allow the app to execute binaries copied into writable app data. `app/src/main/cpp/CMakeLists.txt` builds the native runtime and JNI components and enforces ARM64.

`data/runtime/RuntimeLayout.kt` is the only PRoot command builder. Every launch receives:

- the same root filesystem;
- the same complete `/workspace` bind, not a per-workspace bind;
- shared storage subject to Android permission;
- the Android/kernel binds required by guest tools;
- a guest working directory;
- a clean environment started with `/usr/bin/env -i`.

`GuestContext.kt`, `GuestVariables`, and `GuestEnvironment.kt` derive the guest login environment and context-specific variables. Terminal sessions start the login shell recorded in guest `/etc/passwd`; non-terminal contexts receive the captured login environment plus their non-interactive variables. PRoot's host-only variables do not leak into the guest.

PRoot provides user-space path/syscall translation. It is not a VM or security boundary. Guest `root` remains the Android app UID and cannot bypass Android SELinux, app-private storage, runtime permissions, or protected system/app data.

### Process owners

All guest processes ultimately use `RuntimeLayout.prootCommand()`, but ownership differs:

| Work | Owner and lifetime |
|---|---|
| Terminal shell | `data/runtime/TerminalSessionManager.kt` owns a Termux PTY, emulator, and PRoot per tab. `TerminalCoordinator.kt` publishes session state. Closing/exiting a tab ends that shell. |
| Managed process, saved Run command, or Android build | `RuntimeExecution.kt` and `RuntimeProcesses.kt` own a separate PRoot, input, bounded output/log, process-group stop, exit state, detected ports, and workspace metadata. |
| Git, search, runtime package, and setup command | The suspending operation under `data/git/`, `data/search/`, `data/runtime/`, or `data/android/` owns a short-lived PRoot and cancellation. |
| Language server | `data/language/LanguagePool.kt` and `LanguageServerSession.kt` own stdio, JSON-RPC state, documents, shutdown, and exit for each active server/workspace. |
| Local MCP server | `data/assistant/mcp/McpConnections.kt` and `McpLocal.kt` own the stdio server and idle shutdown. Remote MCP uses HTTP. |

There is no global iCode process cap. `RuntimeProcesses.kt` does not count-limit managed processes, and `TerminalSessionManager.kt` does not cap tabs. Subsystem bounds, such as the number of saved language-service configurations or MCP tools sent in one model request, are not process caps. Android memory, phantom-process pressure, and OEM background policy are the practical limits.

`RuntimeForegroundService.kt` represents eligible terminal, build, server, transfer, install, and assistant work while the process remains alive. `PhoneForegroundService.kt` separately represents enabled phone control and away-from-app microphone use. Foreground service status reduces background termination risk but cannot make a process restorable.

### Process death

Live guest process state is not restorable:

- `TerminalSessionLedger.kt` records enough metadata to report lost terminal sessions after the next start; it does not persist a PTY or reconnect handle.
- `RuntimeProcesses.kt` keeps managed process handles in memory and clears stale process logs on construction.
- `LanguageServiceStore.kt` persists configurations and explicitly does not persist server process state.
- `GitTransferLedger.kt` records interruption for truthful reporting and does not resume a transfer.

After Android kills the process or the phone restarts, terminal shells, managed processes, language servers, local MCP servers, and in-flight commands have ended. The next process reconstructs workspace/UI/document state where persisted and reports losses instead of presenting processes as resumed.

## Editor, syntax, Markdown, and LSP

### Editor and document model

The native editor uses Sora Editor, pinned in `app/build.gradle.kts`. `ui/editor/CodeEditorSessions.kt` retains one Sora view/session per open document while the process lives, preserving renderer-owned undo, caret, selection, scrolling, and folds across tab and route changes. Backend document text, dirty state, disk fingerprints, recovery, and save results remain under `data/workspace/`.

The editor is coupled to language and Git through domain interfaces supplied by the gateway, not by starting processes itself. A successful save schedules Git refresh and LSP document notification in `AndroidICodeGateway.kt`.

### Syntax and Markdown

`ui/syntax/SyntaxEngine.kt` owns bundled TextMate/tm4e grammar loading and native Oniguruma use. `app/src/main/assets/textmate/catalog.json` is the language catalog, and `tools/import-grammars.py` generates/imports the assets and provenance. `docs/editor-grammar-provenance.md` records sources and transformations.

Syntax highlighting does not require LSP. Semantic tokens from an active language server are overlaid through `data/language/LanguageSemanticTokens.kt` and editor adapters.

Native Markdown parsing lives under `domain/markdown/`; rendering lives under `ui/markdown/` and uses the bundled RaTeX native library for math. HTML is parsed into safe presentation structures rather than executed as a browser page.

### Language services

`data/language/LanguageServices.kt` is the feature owner. It composes:

- saved configurations in `LanguageServiceStore.kt`;
- server/process pooling and document synchronization;
- framed JSON-RPC under `data/language/jsonrpc/`;
- capability negotiation, dynamic registration, diagnostics, progress, and workspace edits;
- completion, hover, signatures, definitions, references, symbols, rename, formatting, code actions, and semantic tokens;
- UI and assistant-facing domain interfaces.

Servers are ordinary user-installed guest commands. Editing and grammar coloring continue when no server is configured or when a server fails. Active server state is in memory; only configuration is persisted.

## Search

`data/search/RipgrepService.kt` owns runtime-backed ripgrep execution. `SearchCoordinator.kt` owns cancellable Quick Open indexing and content-search requests, bounds results, suppresses stale responses, and publishes results. The active-document find/replace UI is separate in `ui/screens/tools/EditorFindScreen.kt`.

Workspace search has no mutation path in the current architecture. Project-wide replace is therefore a roadmap feature, not an implied capability of ripgrep search.

## Git and GPG

Git remains the repository authority and runs as the real guest `git` executable:

- `data/git/CliGitService.kt` owns command invocation through `GuestCommand` and reports success from exit status.
- `GitRepositoryClient.kt` owns repository-level operations and parsing.
- `GitWorkspaceCoordinator.kt` owns active repository state, status, diffs, staging, commits, branches, remotes, discard/abort, and refresh.
- `GitTransferCoordinator.kt` owns clone, fetch, pull, push, progress, cancellation, and transfer state.
- `GitConflictCoordinator.kt` and `GitHistoryCoordinator.kt` own conflict and history workflows.
- `GitAuthenticator.kt`, `GitAuthCoordinator.kt`, and `GitCredentialStore.kt` own identity and HTTPS/SSH credential use.
- `data/runtime/GuestGpg.kt`, `GpgBridge.kt`, and the Git auth owners integrate GnuPG and app-hosted pinentry.

Temporary HTTPS credentials are decrypted into an owner-only guest `/tmp` file for one app-managed command and removed in normal completion, failure, and cancellation paths. A hard Android process kill can bypass that cleanup; startup cleanup is not yet implemented and is tracked in the roadmap. App-saved Git secrets use Android Keystore-backed encryption. Repository `.git`, hooks, and Git configuration remain ordinary workspace/runtime data.

`ui/screens/tools/OutputPanelScreen.kt` currently renders the latest Git operation transcript only. It is not a general output bus.

## Terminal and Android Build and Run

The terminal uses Termux's view library, vendored terminal emulator source, and app-built JNI. `TerminalSessionManager.kt` owns terminal tabs and PTYs process-wide; `TerminalLaunch.kt` prepares the login shell and working directory; `TerminalCoordinator.kt` adapts sessions to domain state and intents.

Terminal output belongs to each terminal session. Managed-process output is stored through `ProcessLogs` and can be followed from terminal/run surfaces. HTTP and HTTPS links are dispatched to the external Android browser.

Runtime Tools are catalogued under `domain/RuntimeTools.kt` and installed by `data/runtime/RuntimeToolsCoordinator.kt` and `RuntimePackageInstaller.kt`. Pinned non-apt setup is behind `RuntimeToolSetup` and `PinnedDownload`.

Android toolchain ownership is under `data/android/`:

- `AndroidSdkSetup.kt` installs/configures JDK 17, command-line tools, Platform 36, Build-Tools 36.0.0, and ARM64 host-tool replacements.
- `CmakeSetup.kt` and `AndroidNdkSetup.kt` install pinned ARM64 CMake/Ninja and NDK r27/r28c.
- `GradleSettings.kt` writes guest-wide Android/Gradle safeguards.
- `AndroidProjects.kt` discovers existing Gradle Android application modules.
- `AndroidApps.kt` builds via the project's wrapper, parses diagnostics, and hands the APK to `ApkInstaller.kt`.
- `data/run/RunCoordinator.kt` owns saved commands and Android app run state.

Android's package installer owns user confirmation. The current source does not contain an Android project-template generator.

## Assistant and MCP

### Assistant

`data/assistant/AssistantCoordinator.kt` owns assistant state, provider configuration, chats, turns, questions, commit-message generation, tools, background behavior, and MCP connection to the assistant. `data/assistant/AssistantEngine.kt` is the application seam; `data/assistant/koog/KoogAssistantEngine.kt` is the current Koog adapter.

`data/assistant/AssistantTurns.kt` runs each chat's turn in its own job through `AssistantWork.kt`, which also gives the phone's screen to one chat at a time and idles the MCP servers when the last chat ends. `AssistantQuestions.kt` keeps each chat's approval, pause, or questions on that chat's turn in `AssistantState.turns`. `AssistantQueue.kt` owns each chat's waiting messages, saved with the chat by `AssistantChats.kt`: a message sent while the chat works waits there, the first goes when the work ends, and Send now steers the work in hand at its next step or stops it for another model.

`AssistantCheckpoints.kt` keeps each place's checkpoints for Undo under `<filesDir>/assistant/checkpoints`: a folder per place, named by the SHA-256 of its workspace's location or `home`, with each file content stored once under its SHA-256 and a JSON list per checkpoint. A turn keeps one before each message and again as it ends; `domain/AssistantCheckpoints.kt` decides what is left out and what changed outside a chat's work. `AssistantChatActions.kt` owns the page's chat actions — Try again, take back, compact, delete, share, and Undo, which puts files back through a temporary file and atomic rename and cuts the chat back through `AssistantChats.kt`.

`data/assistant/tools/AssistantToolbox.kt` adapts the same file, search, Git, runtime process, Run, language, voice, and phone owners used by the UI. It does not create parallel repositories or shells. `AssistantGate` and domain permission rules control assistant-requested changes and commands. Those approvals are an assistant interaction boundary, not a restriction on commands the user launches directly in a terminal.

Chats are files under `<filesDir>/assistant/chats`, one JSON line for the chat and one for each message after it; `AssistantChatStore.kt` appends what is new and cuts back only what changed at the end, and writes the chat list, `index.json`, whole through a temporary file and rename. Provider configuration uses private preferences; secrets are encrypted with `data/io/KeystoreCipher.kt`.

### MCP

`data/assistant/mcp/McpServers.kt` owns configured servers and workspace `.mcp.json` discovery. Its collaborators own:

- encrypted server values and tokens;
- workspace-server trust fingerprints;
- local stdio and remote HTTP/SSE transports;
- browser sign-in return through `SignInReturnActivity.kt`;
- tool schema conversion, per-tool choices, prompts/resources, server questions, logs, registry results, and idle shutdown.

Only enabled and trusted workspace servers contribute tools. `McpRules.MAX_TOOLS` bounds tools included in one model request and is not a guest process cap. The protocol behavior is limited by the official MCP SDK version pinned in `app/build.gradle.kts`.

### Coding agents

`data/agents/CodingAgentsCoordinator.kt` owns Settings › Assistant › Coding Agents, created and started by the assistant coordinator, which passes it `AgentIntent`s as it does MCP's. `AgentRegistry.kt` reads the ACP registry's published list, with the one parser of its format, and keeps the last list under `<filesDir>/agents/registry.json`. `AgentInstalls.kt` installs a release into the runtime's home, a build through `fetchPinned` and `Archives`, an npm package through `npm` in the guest after `RuntimePackageInstaller.ensure` has Node.js; `AgentReceipts.kt` reads and writes each agent's receipt. `AgentUpdateWorker.kt` is the daily WorkManager check, which reaches the notice store and the shade directly, as iCode may not be running. `domain/CodingAgents.kt` holds the agent, receipt, state, and intent types and the offered agents with their own folders.

Only `data/assistant/acp/` imports the Agent Client Protocol SDK, as only `data/assistant/koog/` imports Koog. `AcpAgents.kt` runs one process per agent through `GuestStdio`, its stdout the protocol and its stderr its own log, with a session per chat on its one connection, and stops it once no chat has used it for 10 minutes. `AcpTurn.kt` answers one message in the chat's session — a new session sent the conversation so far first — and `AcpReplies.kt` is the one mapping from the agent's updates to `AssistantEvents`: replies, reasoning, steps with their results, context use, and each reply's cost. `AgentChats.kt` implements `data/assistant/AgentAnswers.kt`, through which `AssistantTurns.kt` hands a chat set to an agent its turn; it owns `AgentChatIntent`s — choosing an agent, its sign-in check and ways, keys — and saves open files before each message. `AgentPreferences.kt` keeps the agent chosen last, sign-ins, untested notes given, and keys encrypted with `KeystoreCipher`. A permission request waits in `AssistantQuestions.kt` with the agent's own options (`domain/AssistantApproval.kt`). The SDK logs through SLF4J, which has no provider in the app, so nothing it logs is written; `AcpLoggingTest` fails if one is added.

## Voice and phone control

`data/voice/VoiceCoordinator.kt` owns listening and speaking sessions, selected providers, microphone checks, speech state, sentence streaming, audio focus, and interruption. Provider abstractions and adapters are under `data/voice/speech/`. Android speech recognition and text-to-speech remain the defaults; network providers use encrypted credentials and no silent provider fallback.

`data/phone/PhoneService.kt` is an opt-in, non-exported `AccessibilityService`. It reads and acts on accessibility nodes, performs gestures/screenshots where allowed, and hosts the bubble/glow overlay through UI factories. `PhoneCoordinator.kt` owns setup and state; `AndroidPhoneControl.kt` owns action selection and safety behavior; `PhoneForegroundService.kt` owns enabled background/microphone service state.

Optional Shizuku support is under `data/phone/shizuku/`. Shizuku supplies a separate shell-identity boundary only after the user starts it and grants access. It does not give Android root.

Phone control and voice are high-impact integrations. Their implemented controls do not replace the real-device safety and privacy acceptance listed in [roadmap.md](roadmap.md).

## Persistence

The app does not use Room or an application SQLite database. Persistence is split by owner:

| Data | Current store |
|---|---|
| Workspaces and repositories | `<filesDir>/workspaces`, including each repository's `.git`. |
| Ubuntu runtime, guest home, installed tools, and guest Git/GPG state | `<filesDir>/runtime/rootfs`. |
| Open workspace, tab order, active tab, recents, favorites, last-opened metadata | `data/workspace/WorkspaceStore.kt` through `SharedPreferencesWorkspaceStore`. |
| Dirty document recovery | `<filesDir>/document-drafts` through `DocumentDraftStore.kt`. |
| Assistant chats and attachments | `<filesDir>/assistant/chats` through `AssistantChatStore.kt`. |
| Assistant Undo checkpoints | `<filesDir>/assistant/checkpoints` through `AssistantCheckpoints.kt`. |
| Coding agents and their receipts | The runtime's home, `~/.local/share/icode/agents` and `~/.icode/agents`, through `AgentInstalls.kt` and `AgentReceipts.kt`; the registry's last list in `<filesDir>/agents/registry.json`. |
| Appearance, editor, terminal, explorer, Git, and workspace preferences | DataStore through `data/settings/AndroidWorkspacePreferencesRepository.kt`. |
| Language services, runs, notices, phone/voice state, assistant/MCP/provider metadata, and interruption ledgers | Feature-owned private SharedPreferences stores. |
| Git/provider/MCP/speech secrets | Ciphertext in private stores, keys in Android Keystore through `data/io/KeystoreCipher.kt`. |
| Downloads, logs, syntax/runtime scratch, and PRoot temporary data | App cache and guest `/tmp`; treated as disposable. |

Persisted workspace/document/UI metadata can reconstruct a session view. It cannot reconstruct live guest processes.

## Security boundaries

1. **Android UID:** the app UID and SELinux policy are the primary sandbox. PRoot fake root adds no Android privilege.
2. **Private storage:** workspaces, runtime, chats, drafts, settings, and credentials are private unless explicitly exposed. Backup and device transfer are disabled.
3. **DocumentsProvider and FileProvider:** workspace tree access uses Storage Access Framework URI grants; one-file sharing uses scoped grants. `WorkspacesProvider.kt` blocks traversal and symlink escape.
4. **Shared storage:** `MANAGE_EXTERNAL_STORAGE` is a broad app-wide grant. It is requested through Android settings and governs native and guest access under the same UID.
5. **Secrets:** Android-owned credentials use Keystore-backed encryption. Logs and snapshots omit secret values where iCode controls serialization.
6. **Network:** `app/src/main/res/xml/network_security_config.xml` rejects cleartext for Android HTTP clients except `localhost` and `127.0.0.1`. Guest processes are separate: Git and user-installed tools follow their own URL and transport configuration, and app-managed Git currently accepts `http://` remotes.
7. **Exported components:** `MainActivity`, `SignInReturnActivity`, the permission-protected DocumentsProvider, and the Shizuku provider are exported as declared in `app/src/main/AndroidManifest.xml`. Accessibility, runtime/phone foreground services, and FileProvider are not generally exported.
8. **High-impact grants:** Accessibility, microphone, All Files Access, package installation, notifications, battery optimization, and Shizuku remain Android/user-controlled permissions or settings.
9. **External tools:** a user-installed guest program has the filesystem and network access granted to the app UID. iCode does not claim PRoot isolates an untrusted tool.

The root [Privacy policy](../PRIVACY.md) and [Security policy](../SECURITY.md) are canonical for public policy and reporting.

## Build variants and release topology

`app/build.gradle.kts` defines build types rather than product flavors:

| Build type | Package/version behavior | Web/update behavior |
|---|---|---|
| `debug` | `.debug` application ID suffix and `-debug` version suffix; includes debug UI/capture tooling. | No update channel. |
| `release` | Stable application ID; minification and resource shrinking enabled. | Stable update channel. |
| `staging` | Release-derived `.staging` application ID and version suffix. | No update channel; may use a staging website URL. |
| `preview` | Release-derived `.preview` application ID and numbered version suffix. | Preview update channel. |
| `beta` | Release-derived `.beta` application ID and numbered version suffix. | Beta update channel. |

Separate application IDs produce separate Android UIDs, app-private data, workspaces, runtime trees, permissions, provider authorities, and Keystore entries. `-PicodeWebUrl` controls website integration per build.

The module compiles and targets API 36, requires API 30, uses Java/JVM 17, and packages only `arm64-v8a`. Packaging extracts native libraries and build tasks verify 16 KiB ELF load alignment. `.github/workflows/ci.yml`, `codeql.yml`, and `release.yml` own repository automation; their remaining coverage gaps are recorded in [roadmap.md](roadmap.md).

## Current constraints and roadmap boundary

The following are current architectural facts, not future design:

- one physical `:app` Gradle module;
- phone-oriented shell with no adaptive width-class implementation;
- app-private managed workspaces removable by Android Clear storage;
- all guest processes see all of `/workspace`;
- shared-storage contents are usable only after Android grants All Files Access;
- no iCode-wide process count cap;
- no restoration of live process, PTY, LSP, MCP, transfer, or build state after process death;
- PRoot fake root is not Android root or a security sandbox;
- project search is read-only and Output is Git-only;
- existing Android projects can be built, but no new-project template exists;
- modern MCP behavior is limited by the pinned official SDK.

Implementation gaps, verification matrices, external server work, and decisions must be updated only in [roadmap.md](roadmap.md), not added here as hypothetical architecture.
