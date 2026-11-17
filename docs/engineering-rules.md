# Engineering Rules

This document is the engineering contract for changes to the iCode Android repository. Source and tests remain authoritative for implemented behavior. The repository has one Gradle module, `:app`; domain, data, UI, design-system, and navigation boundaries are package boundaries within it.

## Ownership and dependency direction

| Concern | Current owner | Boundary |
|---|---|---|
| Immutable product state, IDs, intents, effects, validation, capabilities, and result types | `app/src/main/java/dev/icode/mobile/domain/` | Must not depend on Compose, Android storage, PRoot construction, Git CLI, or protocol clients. |
| Android, filesystem, runtime, Git, search, language, persistence, and other external integrations | `app/src/main/java/dev/icode/mobile/data/<feature>/` | Implements domain contracts. Must not own screen layout, navigation decisions, or fixture content. |
| Native editor-view integration | `app/src/main/java/dev/icode/mobile/ui/editor/` | May integrate the editor library, but document text, dirty state, persistence, and save results remain data-owned. |
| Cross-feature composition and intent routing | `app/src/main/java/dev/icode/mobile/data/workspace/AndroidICodeGateway.kt` and focused coordinators | May compose owners. Must not duplicate their parsers, persistence, or state. |
| Process-scoped state | `app/src/main/java/dev/icode/mobile/data/workspace/ICodeProcess.kt` and `ICodeStateHolder.kt` | Owns the process gateway, immutable `StateFlow<ICodeSnapshot>`, effects, and ordered workspace mutations. |
| Presentation and navigation | `app/src/main/java/dev/icode/mobile/ui/`, `app/src/main/java/dev/icode/mobile/navigation/`, and `app/src/main/java/dev/icode/mobile/designsystem/` | Consumes immutable domain state and sends typed intents. Must not perform direct filesystem, Git, process, or DataStore work. |
| Deterministic captures | `app/src/debug/java/dev/icode/mobile/capture/` | Uses production contracts and UI composition. Must not put sample behavior in production screens. |

The dependency direction is:

```text
UI -> domain contract <- data adapter
composition root -> concrete implementations
orchestrator -> feature contracts and adapters
token -> component -> screen -> host
```

Feature-to-feature work goes through a narrow contract or an orchestrator. Do not add circular feature dependencies. `designsystem/` stays feature-free. Data code does not import screen code. Screens do not import `data/`, parse command output, or hold a competing persisted copy of backend state.

The normal state path is:

```text
user action -> ICodeIntent -> AndroidICodeGateway
  -> owning coordinator or adapter -> external result
  -> replacement ICodeSnapshot or ICodeEffect -> UI
```

## One source of truth

For every behavior, enforce all of these rules:

- **One owner:** identify where the canonical fact lives, who mutates it, who observes it, and how it survives restart. UI-local state is limited to transient interaction state.
- **One guarded write path:** all callers, including assistant and automation callers, use the same validated operation. Documents use `data/workspace/WorkspaceFileSource.kt`; Git uses owners under `data/git/`; processes use their runtime contract; preferences use their owning repository.
- **One parser or normalizer per external format:** Git porcelain and diffs, ANSI, runtime output, paths, document encodings, and protocol payloads have one tested parser. Display text is never protocol state.
- **One effective-value resolver:** defaults, workspace overrides, capability gates, and derived status are resolved once and published immutably. Do not persist derived values or repeat fallback logic in screens.
- **One capability truth:** expose provider and prerequisite availability explicitly. Use meaningful states such as `Loading`, `Ready`, `Unavailable(reason)`, `Running`, `Failed(reason)`, and `Cancelled` where applicable. Never turn failure into sample output, fake success, or a silent default.

Representative current owners include `data/workspace/DocumentSessionCoordinator.kt` for open-document decisions, `data/files/FileOperationsCoordinator.kt` for cross-place file operations, `data/search/SearchCoordinator.kt` for cancellable search, `data/git/GitWorkspaceCoordinator.kt` and `GitTransferCoordinator.kt` for Git workflows, and `data/settings/AndroidWorkspacePreferencesRepository.kt` for application preferences.

