# Code separation and hardening, by the user's lifecycle

Status: **plan and execution record** (updated 2026-09-28). The owner's plan; it supersedes the order and scope of
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
| S5 | **Git** — status, diff, stage, commit, branches, conflicts, sync, credentials | SourceControl (299), DiffView, BranchSelector, ConflictResolution, CommitPush, PullProgress, GitAuth (275), DeleteConfirmation | gateway git clusters, `GitRepositoryClient` (353) → `GitWorkspaceCoordinator`, `GitTransferCoordinator`, `GitAuthCoordinator`, `GitInspectParser`; [detailed S5 plan](./git-separation-plan.md) | `GitContract` (all git models, D7) |
| S6 | **Terminal and background** — sessions, background, alerts, lost sessions, notifications | Tools panel Terminal, TerminalSettings, Notifications | already separate: `TerminalSessionManager`, `RuntimeForegroundService`, `TerminalAlerts`, ledger, `NoticeStore`; the gateway only observes; [detailed S6 plan](./terminal-separation-plan.md) | terminal contracts |
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
  warnings in a touched scope fixed), `check-ui-consistency.sh` (allowlist only shrinks),
  from S5 `check-backend-consistency.sh` (allowlist only shrinks);
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

**S2 fix — "last opened" is a time, not frozen text (2026-09-26), done.** `WorkspaceSummary`
and `RecentDocument` carried "4h ago" as text made when recents were last read, so a launcher
left open kept saying it. They now carry `lastOpenedAt`; the launcher, Storage and the empty
editor say how long ago when they draw (the recorded S2 deviation "presentation text in the
snapshot" is closed for these; `activePath` is a fixed display path and stays).

**X fix — one way to say how many (2026-09-26), done.** Counts were written five ways across
scopes — "file(s)", "branch(es)", inline if/else, or never ("1 files" in the folder browser,
seen on the phone). `domain/countOf(count, one, many)` now says it everywhere: folder browser,
workspace switch and close-others dialogs, lost sessions, diff summary, find results, conflict
gate, commit screen, conflict file, runtime packages. 1 test. Gate: 255 tests; lint 0 / 18.

**S3 move — tree, documents, recovery, new items (2026-09-28), done.** Out of the gateway:
`WorkspaceTree` (65: folders read, expanded set, rows with Git status), `DocumentSessionCoordinator`
(202: open, edit, save, select, reorder, close — and the read version a save must still find),
`DocumentRecoveryCoordinator` (246: drafts under their own lock, the unanswered-recovery lock,
the recovered-over-changes guard, the recoverable list), `WorkspaceItemsCoordinator` (91: new
file / folder). The two close paths that kept a draft by slightly different rules turned out to
be one rule (keep it while its recovery question is unanswered and there are no new edits), so
it is written once. The workspace switch in the gateway now calls `tree.reset`,
`documents.clear` and `recovery.workspaceClosing`. Gateway 1551 → 1114. Behaviour unchanged; on
the phone: a workspace opens with its tree, a folder expands, a file opens and closes, recents
say "just now", no draft is written for an unedited file. Gate: 255 tests; lint 0 / 18; UI guard
passes.

**S3 fix — one way to change a tab; recovery pinned by tests (2026-09-28), done.** The block
that replaced one open document in both the tab list and the active document was written five
times; `WorkbenchSnapshot.withDocument(location) { … }` (domain, 2 tests) is now the one way, so
the two can never disagree. `DocumentRecoveryCoordinator` runs on the JVM, so its lifecycle is now
tested (9): an equal draft is dropped silently; a different one locks the tab and asks, saying
whether the file changed since; "keep disk version" deletes and unlocks; restore makes the draft
unsaved work, leaves the recovery list and requires consent to save over a changed file; close
keeps a draft only while its question is unanswered and there are no new edits, for one tab or
several; a save of an unanswered tab asks again; a workspace switch drops only the closing tabs'
drafts; a draft follows the text. Gate: 266 tests; lint 0 / 18; UI guard passes.

**S3 gap plan — changes made outside the editor (2026-09-28).** Requirement: PRD PR-FILE-04
(Explorer refreshes after terminal, Git or external changes), PR-EDIT-07 and US-EDIT-009 (a
dirty file changed on disk offers Reload, Compare, Overwrite; nothing is lost silently),
US-FILE-005 (a clean open file updates). Today nothing notices: `WorkbenchIntent.Refresh` is
sent by no one, and the External Change screen is fixed sample text ("token-utils.ts") whose
buttons only navigate. Steps:

- **A — when to look.** Refresh on app resume, on leaving the Terminal panel, and when a command
  ends in a terminal of the open workspace (the service already samples each session's
  foreground program every 5 s). Requests coalesce into one refresh; it relists the tree,
  refreshes Git, and rebuilds Quick Open's index (which today stays stale for the session).
- **B — open files.** On each refresh, every open file is compared with disk: unchanged → nothing;
  clean and changed → reloaded in place; changed while it has unsaved edits → the External
  Change screen, made real: Reload (disk wins), Compare (disk vs. the editor, through Git's own
  `diff --no-index` into the existing diff view), Overwrite (the editor wins, said explicitly),
  Keep editing. Autosave does not fight it: an automatic save of such a file waits silently.
  Deleted on disk → said once; the tab and its text stay.
- **C — live watching (needs the phone).** Measure whether inotify sees writes the app's own
  processes make under `/storage/emulated`; if it does, a watcher on the open folders makes A
  immediate while iCode is on screen. Measurement started 2026-09-28 and paused: the owner was
  on a call.

**S3 gap A — when to look (2026-09-28), done in code; device check pending (owner on a call).**
`WorkbenchIntent.Refresh` is now sent on app resume and on leaving the Terminal panel, and the
gateway sends it itself when the foreground service sees a command end in a terminal of the open
workspace (`TerminalSessionManager.commandsFinished`). Requests are conflated into one refresh
(settle 300 ms), routed off the dispatch lock and run under it; a refresh relists the tree,
refreshes Git and rebuilds Quick Open's index (before, the index stayed as first built for the
whole session). Gate: 266 tests; lint 0 / 18; UI guard passes.

