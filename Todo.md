# iCode — Android Backend and Integration Todo

Scope: Android only. Website/MongoDB are deferred and not tracked here.
Phase 1 UI is complete; this file tracks only backend and integration work.

Authoritative UI/design references:

- Figma: iCode — Full User Flow
- [docs/prd.md](./docs/prd.md)
- [docs/architecture.md](./docs/architecture.md)
- [docs/layout.md](./docs/layout.md)
- [docs/user-stories.md](./docs/user-stories.md)
- [docs/ui-split-plan.md](./docs/ui-split-plan.md)
- [docs/backend-split-rules.md](./docs/backend-split-rules.md) — prospective repo-wide backend ownership/splitting rules
- `app/src/main/java/dev/icode/mobile/domain/` — `ICodeGateway`, `ICodeSnapshot`, `ICodeIntent`, `ICodeEffect`, and one models file per scope
- `app/src/main/java/dev/icode/mobile/navigation/Route.kt`

Completed baseline:

- All 40 frames are implemented in 39 screen files plus five hosts.
- Shared kit, tokens, and navigation are complete.
- The in-app screen catalog was removed after verification; titles and headings are static.
- Screens use `ICodeGateway` and never access files, Git, processes, or storage directly.
- Screenshots are drawn from debug-only capture fixtures (`app/src/debug/.../capture`); a release
  build contains none.
- Existing unit, lint, build, device, and 39-route capture checks are established.

Names in dated records below are as they were then: the demo layer (`DemoICodeGateway`,
`DemoContent`, `staticDemo`, `demo-fork`) was replaced on 2026-09-26 by the capture fixtures
(`CaptureGateway`, `CaptureFixtures`, `captureMode`, `capture-fork`) —
[code-separation plan](./docs/code-separation-plan.md) K1.

## Remaining work by priority (reviewed 2026-09-24)

Every open item in this file was checked against the code and the plan records on 2026-09-24, not
only against its checkbox. Each item gets its own plan ([backend-split-rules](./docs/backend-split-rules.md) §8)
before code, then one verifiable step at a time.

| # | Work | Why it matters | Where it stands (evidence) | First step |
|---|---|---|---|---|
| 0 | **Close the terminal/background cycle (S6) — done (2026-09-29)** | Finished work shipped | Moves, fixes and device checks recorded in [terminal-separation-plan](./docs/terminal-separation-plan.md) §7; the three older terminal plans are closed | Owner decisions carried: `PROOT_L2S_DIR` as its own step; a side-by-side build for a fresh-install matrix |
| 1 | **Outside changes reach the UI — done (S3)** | Agents and commands in the terminal edit the workspace | Resume/terminal completion plus live `FileObserver` refresh; clean tabs reload, dirty tabs ask Reload/Compare/Overwrite, tree/Git/index refresh; device matrix passed | Process-death/permission-loss acceptance remains with session restore |
| 2 | **Explorer rename / move / copy / delete — done (S3)** | Basic file management was missing | Real long-press Rename/Cut/Copy/Paste/Delete with path guards, confirmation, tab/tree/recent migration and 8 filesystem tests; device matrix passed | No remaining code step |
| 3 | **Workspace search and Quick Open on ripgrep (frames 11/14) — done (S4)** | Search had silently missed files | Real first-use `ripgrep`; streaming, bounded, cancellable, ignore-aware; `WorkspaceIndexer` deleted; device matrix passed ([code-separation plan](./docs/code-separation-plan.md) S4) | Done; S5 Git done too ([record](./docs/git-separation-plan.md)) |
| 4 | **Honest About screen and licences** | Release blocker; licences unshown | About shows real build and runtime facts (2026-09-25); no licence notices for PRoot (GPL-2.0), talloc (LGPL-3.0), Sora (LGPL) or the Termux terminal libraries (Apache-2.0 inside a GPLv3 repository, [terminal-maturity-plan](./docs/terminal-maturity-plan.md) §3); iCode itself has no LICENSE | **Owner:** distribution and licence decision; then real build facts and a licences page |
| 5 | **Developer tools at onboarding** | A new user reaches Source Control without `git`; search will need `rg` | Git: installed where it is first needed — Clone installs it first (S1, 2026-09-26); Source Control and credentials offer it. `rg` comes with S4 | Owner may still want an onboarding step; not added |
| 6 | **Session restore device acceptance** | A killed process must reopen safe workspace/tab state without inventing terminals | Code now persists workspace, tab order and selected tab; missing files are skipped and drafts still ask (S3, 2026-09-28) | Kill the process on device, then verify permission-loss/missing-file fallbacks |
| 7 | **Runtime fidelity — owner decisions** | `rm -rf` of a runtime tree holding emulated hard links fails ("Directory not empty"); `htop` cannot start | [terminal-maturity-plan](./docs/terminal-maturity-plan.md) §6 Open 2–3: `PROOT_L2S_DIR` proposed; `/proc` stand-ins versus the honest error | **Owner** decides; then patch and device A/B |
| 8 | **LSP protocol, then diagnostics** | Completion, diagnostics, Problems navigation | Server configuration and process status exist; no JSON-RPC code | Plan per B-05/B-06 items 7 and 4 |
| 9 | **Output channels; Command Palette acceptance** | Completes the tools panel | Output carries the Git transcript only; palette code slice done | Per B-05/B-06 items 5 and 2 |
| 10 | **Device acceptance backlog** | Much finished code awaits a device pass | Consolidated below; the demo cleanup is done (K1) | One pass, then the 39-route capture |
| 11 | **Code separation S7–S8, X, G and editor colours — done (2026-09-29)** | The owner's "complete the whole remaining, no guess, no duplication, no inconsistency" | Records: [code-separation plan](./docs/code-separation-plan.md) §8 (S7, S8, X3–X5, G1–G2) and §9 (persistence and lock maps); [editor-colors plan](./docs/editor-colors-plan.md) E1–E6 | Owner decisions open: File Watcher exclusions as a feature; the Sora bug report upstream |
| 12 | **One loading behaviour for the whole app (owner, 2026-09-29)** | "Open workspace and other actions take time before the screen responds" | Measured so far: taps in the first seconds after launch are lost; the first file's freeze is gone (E6) — [code-separation plan](./docs/code-separation-plan.md) §8 item L | Measure each action (tap → first response → done), then its own plan |
| 13 | **Workspaces in iCode's storage (owner, 2026-09-30)** | Shared storage cannot hold what toolchains need (links, `+x`, case, speed: npm fails there); the split design brought complexity to every consumer | [workspace-storage-plan](./docs/workspace-storage-plan.md): W1–W7 written — one path model and one bind; the list is the workspaces folder; New Folder, Clone, Import (with the Git repair, checked against real Git), Rename, Delete; the Files provider; Manage space; the migration. Backend type-checked and its tests pass here; screens written against the kit, not yet compiled | The owner's Gradle build, the capture and D1–D12 (W8) |
| 14 | **Background work, as the reference carries it (owner, 2026-09-30)** | A hidden dialog lived only as a row inside iCode; the backend died with its screen, so Back or a swipe from Recents cut a delete, an import, a clone or the runtime install off half-way | [long-operations-plan](./docs/long-operations-plan.md) §6: one backend per process; one derived list of background work; the foreground service keeps it alive with a summary, a silent progress notification per item (Cancel where allowed) and failure pop-ups; a tap opens iCode on the work; in-app rows gone; the dialog shows counts. Backend type-checked and tested here | The owner's Gradle build, then B1–B6 |
| 15 | **iCode's own assistant, on Koog (owner, 2026-10-01)** | "Make plan for Koog integration … and implement it in clean and best way": an agent that reads, changes and runs in the workspace with a provider the user chooses | [assistant-koog-plan](./docs/assistant-koog-plan.md): Koog 1.3.0's Android modules checked here (API read from its sources; Android 11–14 audited with Animal Sniffer — the DeepSeek client excluded); the engine boundary, iCode's tools over its owners, approvals, chats per workspace, Keystore keys, background turns with question pop-ups; screens, routes, R8 and network config. Backend type-checked against the real Koog jars; tests (the real Koog loop with a scripted provider) pass here | The owner's Gradle build, then the bake-off D1–D10 (plan §9) and the open decisions (§10) |