## Screen and data separation

A screen receives immutable state and typed actions. It may own focus, expansion, selection, draft input, and similar short-lived presentation state. Editable state that must survive activity recreation uses `rememberSaveable` or a `Saver`-backed holder.

A screen must not:

- open a store, file, socket, process, Git client, or Android service directly;
- parse external output or derive a second canonical backend model;
- import a sibling screen package, `ui.IcodeApp`, capture fixtures, or a concrete data implementation;
- decide that a write succeeded before the owning repository or adapter commits it.

Navigation state belongs to `app/src/main/java/dev/icode/mobile/navigation/Route.kt`, `ui/NavigationState.kt`, and the route hosts. Adapters do not navigate. Screen hosts dispatch routes; they do not absorb feature business logic.

## Lifecycle and asynchronous work

Each feature must define its behavior for entry, load, action, reaction, interruption, recovery, and deterministic demonstration. Cover no-workspace, unavailable-provider, permission-loss, empty, partial, stale, success, failure, cancellation, retry, second-request, workspace-switch, background, and process-death cases as applicable.

- Give each independent operation the smallest correct lifecycle and owner. Search, Git transfer, tasks, assistant turns, runtime tools, managed processes, terminal sessions, and language servers do not share one global queue.
- Propagate coroutine cancellation. Do not translate cancellation into success or failure.
- Bound file, runtime, and network I/O. Apply timeouts and backpressure at the owning boundary.
- Tag async work with the relevant request, revision, and canonical workspace identity. Reject a result when any identity is stale.
- Define whether a second request cancels, replaces, queues behind, or is rejected by the first.
- Stop screen-bound observers when screens detach without terminating user-started terminal or managed work.
- Never claim that a PTY, process, language server, local MCP server, or transfer resumed after process death. Persist enough metadata to report interruption truthfully.
- Preserve dirty and user-owned data on interruption. Never silently overwrite or delete user files.

## Persistence and migration

Persistence stays with the fact's owner. The application does not use a general Room or SQLite store.

| Data | Current location or owner |
|---|---|
| Workspaces and repositories | `<filesDir>/workspaces`, including `.git` |
| Runtime, guest home, installed tools, and guest Git/GPG state | `<filesDir>/runtime/rootfs` |
| Workspace/session metadata | `data/workspace/WorkspaceStore.kt` |
| Dirty document recovery | `<filesDir>/document-drafts` through `DocumentDraftStore.kt` |
| Assistant chats | `<filesDir>/assistant/chats` through `AssistantChatStore.kt` |
| Appearance, editor, terminal, explorer, Git, and workspace preferences | DataStore through `data/settings/AndroidWorkspacePreferencesRepository.kt` |
| Feature metadata and interruption ledgers | Feature-owned private SharedPreferences stores |
| Git, provider, MCP, and speech secrets | Private ciphertext with keys managed through `data/io/KeystoreCipher.kt` |
| Cache, logs, scratch data, and guest temporary files | Disposable storage |

Keep a stored value's key/schema, type, validator, default, scope, and reset behavior together. Reads and writes go through one repository. A reset removes an override rather than persisting the currently inherited value. Preserve unknown future keys unless the owning schema explicitly rejects them.

Canonical workspace identity is stored once and reused for persistence keys and stale-result checks. Derived values are recomputed. On corruption, permission loss, full storage, or write failure, retain the last confirmed data and expose an actionable error.

The current compatibility rule permits stored keys and formats to change without reading, migrating, or cleaning data written by earlier builds. When stored-data compatibility is required, every format change must include an explicit, tested migration. Do not add speculative migration code.

## Inputs, secrets, logs, and user data

Validate input at the owner that crosses the trust boundary:

