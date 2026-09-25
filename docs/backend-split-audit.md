# Backend Split Audit and Cycle Plan

Status: **evidence** — written by an agent (2026-09-25), verified and corrected 2026-09-26 (§0).
The owner's plan, scope and order are in [code-separation-plan.md](./code-separation-plan.md);
where the two differ, that plan governs. Scoped application of
[backend-split-rules.md](./backend-split-rules.md) §§2–7 to the existing Android backend
(`data/`, `domain/`, `demo/`). It lists every oversized owner, duplication, inconsistency and
verified gap, then splits them in small compilable cycles that preserve current behavior exactly.

> **Note (2026-09-26).** K1 and K2 are done ([code-separation-plan.md](./code-separation-plan.md) §7): `demo/` is gone; the names below are the evidence as measured before.

Owner decisions recorded 2026-09-25:

1. **Granularity:** balanced — one coordinator per real lifecycle, ~90–270 lines; neither a swarm
   of tiny files nor a few large ones. Files near 270 lines are re-reviewed if they grow
   (guard: 300-line budget).
2. **Dead code:** remove it (unreachable dispatch branches, the no-op intent).
3. **Debug logging:** remove leftover probes, but give the backend one real logging + audit
   architecture for development (§6).
4. **Order:** one cycle at a time, review after each cycle; no wholesale rewrite.

Owner decisions recorded 2026-09-26:

5. **Demo layer:** replaced by small **capture fixtures** — only the data the Figma frames draw.
   The simulated backend (every intent answered with fake behaviour) and the tests of that
   simulation are deleted; the frame capture keeps working, proven by a pixel diff.
6. **Name:** "capture" — named by its only use, the Figma frame capture: `CaptureFixtures`,
   `captureMode`, launch extras `capture_route` / `capture`, UI-guard rule `capture-fork`.
   The word "demo" leaves the code, the tools and the living docs.
7. **Location:** debug builds only (`src/debug`); the release APK contains no fixture and no
   capture switch.
8. **Icon sources:** the three source images move from `app/src/main/assets/icons/` (1.4 MB shipped,
   unused) to `design/brand/` in the repository.

## 0. Review 2026-09-26 — what changed in this plan, and why

Verified against the code, the build and the device on 2026-09-26 before any edit.

**Holds (spot-checked):** the numbers in §1 (gateway 1858 lines, 91 intents, 19 effects); D1
(SHA-256 ×4: `RuntimeInstaller`, `DirectFileWorkspaceSource`, `DocumentDraftStore`,
`AndroidWorkspacePreferencesRepository`), D2, D5 (`CliGitService` built again at gateway `925`),
I2/G4 (`SetLanguageServiceRunning` dispatched nowhere), I3, I4 (probes at gateway `218–223`).

**Corrected:**

- **G1 was inverted.** The application ID is `dev.icode.mobile` (debug `dev.icode.mobile.debug`,
  `app/build.gradle.kts:16,45`) and the capture tool already targets it. Nothing to fix.
- **Routes and frames:** 40 routes = 39 with a Figma frame + `Notifications` (owner's page, no
  frame); 40 Figma frames = 39 routed + 3:55 (the failed state of 3:27). The capture takes **39**
  screenshots (`capture-figma-screens.sh`, `RouteTest`).
- **C8 "demo parity split" is dropped.** Splitting the simulated backend into sections that mirror
  C3–C7 would add a second, parallel copy of every coordinator — the opposite of decision 5. With
  the simulation gone first (K1), cycles C2–C7 no longer touch any fake backend.

**Found (not in the original audit):**