### Device acceptance backlog (code complete, not yet accepted on device)

- [ ] Explorer New File / New Folder at root and nested, duplicate-name feedback, selection surface.
- [ ] Settings persistence and a language service saved while another runs — done on device
      (S8.5, 2026-09-29). Still open: two dirty tabs, Git identity, an interrupted repair, runtime
      Repair and Reset run to completion ([next-cycle-plan](./docs/next-cycle-plan.md) "Not yet verified").
- [ ] Editor frames 10/16: Find/Replace, Markdown source/preview, tab select/drag/close, pinch
      zoom, dirty-workspace switch. (Syntax colouring on every file type and theme was accepted on
      the device, [editor-colors-plan](./docs/editor-colors-plan.md) §6.)
- [ ] Git: frame 24 conflict resolution, frame 15 branch delete and remote branches, frames 30/31
      push/pull on a real remote. The code is on the Git CLI and JGit is gone from build and code.
- [ ] Command Palette (B-05/B-06 item 2); Problems/Output panel (item 4a).
- [ ] 100–150 % UI scale on every screen (Runtime settings checked at 150 %, S8.5). The Light
      and AMOLED palettes were accepted by the owner on 2026-09-29.
- [x] Terminal: `vim`, `nano`, a `git log` pager, rotation, cursor styles and the Light palette checked
      on 2026-09-29 (S6 record); the owner had verified the terminal in daily use, including pinch zoom
      and paste.
- [x] "Open Terminal" once produced two sessions: the panel's automatic open used the "+" request;
      fixed in S6.3.
- [ ] Re-run the 40-frame capture before any fixture cleanup.

Current production integration:

Workspace closeout (2026-09-23; ordered audit in [`docs/integration-plan.md`](./docs/integration-plan.md)):

- [x] Classify Explorer/Editor/Search/Quick Open as routes, and Editor Empty/Find/Problems/Output/
      Terminal/External Change/Recovery as states or conditional flows of their parent surfaces.
- [x] Replace Editor Empty's hard-coded recent rows and inert New File tile with real file creation,
      workspace-scoped persisted recent documents, missing-file reporting, and the same New File
      dialog in Explorer and the editor tab menu. Demo fixtures remain in the demo gateway.
- [x] Compile and targeted unit tests for file-name validation, bounded recent ordering, and the
      demo create → tab → close → reopen path.