**S3 gap B — open files against disk (2026-09-28), done; A checked on the phone too.** A refresh
compares every open file with disk (`decideOnDisk`, 5 tests): a clean file takes the new version in
place; unsaved edits over a changed file raise a question; a deleted file is said once and its text
stays. A save that finds a different file on disk (`ChangedOnDiskException`, before a generic
error) raises the same question, and autosave then waits silently until it is answered. The
question is the editor's standard dialog (`IcodeConfirmDialog`, as for draft recovery — owner:
"make it consistent with existing dialogs"), over the screen where it happens: Reload from disk,
Compare versions (Git's `diff --no-index` of the disk file against the editor's text, written in
the file's own encoding to the guest's /tmp and removed after; shown in the diff view, and the
question returns on coming back), Overwrite disk with my edits (saved over exactly the version now
on disk), Cancel (keep editing). The External Change route renders the same dialog for the capture
set; its fixed "token-utils.ts" sample text is gone. On the phone, in a scratch workspace
(`Download/icode-watch-check`): a file written from the terminal appeared on leaving the terminal;
a clean open file changed from outside reloaded on resume; with unsaved edits the dialog came up
by itself; Compare showed disk against editor; Overwrite wrote the editor's text; Reload took the
disk's and removed the draft.

**S3 gap C — measured (2026-09-28).** inotify on `/storage/emulated/0/…` reports writes made by
the app's own PRoot processes: create, open, modify and close-after-write all arrived for a file
written from iCode's terminal. A live watcher is therefore possible and is the next step.

**S3 gap C — live watching (2026-09-28), done.** `WorkspaceWatcher` watches, with inotify
(`FileObserver`), the workspace root, every expanded folder and the folder of each open file, only
while iCode is on screen (`WorkbenchIntent.ScreenVisible` from the activity's start/stop); the set
follows the state. A burst of writes becomes one refresh — after a second of quiet, or every five
seconds while it goes on (`settleDelay`, `watchedFolders`: 4 tests). Quick Open's index is rebuilt
only when entries came or went, not after a plain save. On the phone, with iCode on screen and no
interaction: a file created from outside appeared in the Explorer within 3 s, and an open clean
file changed from outside showed its new text within 3 s. Gate: 275 tests; lint 0 / 18; UI guard
passes. The scratch workspace stays on the phone for the Explorer file-operation checks; it is
removed when S3 is done.

**S3 gap plan — Explorer file operations (2026-09-28).** Todo #2: rename, move, copy, delete, with
guarded paths, confirmation, and open tabs, recents and drafts following the change. As VS Code:
long-press a row → the Explorer's action menu (the one "+" already uses) with Rename, Cut, Copy,
Paste, Delete; Move is Cut then Paste, so no folder picker is needed. Rename uses the create
dialog's form; Delete the standard destructive confirmation ("cannot be undone"; a folder says
"and everything in it"). Guards: inside the open workspace, never the root, never `.git` or in it,
no folder into itself, no overwrite (a copy that would collide becomes "name copy"). A file with
unsaved edits or an unanswered draft question blocks the operation ("save or close it first");
clean open tabs follow a rename or move, are closed by a delete; recents follow or are dropped.
Found meanwhile: an expanded folder deleted outside iCode made every refresh fail — the tree now
drops folders that are gone.