| ID | Finding | Evidence | Cycle |
|---|---|---|---|
| G5 | The runtime probe at gateway `223` logs the literal text `$details` (escaped `$`), not the value | `Log.d("iCodeRuntime", "inspected: ${'$'}details")` | C1 (probes removed) |
| G6 | Flaky unit test: `AndroidWorkspacePreferencesRepositoryTest` "persisted overrides survive repository recreation" failed once (`expected:<6> but was:<4>`), then passed 3× alone and in the full run | run of 2026-09-25 | H0 (root cause before any cycle relies on the gate) |
| G7 | Tool output staged for commit: `.kotlin/errors/*.log` (a compiler crash log), `.freebuff/project-id`; neither is in `.gitignore` | `git status` | H0 |
| G8 | Screenshots staged beside the canonical set: `artifacts/screens-2026-09-25/`, `artifacts/review-2026-09-25/`, `artifacts/review/launch-after-search-wiring.png` | `git status` | H0 |
| G9 | Icon source images ship in the APK, used by nothing: `app/src/main/assets/icons/{png.png, svg.svg, withBG.png}` (1.4 MB) | `grep` finds no reader | H0 (decision 8) |
| G10 | `data/log/` exists and is empty — C1 was started and stopped | `ls` (created 2026-09-26 00:20) | C1 |
| G11 | A product screen imports demo data: `DocumentRecoveryScreen` lists `DemoContent.recoveryDocuments`; its four buttons do nothing (`{}`) or navigate away; nothing in the app opens its route (accepted orphan) | `DocumentRecoveryScreen.kt`, `ui-debt-allowlist.txt` | K1 (real draft list + existing `RestoreDraft`/`DiscardDraft`); opening it at startup stays with session restore (Todo priority 6) |
| G12 | Nothing has been committed since 2026-09-22; 388 paths are staged in one undifferentiated change | `git log`, `git status` | H0 (owner: a baseline commit, so every cycle is its own reviewable diff) |

**The demo layer, measured (2026-09-26):**

| Piece | Lines | Serves |
|---|---|---|
| `demo/DemoWorkbenchGateway.kt` intent simulation (`dispatch` + helpers) | ~545 | nothing a user can reach: it runs only when the capture tool starts iCode with `demo_static=true` (`IcodeApp.kt:71-77`) |
| its snapshot + fixture constants | ~170 | the 39 captured frames; `PaletteCommandsTest` and `CommandPaletteExecutionTest` read only `snapshot.value` |
| `demo/DemoWorkspacePreferencesRepository.kt` | 139 | fixed settings values for the settings frames (setters simulate) |
| `demo/DemoContent.kt` | 35 | fixture constants; also imported by `DocumentRecoveryScreen` (G11) |
| `DemoWorkbenchGatewayTest` (8 tests), `DemoWorkspacePreferencesRepositoryTest` | — | test the simulation, not production |
| "demo" elsewhere | — | main code 12 files (`staticDemo` switch, extras `demo_screen`/`demo_static`, comments such as "Demo back-stack state"), tests 6, tools 3 (`demo-fork` rule, allowlist, capture extras), 14 docs |

Screens that ask the gateway for state when they open — the fixtures must hold the answer, or
the frame changes: Open Folder (`StartFolderBrowser` → the folder browser), Find
(`ClearContentSearch`); the others (`RefreshGit`, `RefreshGitAuth`, `RefreshStorage`,
`RefreshRuntimeDetails`, `MarkNoticesRead`) need no answer for a static frame. The pixel diff in
K1 is the proof, not this list.

## 1. Verified baseline

| Fact | Evidence |
|---|---|
| One `:app` module; packages `domain`, `data/{workspace,git,runtime,settings,language,notices,system}`, `demo`, `ui`, `designsystem`, `navigation` | `settings.gradle.kts`, tree |
| `AndroidWorkbenchGateway.kt`: **1858 lines, 94 methods**, 69 `mutableSnapshot.update`, 95 `mutableEffects.emit`, 42 `runCatching`, 9 `try/catch`, 37 `userMessage(...)` | `wc -l`, `grep -c` |
| `WorkbenchContract.kt`: **516 lines** — 44 snapshot fields, **91 intents**, 19 effects, 2 pure helpers, 1 interface | `awk` counts |
| Gateway unit tests | **none**; parsers/codec/draft store/indexer/projector/revision-guard are covered |
| Backend file budget (rules §4) | target ≤250 lines; >300 requires an ownership review |
| Baseline gate (this cycle) | `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug`, `check-ui-consistency.sh` |
| Baseline gate (2026-09-26) | green: 241 unit tests, lint 0 errors / 23 warnings, UI guard passes |
| Baseline frames | 39 route screenshots (40 Figma frames; 3:55 is a state of 3:27) captured before any edit (K0) |

