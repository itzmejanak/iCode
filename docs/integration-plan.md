# iCode — Integration Plan (real behavior without losing the demo)

**Status:** approved 2026-09-22; Cycles 0 and 1 complete; Cycle 2 installed for user testing 2026-09-23
**Supersedes:** the ad-hoc "Latest workspace-logic round" entries in [`Todo.md`](../Todo.md)
**Reads with:** [`plan.md`](../plan.md), [`docs/prd.md`](./prd.md), [`docs/architecture.md`](./architecture.md), [`docs/user-stories.md`](./user-stories.md), [`docs/ui-split-plan.md`](./ui-split-plan.md)

> **Note (2026-09-26).** Names in this record are as they were then. The demo layer (`DemoICodeGateway`, `DemoContent`, `staticDemo`, `demo-fork`) was replaced by debug-only capture fixtures (`CaptureGateway`, `CaptureFixtures`, `captureMode`, `capture-fork`) — [code-separation-plan.md](./code-separation-plan.md) K1.

### Workspace closeout audit and order (2026-09-23)

This is the current implementation map for the requested workspace end-to-end pass. A design
frame is not automatically a navigation destination: keep its capture route until the real
parent state is verified, then retire only the redundant production route. Git/clone/sync
are excluded from this pass while their separate integration is in progress.

| Surface/frame | Product identity | Current real behavior | Remaining acceptance gap |
|---|---|---|---|
| Explorer `09` | route | real folder tree, expand and open | create/rename/move/copy/delete, refresh and failure states |
| Editor `10` | route | Sora, real tabs, save, Markdown preview | real New File, durable recent files, external-change and recovery cycle |
| Editor empty `25` | **state of Editor `10`** | host already selects it when no document is active | remove hard-coded recent rows and inert New File action; keep capture alias |
| Find `16` | **editor mode** | shares Sora document/searcher | complete device acceptance and return/focus behavior |
| Workspace Search `11` | route | local bounded scan, grouped clickable results | runtime-backed streaming `rg`, cancellation, filters, exact range, device acceptance |
| Quick Open `14` | picker route | real file index and ranking | recent files, hardware/focus behavior and runtime-backed file listing |
| Problems `18` | **bottom-panel tab** | snapshot-backed counts/list | make filter actually filter, empty state, diagnostic source and exact navigation |
| Output `29` | **bottom-panel tab** | none in production; sample Git transcript | real bounded channels and channel selector; no fabricated log lines |
| Terminal `17` | **bottom-panel tab/full expansion** | real sessions behind runtime gate | retain expanded mode; share tab/panel state with Problems and Output |
| External change `38` | **dialog over Editor** | sample-only route | disk fingerprint event, reload/compare/overwrite/cancel without data loss |
| Recovery `39` | conditional recovery flow | sample-only route | durable buffers, startup detection, per-file restore/discard/conflict |
| Workspace Settings `40` | route | local visual controls only | persisted workspace-scoped overrides and truthful unsupported settings |

Delivery order for this pass: (1) file creation and actual recent documents through the
existing editor/Explorer contract; (2) Explorer file operations with exact-path safety and
confirmation; (3) document recovery and external-change states; (4) Search/Quick Open on
the installed runtime's `rg`; (5) diagnostics, command/output panel and settings; (6) collapse
redundant production routes only after the same demo captures and real lifecycle pass.
Each step keeps the current UI controls, adds loading/empty/error/cancel states, checks local
tests/build/lint/consistency and waits for device acceptance when the phone is available.
Never delete demo data before the equivalent real state exists and is verified.

### Launcher recents interaction cycle (2026-09-23)

The recent row's overflow icon must expose Open, Open Terminal, Locate folder,
Copy path, and Remove from recents. Locate opens the folder browser at the exact
stored directory. Remove deletes only the persisted history entry and names
that consequence in its confirmation; it never deletes the directory. Unavailable
entries retain Copy path and Remove, and report why Open cannot proceed. Opening
a terminal switches to the selected workspace and selects or starts a session
bound to that path. The demo gateway exercises the same menu and intents.

Validation: unit tests for history removal, debug build and lint, UI consistency
guard, then a device check of the menu and terminal destination.

UI follow-up: recent rows match the 74 dp home action-card height while retaining
a 48 dp overflow touch target. Use a shared narrow iCode menu and compact
confirmation primitive rather than default Material styling; the same workspace
actions appear in Storage Settings. Existing full-screen recovery dialog frames
keep their routes/content and use the common tighter card padding.

Terminal UX follow-up: the compact extra keys use legible 11 sp labels and
48 dp-high targets; session labels and 48 dp close/new targets are enlarged
within a full-width tab bar. Transcript text starts at the shared 13 sp mono
style while pinch zoom remains available. Tapping the transcript still focuses
the keyboard, but its full-screen ripple is removed so the canvas does not flash.
The terminal alone uses a denser 48 dp header and 40 dp tab bar, with no
inserted spacer: the tabs meet the header above and transcript below cleanly.
The shared top bar keeps its divider by default; the terminal opts out so the
hairline no longer looks like a gap above its tabs.
Close/new controls remain 48 dp wide, with Compose's expanded vertical touch
target. Session IDs remain unique for the lifetime of the manager, while tab
labels reuse the lowest available number: closing `bash 4` and opening a new
tab shows `bash 4` again, and closing all tabs makes the next one `bash`.
Terminal scrollback now follows output only while the user is at the latest line;
reading older lines exposes a jump-to-latest control. Keyboard resize brings the
live prompt above the IME. Tabs remain horizontally swipeable, the selected tab
scrolls into view, and a long-press drag on a label reorders sessions. Real and
demo gateways use the same label-recycling and reorder rules. This does not
replace the interim ANSI transcript with a full VT renderer; that remains Cycle 6.