**S3 gap — Explorer file operations (2026-09-28), done.** Long-press a row → Rename, Cut, Copy,
Paste, Delete (Paste also in "+" once something is cut or copied; per workspace). `WorkspaceFiles`
(8 tests on real folders) does the work with the plan's guards; `WorkspaceItemsCoordinator` blocks a
change that would strand unsaved edits or an unanswered draft question, then lets open tabs, open
folders and recent files follow a rename or move, closes the tabs a delete removed, and refreshes
tree, Git and index. The create effects became `WorkspaceItemChanged` / `WorkspaceItemFailed`, so
rename answers its dialog the same way. The tree no longer fails a refresh when an expanded folder
is deleted outside iCode, and an empty workspace now says so. On the phone, scratch workspace: a
file open in a tab renamed (one tab, new name, text kept); a folder created; Cut + Paste moved the
file into it; Copy + Paste beside itself made "live-added copy.txt"; deleting the folder asked
first ("…and everything in it… cannot be undone") and closed the moved file's tab. Gate: 283
tests; lint 0 / 18; UI guard passes.

**S3 gap — a way into Document Recovery (2026-09-28), done in code; device check pending (owner
using the phone).** While the last session's drafts exist, the Notifications page shows "Unsaved
work from the last session — N files kept as drafts · Review" at the top (the owner's chosen place
for such messages; not a stored notice, since it is work to resolve, not an event), and the bell
keeps its dot. Review opens Document Recovery, whose Restore now opens the file in its own
workspace when it is another recent one (`OpenRecoverableDraft`, `workspaceHolding`); with none, it
says to open that folder first and keeps the draft. The route is no longer unreachable (allowlist
entry removed). Gate: 283 tests; lint 0 / 18; UI guard passes.

**S3 gap — session restore (2026-09-28), done in code; device check pending.** Todo item 8: the
open workspace, its tabs in order and the selected tab are kept in the workspace store
(`session_v1`) as they change. On start they are reopened quietly — no navigation, no recents
bump — when storage access is granted and the folder still exists; files gone from disk are
skipped; a leftover draft still asks its question; terminals are never reopened (their own ledger
reports what ended). This is what the screens Android restores after ending the process in the
background need: before, they came back over an empty workspace. Gate: 283 tests; lint 0 / 18.
Lint found the session write's `edit()…commit()` (19 warnings): the workspace store's two checked
writes now go through one `commitString` helper, marked once — the KTX `edit {}` form would
discard `commit()`'s answer, which the store turns into a reported failure. 17 warnings.

**S4 move — `SearchCoordinator` (2026-09-28), done.** Find in files and Quick Open's index
move out of the gateway into `data/search/SearchCoordinator`, unchanged. Found while reading it:
`WorkspaceIndexer`'s depth limit counts folders visited, not depth, so both searches stop after
about 24 folders of any real project — the "search can miss files" of Todo #3 — fixed next, with
ripgrep as planned.

**S4 fix and harden — real ripgrep search (2026-09-28), done.** `WorkspaceIndexer` and its tests are
deleted. `RipgrepService` provisions the runtime's real `rg` on first use through the new shared
non-interactive `GuestCommand`; provisioning is shared across concurrent requests and is not killed
when one query is replaced. Quick Open uses `rg --files --null`; content search uses ripgrep's JSON
Lines protocol. Both include hidden files, exclude `.git`, honor ignore rules outside a Git
repository, preserve spaces/quotes/Unicode/embedded filename newlines, and stay byte-bounded. Match
byte offsets become the editor's UTF-16 columns. The index is capped at 5,000 files; visible Quick
Open at 50; content at 200 matches per file / 1,000 total, with truthful truncation.