## 2. Oversized owners (split conventions not followed)

| # | File | Lines | Concern clusters inside | Verdict |
|---|---|---|---|---|
| 1 | `data/workspace/AndroidWorkbenchGateway.kt` | **1858** (6.2×) | 12 clusters (below) | **Split now** |
| 2 | `demo/DemoWorkbenchGateway.kt` | **776** (2.6×) | same 91-intent surface + fixtures | **Replaced first (K1)** by debug-only capture fixtures, ~250 lines (§0, decision 5) |
| 3 | `domain/WorkbenchContract.kt` | **516** (1.7×) | models + git vocabulary + snapshot + 91 intents + 19 effects + helpers + interface | **Split now** |
| 4 | `data/runtime/RuntimeInstaller.kt` | **356** | manifest, download+checksum, extract, guest provisioning, dpkg config, health check, flow | **Split when touched** (network vs guest boundary) |
| 5 | `data/runtime/RuntimeForegroundService.kt` | 353 | notification group + session rows + wakelock + install tracking | **Reviewed → keep** (one Service lifecycle; already peeled) |
| 6 | `data/git/GitRepositoryClient.kt` | 353 | 20 commands **+ inspect-section parsing + guest/host path translation** | **Split parser seam now** (no test today) |
| 7 | `data/runtime/TerminalSessionManager.kt` | 332 | session map, main-thread confinement, settings, ledger | **Reviewed → keep** (single lifecycle; already peeled) |
| 8 | `data/workspace/DirectFileWorkspaceSource.kt` | 245 | directory browser + guards **+ document codec pipeline** | **Split when touched** |

Clusters inside `AndroidWorkbenchGateway.kt`: app observers · runtime install/details · system
access + permissions · folder browser · workspace session · recents + storage · file tree ·
documents + drafts · git status/actions · git remote · git auth + identity · language services ·
search + index · dispatch router.

## 3. Duplication (verified, exact sites)

| ID | Duplication | Evidence | Fix cycle |
|---|---|---|---|
| D1 | SHA-256 hex digest implemented 4×, with inconsistent hex formatting | `RuntimeInstaller.kt:338`, `DirectFileWorkspaceSource.kt:239`, `DocumentDraftStore.kt:108`, `AndroidWorkspacePreferencesRepository.kt:188` | C10 |
| D2 | `formatBytes` implemented 4× with different thresholds | `RuntimeInstaller.kt:351`, `RuntimeInstallingScreen.kt:254`, `StorageSettingsScreen.kt:151`, `RuntimeSettingsScreen.kt:142` | C10 |
| D3 | Relative-time formatting 2× | `AndroidWorkbenchGateway.kt:1826`, `CommitPushScreen.kt:167` | C10 |
| D4 | Two write paths for Git config/identity: adapter method vs gateway calling `gitCli.run("config", …)` | `GitRepositoryClient.kt:161` vs gateway `869`, `893`, `1171` | C7 |
| D5 | Second `CliGitService` built inside `provisionGit` although `gitCli` is a field | gateway `86` vs `925` | C7 |
| D6 | Stacked duplicate KDoc on one method | `GitRepositoryClient.kt:129-144` | C1 |
| D7 | One feature's contract split across two owners (git models in the workbench contract) | `WorkbenchContract.kt:129-175` vs `GitContract.kt` | C2 |

