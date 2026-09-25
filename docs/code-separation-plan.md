# Code separation and hardening, by the user's lifecycle

Status: **plan** (2026-09-26). The owner's plan; it supersedes the order and scope of
[backend-split-audit.md](./backend-split-audit.md), which an agent wrote and which is used here only
as verified evidence. Follows [backend-split-rules.md](./backend-split-rules.md) and
[ui-split-plan.md](./ui-split-plan.md). Nothing here is executed before the owner approves it.

## 1. The owner's intention (what this plan must deliver)

1. **Separate the code the way the user lives the app** — by scope along the user's lifecycle
   (onboarding → workspaces → files → search → Git → terminal → tools → settings), respecting
   each scope's boundary, in the UI **and** the backend together.
2. In every scope, resolve the four points: **shared** code used from one place,
   **duplication** removed, **inconsistency** removed, **gaps** closed — and leave it
   **maintainable** (one owner per fact and per lifecycle, files within budget).
3. **Harden** UI and backend to zero known gaps and bugs: every control does something real or
   says it is unavailable; every lifecycle stage (enter, load, act, react, interrupt, recover)
   is handled; every failure is reported once, truthfully.
4. **No "demo"** left in code, tools or living docs; the demo code is deleted, not renamed and
   kept; no unnecessary files in the repository or the APK. Done cleanly, one step at a time.

Decisions already made by the owner and kept: balanced granularity — one owner per real
lifecycle, ~90–270 lines, 300-line budget (2026-09-25); dead code removed; one logging and
audit seam; one cycle at a time with review; capture fixtures, named "capture", debug builds
only; icon sources to `design/brand/` (2026-09-26).

## 2. How each scope is done (the same recipe everywhere)

Each scope is one vertical slice: its contract (`domain`), its backend owner (`data`), its
screens (`ui/screens`) and design-system parts. For each scope, in order:

1. **Map** the user lifecycle of the scope (backend-split-rules §5) and every control on its screens.
2. **Move** (behaviour unchanged): the scope's code out of the shared god objects into its own
   owner — backend coordinator, contract file, screen pieces — proven by the gate (compile,
   tests, lint, UI guard); no routine screen capture (owner, 2026-09-26).
3. **Fix**, one change at a time: the scope's duplication, inconsistency and gaps listed below,
   each with a test where a pure seam exists and a device check of the lifecycle stage it fixes.
4. **Harden**: walk the scope's lifecycle matrix on the device (enter, load, act, react,
   interrupt — background, kill, rotation, permission loss — recover).

A move and a behaviour change never share a step, so every difference in a test or on the
device has one cause.

## 3. Scope map

| # | Scope (user lifecycle) | Screens | Backend today → owner | Contract |
|---|---|---|---|---|
| S1 | **Onboarding and runtime** — launch, splash, install/repair/reset, permissions, launcher | Splash, RuntimeInstalling, PermissionExplanation, RuntimeSettings, About | gateway runtime + system-access clusters, `RuntimeInstaller` (356), `RuntimeInspector`, `AndroidSystemAccess` → `RuntimeCoordinator` | `RuntimeState`, `SystemAccess` |
| S2 | **Workspaces** — open folder, clone, create, recents, switch, storage | Launcher, OpenFolder, CreateFolder, CloneRepository, CloneProgress, StorageSettings, WorkspaceSettings | gateway workspace/browser/recents/storage clusters → `WorkspaceSessionCoordinator`, `WorkspaceLibraryCoordinator` | workspace models |
| S3 | **Files and editing** — tree, open, edit, autosave, save, close, drafts, recovery, outside changes | WorkspaceExplorer, WorkspaceEditor, EditorEmpty, EditorFind, ExternalFileChange, DocumentRecovery, create-item dialog | gateway tree/documents/drafts clusters, `DirectFileWorkspaceSource` (245) → `DocumentSessionCoordinator`, `DocumentRecoveryCoordinator`, `WorkspaceItemsCoordinator` | document models |
| S4 | **Search** — find in files, quick open | WorkspaceSearch (Files / In Files) | `WorkspaceIndexer` + gateway search cluster → `SearchCoordinator` | search models |
| S5 | **Git** — status, diff, stage, commit, branches, conflicts, sync, credentials | SourceControl (299), DiffView, BranchSelector, ConflictResolution, CommitPush, PullProgress, GitAuth (275), DeleteConfirmation | gateway git clusters, `GitRepositoryClient` (353) → `GitWorkspaceCoordinator`, `GitTransferCoordinator`, `GitAuthCoordinator`, `GitInspectParser` | `GitContract` (all git models, D7) |
| S6 | **Terminal and background** — sessions, background, alerts, lost sessions, notifications | Tools panel Terminal, TerminalSettings, Notifications | already separate: `TerminalSessionManager`, `RuntimeForegroundService`, `TerminalAlerts`, ledger, `NoticeStore`; the gateway only observes | terminal contracts |
| S7 | **Tools panel and commands** — problems, output, command palette | ProblemsPanel, OutputPanel, CommandPalette | gateway (diagnostics none yet; Git transcript) | diagnostics contract |
| S8 | **Settings and language services** | Settings root, Appearance, Editor, Languages, LanguageDetail | `AndroidWorkspacePreferencesRepository`, `LanguageServiceStore` + gateway cluster → `LanguageServiceCoordinator` | settings, language-service contracts |
| X | **Across scopes** — app shell, navigation, design system, logging, capture fixtures, repository | `IcodeApp` (321), `MainActivity` | `WorkbenchStateHolder`, `BackendLog`/`WorkbenchAudit`, shared helpers | `WorkbenchGateway`, snapshot/intents/effects files |