- Canonicalize and bound paths. `data/workspace/WorkspaceFileSource.kt` owns guarded text access, encoding and line-ending preservation, fingerprints, conflict detection, and atomic replacement. `data/storage/WorkspacesProvider.kt` rejects path traversal and symlink escape.
- Validate URLs, commands, protocol payloads, and persisted values before use. Prefer typed arguments over display strings or shell concatenation.
- Keep credentials and tokens in the relevant feature's security owner. Use Keystore-backed encryption for Android-owned secrets. Never place secrets in a general settings snapshot, fixture, source file, command transcript, or log.
- Redact credentials and sensitive paths from errors and logs while retaining enough safe evidence to diagnose failure.
- Do not commit provider responses, private workspace content, personal device data, or unsanitized logs and screenshots.
- Preserve user control around storage grants, accessibility, microphone, package installation, notifications, Shizuku, exported components, downloads, providers, MCP, and credential flows.
- Treat PRoot guest root as the Android app UID, not as a security boundary. Android HTTP clients reject non-loopback cleartext through Network Security Config; guest Git and user-installed tools are separate processes and follow their own transport settings.

Any change that stores, transmits, logs, exports, or deletes data requires a privacy check. Any new permission, exported component, deep link, native/runtime boundary, download, provider, MCP path, or credential flow requires a security check. Dependencies and bundled assets require provenance and license review.

## File budgets and splitting

Keep files focused and aim for about 250 lines or fewer. The guards report Kotlin files with more than 300 lines under `data/`, `domain/`, or `ui/`. More than 300 lines triggers an ownership review; it does not justify mechanical extraction. Existing allowlisted oversized files are debt: do not grow them, and split them only in a scoped change or when their ownership is deliberately touched.

Split when at least one real boundary exists:

- responsibilities have different lifecycles;
- a pure parser, validator, or algorithm can be tested without Android;
- an external system needs an adapter;
- multiple entry points need the same guarded operation;
- a class has unrelated reasons to change; or
- a real second consumer needs shared policy or a component.

Keep small, private, same-lifecycle logic with its owner. Avoid package-per-class trees, `Util` or `Manager` dumping grounds, generic repositories, speculative interfaces, event buses, dependency-injection frameworks, and broad constructor graphs. Do not split unrelated code merely because it is nearby or large.

For UI, use one public screen entry point per screen file as the default. Keep screen hosts as route dispatchers, with a target of roughly 60 lines. Design-system files must remain within the 300-line automated threshold when they are under `ui/`; files in `designsystem/` should also stay focused and near that budget even though the line guard does not scan that package.

## Design system

- Use semantic colors, typography, spacing, shape, size, and tone APIs from `app/src/main/java/dev/icode/mobile/designsystem/`.
- Do not use raw `Color(0x...)` outside `IcodeColors.kt` and `designsystem/palettes/`. Do not add numeric `.dp` or `.sp` literals in `ui/screens/`.
- Screens compose design-system components instead of independently restyling raw controls. The application shell owns `IcodeScreen`, insets, system-bar padding, and width clamps.
- Design-system APIs accept primitives, semantic values, and slots, never feature domain models. Screens map domain values to semantic component parameters.
- Keep a feature-private composable beside its screen. Promote it to `designsystem/` when a second real consumer exists or the design-system charter already owns the concept.
- Fully qualified `androidx.*` references do not belong in UI expressions; import the symbol.

## Capture fixtures

There is one capture seam, and it uses production contracts and UI composition:

- `app/src/debug/java/dev/icode/mobile/capture/CaptureFixtures.kt` owns deterministic content.
- `app/src/debug/java/dev/icode/mobile/capture/CaptureGateway.kt` exposes it through `ICodeGateway`.
- `app/src/debug/java/dev/icode/mobile/capture/CaptureEnvironment.kt` selects the debug gateway.
- `app/src/release/java/dev/icode/mobile/capture/CaptureEnvironment.kt` is the release twin; release code contains no fixture data.