## 4. Inconsistency (verified, exact sites)

| ID | Inconsistency | Evidence | Fix cycle |
|---|---|---|---|
| I1 | Three failure-reporting patterns; mixed literal vs `userMessage` text; item creation uses its own effect | 42 `runCatching`, 9 `try/catch`, 37 `userMessage` in one file | C4–C8 |
| I2 | Dead dispatch branches: early returns at 276–296 make `416/428/448-450 -> Unit` unreachable; `SetLanguageServiceRunning` is a live no-op nobody dispatches | gateway; `grep` over `ui/` | **C1** |
| I3 | Fully-qualified refs instead of imports (`dev.icode.mobile.domain.*`, `android.util.Log`, `org.json.JSONObject`) | gateway `613`, `980`, `1259`, `1818`, `217` | **C1** |
| I4 | Leftover `Log.d("iCodeRuntime", …)` probes | gateway `217-227` | **C1** |
| I5 | No written persistence-ownership map (recents JSON, DataStore, drafts, Keystore prefs) | `WorkspaceStore`, `AndroidWorkspacePreferencesRepository`, `DocumentDraftStore`, `GitCredentialStore` | **C0 (doc)** |
| I6 | Domain carries presentation strings (`lastOpened`, `activePath`) | contract `7-15`, gateway `1826` | documented deviation |
| I7 | Lock map undocumented: 6 flows bypass `mutationMutex` | gateway `112`, `276-296`, `128`, `680`, `1443`, `1783` | **C0 (doc)** |

## 5. Gaps found (behavioral / tooling)

| ID | Gap | Evidence | Owner |
|---|---|---|---|
| G1 | ~~Capture tool targets a stale package name~~ — **inverted; no gap** (§0): the app is `dev.icode.mobile(.debug)` and the tool matches | `app/build.gradle.kts:16,45`, `tools/capture-figma-screens.sh:10` | — |
| G2 | Gateway has no characterization tests and builds its own Android adapters | gateway constructor `80-130` | C1 seam + C3–C8 pure tests |
| G3 | Inspect-section parsing (refs, last commit, path translation) has no unit test | `GitRepositoryClient.kt:41-105` | C7 |
| G4 | `SetLanguageServiceRunning` intent cannot change state in production | gateway `449`, Demo `418` | **C1 (removed)** |

## 6. Target design — balanced coordinator map (13 files + slim orchestrator)

One state owner, one write path per resource, one owner per lifecycle. No new Gradle module, no DI
framework, no service layer, no event bus, no repository wrappers around single calls.

| # | File (`data/…`) | ~lines | Owns |
|---|---|---|---|
| 1 | `workspace/WorkbenchStateHolder.kt` | 80 | the one `MutableStateFlow<WorkbenchSnapshot>`, `MutableSharedFlow<WorkbenchEffect>`, mutation lock policy, `onMain`, `emptySnapshot()` |
| 2 | `workspace/WorkbenchObservers.kt` | 120 | app-lifetime observers: terminals snapshot, session endings, notices/ledger, terminal preferences, runtime state report |
| 3 | `workspace/WorkspaceSessionCoordinator.kt` | 260 | open/switch/refresh workspace, file-tree toggle, create folder, folder browser + permission gate |
| 4 | `workspace/WorkspaceLibraryCoordinator.kt` | 170 | recent workspaces, recent documents, stale removal, storage measurement + cache cleanup |
| 5 | `workspace/WorkspaceItemsCoordinator.kt` | 90 | create file/create folder inside a workspace, creation guards, item effects |
| 6 | `workspace/DocumentSessionCoordinator.kt` | 260 | open/edit/save/close/select/reorder documents; one guarded write path |
| 7 | `workspace/DocumentRecoveryCoordinator.kt` | 140 | drafts, unresolved recovery, recovered-external-change guard |
| 8 | `git/GitWorkspaceCoordinator.kt` | 230 | inspect/refresh/decorate, stage/commit/branch/discard/abort, diff, conflict, git provisioning |
| 9 | `git/GitTransferCoordinator.kt` | 130 | fetch/pull/push/clone + progress publishing |
| 10 | `git/GitAuthCoordinator.kt` | 200 | tokens, SSH, connection test, identity read/write (through `GitRepositoryClient`) |
| 11 | `language/LanguageServiceCoordinator.kt` | 180 | language-service config + process lifecycle |
| 12 | `workspace/SearchCoordinator.kt` | 90 | content search + file index (swap point for ripgrep later) |
| 13 | `runtime/RuntimeCoordinator.kt` | 230 | install/repair/reset, runtime details/state, system access + permissions |
| — | `WorkspaceGateway` orchestrator (renamed file, same class) | 250 | fields, `init`, dispatch router, terminal accessors, `close()` |