- [x] Explorer `+` now offers New File and New Folder through one compact dialog. Both target the
      selected directory (or selected file's parent), validate against the active workspace, and
      retain the typed name on creation failure. Created subfolders refresh the tree without
      changing workspaces; Home's separate new-workspace flow is unchanged.
- [x] Explorer selection uses the activity-rail surface color with square corners.
- [ ] Device acceptance for this slice: verify New File/New Folder at root and nested folders,
      duplicate-name feedback, and the Explorer selection surface on the installed app.
- [ ] Next: Explorer rename/move/copy/delete with safe selection and confirmation;
      recovery/external changes; runtime-backed `rg` search; diagnostics/output/commands/settings.
      Do not remove a route until its replacement passes its real lifecycle.

Workspace settings and Explorer follow-up (2026-09-23):

- [x] Explorer list uses a bounded lazy scroller; header and activity rail stay fixed. The
      Workspace Settings entry is a gear at the bottom of the rail, not a header overflow action.
- [x] Tab/indent default `4` and workspace indent-size/tab-type overrides use one app-private
      DataStore. Reset removes only that workspace override; existing and future Sora sessions
      apply the effective value without replacing their document text or undo state.
- [x] Git Auto-Fetch is off by default, opt-in on open/resume, gated by runtime/repository/remote,
      and reuses real Fetch. File Watcher Excludes stay visibly unavailable: the watcher follows only
      the folders open in the Explorer and those of open files (S8.4b removed the unused field and
      the capture's sample patterns); making exclusions a feature is the owner's decision.
- [x] Git author name/email start unset. The credentials flow now saves a guest-wide default
      or an explicit per-repository override, with reset to the inherited default; both settings
      and Source Control can reach it. A new repo inherits the global identity through Git.
- [x] Compilation, unit tests, lint and UI consistency guard pass for this slice.
- [x] Updated debug APK installed on the connected phone without clearing app data. On-device
      smoke check confirmed the Explorer list scrolls while its header/rail stay fixed, the gear
      opens Workspace Settings, and Git identity starts empty rather than prefilled.
- [ ] User acceptance remains for settings persistence, two dirty tabs and Git identity; rerun
      40-frame capture before fixture cleanup.

- [x] Normal launches use `AndroidICodeGateway`; static capture launches retain `DemoICodeGateway` only for deterministic visual verification.
- [x] All-files access opens the real per-app Android settings surface and verifies the result on return. *(Retired by [workspace-storage-plan](./docs/workspace-storage-plan.md), W3–W4: workspaces live in iCode's storage.)*
- [x] The in-app folder browser lists and navigates real phone-storage directories without redirecting to Android's Files app or copying source files. *(Retired by [workspace-storage-plan](./docs/workspace-storage-plan.md), W3–W4: workspaces live in iCode's storage.)*
- [x] Recent workspaces persist across launches, deduplicate by location, and report revoked or missing locations as unavailable. *(Retired by [workspace-storage-plan](./docs/workspace-storage-plan.md), W3–W4: workspaces live in iCode's storage.)*
- [x] Recent-workspace menus now open the project, open a path-bound terminal, locate its folder,
      copy its path, or remove only its history entry after confirmation. Launcher cards match the
      74 dp home action-card height; the narrow menu and compact dialog are shared UI-kit components. *(Retired by [workspace-storage-plan](./docs/workspace-storage-plan.md), W3–W4: workspaces live in iCode's storage.)*
- [x] Explorer rows come from the selected provider, sort folders first, expand directories lazily, and guard cyclic provider results.
- [x] Create Folder uses the same in-app browser for its destination, validates UTF-8 name limits and traversal/separator cases, creates at the selected path, then opens the new workspace. *(Retired by [workspace-storage-plan](./docs/workspace-storage-plan.md), W3–W4: workspaces live in iCode's storage.)*
- [x] Explorer opens real text documents; the editor tracks dirty/read-only state and saves to the original document.
- [x] UTF-8/UTF-16, BOM, and existing line endings are preserved; malformed, binary, and oversized documents fail explicitly.
- [x] Workspace/tree/name/codec behavior has unit coverage, and open/read/edit/save plus folder creation passed on the attached physical device.

Workspace-logic round (Git gate and search) — partially reverted:

- [x] Real Git repositories drive the workspace: discovery, JGit-backed init, stage/unstage, author
      identity, first-commit validation, branch create/switch, unborn-repository state.
- [x] Quick Open searches the real workspace file index and opens actual files.
- [x] Editor find/replace operates on the real active document.
- [x] **Regressed, then corrected in Cycle 0:** that round deleted Push/Fetch/Pull, the Source Control
      row routing to diff/delete/conflict, and the whole Git-credentials frame, and forked demo
      bodies into six product screens. See [`docs/integration-plan.md`](./docs/integration-plan.md) §1.
- [x] **Replaced in S4 (2026-09-28):** `WorkspaceIndexer` is deleted. Quick Open uses
      NUL-delimited `rg --files`; content search uses ripgrep JSON, with ignore/hidden/`.git` policy,
      cancellation, stale-result suppression and visible caps. Device fixture passed.

Cycle 0 — restore and guard (complete):

- [x] Push/Fetch/Pull restored on the commit summary; frame 31 reachable again.
- [x] Source Control rows open the diff, the delete confirmation or the conflict workflow by status;
      staging moved to an explicit trailing action so a review tap cannot stage by accident.
- [x] Frame 32 Git credentials restored, with commit identity added as a section instead of
      replacing the frame.
- [x] All six `staticDemo` content forks removed from screens; `DemoICodeGateway` is now the
      single demo source of truth and emits effects like the production gateway.
- [x] Search-as-you-type restored (debounced) and search results open their file.
- [x] Editor find match/line computation is linear instead of quadratic; document preview bounded.
- [x] Ahead/behind and upstream read from the local ref database; conflicts sort first.
- [x] New guards: `orphan-route` (every route has an entry point) and `demo-fork` (no staticDemo in
      screens), both ratcheted through `tools/ui-debt-allowlist.txt` with written reasons.
- [x] Unit tests, debug APK assembly, Android lint and the UI consistency guard pass.
- [ ] 40-frame capture not re-run this round (skipped at the user's request; device verification
      happens in their own testing).

Cycle 1 — Linux runtime, step 1 of 7 (packaging spike) complete:

- [x] Verified on the attached device, from the app's own process: a packaged binary in
      `nativeLibraryDir` **executes** (exit 0, arguments passed through), and the same binary copied
      into `filesDir` is **refused** (`error=13, Permission denied`). W^X holds; the packaging
      approach in `runtime/bootstrap/README.md` is sound.
- [x] `packaging.jniLibs.useLegacyPackaging = true` — without it native libraries stay compressed in
      the APK and nothing reaches `nativeLibraryDir` to execute.
- [x] `ExecProbeActivity` (debug source set only) is the on-device verification path; this ROM
      stalls the instrumentation runner, so `NativeExecSpikeTest` covers CI and emulators instead.
      (Both removed 2026-09-26 with the finished spike; see runtime/bootstrap/README.md.)
- [x] Runtime design settled against a working reference implementation
      ([techjarves/Mobile-Harness](https://github.com/techjarves/Mobile-Harness), MIT): build PRoot
      from source with the NDK and deliver it via the *carrier pattern* (a decoy shared-library
      target that AGP packages, overwritten post-build by the real executable), rather than
      vendoring and patching Termux `.deb` binaries. The earlier fetch script was deleted.
- Rule: **do not verify exec with `run-as`** — it runs in the `runas_app` SELinux domain, which is
      allowed to execute app data files, and reports the opposite of the truth.
- [ ] Open: PRoot is GPL-2.0 (talloc LGPL-3.0). It ships as a separate executable, so this is
      aggregation, but source and licences must accompany any release. Settle before the first build.
- [x] Steps 2-7 were completed in the runtime cycle below, including the real external-directory
      bind to `/workspace`, PTY process contract, foreground service, and coordinator.

Cycle 1 — Linux runtime, complete (verified on the attached device):

- [x] **Step 1 packaging** — a packaged binary in `nativeLibraryDir` executes; the same binary in
      `filesDir` is refused (`error=13`). Measured from the app's own process, never `run-as`.
- [x] **Step 2 native build** — PRoot 5.1.107.92, talloc 2.4.3 and libandroid-shmem compiled from
      source by the NDK and delivered with the *carrier pattern* (decoy shared-library target that
      AGP packages, overwritten post-build by the real executable). `NEEDED: libtalloc.so` is
      unversioned, so no `patchelf` is needed.
- [x] **Step 3 loader** — `PROOT_LOADER` is honoured; PRoot never extracts a loader. Proven by a
      traced exec (`proot -r / sh -c ...`) succeeding on device.
- [x] **Step 4 rootfs** — Ubuntu 24.04.5 base arm64 downloaded, SHA-256 verified, extracted through
      PRoot, health-checked. `READY: Ubuntu 24.04.5 LTS`, `id` reports `uid=0(root)`.
- [x] **Step 5 workspace bind (the risky divergence)** — the user's *real* external directory binds
      to `/workspace`. A file written inside the guest appears at the host path. Direct-path
      ownership survives PRoot.
- [x] **Step 6 process + PTY contract** — JNI bridge with `posix_openpt`/`grantpt`/`unlockpt`,
      `TIOCSCTTY` and `TIOCSWINSZ`. Verified in the guest: `T=/dev/pts/38 C=100 U=root`.
- [x] **Step 7 foreground service** — `specialUse` service keeps installs and sessions alive; the
      OS killed an unprotected extraction mid-way before this existed.

Owner's round of 2026-09-25 (records: [attention-labels-cleanup-plan](./docs/attention-labels-cleanup-plan.md),
[ui-polish-plan](./docs/ui-polish-plan.md)):

- [x] Notification rows name a program by what was run (`node …/freebuff` → freebuff), not a thread name.
- [x] Terminal alerts on their own channel: a bell ("needs your attention") and a command of 30 s or
      more finishing ("npm finished · after 2 min"), only for a session out of sight; switches in
      Terminal Settings. Verified on device.
- [x] Notifications page behind a bell on the launcher (unread dot); "terminal sessions ended"
      notices live there instead of a card. Verified on device.
- [x] Real code no longer named demo (`IcodeApp`, `Route`, `NavigationActions`, `NavigationState`);
      application ID `dev.icode.mobile`, version 0.1.0; About shows real build and runtime facts.
- [x] Found on the way: libandroid-shmem's compiled-in path of another package and its endless
      loop (patched); capture tools expecting 40 routes; three capture frames without their fixtures.
- [x] Diff and conflicts: pinch zoom; no "MODIFIED" label. Explorer: VS Code-style Git colours,
      folders showing the strongest change beneath them. Terminal: More… menu works. Branch sheet:
      the new-branch field stays above the keyboard.
- [x] Second- and third-level bars moved to one shared bottom bar (the editor's): Find, Settings,
      Problems, Output, Git credentials, Diff.
- [ ] Owner to try on the device: pinch zoom (adb cannot pinch), the redesigned bars in daily use.

Terminal maturity and background sessions (2026-09-24, verified on device):

- [x] Termux `terminal-view`/`terminal-emulator` v0.118.3 render the terminal: claude, opencode and
      freebuff TUIs, `less`, `sudo`/apt `[Y/n]` prompts, IME Enter, arrows, Ctrl keys. `exit` closes
      its tab; a signal death keeps the tab with its cause. PRoot patches (link emulation, exit
      status, hang-up/kill-on-exit, signal state) and PRoot's seccomp filter (a one-byte read/write loop:
      10.4 s → 95 ms; path-translated calls unchanged).
      Record: [terminal-maturity-plan](./docs/terminal-maturity-plan.md) §6.
- [x] Sessions outlive the screen: one session manager per process; a foreground service with one
      notification row per session (Exit, per-session wakelock, tap opens that terminal); the
      drawer shows iCode's own icon and a status-bar glyph traced from the logo.
- [x] Onboarding asks storage, notifications and battery optimisation one at a time with Android's
      own prompts; optional ones are offered once; Terminal Settings → Background shows and changes them.
- [x] After Android ends the process, the next start names the sessions that ended and why
      (`ApplicationExitInfo`), once. Record: [terminal-background-plan](./docs/terminal-background-plan.md) §6.
- Platform limit, not a task: XOS pauses every backgrounded app (Termux too), so on this phone a
      session is kept, not running, while iCode is off screen (recorded in the plan).

Terminal session/PTY lifecycle, complete (full VT rendering remains Cycle 6):

- [x] Multiple named sessions, select, close; `TerminalSessionManager` owns them.
- [x] Real exit state from `waitpid` — `exit 7` reports `Exited code=7`, and a signal renders as
      "terminated by signal N". A session the user closed is never reported as an exit.
- [x] Ctrl+C reaches the foreground job through the controlling terminal (verified: `sleep 60` then
      `^C` returns the prompt). Extra keys send ESC, TAB, Ctrl-C/D/Z and arrow sequences.
- [x] Bounded ANSI transcript: newline, carriage-return overwrite, backspace, tab and erase-in-line;
      colour dropped; cursor addressing reported honestly as unrendered rather than faked.
- [x] URL detection routes to Android's browser through `ACTION_VIEW`; no internal preview.
- [x] Frame 17 keeps every affordance it had, plus real input and a close action; `+` opens a
      session and the overflow icon keeps frame 29 reachable.

Terminal corrections from on-device review:

- [x] Terminal text and session labels now start at the shared 13 sp mono size; the extra-key
      labels are 11 sp with 48 dp-high controls, and tab close/new targets are 48 dp. Transcript
      taps still focus the keyboard without flashing a full-screen ripple.
- [x] Terminal-only header/tab density adjusted after a device capture: 48 dp header + 40 dp tab
      bar meet directly with no spacer, while close/new remain 48 dp wide with expanded touch bounds.
- [x] Kept the original Termux-style frame: no command box or RUN button. Typed characters go
      straight to the pty through a hidden input, and the key row is the frame's original set
      (ESC TAB CTRL ALT and arrows) spread evenly across the full width.
- [x] CTRL and ALT are sticky modifiers: tap CTRL then a letter to send Ctrl+letter one-handed.
- [x] Tab strip spans the full width; the scroll modifier had made the bar size itself to its tabs
      and leave an unpainted gap. Each tab carries its own close, so closing cannot hit the wrong one.
- [x] Fixed duplicate sessions: the open effect keyed on the session count, so it re-fired on every
      list change and spawned a new shell each time (`bash 3`, `bash 4`).
- [x] Fixed dropped keystrokes: holding the hidden field at the empty string raced the IME, so
      "echo" arrived as "eh". It now keeps the composed text and sends only the difference,
      emitting DEL for deletions and resetting at each line.
- [x] Terminal hidden input requests no auto-capitalization or autocorrection, so a soft keyboard
      does not turn literal commands such as `cd` into `CD`; device keyboard acceptance remains pending.
- [x] **Keyboard layout, app-wide**: the window used adjustPan, so focusing an input shoved the top
      bar and tab strip off-screen. `MainActivity` now uses `adjustResize`, and `IcodeScreen` pads
      for `navigationBars union ime` so every screen handles the keyboard once, in one place.
- [x] `clear` works: erase-in-display clears the transcript instead of only warning.
- [x] The "full-screen drawing" notice no longer fires on ordinary prompts — bracketed paste
      (`ESC[?2004h`) and cursor-home are normal; only the alternate screen buffer and real cursor
      addressing count.
- [x] Removed "groups: cannot find name for group ID ..." at every shell start by registering the
      app's Android supplementary GIDs in the guest `/etc/group`, read from `/proc/self/status`.
- [x] Pinch-to-zoom sizes the transcript; long-press selection and copy come from a
      `SelectionContainer`. The hidden input is excluded from accessibility.
- [x] Terminal output follows the latest line while the user is at the bottom, preserves scrollback
      while they read older output, and offers a lower-right jump-to-latest control when needed.
- [x] Opening the keyboard keeps the live prompt above the IME; selected or newly created tabs
      reveal themselves in the horizontally scrollable strip. Long-pressing a tab label allows
      drag-reordering without stealing the normal horizontal swipe or the close target.
- [x] Real and demo gateways share tab-name allocation and reorder semantics: closing `bash 4`
      then opening a tab reuses `bash 4` without reusing the closed session ID.
- [x] Terminal follow/scrollback, jump-to-latest, keyboard resize, and demo tab reorder were
      checked on the attached device; unit tests, lint, assembly and UI consistency pass.

Entry-flow corrections found while testing on device:

- [x] Splash chained Splash -> RuntimeInstalling -> PermissionExplanation on fixed delays, so the
      permission screen appeared on every launch even when already granted. Entry now routes on the
      real `isExternalStorageManager()` result.
- [x] Runtime preparation no longer happens at startup; it begins when a runtime-backed capability
      is requested and returns to the caller (US-ENTRY-003). Install progress is the real download
      fraction instead of a hardcoded 70.5%.
- [x] Fixed a snapshot race: concurrent non-atomic read-modify-write in the gateway clobbered
      freshly written fields, which left the splash waiting forever. All writes are atomic now.
- [x] Fixed a descriptor-ownership bug: `ParcelFileDescriptor.adoptFd` results were not retained, so
      finalization closed the pipe mid-read ("read interrupted by close() on another thread").
- [x] Debug probe activities hand the screen back and finish, instead of sitting blank in front of
      the user.
- [x] Unit tests, debug APK assembly, Android lint and the UI consistency guard pass.

Next delivery phase — one existing demo page and its full user lifecycle at a time, following
[`docs/integration-plan.md`](./docs/integration-plan.md) §2 and §5:

- [x] **First audit the currently reported search flow.** The device is showing frame 16
      (`README.md` Find/Replace), not frame 11 (Search Workspace). Frame 16 uses a separate
      400-line preview; Next/Previous changes only its counter, invalid regex silently looks like
      zero matches, and the match is never revealed in the real editor. Do not mark it complete
      because text replacement updates the document string.
- [x] Split engine ownership before coding (editor half done in Cycle 2; ripgrep half is priority 3): file-level find/replace belongs to the selected
      editor engine (Sora Editor per architecture §10.4); project-wide content search and Quick
      Open belong to the search service (`ripgrep` via the runtime process contract per §10.7).
      Verify current APIs, artifact/licence and runtime binary availability first; do not build
      another UI-owned matcher or scanner.
- [x] Deliver frame 16 as a vertical slice (delivered on Sora in Cycle 2; device acceptance in the backlog): keep its demo controls and layout; wire search,
      highlighting, exact match reveal, wraparound navigation, replacement, invalid-regex
      feedback, read-only/dirty/save behavior and back navigation through the real document.
      Preserve deterministic demo capture until the same contract is verified on device.
- [x] Cycle 2 editor implementation installed for user testing: Sora 0.24.6 replaces the
      BasicTextField and separate 400-line Find preview with one per-document editor session.
      Find now uses the engine's match highlighting, reveal, cyclic navigation and replace
      operations; invalid regex is visible. Native pinch zoom is enabled with bounded text
      sizes. TextMate grammars are bundled offline for Java, Kotlin, Python, XML, HTML,
      JavaScript/JSX, TypeScript/TSX, JSON, CSS, shell, Rust, Go, YAML and Markdown; unknown
      text stays editable without a guessed language. Markwon provides Markdown preview.
- [x] Real ordered file tabs: opening another file preserves dirty tabs; select, horizontal
      swipe, long-press drag reorder, close, close others and close saved work through the
      shared gateway. Each open file retains its editor undo/caret/scroll/zoom session.
      Dirty close and dirty workspace switch offer Save/Discard/Cancel; re-entering the same
      workspace does not clear open tabs. Demo fixtures use the same tab contract.
- [x] Fresh-install editor/search regression pass: switching `README.md` and `ssl.py` now
      rekeys the displayed Sora view with its document session, so tab content follows the
      selected file. Find restores query focus after selecting a match; separate-letter input
      was verified on device. Workspace Search (`ssl`) and Quick Open (`readme`) retained
      input focus during separate-letter device tests. Splash no longer forces runtime
      installation before runtime-independent file browsing.
- [ ] User device acceptance for frame 10/16: exercise Find/Replace, syntax coloring,
      Markdown source/preview, tab selection/drag/close, pinch zoom, and dirty-workspace
      switch. (The empty editor's recent rows and New File tile were made real in the workspace
      closeout above.) Only after this gate remove the touched frame's obsolete demo aid.
- [x] Delivered frame 11 Search Workspace and frame 14 Quick Open in S4: streaming/cancellable
      `rg` results, ignore rules, exact match offsets/range open and no search under the gateway
      mutation mutex. `WorkspaceIndexer` was deleted only after both real flows passed.
- [x] Navigation handoff for frames 11/14: Explorer, empty editor, file Find and Quick Open
      expose Search Workspace; the content-search page links back to filename Quick Open and
      shows the active workspace. Switching between the two search tools replaces the current
      route so Back does not accumulate duplicate pages. Results carry source line/column into the editor session;
      stale results are hidden while a new query is pending and regex errors show inline.
      Build, unit tests and lint passed before the route-replacement tweak; local compilation
      also passed afterward. Device result-to-caret acceptance and final reinstall remain
      pending while Git integration work shares the phone; do not treat this as completion of frame 11/14.
- [x] Resume Git frames 12, 13, 30, 32, 31 and 24 (on the Git CLI in Cycle 4; device acceptance of 24/30/31 in the backlog), one reachable interaction at a time, with the
      real Git CLI behind `GitService`; remove JGit only after the replacement is verified.
- [ ] For each touched frame, inventory its controls/routes, implement real success and
      loading/empty/error/cancel/recovery states, close missing gaps, verify tests/build/lint/
      UI guard/device. No capture fork or fake success; keep the 39-route capture path. (The
      demo aids themselves were removed in one step on 2026-09-26, K1.)

Still open: PRoot is GPL-2.0, so source and licences must accompany any release. Sora Editor's
LGPL distribution obligations also need review before its release build.

Cycle 4 — Git on the real client (in progress, Claude):

- [x] **Blocking unknown resolved on device.** The Ubuntu base ships no `git` (nor `ripgrep`,
      `curl`, `wget`). apt does reach the network from inside the guest
      (`Fetched 36.5 MB in 1min 1s`), so `apt-get install -y git` is a viable provisioning path and
      no pre-baked rootfs bundle is needed yet. **`git version 2.43.0` now runs in the guest.**
      This also settles the same question for Cycle 3's ripgrep.
- [x] Git domain contract (`domain/GitContract.kt`): diff lines/hunks/file diffs, conflict sections,
      in-progress operations, and a `GitOutcome` that carries Git's own error text.
- [x] `GitPorcelainParser` for `status --porcelain=v2 --branch -z`: branch/upstream/ahead/behind,
      renames with their original path, unmerged entries, and NUL-safe paths. A file that is staged
      *and* edited again reports in **both** groups, the way Git actually describes it.
- [x] `GitDiffParser` for unified diffs: hunks, independent old/new line numbering, renames,
      binary files, `\ No newline`, and omitted hunk counts.
- [x] `GitCommandLine`: POSIX single-quoting for every argument, stderr redirected to a file so
      NUL-delimited porcelain output stays byte-exact, `core.quotepath=false` and
      `--no-optional-locks`.
- [x] `CliGitService`: runs git in the guest over the process contract, with status/diff/provision.
- [x] 38 new unit tests built from **real** git output captured from a repository with a staged
      edit, a further unstaged edit of the same file, a rename with spaces in both names, a
      deletion, and an untracked file with a space. 98 tests pass overall.
- [x] Next: wire `CliGitService` into the gateway and the Git screens (12, 13, 15, 24, 30, 31, 32,
      07, 08), then delete JGit. This touches shared gateway files, so it needs coordination.

Cycle 4 — Git end to end (verified on device against a real private repository):

- [x] `CliGitService` + `GitRepositoryClient` run the real `git` in the runtime; the gateway no
      longer uses JGit for status, diff, stage, commit, branches, discard, clone or sync.
- [x] Repository inspection is **one** process: a single script emits toplevel, status, branches,
      remote, identity and last commit separated by RS. Starting PRoot costs about a second, so six
      commands per refresh was not viable.
- [x] Recents read `.git/HEAD` directly instead of starting a Git process per workspace, so the
      launcher stays instant.
- [x] Frame 13 Diff view renders Git's own patch: hunks, independent old/new numbering,
      previous/next hunk navigation, binary and no-change states.
- [x] Frame 12 Source Control opens the real diff for a change, and distinguishes "not a
      repository" from "Git is not installed yet".
- [x] Frames 07/08 Clone: real clone with the destination browser, derived directory name, and
      progress that ends when Git exits rather than after a fixed delay.
- [x] Frame 32 Git credentials rebuilt as a three-step flow (identity -> authentication -> verify)
      instead of one long page, with a provider dropdown (GitHub/GitLab/Bitbucket/Other) driving a
      single relevant action rather than a button per host.
- [x] Auth is real: PAT encrypted by the Android Keystore and exposed to Git through a 0600
      credential helper, or an ed25519 SSH key generated in the runtime with a copy/"Add to
      <provider>" hand-off. `ls-remote` verifies against the real remote.
- [x] **Proven end to end**: SSH key generated on device, added to GitHub by the user, connection
      test reported `Connected - 1 branch(es) visible`, and a private repository cloned to real
      external storage with its files on disk.
- [x] Two runtime limits found and fixed (see runtime/bootstrap/README.md): Android storage
      supports neither hard links nor symlinks and `--link2symlink` breaks `rename` there, which
      was failing every clone; and Git's dubious-ownership check must be waived for the bound
      workspace, now recorded globally so the user's own terminal works too.
- [x] 109 unit tests; parsers are covered by fixtures captured from real `git` output.
- [ ] Remaining in this cycle: device verification only. Conflict resolution (frame 24) and branch
      delete/remote branches (frame 15) are implemented in the gateway, and JGit is gone from the
      build and the code (checked 2026-09-24).

S4 — Search and Quick Open on real ripgrep (complete 2026-09-28):

- [x] First-use runtime provisioning shared across concurrent requests; cancelled queries do not
      interrupt apt. `GuestCommand` streams bounded newline/NUL records and kills/reaps on cancel.
- [x] Quick Open uses `rg --files --null`; content uses `rg --json`. Hidden files are included,
      `.git` excluded, ignore rules honored, spaces/quotes/Unicode/newlines preserved, byte offsets
      converted to editor columns, and all caps shown honestly.
- [x] `SearchCoordinator` owns independent jobs/generations off the gateway mutation lock;
      stale query/workspace results cannot publish. `WorkspaceIndexer` and its tests are gone.
- [x] Search result groups are functional compact accordions; count text no longer wraps into tall
      rows. Explorer expansion stays responsive; tapping blank Explorer space makes creation target
      the workspace root without a full-area ripple.
- [x] Device: 355 matches / 69 files in a real workspace; isolated hidden/ignore/`.git` fixture;
      Quick Open; accordion collapse/expand; Explorer root destination. Fixture cleanup touched only
      the known scratch paths.
- [x] Final gate: debug/release compile, 295 unit tests, lint 0 errors / 16 warnings, UI guard.

S5 — Git (complete 2026-09-28; record in [`docs/git-separation-plan.md`](./docs/git-separation-plan.md) §12):

- [x] Moves: `GitInspectParser`, `GitWorkspaceCoordinator`, `GitTransferCoordinator`,
      `GitAuthCoordinator`, `GitConflictCoordinator`; the gateway keeps no Git call (1227 → 831 lines).
- [x] Fixes: one package installer; cancellable Git with its own stderr; NUL-framed inspect; parent
      repository, submodule and worktree mounts; detached HEAD; stale refresh; exact discard; per-block
      conflicts through the editor's writer; remote branch by full ref; Add Remote; token never in saved
      state nor in plain text between commands; masked transcripts; transfers off the lock, one at a
      time, cancellable; clone branch; `--` before addresses; interrupted transfers reported.
- [x] `tools/check-backend-consistency.sh` joins the routine gate. 391 tests; lint 0 / 14.
- [ ] Owner, with their credentials: HTTPS token and SSH on a private repository, push, and the
      legacy-credential migration on a phone that had a saved token.

Repository baseline:

- Module: `:app`
- Namespace: `dev.icode.mobile`
- Application ID: `dev.icode.mobile` (debug builds `dev.icode.mobile.debug`)
- Minimum/target: API 30/36

UI coverage by current route group:

| Group | Frames | Backend/integration demand |
|---|---|---|
| ENTRY | 10 | Splash/runtime install/error/progress/retry/diagnostics; permissions/storage handoff; launcher/recents; create/open folders; clone/progress/auth |
| WORKSPACE | 4 | Explorer/tree state and operations; editor/documents/empty/tabs; search query/filters/results; bottom-panel terminal/problems links |
| TOOLS | 13 | Status/stage/unstage/commit; diffs; branches; conflicts; push/pull/fetch/progress; Git credentials; terminal/sessions/keys; problems; output; Quick Open; palette |
| SETTINGS | 10 | Settings hierarchy/search/overrides/reset; editor/terminal/Git/language/runtime/storage/appearance/scope; diagnostics/build/services; persistence/migrations |
| RECOVERY | 3 | Delete confirmation; external-change detection/reload/compare/overwrite; auto-recovery restore/discard |

Done when all screens consume only the gateway contract or clearly scoped local presentation state, every actionable control has an owner, and no platform adapter design is being inferred from sample strings.

## Backend working rules

- Before starting a package, inspect every affected route in `navigation/Route.kt`, its screen implementation, the snapshot fields it reads, and the intents it dispatches.
- Plan route by route before coding: affected frames, contract changes, gateway behavior, error mapping, cancellation, and tests. Never implement on an unverified assumption.
- Domain models must not contain UI, Compose, filesystem-implementation, process, or database types.
- Keep every feature behind `ICodeGateway`; screens continue to compose only.
- Do not remove routes, change back behavior, regress capture output, or restore a screen catalog.

### B-01 — Domain and gateway boundary

Use the contract-freeze checklist above. B-01 owns domain value types, state/effect boundaries, intent semantics and contract tests; it does not implement platform adapters.

### B-02 — Workspaces, files, storage, runtime bootstrap, and recovery storage

- Workspaces live in iCode's storage ([workspace-storage-plan](./docs/workspace-storage-plan.md)): the list is the workspaces folder; create, clone, import, rename and delete; last-opened times.
- Implement listing, metadata, creation, deletion, storage metrics, cleanup, and storage errors.
- Watch external changes with file identity, fingerprints, and stale-event suppression.
- Store versioned recovery buffers and resolve safe destructive paths.
- Implement runtime install/verify/health/start/stop/update/repair/reset and diagnostics.
- Preserve runtime-independent user data and report storage failures accurately.
- Done when ENTRY, Explorer, storage, recovery-storage, and runtime-health flows have real state and errors.

### B-03 — Documents, editor state, find/replace, and opening behavior

- Implement open/close/dirty/saving/saved/read-only/missing/external-change/conflict lifecycle.
- Save to original files with encoding and line-ending preservation.
- Implement autosave, undo-safe state capture/restoration, and recovery-safe save failures.
- Implement find options, replacement preview/confirmation, and exact range opening.
- Implement reload, compare, explicit overwrite, restore, discard, and cancel behavior.
- Done when editor, search/find, empty-editor, launching, recovery, and problem-navigation flows operate on real documents.

### B-04 — Git discovery, status, diffs, actions, sync, conflicts, and credentials

- Detect normal, parent, worktree, nested, detached, unborn, and submodule repositories.
- Parse NUL-safe porcelain output, renames, conflicts, untracked/binary/large files, and share one snapshot.
- Parse staged/unstaged diffs, hunks, and line metadata; distinguish context/add/remove/conflict sides.
- Validate clone destination/URL/branch/authentication, progress, cancellation, and safe failure.
- Implement stage/unstage/restore/discard/commit; searchable branches; create/switch; valid merge/rebase/cherry-pick/revert handling.
- Implement current/incoming/both/manual-resolution workflow and explicit resolution marking.
- Implement fetch/pull/push with identity, SSH/credential, offline, hook, conflict, and remote-rejection errors.
- Keep credentials separate from workspaces and test authentication safely.
- Done when all source-control, diff, branch, commit, sync, auth, recovery-delete, and conflict frames have real Git behavior.


### B-05 — Process/PTY, terminal, search, commands, LSP, diagnostics, and output

- Provide one process/PTY API for arguments, working directory, environment, input/output, exit, signals, cancellation, timeout, resize, sessions, foreground ownership, and honest lifecycle states.
- Route terminal, Git, ripgrep, and language-server execution through that API; do not parse UI text.
- Support multiple terminal sessions, workspace directories, scrollback, selection, copy/paste, extra keys, URLs/external browser, and stopped-process reporting.
- Implement ranked Quick Open, recent files, availability/scoped commands, keyboard/touch navigation, and focus restoration.
- Implement streaming, bounded, cancellable ripgrep search with filters/regex/include/exclude/stale-result suppression and grouped exact-range results.
- Validate replacement previews, file/match exclusions, explicit confirmation, and truthful partial-failure reports.
- Implement generic LSP configuration, JSON-RPC lifecycle/capabilities/version synchronization/restart/backoff, and advertised-only language features.
- Aggregate diagnostics for editor, Explorer, Problems, Problems filters, and exact navigation.
- Provide bounded sanitized output channels for runtime, Git, search, and language services.
- Done when terminal, Quick Open/palette, workspace search, Problems, output, command-launched changes, and external modifications react correctly.

#### B-05/B-06 close-out — commands, panels, engine, lifecycle, About (2026-09-23)

Work one slice at a time under [`docs/backend-split-rules.md`](./docs/backend-split-rules.md) and
[`docs/commands-panels-plan.md`](./docs/commands-panels-plan.md). These are **not** one completion
checkbox: existing process status and editor recovery do not imply LSP diagnostics or session restore.

1. [x] **Command Palette first code slice:** replace screen-owned sample routing with a typed,
   queryable catalog; gate actions on the current workspace/document/Git capability; dispatch
   existing Save/Push/Pull intents or navigate to the real message/search/terminal tools. Add
   catalog tests. Compile and unit tests pass; device touch/IME/keyboard acceptance is still open.
2. [ ] **Command Palette acceptance:** verify search-as-you-type focus, empty result, disabled
   reasons, touch and hardware-key selection, Save failure, remote rejection, and back-stack
   behavior on device and in deterministic capture. Add a soft-keyboard submit action only if it
   uses the same executor; do not display shortcuts that are not globally registered.
3. [x] **Problems presentation code slice:** severity chips now filter actual rows; counts still
   describe all diagnostics; zero-diagnostic production state says a clean-code check is not yet
   available. The inert filter icon is removed. Severity/empty behavior has unit coverage; device
   and deterministic-capture acceptance remains open. Demo diagnostics stay in the demo gateway.
4. [ ] **Diagnostics provider + navigation:** after LSP integration, give diagnostics canonical
   workspace/file/range/version identity, publish one workspace-scoped aggregate, expire stale
   versions, and open the exact file/range from Problems and Explorer. Handle server death and
   workspace switch; no parsing of display strings such as `line 42:15`.
4a. [x] **One tools panel, not three demo destinations:** Editor/empty-editor Problems and Terminal
    now open one panel route with Terminal, Problems and Output tabs; tab changes do not stack
    pages, Back returns to the caller, and legacy frame IDs remain capture aliases. Opening
    Problems/Output does not start a shell. Output no longer contains the fixed `App.tsx` preview,
    timestamps or fake Git log; it shows the latest real bounded Git-operation transcript and
    truthful idle/running/failure states. Device/capture acceptance is still pending.
5. [ ] **Output panel:** replace fixed Git lines, fake timestamps and `App.tsx` preview with bounded,
   sanitized runtime/Git/search/language-service event channels from their real owners. Git
   now uses `gitOperation.transcript`; broader channel history/selection, redaction, scroll/follow,
   restart and workspace-switch behavior remain open. No unsupported channel is selectable.
6. [ ] **Terminal full VT acceptance:** Termux `terminal-view`/emulator and its own session/input
   path are now integrated by the terminal cycle in [`docs/terminal-maturity-plan.md`](./docs/terminal-maturity-plan.md).
   Verified on device 2026-09-24: TUIs (claude, opencode, freebuff), `less`, IME, prompts, exit and
   signals. Remaining: the terminal line of the acceptance backlog; `htop` waits on the `/proc`
   decision (priority 7).
7. [ ] **LSP protocol cycle (separate from completed configuration/process status):** connect
   JSON-RPC initialize/shutdown, capabilities, document versions/changes and cancellation over
   the existing guest process contract. Expose completion, diagnostics, rename and formatting
   only when advertised and implemented; test absent/malformed servers and restart/backoff.
8. [x] **Session restore — code done (S3, 2026-09-28; owned by `WorkspaceSessionCoordinator` since X3);
   device acceptance is priority 6:** persist prior workspace identity, open file IDs/order/selection and
   supported editor/panel state in the existing session/settings owner; validate access and file
   existence on restart. Keep dirty text in the guarded draft owner, never silently reopen a
   terminal command or claim a dead PTY is running. Test process death, permission revocation,
   corrupt state, workspace switch and recovery choices.
9. [ ] **About (priority 4; version, runtime and editor facts are real since 2026-09-25):** replace version/runtime/editor literals with verified build/install facts;
   make resource/diagnostic rows real actions or visibly unavailable, never no-op taps. Audit
   update, export, licence, source and issue destinations before wiring them; project URLs will be
   provided by the user later, so do not invent them. Redact secrets and workspace content from
   any export. Test installed APK facts and failure/absence states.

### B-06 — Settings, persistence, sessions, migrations, and hardening

- [x] Document the repo-wide backend split/ownership rules and the scoped Appearance/Editor
      implementation plan in [`docs/backend-split-rules.md`](./docs/backend-split-rules.md) and
      [`docs/settings-backend-split-plan.md`](./docs/settings-backend-split-plan.md). Apply the
      rules to new work; defer splitting existing implementations until that scope is requested.
- [x] Record scope decisions: Appearance scales UI text only; derive Light/AMOLED palette candidates
      for review (accepted by the owner 2026-09-29); guarded autosave defaults to After 1 second on
      fresh installs.
- [x] Implement the Appearance/Editor typed settings and persistence seam on the existing
      DataStore; apply values to current/new editor sessions, Markdown, UI tokens and system bars.
- [x] Connect guarded one-second autosave and real app-private recovery drafts, with a restore
      choice on reopen and explicit overwrite confirmation after external file changes.
- [x] Make Settings Root search actionable and remove the superseded page-local editor settings state.
- [x] Terminal Settings now persist app-wide shell, font, live scrollback limit and extra-key row
      through the existing preferences repository. Configured shells are checked inside the real
      runtime. (Superseded: Termux's renderer draws the cursor and the style setting now applies
      live; device check in the backlog.)
- [x] Language Services now save user-defined commands and configuration, select the correct
      detail, and show observed guest-process start/stop/exit/PID. No advertised LSP capability,
      completion or diagnostic is claimed before the separate protocol integration cycle.
- [x] Storage Settings now measures real device/runtime/cache/recent-workspace usage with bounded,
      symlink-safe scans. Confirmed cleanup removes only runtime cache artifacts or stale recent
      shortcuts, never workspace files or recovery drafts.
- [x] Runtime Settings repair/reset now enters the real progress/failure screen and retries the
      same operation. Repair preserves home across interrupted attempts; Reset requires confirmation.
- [ ] Device acceptance remains for these four settings surfaces, including a deliberately
      interrupted repair and a user-installed language server (a service saved while another
      runs was checked with throwaway `sleep` commands, S8.5). Full LSP handshake/capabilities
      and session restore remain future work (priorities 8 and 6).
- [ ] Review the 100–150% UI scale/comfortable spacing on device. (Light/AMOLED palettes: accepted
      by the owner on 2026-09-29.) Format On Save remains unavailable
      until a real formatter exists; the separate recovery-center/session-restore cycles remain open.
- Implement hierarchical categories and settings keys, types, defaults, validation, app/workspace scope, restarts, and migrations.
- Support workspace overrides and reset-to-inherited behavior for the existing settings catalog.
- Persist workspaces/recents/settings/sessions; restore workspace, tabs, editor state, Explorer/panels/route; verify terminal status rather than invent it.
- Handle corrupt-state fallbacks, unavailable/migrated settings, permission revocation, removed/full storage, offline conditions, process death, cancellation, malformed tool output, adaptive layouts, accessibility, performance, privacy, and log redaction.
- Done when invalid settings are blocked, recovery is safe, and state survives supported interruptions.

## Integration cutover

- Implement production `ICodeGateway` and wire every existing intent.
- Replace demo snapshot/fixtures without changing screen composition (done: K1, 2026-09-26).
- Preserve all 39 routes, back behavior, scroll/clipping behavior, and capture workflow.
- Ensure every connected frame has genuine loading/empty/error/conflict/recovery/cancelled/read-only behavior.
- Test storage, runtime, Git, process, network/auth, malformed-output, cancellation, restart, rotation, backgrounding, and uninstall/update paths.

## Post-integration demo cleanup — done (2026-09-26)

Done in one step, [code-separation plan](./docs/code-separation-plan.md) K1: the demo gateway,
`DemoContent` and their tests are deleted; screenshots are drawn from debug-only capture fixtures
(`app/src/debug/.../capture`), which a release build does not contain; the timing gates that
keep a capture still are `captureMode` (guard `capture-fork`); no product string says demo.
Checked with unit tests, build, lint, the consistency guard, the 39-route capture and the device.

## Validation

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `tools/check-ui-consistency.sh`
- `tools/check-backend-consistency.sh`
- `tools/capture-figma-screens.sh`
- Install and test on a physical Android device.
- Pass the five production flows: edit/commit, command-driven workspace changes, development-server URL, interruption/recovery, and unfamiliar language-server setup.

## Non-goals

- Website, server, browser bundle, and MongoDB work.
- UI restyling, new product surfaces, and new features.
- Provider-specific agent integrations beyond ordinary user-installed commands.
- Filesystem, process, Git, IPC, or database work on the UI thread.
- Silent overwrites, stale-state publication, false success, or credential exposure.