Production screens must not select sample content, fabricate success, or change product behavior for capture. The only permitted `actions.captureMode` branches are timing gates that prevent automatic navigation while a stable frame is captured. New fixture data belongs in `CaptureFixtures.kt`, not in screens.

## Notices and sheets

The owning feature assembles user-facing notice text in `app/src/main/java/dev/icode/mobile/domain/notices/`. `data/notices/NoticeStore.kt` may reconstruct persisted notices. Screens and coordinators do not construct `Notice` text at the display site.

User-facing results flow through the notice model, `ui/NoticeHost.kt`, the Notifications page, and Android notification channels as appropriate. Do not use Android toasts.

Confirmation and modal interaction use `designsystem/IcodeConfirmSheet`, `IcodeModalSheet`, and the app-wide `designsystem/IcodeSheetHost.kt`. Do not use Compose dialogs, Android `AlertDialog`, AppCompat dialogs, or `DialogFragment`.

## Automated consistency guards

Run both scripts in normal verification mode. The tables below describe what the scripts actually scan; broader rules in this document still apply when no script checks them.

### `tools/check-backend-consistency.sh`

| Rule | Automated check |
|---|---|
| `backend-lines` | Reports each `*.kt` under `app/src/main/java/dev/icode/mobile/data/` or `domain/` with more than 300 lines. |
| `apt-owner` | Reports literal `apt-get` matches in main Kotlin except files ending in `RuntimePackageInstaller.kt`. |
| `git-stderr-file` | Reports any literal `icode-git-stderr`, the forbidden shared stderr-file convention. |
| `workspace-git-cli` | Reports `gitCli.run(`, `gitCli.runIn(`, or `gitCli.runScript(` calls under `data/workspace/`; Git invocation belongs under `data/git/`. |
| `screen-data-import` | Reports lines beginning exactly `import dev.icode.mobile.data.` under `ui/screens/` and `designsystem/`. |
| `store-owner` | Extracts names passed to `getSharedPreferences(` and `preferencesDataStore(`. It reports an unresolved non-literal/non-local-constant name and any store name opened by more than one recorded owner. |
| `sha256-owner` | Reports `"SHA-256"`, `"SHA256"`, or `MessageDigest` outside `data/io/Sha256.kt`. |
| `byte-format` | Reports likely division by 1024 or human-readable KB/MB/GB/KiB/MiB/GiB formatting outside `domain/ByteSize.kt`; comment lines are excluded. |
| `notice-text` | Reports constructor-like `Notice(` calls outside `domain/notices/` and `data/notices/NoticeStore.kt`, excluding class declarations and positional constructions beginning with a number. |
| `tm4e-owner` | Reports `org.eclipse.tm4e.*` and `io.github.rosemoe.sora.langs.textmate.registry.*` use outside `ui/syntax/`; comment lines and non-main source sets are not scanned. |
| `acp-owner` | Reports `com.agentclientprotocol.*` use outside `data/assistant/acp/`, the one owner of the external coding agents; comment lines and non-main source sets are not scanned. |

### `tools/check-ui-consistency.sh`

| Rule | Automated check |
|---|---|
| `raw-color` | Reports `Color(0x` in main Kotlin outside `designsystem/IcodeColors.kt` and `designsystem/palettes/`. |
| `fq-androidx` | Reports `androidx.` under `ui/` except import lines. |
| `dp-literal` | Reports numeric `.dp` literals under `ui/screens/`. |
| `sp-literal` | Reports numeric `.sp` literals under `ui/screens/`. |
| `toast` | Reports `android.widget.Toast` or `Toast.makeText` in main Kotlin. |
| `dialog` | Reports Compose `Dialog`, Material `AlertDialog`/`BasicAlertDialog`, Android/AppCompat `AlertDialog`, or `DialogFragment` in main Kotlin. |
| `capture-fork` | Reports `actions.captureMode` under `ui/screens/`. |
| `orphan-route` | Parses entries from `navigation/Route.kt` and reports a route with no reference outside `Route.kt` and files ending in `ScreenHost.kt`. Any other reference counts as an edge. |
| `ui-lines` | Reports each `*.kt` anywhere under `ui/` with more than 300 lines. |