`SearchCoordinator` owns separate cancel-previous jobs and generations for index/content work, so a
new query kills/reaps the old `rg` and a result from an old workspace cannot publish. Search runs
outside the gateway mutation lock and starts lazily only when Search/Quick Open is used. A workspace
change or file-structure change invalidates only an index that was actually used.

Found and fixed on the touched UI: Search status and results no longer overlap; file groups are real
accessible accordions; match counts remain one line beside an ellipsized filename; collapsed groups
hide their path and matches. Explorer folder taps no longer queue behind slow Git refresh, and
clicking blank Explorer space selects the workspace root for New File/New Folder without a full-area
ripple. `ExplorerRow` moved beside its item actions to restore the 300-line UI budget
(`WorkspaceExplorerScreen` 229, `ExplorerItemActions` 225), behavior unchanged.

Device: in `superpowers`, real search returned **355 results in 69 files · 69 files scanned**; the
accordion changed from Collapse→Expand and removed/restored its rows; Quick Open returned real
README paths. An isolated scratch fixture found a visible spaced/Unicode file and `.hidden.txt`,
excluded nested `.ignore` content and `.git/config`, exactly **2 results in 2 files**. Explorer
expanded/collapsed immediately, and after selecting a nested folder a blank-area tap made the create
dialog say `In icode-watch-check` (the workspace root). The isolated search fixture was removed;
no other workspace data was touched.

Final gate after the UI-budget extraction: debug/release Kotlin compile, **295 unit tests** (0
failures/errors), lint **0 errors / 16 warnings**, and UI consistency pass. A fresh APK from the
same build step was installed for the preceding device checks; no routine 39-screen capture ran.

**S5 — Git (2026-09-28), done.** Detailed plan and full record:
[git-separation-plan.md](./git-separation-plan.md) §12. Moves: `GitInspectParser`,
`GitWorkspaceCoordinator`, `GitTransferCoordinator`, `GitAuthCoordinator` (plus `GitConflictCoordinator`,
a lifecycle of its own), each behind a fakeable seam; the gateway keeps no Git command, credential or
config call (1227 → 831). Then one fix per commit: one package installer; Git on the cancellable guest
command; NUL-framed inspect; the repository around a subfolder, submodule or worktree; explicit detached
HEAD and stale-refresh guard; exact discard; per-block conflict writes through the editor's writer; remote
branch by full ref and Add Remote; the token out of saved state and out of plain text between commands;
masked transcripts; transfers off the lock, one at a time, cancellable, clone branch, `--` before
addresses, and a notice for a transfer cut off by process death. Also found and fixed: a template label
read aloud, an unnamed folder row, autocorrected addresses, and a Back loop after cloning. Deviation:
`safe.directory` stays `*` (Git 2.43 has no prefix form; exact entries would break submodules). New
routine gate member: `tools/check-backend-consistency.sh` (file budget ratchet, one apt owner, no shared
stderr file, no Git calls from `data/workspace`, no `data` imports in screens). Gate: 391 tests; lint
0 errors / 14 warnings; both guards pass. Device matrix recorded there, with what needs the owner's
credentials.

**S6 — terminal and background (2026-09-29), done.** Record: [terminal-separation-plan.md](./terminal-separation-plan.md)
§7. Moves: `TerminalCoordinator` out of the gateway (831 → 769); `TerminalNotifications` and
`TerminalLaunch` out of the service and the manager, both now within budget. Fixes: the panel's
automatic open reuses the running session (one request, one session — the likely cause of the
double session seen on 2026-09-24); a session's alerts leave with it; the build fails if a shipped
64-bit library is not 16 KB aligned. Checked on the device before the owner, who had already
verified the terminal, asked to stop. Found for the owner: the landscape keyboard leaves no
terminal rows; segmented controls do not report their selection to screen readers. The three older
terminal plans are closed. Gate: 406 tests; lint 0 errors / 11 warnings; both guards pass.


## 8. What remains — measured 2026-09-29

Owner (2026-09-29): "what's remaining and what gaps and issues; let's complete the whole remaining
one by one, no guess, no assumption, no duplication, no inconsistency". Every row below was
re-measured in the code on 2026-09-29, not copied from the dated tables in §4 or in
[backend-split-audit.md](./backend-split-audit.md); a suspicion that did not hold is listed as such.

### Found