### Search-first delivery decision (2026-09-23)

Cycle 2 implementation uses Sora Editor `0.24.6` from Maven Central under
`io.github.rosemoe` (the old group in the table below is superseded). Its
per-document view owns find, replace, caret, undo, pinch zoom and incremental
coloring. Bundled TextMate grammars cover the source types listed in `Todo.md`;
Markwon `4.6.2` rendered the Markdown preview (since replaced: [markdown-rendering-plan.md](./markdown-rendering-plan.md)). The real and demo gateways now
expose an ordered multi-document tab collection and save/discard/cancel for
dirty close and workspace switch. Unit tests, debug assembly, lint and UI
guard passed; the APK was installed and launched on the attached phone.
User interaction acceptance remains open, so the demo gateway stays available
for deterministic capture. Next is frame 11 workspace search, then frame 14
Quick Open, after verifying ripgrep in the installed runtime.

The user-reported device view is frame `16 Editor Find` over `README.md`, **not**
frame `11 Workspace Search`. Its two blank controls lack context, and its code
body is a non-scrollable, capped preview rather than the real editor surface.
Source inspection confirms that Next/Previous change only a match counter,
invalid regex quietly appears as zero matches, `wholeWord` is ignored when
regex is selected, and Replace rewrites the complete document string. None of
these proves correct caret/selection, exact-range reveal or undo behavior.

Treat the two search domains separately. File-level search is an editor-engine
operation: verify the current Sora Editor artifact, API and LGPL obligations,
then integrate it behind the replaceable editor contract and make frame 16
drive the same open document/editor instance. Project-wide search and Quick
Open use the runtime process contract with ripgrep; verify the binary exists
in the installed image before wiring the UI. `rg --json` exposes per-match
line/offset data, and its default ignore behavior is the desired starting
point, but exact ranges and explicit filters still need contract/UI work.
Neither feature should get another hand-written matcher in a Compose screen.

Execution order for the next three cycles is **2 Editor/Find → 3 Workspace
Search/Quick Open → 4 Git**. The live issue determines this order; all three
retain the same demo→real→gaps→cleanup and verification gates. Each cycle is
split into one frame/user interaction at a time, not a wholesale screen rewrite.

---

## 0. Why this plan exists

The previous integration round wired real files, real JGit and a real scanner — but it paid for that
by deleting working UI, orphaning routes, forking demo logic into production screens, and
hand-rolling two engines the architecture had already chosen. Section 1 is the evidence; sections
2–4 are the rules and the corrected order of work.

Nothing in this plan removes a capability. Every cycle ends with the same or a larger set of
reachable screens and working controls than it started with.

---

## 1. Audit — what the last round actually changed

### 1.1 UI affordances removed, routes orphaned

| Frame | The demo offered | After the round | Consequence |
|---|---|---|---|
| 30 Commit & Push | `Push (1)`, `FETCH`, `PULL` | all three deleted, replaced by a "sync is not implemented yet" notice | **31 Pull progress now has zero navigation entry points** |
| 12 Source Control | row tap → 13 Diff view (`M`) / 37 Delete confirmation (`D`); `Resolve Sample Conflict` → 24 | every row only stages; the conflict button is `staticDemo`-only | **24 Conflict resolution unreachable in production**; 13 Diff view reachable only from 38, which is itself orphaned |
| 32 Git credentials | auth-method choice, SSH key detail, `Test Git Connection` | production replaces the whole frame with a different "Git Identity" screen | **Frame 32 no longer exists as a product surface** |
| 10 Workspace editor | three document tabs | one tab; opening a second file is refused while the first is dirty | US-EDIT-002 (multiple documents) lost |
| 11 Workspace search | search-as-you-type | an explicit `Search` button was added | slower loop; PR-SEARCH-04 (streaming) unmet |
| 11 + 14 results | — | results are not clickable | PR-SEARCH-03 / US-SEARCH-001 "selecting a result opens the exact range" unmet |
| all 40 | `IcodeType.Title`, bordered top bar | `IcodeType.Heading`, hairline divider | **accepted** as an intentional design correction (user decision, 2026-09-22); the 40 captures are re-baselined, not reverted |

Routes with **no** navigation entry point today: `PullFetchProgress`, `ExternalFileChange`,
`DocumentRecovery`. Effectively dead: `DiffView`, `ConflictResolution`.

### 1.2 Demo logic forked into production screens

`WorkspaceSearchScreen`, `QuickOpenScreen`, `EditorFindScreen`, `WorkspaceEditorScreen`,
`SourceControlScreen`, `GitAuthScreen` now each contain an `if (actions.staticDemo) … else …`
branch with a private demo body.

This breaks the rule `Todo.md` already states — *"Demo fixtures remain isolated in
`DemoICodeGateway` and `DemoContent`"* — and makes the documented "post-integration demo
cleanup" impossible to perform mechanically: the demo is no longer one deletable object, it is six
hidden branches inside product code.

### 1.3 The search logic is wrong