## 4. Findings, per point (evidence 2026-09-25/26)

### Shared and duplication

| Where | What | Scope |
|---|---|---|
| `RuntimeInstaller`, `DirectFileWorkspaceSource`, `DocumentDraftStore`, `AndroidWorkspacePreferencesRepository` | SHA-256 hex written 4× | X (`Sha256`) |
| `RuntimeInstaller`, `RuntimeInstallingScreen`, `RuntimeSettingsScreen`, `StorageSettingsScreen` (`formatStorageBytes`) | byte sizes formatted 4×, each differently | X (`ByteSize`; K1 made it from Storage's and moved Storage onto it) |
| gateway `relativeTime`, `CommitPushScreen` | relative time 2× | X (`RelativeTime`; K1 made it from Commit's and moved Commit onto it) |
| `WorkspaceIndexer`, `DirectFileWorkspaceSource` | `readBounded` 2× | S3/S4 (one bounded reader) |
| gateway, `RuntimeInspector` | `installedRelease` 2× | S1 |
| `RuntimeInspector.measure`, `StorageInspector.measure` | tree size measured 2× | S1/S2 |
| gateway `gitCli.run("config"…)` ×3 vs `GitRepositoryClient`; `CliGitService` built twice | two write paths for Git config (D4, D5) | S5 |
| ~~`WorkbenchContract` vs `GitContract`~~ | ~~one feature's models in two contracts (D7)~~ — all Git models in `GitContract` since X2 | X2 |
| ~~`demo/` (950 lines)~~ | ~~a second, simulated copy of the whole backend~~ — removed in K1 | X (K1) |

### Inconsistency

| What | Scope |
|---|---|
| Failures reported three ways (42 `runCatching`, `try`, 37 `userMessage`) — some log, some toast, some both, some neither | every scope (one `report` path: one log line + one user-visible effect) |
| Fully-qualified names instead of imports (gateway 214, 218–223, 613, 980, 1259, 1818) | X |
| `SharedPreferences.edit()` without the KTX form in 4 stores, `edit {}` elsewhere | S2, S5, S8 |
| Presentation text inside the snapshot (`lastOpened`, `activePath`) | S2 (kept; recorded deviation — moving it changes captured pixels) |
| ~~"demo" names and wording (12 main files, 6 tests, 3 tools, 14 docs)~~ — renamed in K1 (code, tools), K2 (docs) | X (K1, K2) |

### Gaps (a user can meet them)

| Gap | Scope |
|---|---|
| Changes made outside the editor (an agent in the terminal) never reach the Explorer, Source Control or open editors: no watcher; `WorkbenchIntent.Refresh` is dispatched by nothing; the ExternalFileChange frame is unreachable | S3 |
| Explorer cannot rename, move, copy or delete | S3 |
| DocumentRecovery: nothing opens it; restoring a draft from another workspace is refused ("outside the active workspace"). (Its fixed list and do-nothing buttons: fixed in K1) | S3 |
| Find in files can miss files (`WorkspaceIndexer`: ~24-directory cap, early stop, no ignore rules, not cancellable) | S4 |
| Session restore: open workspace and tabs are lost after a kill | S3 |
| About: update, diagnostics, licences, source, docs, notes, issues unavailable (URLs and licence are the owner's) | S1 |
| No Git on a fresh install until the user finds Source Control | S1/S5 |
| Git inspect parsing has no test; the gateway has no test | S5, X |
| Probe logs the literal `$details` (gateway 223) | S1 |
| Install space check ignores clearable space (`UsableSpace`, lint) | S1 |
| Markdown preview view handles touch without `performClick` (accessibility, lint) | S3 |
| A status animation reads state in a non-lambda `offset` (recomposes every frame, lint) | X |
| Flaky test: `AndroidWorkspacePreferencesRepositoryTest` failed once, passed after | S8 |

### Maintainability and repository

| What | Scope |
|---|---|
| `AndroidWorkbenchGateway` 1858 lines / 95 functions; `WorkbenchContract` 516; `GitRepositoryClient` 353; `RuntimeInstaller` 356; `IcodeApp` 321 | their scopes |
| Nothing committed since 2026-09-22; 388 paths staged as one change | X (H0) |
| Tool output staged: `.kotlin/errors/*.log`, `.freebuff/project-id`; dated screenshot sets beside the canonical one | X (H0) |
| Icon sources shipped in the APK (1.4 MB, unused) | X (H0) |
| Debug leftovers of the finished packaging test: `ExecProbeActivity`, `RuntimeProbeActivity`, prebuilt `libexectest.so` (the only 4 KB-aligned library in the APK) — **owner to confirm removal** | X (H0) |
| `libtermux.so`: ours ships (16 KB-aligned, verified) only because `pickFirsts` takes it before the library's 4 KB copy — make it explicit; lint's `Aligned16KB` then stops warning about a file that is not shipped | S6 |
| `data/log/` exists empty (a stopped attempt) | X |
| The in-app brand mark `icode_launcher_art.png` is a 1254 px, 714 KB bitmap drawn at logo size (splash, About) | X (use the launcher's own density-sized art) |

## 5. Order

| Step | What | Done when |
|---|---|---|
| **H0** | Repository: owner-approved baseline commit; `.gitignore` for tool output and dated captures, unstaged; icon sources → `design/brand/`; debug leftovers removed if the owner confirms; flaky test root-caused | gate green 3 runs in a row; nothing stray in `git status`; APK smaller |
| **K0** | Capture baseline: the 39 route screenshots from the current build | the parity reference exists |
| **K1** | Demo → capture fixtures (backend-split-audit §6): debug-only `CaptureFixtures`/`CaptureGateway`/`CapturePreferences`, release stub; `captureMode`, `capture_route`/`capture`, `capture-fork`; the simulation and its tests deleted; palette tests on their own snapshot; DocumentRecovery on real drafts with the existing restore/discard | pixel diff 0 over 39 frames; release APK has no fixture; no "demo" in `app/src` or `tools` |
| **K2** | Living docs reworded; dated records keep their names with a note | "demo" only in dated records |
| **X1** | Foundation shared by every scope: `WorkbenchStateHolder`; `BackendLog` + `WorkbenchAudit` (probes removed); one failure-report path; shared `Sha256`, `ByteSize`, `RelativeTime`, bounded reader — **before** the scopes, so no scope copies them again | gate; each helper tested |
| **X2** | Contract split into one file per scope (same package, no call-site change); git models into `GitContract` | gate; zero call-site edits |
| **S1 … S8** | Each scope by the §2 recipe, in the user's lifecycle order: move → fix → harden | per scope: gate, its lifecycle matrix on the device |
| **G** | A backend consistency guard (file budget, one writer per store, no duplicate helpers, no UI → `data` imports) added to the routine gate | guard green and enforced |

Why this order: repository and demo first, so no later step carries dead weight; shared
foundations before scopes, so duplication is removed once instead of copied again; scopes in
the order a user meets them, so each scope is verified with everything before it already solid.

## 6. Verification

- Gate every step: `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` (no new warning;
  warnings in a touched scope fixed), `check-ui-consistency.sh` (allowlist only shrinks);
  from K1 `assembleRelease` with no fixture in its dex; from G the backend guard.
- No routine screen capture (owner, 2026-09-26: it does not serve this goal). A deliberate UI
  change is named in its step; the capture runs only when the owner asks.
- Device: an APK built in the same step; each scope's lifecycle matrix, including background,
  process kill and permission loss; results recorded in this document's §7.

## 7. Record

**H0 — repository (2026-09-26), done.**

- Baseline commit `d43cfc8` on the new branch `code-separation` (the owner approved a baseline
  commit; `master` stays at `95bb588` until the owner merges): the 344 staged paths of the work
  since 2026-09-22. Scanned before committing: no token, key or credential content in it.
- Left out of history and now ignored: `.kotlin/` (a compiler crash log), `.freebuff/`, the dated
  screenshot sets and a stray screenshot. `.gitignore` versions only the canonical capture set
  (`artifacts/screens/`, `artifacts/review/contact-sheet-*.png`); the files stay on disk.
- Icon sources moved to `design/brand/` (`icon.png`, `icon.svg`, `icon-with-background.png`); the
  APK no longer contains them.
- The finished packaging spike removed (owner confirmed): `ExecProbeActivity`,
  `RuntimeProbeActivity`, `libexectest.so`, the debug manifest that declared them, and
  `NativeExecSpikeTest` (it executes `libexectest.so`, so it could only fail without it). Its
  record in `runtime/bootstrap/README.md` and `Todo.md` notes the removal.
- The empty `data/log/` directory removed.
- **Flaky test, root cause measured:** `AndroidWorkspacePreferencesRepositoryTest` reopened a
  DataStore file after `cancel()`, which returns before the first instance has closed. Under CPU
  load, a probe repeating that 300 times failed 17 times with DataStore's own error ("There are
  multiple DataStores active for the same file"), which the repository reports as a load error
  with default values — the `expected:<6> but was:<4>` seen on 2026-09-25; with
  `cancelAndJoin()` 0/300. Both reopen points now wait, and the reopened read asserts no load
  error. The probe was removed. Production is not affected: every repository shares the one
  process-wide store of the `preferencesDataStore` delegate.
- Checks: gate green three times in a row with lint in parallel (241 tests each); after the
  removals: `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, 241 tests, lint 0 errors / 23
  warnings, UI guard passes; the APK has no icon source and no `libexectest.so`. (The debug APK's
  total size is not a measure: its un-minified dex files are laid out differently from build to
  build.)

**K0 — capture reference (2026-09-26), done.** Commit `61df5a5`: the 39 route screenshots from
the H0 build replace the 2026-09-22 set; the contact sheets under their new names.

**K1 — demo → capture fixtures (2026-09-26), done.**

- `app/src/debug/.../capture/`: `CaptureFixtures` (the former demo snapshot, plus the folder
  browser and recovery drafts it used to produce on request), `CaptureGateway` (draws the
  fixtures; ignores every intent), `CapturePreferences` (fixed values; stores nothing),
  `CaptureEnvironment` (the extras `capture_route` / `capture`). `app/src/release` has the
  same `CaptureEnvironment` answering null. Main code keeps only `CaptureState` (the gateway and
  preferences pair) and `captureMode`.
- **Found and fixed:** a release build honoured `demo_screen`, so any app could start iCode on
  any screen, onboarding skipped. Only a debug build reads the extras now.
- Deleted: `demo/` (950 lines) and its two test classes (11 tests of the simulation). One of
  them stated a rule of the real store that had no test there — terminal alerts on until
  switched off — now tested on `AndroidWorkspacePreferencesRepository`, across a restart.
  Palette tests use their own snapshot (`PaletteTestSnapshot.kt`).
- Document Recovery on real drafts: `recoverableDrafts` in the snapshot holds the drafts found
  at start (the last session's unsaved work) and shrinks as each is restored or discarded,
  under the draft lock so a discarded one cannot come back. `DocumentDraftStore.list()` reads
  headers only (a draft's text is never loaded to list it). Restore opens the file, where the
  existing prompt asks draft against disk; Discard asks first (the unsaved text is deleted);
  "Restore all" covers the drafts of the open workspace. `IcodeApp` now queues draft prompts —
  one slot lost all but the last when several files opened at once.
- Shared `ByteSize` and `RelativeTime` (domain) made from the Storage and Commit copies, which
  now use them — output unchanged; X1 moves the other copies and settles one size rule (today
  "1.8 KB" reads "2 KB", Storage's rounding).
- Tools: `capture-fork` rule and allowlist (a stale `PullProgressScreen` entry, 0 uses, dropped);
  the capture script's extras.
- Checks: `compileDebugKotlin`, `compileReleaseKotlin`; 232 unit tests pass (241 − 11 simulation
  tests − 1 replaced + 3 new); lint 0 errors, 23 warnings (unchanged); UI guard passes;
  `grep -ri demo app/src tools` → nothing. Release dex: no fixture string and no capture class
  (R8 removed `CaptureState` too); the debug dex has them. Capture: 38 of 39 frames
  pixel-identical to K0 below the status bar; frame 38 (Document Recovery) changed as intended —
  sizes and times from the shared formatters, every draft's dot amber (the demo greyed one for no
  reason) — and is the new reference. Device (APK from this build): the real app opened on
  Document Recovery with the phone's draft folder empty shows "Nothing to recover". Restoring was
  not run on the phone: autosave would write the draft into a real project file.


**K2 — living docs (2026-09-26), done.**

- Reworded where the text states how things are now: `README.md` (it also said Git, runtime,
  terminal and settings still ran on fixtures, "40 screens", and "zero debt" — all stale; now
  what the app does, the 39-route capture and its extras, the packages), `backend-split-rules.md`
  (rule 6 is now "one capture seam"; package list current), `ui-split-plan.md` rules 2, 3, 8, 10,
  `plan.md` guardrails, `editor-grammar-provenance.md`, and `Todo.md`'s open items (the About row
  said `0.1.0-demo`, fixed on 2026-09-25; the demo-cleanup section is now its done record).
- Dated records keep their names, with one note each naming the replacement: `Todo.md`,
  `integration-plan`, `attention-labels-cleanup-plan`, `settings-backend-split-plan`,
  `terminal-background-plan`, `commands-panels-plan`, `ui-polish-plan`, `sudo-capability-plan`,
  `backend-split-audit`. The owner's rule name "demo→real→gaps→cleanup" stays: it names the way
  of working, not code.

**X1a — shared helpers (2026-09-26), done.**

- `data/io/Sha256` replaces the four SHA-256 copies (runtime archive check, file fingerprints,
  draft file names, settings keys). Its output is pinned by the published test vector: a
  different form would orphan every stored draft and workspace setting.
- `data/io/readAtMost` replaces the two bounded readers (editor 5 MB, search 5 MB); each keeps
  its own message.
- `domain/ByteSize` now serves all five formatters (Runtime installer, Runtime installing,
  Runtime settings, Storage, Create folder). One rule: one decimal below ten and always for
  gigabytes — "4.2 KB", "12 KB", "14.2 GB free of 16.0 GB". Visible changes: sizes under 10 MB
  gain a decimal ("5.5 MB" was "6 MB"), under 1 MB they read in KB instead of "0 MB", and
  Document Recovery reads "1.8 KB" again instead of "2 KB".
- `domain/RelativeTime` serves the gateway's recents too; under a minute now reads "just now"
  there as on the commit screen (was "now").
- Checks: compile debug and release; 244 unit tests (12 new, one file per helper); lint 0 errors,
  23 warnings (unchanged); UI guard passes. The routine screen capture is no longer run (owner).

**X1b — one log seam (2026-09-26), done.**

- `data/log/BackendLog`: tags `iCode/<subsystem>`; debug and info lines in debug builds only,
  warnings and errors always; every line (stack traces included) passes `credentialSafe`, which
  masks `user:token@` in URLs — a Git error can echo the remote URL. Its writer is replaceable,
  so unit tests read what would be logged (3 tests: token masked, SSH address kept, tag and error).
- The runtime probes are gone (`refreshRuntimeDetails` logged every dispatch at debug and, through
  an escaped `$`, the literal text `$details` instead of the details); an inspection failure is now
  one warning. The terminal library's error and warning callbacks write through the seam
  (`iCode/terminal/<tag>`); its info/debug callbacks stay off (they would carry typed text).
- **`WorkbenchAudit` not built** (deviation from the audit doc §6): an in-memory ring that nothing
  reads is the kind of dead weight this plan removes. The diagnostics export (S1, About) will
  decide what history it needs; until then failures are logged through `BackendLog`.
- Checks: compile debug and release; 247 unit tests; lint 0 errors, 23 warnings (unchanged); UI
  guard passes.

**X1c — state holder and one failure path (2026-09-26), done.**

- `data/workspace/WorkbenchStateHolder`: the one snapshot and effect stream, the mutation lock,
  `onMain`, the empty snapshot, and the failure path — `report(subsystem, fallback, error)` for a
  message, `failureText(...)` where the failure is shown inside state (failed runtime, failed Git
  operation, item-creation error, language load error). Both write one `BackendLog` warning and
  give the user the error's own words or the fallback, with any `user:token@` masked (tokens
  never reach the screen).
- The gateway uses it everywhere (71 updates, 36 reads, 96 emits, 3 locks, 7 main-thread calls):
  37 failure sites that showed a message and logged nothing now do both. Every coordinator split
  out in S1–S8 takes the holder, so none needs its own flows or its own report helper.
- Not changed here: failures that are swallowed on purpose (`runCatching { … }.getOrNull()` with
  a fallback) — each scope reviews its own against the lifecycle, with the report path ready.
  Noted for S5: the Git transcript shown in Output is not masked the same way.
- Checks: compile debug and release; 250 unit tests (3 new: one line and one message; fallback
  words; token masked on screen and in the log); lint 0 errors, 23 warnings; UI guard passes.

**X1d — dead code, names, X-scope lint, brand art (2026-09-26), done.**

- `SetLanguageServiceRunning` removed (nothing dispatched it; the gateway ignored it). The locked
  dispatch lost its three unreachable `-> Unit` branches: the Kotlin 2 compiler counts the early
  returns above it (`SelectLanguageService`, `RefreshStorage`, `RefreshRuntimeDetails`,
  `UpdateDocumentText`) toward exhaustiveness, so a new intent must still be placed.
- Fully-qualified names replaced by imports in the gateway (7), `RuntimeInspector`,
  `RuntimeInstaller`, `WorkspaceSettingsScreen`.
- Lint: the progress sweep reads its animated value at placement (`offset { }`), so it no longer
  recomposes every frame; `toUri()` in `IcodeApp`; the fixture's sample rootfs path is marked as
  text, not a file access. 23 → 20 warnings; the rest belong to their scopes (`UseKtx` stores S2/S5/S8,
  `UsableSpace` S1, `ClickableViewAccessibility` S3, `Aligned16KB` S6) or are version/policy notes.
- Brand mark: the 1254 px, 714 KB bitmap in `drawable-nodpi` becomes the same art per density at
  its largest drawn size (74 dp): 90 KB for all five, ~24 KB loaded on this phone. Checked on the
  device (About, capture mode): same composition, size and place; edges slightly crisper.
- Checks: compile debug and release; 250 unit tests; lint 0 errors, 20 warnings; UI guard passes.

**X2 — contract split (2026-09-26), done.**

- `WorkbenchContract.kt` (528 lines) is gone; same package, so no call site changed. Per scope:
  `WorkspaceModels` (S2, with storage), `DocumentModels` (S3, with drafts and recents),
  `SearchModels` (S4), `GitContract` (S5 — `GitChange`, `GitCommitSummary`, `GitRepositoryState`,
  `GitStatusCodes` joined the rest of Git's models: D7 closed), `DiagnosticsContract` (S7),
  `LanguageServiceContract` (S8); across scopes: `WorkbenchSnapshot`, `WorkbenchIntent` (145 — its
  members are nested, so it stays one file), `WorkbenchEffect`, `WorkbenchGateway`, `UrlDetector`,
  `WorkspaceNameRules`. Largest file 256 (`GitContract`).
- Living references updated (`Todo.md`, `backend-split-rules.md`); dated records keep the old name.
- Checks: compile debug and release; 250 unit tests; lint 0 errors, 20 warnings; UI guard passes.

**S1 move — `RuntimeCoordinator` (2026-09-26), done.** `data/runtime/RuntimeCoordinator` (140)
owns runtime state on disk, install/repair/reset (its job and the "already running" and "sessions
running" guards), the runtime details read, and system access (read, answered, declined). The
gateway routes to it (1849 → 1749 lines); the storage prompt's hand-off to a waiting folder
browser stays in the gateway until S2 owns the browser. Behaviour unchanged. Gate: 250 tests; lint
0 / 20; UI guard passes.

**S1 fixes — duplication and space (2026-09-26), done.**

- One tree measurer, `data/io/measureTree`, for the Runtime screen's rootfs size and the Storage
  screen's runtime, cache and workspace sizes. The two copies disagreed: Storage's gave up
  ("Size unavailable") on any socket or pipe — a terminal program's socket in the runtime was
  enough — and Runtime's skipped unreadable folders, printing a smaller number as if exact. Now:
  special files hold nothing, an unlistable folder or a walk past its bound means no number.
  5 tests. On the phone today: no unlistable folder or special file in the runtime, so nothing
  shown changes.
- The installed release is read in one place, `RuntimeLayout.installedRelease()`.
- The install's space check counts what Android can free (`getAllocatableBytes`) and asks it to
  (`allocateBytes`) when the space is only there once other apps' caches go; before, a phone full
  of clearable cache was refused. The Runtime screen keeps showing what is free right now, as the
  Storage screen does (lint notes the difference; marked as deliberate).
- Checks: 254 unit tests; lint 0 errors, 18 warnings; UI guard passes.

**S1 fix — Git for a first clone (2026-09-26), done.** A fresh install had no Git, and Clone —
often the first thing a new user does — ran `git clone` anyway and failed with a shell error.
Clone now installs Git first when it is missing, as a step of the same progress ("Installing
Git…"); if that fails, the clone form shows why. No separate onboarding step is added: Git is
installed where it is first needed (Clone, Source Control, credentials). Not run on the phone —
its runtime already has Git, and removing it would change your environment. Gate: 254 tests;
lint 0 / 18; UI guard passes.

**S1 harden — Runtime screen while loading (2026-09-26), done.** Found on the phone: for the
first ~7–10 s the Runtime screen said "Runtime Healthy — No base system is installed yet" with
every row "Not measured", and About said "Runtime Version: Not installed" — the empty details
read as facts while the rootfs (2.3 GB) was being walked. Now the header comes from the runtime
state known at start-up; the quick facts (release, architecture, path, packages, PRoot, free
space) are published at once and the size after it (`RuntimeDetails.inspected`,
`rootfsSize: StorageMeasurement?`), so Disk Usage reads "Measuring… · 25.8 GB free" and then
"2.3 GB used · 25.8 GB free" in the Storage screen's words (shared `storageSizeLabel`); About says
"Checking…". A second refresh while one runs is not started. `measureTree` walks with one
file-system call per entry (it had four). Checked on the phone at 1 s and 10 s. Gate: 254 tests;
lint 0 / 18; UI guard passes.

**S2 move — library and folder browser (2026-09-26), done.**
`WorkspaceLibraryCoordinator` (163): recent workspaces and recent files — the one writer of the
workspace store — plus storage measurement, stale-shortcut removal and cache cleanup.
`FolderBrowserCoordinator` (112): the browser, its wait for all-files access, destinations and
folder creation. The workspace switch itself stays in the gateway for now: it resets state that
S3–S8 still hold there (tree, documents, drafts, index, language processes) and becomes a
sequence of calls to their owners as they move. Gateway 1749 → 1551. Behaviour unchanged; on the
phone: launcher recents, Open Folder browser and Storage (runtime 2.3 GB, a workspace 58 MB) all
load. Gate: 254 tests; lint 0 / 18; UI guard passes.