The UI script's shared matcher excludes `IcodeColors.kt` and `designsystem/palettes/` from `raw-color`, `dp-literal`, `sp-literal`, `toast`, `dialog`, and `capture-fork` scans. This implementation detail does not relax the repository-wide prohibition on toasts and dialogs.

### Allowlist ratchet

`tools/backend-debt-allowlist.txt` and `tools/ui-debt-allowlist.txt` store `rule|path|max` rows. Verification compares each current report row with the exact rule and path:

- no matching row is a new violation and fails;
- a count above `max` is a regression and fails;
- a lower count passes without requiring an immediate allowlist edit;
- stale allowlist rows are ignored;
- moving or renaming violating code changes its path and creates a new violation.

Current backend allowances are only:

```text
backend-lines|app/src/main/java/dev/icode/mobile/data/workspace/AndroidICodeGateway.kt|337
backend-lines|app/src/main/java/dev/icode/mobile/data/workspace/DocumentSessionCoordinator.kt|313
```

Current UI allowances are only the two-count capture timing gates in:

```text
app/src/main/java/dev/icode/mobile/ui/screens/entry/CloneProgressScreen.kt
app/src/main/java/dev/icode/mobile/ui/screens/entry/RuntimeInstallingScreen.kt
app/src/main/java/dev/icode/mobile/ui/screens/entry/SplashScreen.kt
```

and one-count `orphan-route` entries for `EditorEmpty`, `LauncherEmpty`, and `ExternalFileChange`.

`--update` rewrites the entire allowlist from the sorted current report. The script does not prove that rewritten counts decreased, so review must enforce the ratchet: use `--update` only to record deliberately reduced debt, never to admit a regression.

## Testing expectations

Behavior changes require tests at the closest existing package and documentation updates where the contract or public behavior changes. Cover the applicable layers and failure modes:

- pure domain rules, validation, parsers, and effective-value resolution;
- adapter integration with realistic protocol fixtures;
- UI state and navigation;
- persistence, restart, and migration behavior;
- cancellation, interruption, stale-result rejection, and recovery;
- deterministic capture and visible-state parity; and
- device acceptance for Android, native, permission, lifecycle, or process behavior.

Use `app/src/test/java/` and `app/src/test/resources/` for JVM tests and fixtures, `app/src/sharedTest/java/` for tests shared with device source sets, and `app/src/androidTest/java/` for instrumentation.

For a broad code change, run:

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./tools/check-backend-consistency.sh
./tools/check-ui-consistency.sh
```

Also run `./gradlew :app:assembleRelease` when changing native code, packaging, shrinking, manifests, build types, dependencies, or debug/release source-set boundaries. Run `:app:connectedDebugAndroidTest` when the behavior needs device coverage, and `tools/capture-figma-screens.sh` for deliberate visual verification. Include before/after captures for visible UI changes. State which checks were not run.

## Pre-change ownership checklist

Before coding, answer these questions in the issue or change description:

1. What exact entry points, routes, controls, and existing actions must remain?
2. Where is the canonical state, who may mutate it, and which consumers react?
3. What is the single guarded write path, parser, effective-value resolver, and capability source?
4. Which request/revision/workspace identity rejects stale async results, and what cancels on close, replacement, workspace switch, or process death?
5. Where is data stored, what are its scope/default/reset rules, and does the change require compatibility handling?
6. Which paths, permissions, credentials, logs, external protocols, and user-data risks cross a trust boundary?
7. Which focused files and tests prove success, failure, cancellation, restart, multi-workspace behavior, and capture parity before old code is removed?