`data/workspace/WorkspaceIndexer.kt`:

1. **Depth bug (both `indexFiles` and `search`).** `depth += 1` runs once per *dequeued directory*,
   not once per tree level, while the loop guard is `depth <= MAX_DEPTH (24)`. A workspace with
   more than ~24 directories silently stops being scanned. Quick Open and search both under-report
   on any real repository, with no truncation signal for the part that was skipped.
2. **One file ends the whole search.** `if (matches.size >= MAX_MATCHES_PER_FILE || totalMatches >= MAX_TOTAL_MATCHES) truncated = true`
   sets the loop-terminating flag, so a single file with 200 matches truncates the entire run.
3. **`wholeWord` is silently ignored when `regex` is on** — the `when` branches are exclusive.
4. **Whole files are read into memory** (up to 5 MB each) and decoded before any binary or
   extension pre-filter runs.
5. **No exclusions.** `.gitignore` is not honoured (US-SEARCH-002) and there are no include/exclude
   patterns (PR-SEARCH-02). Only `.git` is skipped.
6. **Not streaming, not cancellable** (PR-SEARCH-04), and it runs while holding the gateway's single
   `mutationMutex`, so a slow search freezes every unrelated intent.
7. **Results are inert** — `ContentMatch` carries line and column, but nothing consumes them.

### 1.4 Editor find is quadratic and unbounded

`EditorFindScreen.findMatches` recomputes `lineStarts.count { it <= matcher.start() }` for every
match — O(matches × lines). The result body renders every line of the document in a non-lazy
`Column`. `Replace All` rewrites the entire document through one `UpdateDocumentText`. Match
navigation updates a counter but never scrolls to the match.

### 1.5 Hand-rolled where the architecture had already chosen an engine

| Capability | `architecture.md` §25 | What was built |
|---|---|---|
| Editor | replaceable native engine, Sora Editor named as candidate (§10.4) | `BasicTextField` holding the whole file as one `String` — no line numbers, no highlighting, no folding, no gutter |
| Content search | `ripgrep` process (§10.7) | a bespoke JVM directory walker |
| Git | Git CLI with porcelain/NUL output | JGit (defensible before the runtime exists — but undeclared as an interim deviation) |
| Persistence | Room + DataStore (§12) | `SharedPreferences` |

### 1.6 Lifecycle rules that contradict the user stories

The gateway blocks opening a second file (`"Save the open document before opening another file"`)
and blocks switching workspaces while dirty. US-EDIT-002 and US-WS-005 require **save / discard /
cancel choices**, not a refusal.

---

## 2. The rules this project now follows

1. **Demo → real → gaps → cleanup, per surface, in that order.** Inventory a frame's controls and
   navigation edges first; make them real; add what the frame implied but never wired; only then
   delete the demo path for that frame.