Each coordinator takes only the adapters it needs (2–4) plus the state holder; tests can construct
one coordinator with fakes. The orchestrator remains the composition root and keeps the public
`AndroidWorkbenchGateway(context) : WorkbenchGateway` type, so `AndroidWorkbenchViewModel`, the
terminal accessors and the capture fixtures are untouched.

**Capture fixtures (replace the demo layer, K1):**

| File | Source set | ~lines | Owns |
|---|---|---|---|
| `capture/CaptureFixtures.kt` | `src/debug` | 170 | the one fixture `WorkbenchSnapshot` the 39 frames draw (including the folder browser Open Folder asks for, the diff, conflict and discard subjects) and its constants |
| `capture/CaptureGateway.kt` | `src/debug` | 30 | `WorkbenchGateway` over that snapshot: every intent ignored, no effects — a frame is a picture, not a session |
| `capture/CapturePreferences.kt` | `src/debug` | 50 | the fixed `WorkspacePreferences` the settings frames draw; setters ignored |
| `capture/CaptureEnvironment.kt` | `src/debug` + `src/release` | 15 each | `CaptureEnvironment.create(): Pair<WorkbenchGateway, WorkspacePreferencesRepository>?` — the fixtures in debug, `null` in release, so the release APK has no fixture and ignores the capture extras |

`captureMode` (was `staticDemo`) stays in main as a behaviour switch — "do not auto-navigate
while a frame is captured" — and is always false in release. Unit tests stop borrowing app
fixtures: the palette tests build their own snapshot in the test source set.

**Contract split (same `dev.icode.mobile.domain` package ⇒ zero import churn):**
`WorkspaceModels.kt`, `WorkspaceStorageModels.kt`, `WorkspaceSearchModels.kt`,
`DiagnosticsContract.kt`, `LanguageServiceContract.kt`, `WorkbenchSnapshot.kt`,
`WorkbenchIntent.kt`, `WorkbenchEffect.kt`, `WorkbenchGateway.kt`, `UrlDetector.kt`,
`WorkspaceNameRules.kt`; the git models move into the existing `GitContract.kt` (D7).

**Persistence ownership map (I5 — one writer per fact):**