| # | Finding | Evidence | Step |
|---|---|---|---|
| R1 | Whether a push, pull or fetch may start is decided in three places that disagree: the palette checks only for a remote; Commit & Push enables Push and Pull whenever a remote exists; `GitTransferCoordinator` refuses a detached HEAD. A refused push still opens "Syncing Repository", which then shows an older operation or "Nothing in progress". Pull is offered without an upstream, where `git pull --ff-only` must fail. Two wordings for one fact ("HEAD is detached…", "No branch checked out…") | `PaletteCommands.kt:37-38`, `CommitPushScreen.kt:47`, `GitTransferCoordinator.kt:86-90`, `PullProgressScreen.kt:97-105`, `GitRepositoryClient.kt:168-169` | S7.1 |
| R2 | A sample value in the domain: `Diagnostic.source` defaults to `"TS"`; only the capture fixture builds diagnostics | `DiagnosticsContract.kt:10`, `CaptureFixtures.kt:92-94` | S7.2 |
| R3 | Saving a new or a stopped language service rebuilds every row with `running = false`: another server still running reads Stopped until its process changes state | `AndroidWorkbenchGateway.kt:457-468, 507-510` | S8.2 |
| R4 | One fact twice: `LanguageService.running` and `.status`; nothing reads `running` | `LanguageServiceContract.kt:9,14`; no reader in `app/src` | S8.2 |
| R5 | The checked SharedPreferences write is written twice; `LanguageServiceStore` is the last `UseKtx` lint warning | `WorkspaceStore.kt:141-144`, `LanguageServiceStore.kt:40` | S8.3 |
| R6 | The segmented control draws its selected label in `OnAccent` — the colour for text *on the accent* — over the grey `Outline`: on Dark it is near-black on dark grey, fainter than the unselected option. Neither it nor `ChoiceChip` tells a screen reader which option is selected | `IcodeControls.kt:52-66, 71-86`; `artifacts/screens/19-editor-settings.png` (Tab Size); S6 record | S8.4 |
| R7 | Language services still live in the gateway (config, processes, view) | `AndroidWorkbenchGateway.kt:84-97, 206-212, 403-407, 457-557` | S8.1 |
| R8 | The workspace session still lives in the gateway: open/switch, refresh loop, watcher, session restore, drafts of another workspace, compare with disk | `AndroidWorkbenchGateway.kt:559-769` | X3 |
| R9 | Over the 300-line budget: gateway 769 (router ~200 of it), `RuntimeInstaller` 340, `DocumentSessionCoordinator` 314, `IcodeApp` 356. The UI guard checks `ui/screens` only, so `IcodeApp` is unguarded | `wc -l`; `tools/check-ui-consistency.sh` | X4, G1 |
| R10 | Byte sizes formatted outside `ByteSize`: the terminal's download line; `GuestCommand`'s two limits say "MB" and "MiB" for the same unit | `TerminalControls.kt:73`, `GuestCommand.kt:122, 232` | X5 |
| R11 | The persistence and lock maps in the audit are stale (no notices, ledgers or language store; "session restore: none yet"). Today each of the 9 stores is opened in exactly one file — nothing enforces it | `getSharedPreferences`/`preferencesDataStore` grep; audit §6 | G1, G2 |
| — | *Did not hold:* language servers are stopped on a workspace switch (`AndroidWorkbenchGateway.kt:598`); restart is bounded (stop kills after 2 s, restart waits 3 s) | read | — |