2. **Zero affordance loss.** A cycle may add controls and routes. It may never end with fewer.
   A capability that cannot be made real yet stays visible and honest ("not available until the
   runtime is installed") rather than being deleted.
3. **No `staticDemo` branches in screens.** Screens are single-path. Demo data lives in
   `DemoICodeGateway` / `DemoContent` and is injected through the same contract.
4. **Library first.** Check `architecture.md` §25, research the current docs, adopt the library
   behind the module contract. Record every interim deviation explicitly.
5. **Plan, then one small cycle at a time**, each compiling, tested and validated on its own.
6. **No restyling.** Visual changes are out of scope unless a design inconsistency is being corrected
   deliberately and noted.

These are saved as durable project rules.

---

## 3. Library decisions

| Capability | Decision | Why |
|---|---|---|
| **Editor engine** | **Sora Editor** (`io.github.rosemoe:editor:0.24.6`), hosted in Compose via `AndroidView` and a per-document `CodeEditorSession` adapter | Ships incremental syntax highlighting (TextMate), line numbers, undo/redo, search & replace, folding, bracket pairs and native pinch zoom. Covers the core frame 10/16 interactions that we would otherwise hand-build. Architecture already names it as the candidate. |
| **Git** | **The real `git` CLI through the runtime process contract.** JGit stays only until the runtime runs `git`, then it is deleted (user decision, 2026-09-22 — "no temporary solutions") | Architecture §25 already chose the Git CLI. Building remote sync on JGit would mean writing credential, SSH-agent and progress plumbing twice and throwing one away. |
| **Content search** | **`ripgrep` through the runtime process contract.** `WorkspaceIndexer` is deleted when it lands | Same reasoning: a hand-written scanner can never match ripgrep's ignore handling, speed or correctness. |
| **Terminal** | Termux `terminal-emulator` + `terminal-view` (confirm licence and API in the cycle) | Avoids writing a VT/ANSI emulator by hand. |
| **Runtime** | PRoot Ubuntu ARM64 per §14 — **moved to the front of the queue** | It is the dependency under Git, search, terminal and language services. Everything downstream is a stopgap until it exists. |
| **Language services** | generic LSP client (LSP4J vs. minimal JSON-RPC decided in its cycle) | §10.8 forbids per-language adapters. |
| **Persistence** | Room + DataStore per §12; `SharedPreferences` is interim | Migration planned in the persistence cycle. |

### 3.1 Why the runtime comes first

Git, search, the terminal and language services are all *the same dependency*: a process running
in a Linux userspace. Building each one twice — once on a JVM stand-in, once on the real runtime —
is the "dumping code" the project is trying to avoid. So the runtime moves ahead of every other
capability, and each feature is written once, against the process contract.

Until a feature's real engine exists, its frames stay reachable and state honestly what is missing.
They are never deleted and never faked.

**Constraint that shapes this cycle:** on modern Android the app may not execute binaries from its
data directory (W^X). Executables must ship inside the APK's native library directory — the reason
Termux packages every binary as a `lib*.so`. PRoot, its loader and the bootstrap tools have to be
packaged that way, and the Ubuntu rootfs unpacks into app-private storage as data only. This is
verified first in the cycle below, because it determines the whole packaging approach.

---

## 4. Execution cycles

Each cycle is independently compilable, testable and capture-clean. Validation for **every** cycle:

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
tools/check-ui-consistency.sh
tools/capture-figma-screens.sh     # all 40 routes
```
plus a physical-device pass on the frames the cycle touched.

### Cycle 0 — Restore what was removed, and guard it

**Goal:** every frame reachable and every demo control present again, before any new behavior.

- Restore `30 Commit & Push` Push/Fetch/Pull as real controls that navigate to `31` and report
  honestly that no remote is configured — instead of deleting them.
- Restore `12 Source Control` row routing: `M` → `13 Diff view`, `D` → `37 Delete confirmation`,
  conflicted → `24 Conflict resolution`; keep stage/unstage as an explicit trailing action.
- Restore `32 Git credentials` as the credentials frame; move Git identity to its own route or to
  Workspace settings where it belongs.
- `38 External file change` and `39 Document recovery` fire on their own in Cycle 5; until then the
  `orphan-route` guard records them as accepted debt rather than pretending they are reachable.
- Remove the six `staticDemo` forks by moving demo data into `DemoICodeGateway`, so every
  screen is single-path and reads the same snapshot in both modes.
- **New guard rules in `tools/check-ui-consistency.sh`:**
  `orphan-route` — every `Route` value has ≥ 1 navigation entry point;
  `demo-fork` — no `actions.staticDemo` inside `ui/screens/**`.

**Files:** `CommitPushScreen.kt`, `SourceControlScreen.kt`, `GitAuthScreen.kt`, `IcodeScaffold.kt`,
`ExternalChangeScreen.kt`, `DocumentRecoveryScreen.kt`, `tools/check-ui-consistency.sh`, allowlist.
**Result:** 40/40 reachable, zero demo forks, captures match the approved set.

### Cycle 1 — Linux runtime (the dependency everything else waits on)

**Goal:** a real Ubuntu ARM64 userspace under PRoot, owned by one runtime coordinator, exposing one
process/PTY contract. No feature code in this cycle.

The approach is validated by [`techjarves/Mobile-Harness`](https://github.com/techjarves/Mobile-Harness)
(MIT), which ships Ubuntu under PRoot on unrooted Android. Its packaging technique is documented in
`runtime/bootstrap/README.md` and adopted here; its agent-specific bridges are not (they are a
documented non-goal in `architecture.md` §4).

Steps, each verifiable on the device:

1. **Packaging spike — done.** Executing from `nativeLibraryDir` works; executing from the data
   directory is refused. Verified from the app's own process, not `run-as`.
2. **Native build.** `termux/proot` and `termux/libandroid-shmem` as submodules plus a vendored
   talloc, compiled by the NDK through CMake, delivered into the APK with the carrier pattern.
   Settle the GPL-2.0 distribution question here, before anything ships.
3. **Loader proof.** Confirm on device that PRoot honours `PROOT_LOADER` and never tries to extract
   its loader — the one thing the spike could not test without a real PRoot binary.
4. **Rootfs.** Versioned manifest (`sha256`, sizes, base image, ABI) under `runtime/bootstrap/`;
   download on first run, verify, extract. Frames `02` and `03` stop being static.
5. **Workspace bind.** Bind the user's *real* MANAGE_EXTERNAL_STORAGE path to `/workspace` and prove
   a write inside the guest lands in the original directory. This is where iCode diverges from
   Mobile-Harness, which binds app-internal storage, so it carries the most risk.
6. **Process + PTY contract.** One `ProcessService` (`architecture.md` §10.5) over a JNI bridge using
   `posix_openpt`/`grantpt`/`unlockpt` with `TIOCSWINSZ` resize — a real PTY, not pipes.
7. **Foreground service and coordinator state** — installing / ready / failed / repairing, which
   features await instead of bootstrapping independently. Frame `23` shows real facts.

**Validation:** `uname -a`, `git --version`, `rg --version` inside the runtime; a file written in the
guest appears in the user's real directory; process state survives an app restart truthfully.

### Cycle 2 — Editor engine and file-level Find/Replace (Sora Editor)

First inventory frame `16` against the demo and US-EDIT-006, then replace its detached preview
with operations against the same open document/editor instance as frame `10`. Put Sora Editor in an
`AndroidView` behind an `EditorEngine` contract rather than adding more Compose-side matching.
Start with exact match highlight/reveal, current/total, previous/next wrap, Replace/Replace All,
invalid-regex feedback, dirty/read-only behavior, undo and save. Preserve the frame 16 controls,
navigation and capture while the engine is introduced. Then migrate the editor body in frame `10`
and wire frame `20` settings to the engine: line numbers, highlighting, folding and diagnostic
markers. Verify the artifact/API and LGPL distribution obligations before adding the dependency.

### Cycle 3 — Search on ripgrep

First deliver frame `11` through a `SearchService` backed by a streaming, cancellable `rg --json`
process: ignore rules, explicit include/exclude patterns, case/word/regex, bounded grouped results,
and selection that opens the exact range. Keep search off the gateway mutation mutex and ensure a
new query cancels the old process without stale results. Then deliver frame `14` Quick Open using
`rg --files` from the same runtime, with name-before-path ranking and honest truncation. Verify
`rg` availability in the installed runtime image and provide truthful provisioning/error states.
**Delete `WorkspaceIndexer` only after both pages and their demo captures pass.**

### Cycle 4 — Git on the real client

**Blocking unknown, settle before anything else in this cycle.** The installed Ubuntu 24.04 base
image ships `apt` and `dpkg` but **no `git`** — and no `ripgrep`, `curl` or `wget` either, which
also gates Cycle 3. Verified on device: `usr/bin` holds 205 entries including the apt family, and
`usr/bin/git` does not exist. So "call the real git CLI" is not yet possible.

**Resolved on device.** apt runs and reaches the network from inside the guest:

```
$ apt-get update
Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease
Fetched 36.5 MB in 1min 1s (599 kB/s)
Reading package lists... Done
```

so `apt-get install -y git` is a viable provisioning path and **no pre-baked rootfs bundle is
required yet**. Note this also settles the same question for Cycle 3's `ripgrep`.

Why executing an apt-installed binary is allowed at all: the guest binary lives in app-private
storage, which Android forbids executing. It works because PRoot never asks the kernel to exec it —
`PROOT_LOADER` points at the packaged loader, which maps and enters the target ELF itself. The
already-working `/bin/bash` session is the proof.

Provisioning is therefore a real, visible step: on first Git use, install git through the existing
preparation surface (frames `02`/`03`) with truthful progress, and report failure honestly rather
than silently degrading. Budget roughly a minute on a good connection — git pulls perl.

A pre-baked rootfs bundle with git and ripgrep already installed remains the better long-term
startup experience, and is the approach `techjarves/Mobile-Harness` takes; it needs build and
hosting infrastructure that does not exist yet, so it is a later optimisation, not a blocker.

Until git actually runs, **JGit stays**. Deleting a working implementation before its replacement
exists is exactly what the demo-to-real rule forbids.

Then deliver one reachable interaction at a time:

| Step | Frame | Today | Target |
|---|---|---|---|
| 2 | — | JGit | `GitService` over the process contract, `git status --porcelain=v2 -z` |
| 3 | `13` Diff view | static | real staged/unstaged diffs, hunks, previous/next navigation |
| 4 | `12` Source control | status, stage/unstage, commit | discard/restore with confirmation, refresh, in-progress merge/rebase state |
| 5 | `15` Branch selector | list/create/switch | delete, remote branches, blocked-switch reporting |
| 6 | `24` Conflict resolution | static | real conflicts, accept current/incoming/both, explicit mark-resolved |
| 7 | `07`/`08` Clone | static | real clone with progress and cancellation |
| 8 | `30`/`31` Sync | honest "no remote" | fetch/pull/push with the user's credentials |
| 9 | `32` Git credentials | static | real credential and SSH configuration |
| 10 | — | JGit present | **delete JGit and its dependency** |

**Validation:** every operation's result matches the same `git` command run against the same
repository from a shell.

### Cycle 4a — Frames that are states, not pages

Several design frames are not separate destinations; they are states of a surface that already
exists. Keeping them as routes duplicates layout, and some can only be reached through a navigation
edge invented for the demo. Each one below should collapse into its parent's state while its route
stays registered, so static capture still produces all 40 frames.

| Frame | Really a | Consequence today |
|---|---|---|
| `03` Runtime error | failure state of `02` Runtime installing | two screens rendering the same progress card |
| `08` Clone progress | in-progress state of `07` Clone repository | progress cannot return to its own form on failure |
| `31` Pull progress | in-progress state of `30` Commit and push | reachable only through an invented edge |
| `06` Launcher empty | empty state of `05` Launcher home | already a parameter; route kept only for capture |
| `25` Editor empty | empty state of `10` Workspace editor | already resolved in the host by document count |
| `17`/`18`/`29` Terminal, Problems, Output | three tabs of one bottom panel | switching tabs navigates instead of switching |
| `37` Delete confirmation | a confirmation dialog | a destructive prompt should not be a back-stack entry |
| `38` External file change | a dialog over the editor | same |

Genuine list-to-detail pairs stay as routes: `12` → `13` Diff, `21` → `22` Language detail, and the
Settings hierarchy.

**Rule going forward:** a frame earns a route only when it has its own back-stack identity. A state,
a progress phase, a tab, or a confirmation belongs to its parent surface.

### Cycle 5 — Documents, tabs, recovery, external change

Multiple open documents with a real tab strip and close-with-save/discard/cancel. Remove the "save
before opening another file / switching workspace" blocks in favour of the choices the user stories
require. Recovery buffers, disk-fingerprint watching, and `38` / `39` firing on their own — which
clears their allowlist entries.

### Cycle 6 — Terminal and processes

Termux terminal view over the PTY contract: multiple sessions, scrollback, extra keys, URL
detection into the external browser, truthful stopped-process reporting. Frames `17`, `29`, `34`.

### Cycle 7 — Explorer file operations

Create, rename, move, copy, delete with concrete-path confirmation; `37 Delete confirmation` fires
from the Explorer; binary and large-file safe states.

### Cycle 8 — Language services

Generic LSP client over the process contract, capability negotiation, diagnostics into editor,
Explorer and `18 Problems`. Frames `21`, `22`.

### Cycle 9 — Settings, persistence, sessions

Hierarchical settings registry, app/workspace scope, Room + DataStore migration off
`SharedPreferences`, session restore. Use the prospective [backend splitting rules](./backend-split-rules.md);
Appearance and Editor Settings have a scoped [implementation plan](./settings-backend-split-plan.md).

### Cycle 10 — Per-frame demo cleanup

Only now, and only frame by frame as each passes its checks: delete the demo gateway path,
`DemoContent` entries and demo copy, then re-run every validation gate.

---

## 5. Definition of done, per cycle

- [ ] No route lost an entry point; `orphan-route` guard passes.
- [ ] No `staticDemo` branch added to a screen; `demo-fork` guard passes.
- [ ] Every control the demo frame had still exists and does something truthful.
- [ ] Loading / empty / error / cancelled / read-only states are real, not inferred from a click.
- [ ] Unit tests cover the parsing and state transitions the cycle introduced.
- [ ] All five gradle/tooling gates pass; 40/40 captures reviewed.
- [ ] Physical-device pass on the touched frames.

## 6. Cycle record — Git end to end (complete)

Every Git surface now reads real repository state and every Git action runs the
real `git` binary. Verified on device against the private repository
`git@github.com:itzmejanak/icodeTest.git`.

### What became real

| Frame | Was | Is |
| --- | --- | --- |
| 24 Conflict resolution | static `auth.ts` markers | the conflicted file, its own marker blocks and branch labels; Current/Incoming/Both rewrite the file; Mark Resolved runs `git add` |
| 15 Branch selector | `DemoContent` import | local branches, remote-tracking branches, switch, create, delete with confirmation |
| 31 Pull progress | scripted output, fixed 52% bar | live fetch/pull/push: Git's own percentage and stderr transcript |
| 30 Commit & push | buttons that only navigated | Fetch/Pull/Push dispatch the real operations |
| 37 Delete confirmation | hard-coded folder | confirms discarding one real change, with the consequence stated per status |

### Bugs found and fixed while verifying

1. **Explorer Git decorations never worked.** `git rev-parse --show-toplevel`
   runs inside the guest and returned `/workspace`, which no Android path ever
   matched, so the status map was always empty. Translated back to the host path.
2. **`git` could not write in the user's own terminal.** The terminal bound the
   workspace with `--link2symlink` still on, so `git commit` failed to create
   `.git/objects/xx/`. The same rule the Git client already had now applies to
   terminal sessions.
3. **Source Control was stale after an editor save.** Saving now schedules a
   coalesced refresh, so changes appear without a manual reload.
4. **`git branch -a --format='%(refname:short)'` listed `origin` and
   `origin/main` as local branches.** Partitioned on the full refname instead.
5. **Discard did nothing useful for a staged file.** `checkout --` restores only
   the worktree; it is now `restore --source=HEAD --staged --worktree`, with
   `clean` for untracked and `rm` for newly added files.
6. **A half-finished merge was invisible.** `GitInProgressDetector` existed but
   nothing called it; Source Control now shows the state and offers Abort.
7. **The commit message survived its own commit** and would be reused by accident.
8. **A bottom sheet taller than the screen lost its first rows.** It scrolls now.

### Interaction decisions

- Destructive actions (discard a change, delete a branch) get a **visible
  control and a confirmation dialog**. A long press warns nobody and cannot be
  undone, so it is not used for them.
- Editor tabs keep a long press for their action menu, replacing the separate
  three-dots button, because that menu is not destructive.
- The activity rail carries a VS Code style count of changed files.
- The guest mounts a workspace at `/workspace/<project>` and the shell prompt is
  the project name and subpath, not `root@localhost:/workspace`.

## 7. What remains

Git is done. The demo fixtures still standing are not Git, and each needs a
subsystem that does not exist yet rather than wiring:

| Surface | Needs |
| --- | --- |
| Document recovery (frame 33) | an autosave/draft store written as the user types, so there is something to recover |
| External change (frame 34) | a file watcher on open documents |
| Output panel, Command palette | a command registry and a build/task runner |
| Settings screens | a persisted preferences store, then applying each setting |

## 8. Cycle 11 — autosave and recovery

### Affordances that must survive (nothing in these two frames is dropped)

Frame 33, Recover Unsaved Work:
- "Unsaved changes detected" notice explaining an interrupted shutdown
- one row per recoverable document: state dot, name, size, path, "Auto-saved: <time>"
- per row: **Restore Cache**, **Discard**
- footer: **Restore All Workspace Docs**, **Discard All Recovery Files**

Frame 34, File Changed Externally:
- the file's name and "Modified state"
- the explanation that the file changed on disk while edits are unsaved
- **Disk Version** and **IDE Cache** timestamps, disk marked newer
- **Reload From Disk**, **Compare Versions Side-by-Side**,
  **Overwrite Disk with My Changes**, **Ignore & Keep Editing**

### Gaps these frames imply but no code provides

1. Nothing ever writes a draft, so there is never anything to recover.
2. Nothing records that a shutdown was unexpected.
3. Nothing reads a file's modification time, so "changed externally" cannot be
   detected and the two timestamps cannot exist.
4. There is no diff between an in-memory draft and the file on disk.

### Steps

| Step | Deliverable | Verified by |
| --- | --- | --- |
| R1 | `DocumentDraftStore`: drafts in app-private storage, each with its text, when it was saved, and the size and mtime of the file it came from | unit tests over a temp directory |
| R2 | The gateway writes a draft as the user types (coalesced) and deletes it once the document is saved or closed clean; a marker records clean shutdown | on-device: kill the app mid-edit, draft survives |
| R3 | Frame 33 on real drafts, with Restore/Discard per row and for all | on-device |
| R4 | Disk mtime and size recorded at open and save; a mismatch with unsaved edits raises frame 34 with both real timestamps | on-device: edit in the app, change the file from the terminal |
| R5 | Compare runs `diff -u` between draft and disk in the runtime and renders through the existing `GitDiffParser` | on-device |
| R6 | `DemoContent.recoveryDocuments` deleted; guard rules extended | `tools/check-ui-consistency.sh` |

**Decision — how the comparison is produced.** The runtime already ships
`diff`, and `GitDiffParser` already turns unified diff text into the model the
diff surface renders. Compare therefore runs `diff -u` in the guest rather than
adding a diff library or hand-writing an LCS: one engine, one parser, one
rendering path, consistent with section 3.

## 9. Workspace Settings — real scope and effects (in progress)

This cycle owns frame `40` and the application values it inherits. It must not
turn the capture frame into a second, independent settings store. The route is
reachable from Explorer and from Settings Root; when no workspace is open, the
root link must offer Open Workspace instead of presenting editable overrides
for the placeholder `No workspace`.

### Verified starting state and ownership

| Visible control | Current behavior | Real owner and required effect |
|---|---|---|
| Indent Size / inherited value / Reset to Inherited | hard-coded `2` and inherited `4`; Reset is text only | one application editor setting plus an optional workspace override; effective value configures Sora for all open editor sessions; reset removes only the workspace key |
| Tab Type — Spaces/Tabs | saveable Compose state, lost with the page/process | optional workspace override, resolved against the application editor setting; changes future indentation, not existing document bytes |
| Git Auto-Fetch | saveable toggle only; no fetch is scheduled | workspace-scoped opt-in on workspace open or app resume, only for an available repository/remote/runtime; Git reports network/auth failures and never pulls |
| File Watcher Excludes / Add Exclude Pattern | fixed sample text and inert action | keep the visual section, but editing stays unavailable until the external-change watcher consumes validated patterns |

The user selected `4` as the real application indent/tab-width default. The
application Editor Settings page and workspace override now resolve through
one app-private DataStore; the former page-local Tab Size state was removed.
The watcher is part of the separate recovery/external-change cycle, so its
exclusion editor remains visibly unavailable until Claude's watcher consumes
it. The three sample patterns remain a deterministic demo fixture only.

The Workspace Settings entry belongs at the bottom of the Explorer activity
rail, as a settings-gear icon, not the header's three-dot navigation control.
This navigation change is implemented; the Explorer file list is now a bounded
lazy scroller while the header and activity rail stay fixed.

Git commit identity belongs to Git's guest-wide global config as the default,
with an explicit repository-local override. The prior flow wrote only local
config and forced re-entry on each new repo. The credentials flow now offers
both scopes and reset-to-default, and neither production nor demo initializes
an author identity for the user. Source Control and Workspace Settings link to
the same configuration flow. Git's effective identity remains the commit gate.

### User lifecycle and boundaries

1. Open Workspace Settings from the active Explorer or switch from Settings
   Root. Show the active workspace and which values are inherited versus
   overridden. No active workspace means no editable workspace state.
2. Read application settings, then that workspace's overrides, through one
   typed settings registry. Resolve each effective value as override-or-
   application value. Only settings declared workspace-scopeable may be saved
   under a workspace. Persist preferences in app-private DataStore, not in the
   user's project or a screen's `rememberSaveable` state.
3. On a valid edit, update the repository transactionally and reflect the
   resulting value on both Settings pages. An invalid value does not persist;
   a write failure stays visible. Reset to Inherited deletes the override,
   immediately reveals the current application value, and never resets other
   workspaces.
4. Apply editor settings to the *existing* per-document Sora sessions without
   replacing their text, undo stack, selection, scroll or dirty flag. On
   workspace switch, resolve settings for the new root; on return or process
   restart, reload them from storage. Do not confuse Tab Type (insert tabs vs
   spaces) with Tab/Indent Size (display/insertion width).
5. Auto-Fetch is off by default, triggered on workspace open and app resume
   only when the active workspace has a repository, remote and ready runtime.
   No background polling or pull is implied. Git itself reports offline or
   authentication failure; no connectivity guess claims success. Manual Fetch
   remains available regardless of this preference.
6. Store watcher exclusions only for the external-change watcher; they do not
   silently become search or Git ignore patterns. Reuse the same validated
   matcher when the watcher is connected. Preserve the frame's three example
   patterns in deterministic demo capture, not as hard-coded production data.

### Delivery gates

- [x] Settle the product choices: Auto-Fetch is opt-in on open/resume; exclusion
  editing waits for watcher integration.
- [x] Implement the typed effective-value resolver and DataStore persistence
  with unit tests for inherit, override, reset, invalid values, two workspaces
  and a new DataStore instance reading saved state. No active workspace is
  guarded. The affected global editor value is no longer page-local.
- The installed Sora `0.24.6` API has been checked: `CodeEditor.setTabWidth(int)`
  controls width, while `TextMateLanguage.useTab(boolean)` and
  `TextMateLanguage.setTabSize(int)` control indentation for supported
  grammars. Plain-text/unknown files use `EmptyLanguage` and need the same
  behavior verified rather than a grammar-only implementation. Test live
  apply with two dirty tabs and a workspace switch; settings must not edit
  document contents or erase undo state.
- [x] Apply effective tab width/type to existing and new Sora sessions and
  opt-in fetch through the existing Git command. Keep watcher exclusion editing
  unavailable until the real watcher is connected.
- [x] Install the updated APK at the user's request. Device smoke confirmed
  Explorer scrolling, fixed header/rail, gear navigation and empty default
  author-identity fields.
- [ ] The user will accept dirty tabs, reopen/restart, switching roots, fetch
  offline/auth failure and global/local Git identity in the installed app.
- Pass unit tests, compilation, lint, UI consistency, 40-frame capture and a
  device lifecycle test (change, leave/reopen, restart, switch workspace,
  reset, and error cases) before removing the frame's demo fixture.

## 9. Cycle 12 — runtime lifecycle (P1–P4 complete)

Follows [backend-split-rules.md](./backend-split-rules.md). Verified by fresh
uninstall/install on device, not by compilation.

### What was actually wrong

Found by installing from clean and following the user's own path, with file and
line evidence rather than inspection of the design:

1. **Onboarding never provisioned the runtime.** `InstallRuntime` is dispatched
   in exactly one place (`RuntimeInstallingScreen`), and the only routes into
   that screen were the Terminal and a Retry button. `SplashScreen` went
   straight to the permission step or the launcher, so a new user reached a
   launcher reading `RUNTIME UNAVAILABLE` with no way to act on it. Dead code in
   `RuntimeInstallingScreen` — a branch sending the user to the permission step
   "during onboarding" — proved the step had existed and been lost.
2. **Retry could never work.** `RuntimeError` → `RuntimeInstalling`, which saw
   `RuntimeState.Failed` and navigated straight back to `RuntimeError`. A loop.
3. **`RuntimeErrorScreen` was entirely fabricated** — a hard-coded
   `ERR_HASH_MISMATCH`, invented expected/received digests, and a literal
   "Storage stable: 45.2 GB free space." It also had a demo hook: the install
   log card jumped to that fake failure from a healthy install.
4. **Declining the permission was a one-way door.** `Not Now` led to a launcher
   identical to the granted one, and no screen anywhere navigated back to the
   permission step.
5. **The download reported nothing for its first megabyte.** `onProgress` fired
   only per `PROGRESS_STEP_BYTES`; on a slow link the screen said "The Linux
   runtime is not installed yet" for minutes while the download was running.
   Observed at 2.9 KB/s on device.
6. **Extraction was judged by tar's exit code.** Android's tar reports a
   non-zero exit for conditions it recovers from, so a complete root filesystem
   could still be reported as "Extraction failed (exit N)" — which is the toast
   the user saw, with a fully extracted tree on disk.
7. **Extraction looked hung.** The bar parked at a fixed 0.9 for the minutes
   extraction takes, with one unchanging label.

### Decision: frame 03 is a state, not a page

`RuntimeState` is already one machine — `Unknown / NotInstalled / Downloading /
Installing / Ready / Failed` — and the install screen already switched on all
six. A separate route for `Failed` is what created the retry loop. By the rule
in §4a, a frame earns a route only when it has its own back-stack identity;
`Failed` has none. Frame 3:55 is now the failed state of frame 3:27, recorded in
`RouteTest` so the catalog still accounts for all 40 frames and dropping
one silently still fails.

### Changes

| Area | Change |
| --- | --- |
| `navigation/OnboardingRoute.kt` (new) | The single resolver for what a launch leads to, pure and unit-tested. Both the splash and the installer ask it, so they cannot disagree about the order |
| `navigation/NavigationActions.kt` | Adds `canGoBack` — the only thing that distinguishes "opened from the Terminal" (return to it) from "first step of onboarding" (route onward) |
| `SplashScreen` / `PermissionScreen` / grant effect | Onboarding steps `replace` instead of `navigate`, so Back from the launcher leaves the app rather than walking back through onboarding |
| `RuntimeInstallingScreen` | Absorbs the failed state: real reason, real free space, a Retry that re-dispatches `InstallRuntime`, and "Continue without the runtime" because browsing and search never needed it. Demo tap-hook removed |
| `LauncherScreen` | `RUNTIME UNAVAILABLE` is now an action; a missing-storage-access notice offers the way back that did not exist |
| `RuntimeInstaller` | Reports the download before the first byte and on a timer; judges extraction by the tree it produced, keeping tar's exit code and output as evidence; streams tar's own entry count so extraction shows real movement |
| Deleted | `RuntimeErrorScreen.kt`, `Route.RuntimeError` |

### Verified on device

Fresh uninstall and install: **Splash → Installing Runtime → All Files Access →
Launcher**, with a real percentage, real byte counts and real free space during
the download.

### Not yet done

P5 (declare and provision developer tools such as `git` from onboarding) and the
`apt update` / `apt upgrade` / `git install` terminal check are outstanding.