| Fact | Owner | Storage | Never stored in |
|---|---|---|---|
| Recent workspaces, recent documents | `WorkspaceStore` | SharedPreferences `workspace_history` (`recent_workspaces_v1`, `recent_documents_v1`) | DataStore, workspace dirs |
| App + workspace preferences | `WorkspacePreferencesRepository` | DataStore `workspace_settings` (hashed workspace keys) | SharedPreferences, snapshot |
| Document recovery drafts | `DocumentDraftStore` | app-private `files/document-drafts/*.draft` (`icode-draft/1`) | DataStore, workspace dirs |
| Git credentials | `GitCredentialStore` | Keystore-encrypted SharedPreferences `icode.git.credentials` | logs, snapshots, workspace dirs |
| Offered permissions | `AndroidSystemAccess` | SharedPreferences `onboarding` (`offered.*`) | DataStore |
| Installed runtime marker | `RuntimeLayout.installMarker` | `runtime/installed.json` | preferences |
| Open workspace/tabs | none yet (session restore is Todo #6) | — | — |

**Lock map (I7 — what is serialized and what is not):**

| Path | Lock | Why |
|---|---|---|
| `dispatch` → locked handler | `mutationMutex` | one state transition at a time |
| `UpdateDocumentText`, `SelectLanguageService`, `RefreshStorage`, `RefreshRuntimeDetails` | none (routed before the lock) | keystroke rate and read-only reads must not queue |
| draft read/write/delete | `draftMutex` | slow file I/O off the mutation lock |
| `scheduleGitRefresh` | `mutationMutex` inside its own coroutine | coalesced git refresh after saves |
| runtime install, storage refresh | own `Job` + cancel-previous | long-running, latest wins |

**Logging + audit architecture (replaces ad-hoc probes):**

- `data/log/BackendLog.kt` — one seam: `d(subsystem, message)`, `i`, `w`, `e(subsystem, message, error)`;
  tag `iCode/<subsystem>`; `d`/`i` only in debug builds, `w`/`e` always; `credentialSafe(text)` strips
  `user:token@` from URLs and never logs secrets.
- `data/log/WorkbenchAudit.kt` — bounded in-memory ring (last 200) recording risky operations with
  outcome: discard changes, delete branch, runtime reset/repair, recovered-draft overwrite,
  credential save/clear, workspace switch, item creation. Logged via `BackendLog`; the ring is the
  hook for a later dev screen (no UI change now).
- Failure policy (I1): each coordinator reports through its own `report*` helper with a context
  string, so every failure produces one log line **and** exactly one effect (message or item effect).

## 7. Cycles (each compiles, verifies, preserves behavior)

| Cycle | Scope | Deliverable | Exit criteria |
|---|---|---|---|
| **C0** | Audit + baseline | this document; baseline gate; baseline 39-route frames | gate green; frames captured before any edit |
| **H0** | Repository hygiene | `.gitignore` gains `.kotlin/`, `.freebuff/`, dated capture folders; those unstaged (kept on disk); icon sources → `design/brand/` (G9); G6 flaky test root-caused and fixed; owner-approved baseline commit (G12) | gate green three runs in a row; APK 1.4 MB smaller; `git status` shows no tool output |
| **K0** | Capture baseline | 39 screenshots from the current build into `artifacts/screens/` (replacing the 2026-09-22 set) | 39 files; the set is the parity reference for K1 |
| **K1** | Demo → capture fixtures | §6 capture files in `src/debug` + `src/release`; `staticDemo`→`captureMode`, extras → `capture_route`/`capture`; `demo-fork`→`capture-fork` (script + allowlist); simulation, `DemoContent`, both demo test classes deleted; palette tests on a test-local snapshot; `DocumentRecoveryScreen` reads real drafts (G11); code comments reworded | gate green; **pixel diff = 0 over all 39 frames vs K0**; `assembleRelease` builds and its dex holds no `capture` class; `grep -ri demo app/src tools` → 0 |
| **K2** | Living docs | "demo" → capture fixtures in `Todo.md`, `README.md`, `backend-split-rules.md` (its demo seam rule), `ui-split-plan.md` rules, `architecture.md`; dated records (integration-plan cycles, earlier plans) keep the names they had, with a one-line note | `grep` shows "demo" only in dated records |
| **C1** | Foundation: `WorkbenchStateHolder`, `BackendLog`/`WorkbenchAudit`, dead-code removal (I2/G4), import hygiene (I3), stray probes (I4), D6 | new `data/log/*`, `workspace/WorkbenchStateHolder.kt`; gateway uses them; lock-policy function + test | gate green; device launch clean; no UI/capture change |
| **C2** | Contract split | 11 `domain/` files + git models into `GitContract.kt` (D7) | gate green; identical types/tests; zero call-site edits |
| **C3** | Workspace cluster | `WorkspaceSessionCoordinator`, `WorkspaceLibraryCoordinator`, `WorkspaceItemsCoordinator` | gate green; capture diff = 0; workspace/browser/recents/storage device pass |
| **C4** | Document cluster | `DocumentSessionCoordinator`, `DocumentRecoveryCoordinator` | save/close/draft/external-change tests + device pass; capture diff = 0 |
| **C5** | Git cluster | `GitWorkspaceCoordinator`, `GitTransferCoordinator`, `GitAuthCoordinator`, `GitInspectParser` + test (G3), one config write path (D4/D5) | git device matrix incl. failure text; capture diff = 0 |
| **C6** | Language + search | `LanguageServiceCoordinator`, `SearchCoordinator` | gate green; language/search device pass |
| **C7** | Runtime + observers + orchestrator final | `RuntimeCoordinator`, `WorkbenchObservers`, slim orchestrator; failure policy I1 | orchestrator ≤300 lines; runtime repair/reset device pass |
| ~~C8~~ | ~~Demo parity split~~ | **dropped (§0)** — K1 removes the fake backend it would have mirrored; each C-cycle instead re-runs the capture | — |
| **C9** | Guard + docs | `tools/check-backend-consistency.sh` + allowlist ratchet; architecture/Todo updates | guard enforced in the routine gate |
| **C10** | Dedup close-out | D1 `Sha256`, D2 `ByteSize`, D3 `RelativeTime` shared helpers | single implementation each, tests, capture diff = 0 |

Cycle rules: one behavior change per cycle at most; never mix a move with a behavior edit; after
each cycle, re-run the gate and re-capture affected frames; a cycle that cannot show parity does
not proceed.

## 8. Behavior-preservation contract (never broken)

- 40 routes (39 with a Figma frame + Notifications) and back behavior; 40 Figma frames (39 captured + 3:55 as a state).
- `WorkbenchGateway` surface: 91 intents, 19 effects, 44 snapshot fields. (C1 removes exactly one
  intent — `SetLanguageServiceRunning` — because nothing dispatches it; recorded as a contract
  cleanup, verified against `ui/` and tests.)
- Persistence keys/formats from §6; drafts `icode-draft/1`; Keystore credentials.
- Terminal lifetime: sessions outlive screens; only the user ends them.
- One guarded document write path, including the autosave revision check.
- Deterministic capture output: 39 screenshots, pixel-identical to the K0 reference except where a cycle means to change UI (none of H0–C10 do).
- The release APK carries no capture fixture (checked from K1 on).

## 9. Verification gate and honest limits

Gate: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`,
`tools/check-ui-consistency.sh` (allowlist may only shrink), and — from C9 —
`tools/check-backend-consistency.sh`. From K1: `:app:assembleRelease` (no fixture in its dex) and
the 39-frame capture diff against K0. Device installs always use an APK built in the same step. Device: install + launch + logcat for runtime errors;
frame diff for touched UI (none of C1–C7 change UI).

Limits (recorded, not hidden):

- The gateway builds its own Android adapters today, so C1 adds no gateway-wide tests; tests land
  with each cluster where a pure seam exists (parsers, codec, draft store, revision guard, lock
  policy, tree projector). The rest is verified by device matrix + frame parity.
- `RelativeTime`/`ByteSize` formatting currently produces presentation strings inside the snapshot
  (I6). C10 keeps the exact same output; moving formatting to the UI would change captured pixel
  content and is explicitly out of scope.
- `RuntimeInstaller` (356 lines) and `DirectFileWorkspaceSource` (245 lines) are **not** split now:
  the first waits until runtime work touches it, the second when documents/browser work does
  (rules §4: a large file is an audit signal, not an automatic extraction rule).