Owner decisions (2026-09-29): the selected option of a segmented control is an **accent pill**
(as the filter chips); the Light and AMOLED palettes are **accepted** ("from review I have selected
those, they are good"), so their review note goes.

### Steps, in the user's lifecycle order (each: gate, commit; device where it touches a screen)

| Step | What | Kind |
|---|---|---|
| S7.1 | One rule for fetch, pull and push (`GitSyncRules`, domain, tested): no workspace, Git missing, no repository, an operation running, no remote, detached HEAD, pull without upstream — one reason text each; the palette and Commit & Push disable with it; the coordinator keeps its guard with the same text | fix |
| S7.2 | `Diagnostic.source` without a default; the fixture names its source | fix |
| S7.3 | Device: palette (focus, search, empty, disabled reasons, touch and key selection, Back), Problems empty state, Output idle / failed | harden |
| S8.1 | `LanguageServiceCoordinator` out of the gateway, behaviour unchanged | move |
| S8.2 | Saving keeps every other service's live state; `running` removed (status is the one fact) | fix |
| S8.3 | One checked-write helper for both SharedPreferences stores | fix |
| S8.4 | Map every settings control (root search, Appearance, Editor, Terminal, Workspace, Languages, Language detail, Storage, Runtime, About) and fix what does not hold; R6 first | fix |
| S8.5 | Device: settings persist across a restart; a language service saved while another runs | harden |
| X3 | `WorkspaceSessionCoordinator` out of the gateway, behaviour unchanged | move |
| X4 | Ownership review of each file over 300: split at a real boundary, or record why it is one owner | move / record |
| X5 | R10 through `ByteSize` | fix |
| G1 | Guard: each store opened in one file; SHA-256 only in `Sha256`; no byte formatting outside `ByteSize`; the line budget for every `ui/` file | guard |
| G2 | Docs: the current persistence and lock map here; audit §6 marked superseded; Todo and README agree | docs |

### Added by the owner (2026-09-29)

| # | Request | Plan |
|---|---|---|
| E | "Colours for every file type in the editor, on every theme, nothing left over; redesign if it does not scale" | [editor-colors-plan.md](./editor-colors-plan.md): one file-type registry, one syntax palette per theme, one scope table, themes built in code, contrast and coverage proven by tests; 26 more file types |
| L | "Open Workspace and other actions take time before the screen responds — design one loading behaviour for the whole app, backend and frontend aligned, for a smooth experience" | Measure first: for each action, the time from tap to first visible response and to done, and what runs in between (the mutation lock, disk, Git, the runtime). Then one design — an immediate acknowledgement on tap, one progress vocabulary in the design system, state the backend publishes as it starts rather than when it ends — applied action by action. Its own plan once measured. Measured so far (2026-09-29, cold start): a tap on a recent workspace in the first ~0.5 s after the home screen shows does nothing, with no sign it was lost; from ~1.5 s it opens. The first file's ~1.4 s freeze is gone (E6) |

Order: E next (the owner's newest request), then S8, X, G, then L's measurement and plan.

### Not in this programme (features or the owner's)

Diagnostics need a language-server client first (Todo 8); Output channels beyond Git (Todo 9);
licences and About (Todo 4); `PROOT_L2S_DIR` and `htop` (Todo 7); HTTPS/SSH push with the owner's
credentials; terminal rows under the landscape keyboard (S6 finding, a layout decision); a
developer-tools onboarding step (Todo 5).

**S7 — tools and commands (2026-09-29), done.**

- S7.1 (`1344622`): `GitActionRules` is the one rule for whether fetch, pull or push can start —
  workspace, Git, repository, busy; then remote, branch, commits, upstream — used by the palette,
  Commit & Push, the automatic fetch, and again by `GitTransferCoordinator` as the job starts. A
  refused sync no longer opens the progress screen on an older operation; Pull without an upstream
  and Push without a commit are no longer offered to fail. 6 tests (4 new for the rule).
- S7.2 (`fa4a737`): `Diagnostic.source` has no sample default.
- S7.3, device (2026-09-29, fresh APK): the palette opened with the field focused; "pull" filtered
  to one command; "zzq" said "No matching commands"; disabled rows gave their reasons ("Stage
  changes first", "No unsaved changes"; in a repository without a remote, "Add a Git remote
  first" for Push and Pull); Enter ran the selected command and Back returned to the editor, not
  the palette; Problems said "No diagnostics reported" with 0/0/0; Output said "No Git output
  yet · IDLE". Commit & Push without a remote was not re-checked: the owner started using the
  phone. Gate: 424 tests; lint 0 errors / 12 warnings; both guards pass.

**S8 — settings and language services (2026-09-29), in progress.**

- S8.1: `LanguageServiceCoordinator` (`data/language`) owns the saved services, their server
  processes and the rows the Languages settings show — select, save, remove, start, stop,
  restart, stop all. The gateway routes the intents and stops the servers on a workspace switch
  and on close (769 → 638 lines; the guard's ratchet lowered with it). Code moved unchanged:
  `running` stays for S8.2. Gate: 449 tests; lint 0 errors / 12 warnings; both guards pass.
- S8.2: `LanguageServiceRules.rows` builds the rows from the saved configuration and keeps the
  process state shown for each — saving one service no longer shows another running server as
  Stopped (R3); the service just saved starts from Stopped. `LanguageService.running` is gone:
  `status` is the one fact (R4); the capture fixtures name their status. 4 tests (one fails on
  the old code). Gate: 453 tests; lint 0 errors / 12 warnings; both guards pass. The phone check
  is S8.5.
