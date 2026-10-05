# Terminal attention, program labels, and the demo-name cleanup

Status: **plan** (2026-09-24). Follows [backend-split-rules.md](./backend-split-rules.md);
builds on [terminal-background-plan.md](./terminal-background-plan.md).

> **Note (2026-09-26).** Names in this record are as they were then. The demo layer (`DemoICodeGateway`, `DemoContent`, `staticDemo`, `demo-fork`) was replaced by debug-only capture fixtures (`CaptureGateway`, `CaptureFixtures`, `captureMode`, `capture-fork`) — [code-separation-plan.md](./code-separation-plan.md) K1.

Three owner requests, one plan: (A) the notification row showed "running
MainThread" for freebuff — fix the cause, not the case; (B) tell the user when a
command or an agent (Claude Code, Codex, …) finishes or needs them, as Mobile
Harness does; (C) real code still carries "demo" names and the About page is
demo content — make it real; then (D) a regression pass across the app.

## 1. Evidence (2026-09-24)

**A — the label.** freebuff running in tab `bash` (device, `ps` and `/proc` read
as the app's uid):

| pid | `stat` name (`comm`) | `cmdline` | role |
|---|---|---|---|
| 15173 | `bash` | `/bin/bash -l` | shell, PRoot's child |
| 15435 | **`MainThread`** | `node /usr/local/bin/freebuff` | foreground group leader (PRoot's `tpgid`) |
| 15446 | `freebuff` | `/root/.config/manicode/freebuff` | the real binary, same group |

`TerminalForeground` names the group leader by its `comm`. That field is the
main thread's name, which programs set themselves (Node names its main thread
`MainThread`) and the kernel cuts to 15 characters. Every Node, Bun or Python
program that renames a thread, and every interpreter-run script (`node x.js`
shows `node`), gets a wrong or useless label. The arguments are what was run.

**B — what the terminal can hear.** Mobile Harness drives its agent itself
(non-interactive, its own chat UI), so its code knows start, progress and the
end, and posts "Task completed" / "Task needs attention" on a separate
DEFAULT-importance channel (`RuntimeExecutionService`). iCode runs agents
interactively in a real terminal, so it can only observe. Measured in the
emulator iCode uses (Termux 0.118.3, bytecode of `TerminalEmulator`):

- BEL reaches the app (`TerminalSessionClient.onBell`); iCode ignores it today.
- OSC 0/1/2 (title), 4, 10–12, 52, 104, 110–112, 119 are handled; **OSC 9, 99 and
  777 — the desktop-notification escapes — are dropped** without a callback.
- Claude Code (docs): `preferredNotifChannel: "terminal_bell"` rings the bell
  "when Claude completes a task or pauses for input"; `Notification` and `Stop`
  hooks run a command.
- Codex (docs): `notify` runs a program with JSON on `agent-turn-complete`; its
  TUI notifications use terminal escapes that the emulator drops.
- A command's end is already observable: the service samples PRoot's `tpgid`
  every 5 s; a program leaving the foreground returns the shell to its prompt.
  `/proc` does not give its exit status.
- On this XOS phone iCode is frozen ~5 s after leaving the screen
  (terminal-background-plan S3a), so nothing runs — and nothing finishes —
  while it is away; on stock Android the sessions run and alerts arrive live.

**C — "demo" in real code.** Real code named demo: `IcodeDemoApp` (the
composition root), `DemoScreen` (the route enum, 205 uses), `DemoGroup` (47),
`DemoActions` (navigation actions, 90), `DemoNavigationState`, versionName
`0.1.0-demo`, applicationId `dev.icode.mobile.demo`. Genuine demo — fixtures
for the 40-frame capture only: `demo/DemoICodeGateway`, `DemoContent`,
`DemoWorkspacePreferencesRepository`, the `staticDemo` capture switch. Product
text still demo: About ("0.1.0-demo", "Sora (integration planned)", "What's
new in this demo", seven rows whose taps do nothing). Tools that read the
names: `tools/check-ui-consistency.sh` (parses `DemoScreen.kt`),
`tools/capture-figma-screens.sh`.

## 2. Design

**A — label from the arguments.** `ProgramName.of(argv)` (pure, tested with the
captured argv): the basename of `argv[0]`; when that is an interpreter or
launcher (`env`, `node`, `bun`, `deno`, `python*`, `ruby`, `perl`, `php`,
`java`, `sh`/`bash`/`dash`/`zsh`), the basename of the first argument after its
options — the script — unless a shell was given `-c` (then the shell's name).
`comm` stays only as the fallback when `cmdline` is empty or unreadable.
Control characters are removed and the label is capped.

**B — attention and completion, from signals that exist.**

1. A second channel, "Terminal alerts" (DEFAULT importance: sound/heads-up per
   the user's channel settings), apart from the silent session rows.
2. **Bell** → "claude needs you · superpowers — bash" (the program, from A),
   only when the user is not looking at that session (iCode not visible, or
   another tab or screen shown). One alert per session, updated, not stacked;
   cleared when that session is shown; tap opens it.
3. **Command finished** → when a program that held the foreground for at least
   a threshold (**owner's decision: 30 s**, on by default) returns the shell to its prompt, and the user is not looking:
   "npm finished · superpowers — 2 min". "Finished", never "succeeded": the
   exit status is not observable.
4. Terminal Settings → Background: a switch for each, and the threshold.
5. Later, own plan: an `icode-notify` guest command for agents' hooks (Claude
   Code `Stop`/`Notification`, Codex `notify`) with the agent's own message,
   and an offer — with consent — to set Claude Code's `terminal_bell`.

"Looking" = iCode's activity started **and** that session's view attached
(`TerminalSessionManager.observe`). Owner: the service decides and posts; the
manager reports bells and the observed session.

**C — real names; honest About.** Renames, whole-word, one step:
`IcodeDemoApp`→`IcodeApp`, `DemoScreen`→`Route`, `DemoGroup`→`RouteGroup`,
`DemoActions`→`NavigationActions`, `DemoNavigationState`→`NavigationState`
(files, tests, tools, doc links). The fixture layer keeps its demo names — it is
the demo — until the final cleanup gate (Todo "Post-integration demo cleanup").
versionName `0.1.0`. About: real app version, runtime release, editor engine
and minimum Android from the build and the runtime; rows with no destination
yet (updates, diagnostics export, licences, source, documentation, release
notes, issues) shown as unavailable instead of taps that do nothing — their
URLs and the licence decision are the owner's (Todo priority 4).
applicationId: **owner's decision (2026-09-24): rename now** to
`dev.icode.mobile` — a separate app on the phone (runtime, permissions, Git keys
and recents set up again; the old `.demo` app stays until the owner removes it).

**D — regression.** Unit tests, lint, UI guard, assembly; the 40-frame capture
(every route renders after the renames); on the device: onboarding, launcher,
workspace, Explorer, open/edit/save, find, search, Quick Open, Source Control,
terminal (labels for freebuff/claude/`sleep`/`python3 -m http.server`, `exit`,
tabs), notification rows and alerts, settings pages, About, lost-sessions
notice.

**N — a Notifications page (owner's request, 2026-09-25).** The "sessions ended"
notice as a card on the home screen is too big. Instead: a bell before the
Settings gear on the home screen, with a dot while anything is unread, opening a
Notifications page that lists the notices.

- **N1:** `AppNotice` (domain; first kind: terminal sessions ended, with the
  S5 report's sessions, cause and time). `NoticeStore` (data, one per process,
  app-private `SharedPreferences`, newest first, at most 50) — notices survive
  a restart until deleted. The ledger hands its report over once per process
  (`takePrevious`), the gateway stores it; the snapshot carries `notices`.
  Intents: mark all read (on opening the page), delete one, clear all. The
  launcher card goes; its text helpers become the page's compact item. A new
  route `Notifications` — the first without a Figma frame, so `figmaNode`
  becomes nullable and `RouteTest` lists such routes explicitly; the capture
  script keeps capturing the 39 Figma routes.
- **N2 (proposed, not built):** terminal alerts (bell, command finished) also
  recorded on the page, so they can be read later.

## 3. Steps

| Step | Change | Verify |
|---|---|---|
| **A1** | `ProgramName` + `cmdline` in `TerminalForeground` | Tests from captured argv; device: freebuff row reads "running freebuff" |
| **C1** | Renames + tools + doc links | Build, tests, lint, UI guard, capture script dry run |
| **C2** | versionName; About made real | Device: About shows installed facts; no inert taps |
| **B1** | Alerts channel; bell → alert; looking-state | Device: `printf '\a'` in a backgrounded tab alerts; not when watching |
| **B2** | Command finished → alert; Settings switches | Device: `sleep 40` in background alerts; `sleep 5` does not |
| **D** | Regression pass | As above; record in §4 |

## 4. Record

**A1 (2026-09-24) — done, verified on device.** `ProgramName` (5 tests from the
captured argv) names the foreground leader from `/proc/<pid>/cmdline`; `comm` is
the fallback. freebuff in tab `bash`: the row read "superpowers · running
MainThread" before, "superpowers · running freebuff" after.

**C1–C2 — done.** Renamed `IcodeDemoApp`→`IcodeApp`, `DemoScreen`→`Route`,
`DemoGroup`→`RouteGroup`, `DemoActions`→`NavigationActions`,
`DemoNavigationState`→`NavigationState` (six files moved, 63 files updated:
code, tests, `check-ui-consistency.sh`, docs). applicationId `dev.icode.mobile`
(debug `dev.icode.mobile.debug`), versionName `0.1.0`; the APK reports
`dev.icode.mobile.debug` / `0.1.0-debug`. About: app version, runtime release,
editor engine (`EDITOR_ENGINE_VERSION`, the same value the Sora dependency uses)
and minimum API come from the build and the runtime; the seven rows without a
destination say "Not available yet" and take no tap. Found on the way:

- `libandroid-shmem` was compiled with `_PATH_TMP="/data/data/dev.icode.mobile.demo/cache/"`
  (copied from Mobile Harness, which names its own package): right for one
  package only, and the debug app could not write it. Worse, a failing
  `symlink()` made its key loop spin forever holding a mutex. Patch
  `third_party/patches/libandroid-shmem/0001` takes the directory from
  `PROOT_TMP_DIR` at run time and returns an error instead of spinning; the
  build's patch step became one function for both submodules. Reached only
  through PRoot's `--sysvipc`, which iCode does not pass.
- `tools/capture-figma-screens.sh` still expected 40 routes; there are 39 since
  frame 3:55 became the failed state of 3:27 (integration-plan). Fixed, with the
  reason in the script.
- `DocumentRecoveryScreen` still lists `DemoContent` documents. Its route is an
  accepted orphan (no real entry point), so users never see it; making it real
  is the recovery centre, part of Todo priority 6 (session restore).

**B1–B2 — built, device check pending.** `TerminalAlerts` (own channel
"Terminal alerts", DEFAULT importance; one alert per session, replaced by the
next; tap opens that session; cleared when the user looks at it), `CommandRuns`
(pure, 5 tests), bells from `TerminalSessionCallbacks.onBell`, "looking" =
`MainActivity` started and the session's view attached. Terminal Settings →
Background: "Alert When a Program Asks" and "Alert When a Long Command Ends"
(30 s), both on by default.

**Fresh install (the renamed app) — onboarding verified by the owner:** runtime
downloaded and extracted in under 30 s, then "Step 1 of 3"; after the owner's
own taps: all-files allowed, notifications granted (`USER_SET`), battery exempt,
both optional steps recorded as offered.

A first bell check on the device (`sleep 8; tput bel`, then the Problems tab)
showed no alert — it ran on an APK assembled before B1–B2 existed (lint and
unit-test tasks do not rebuild the APK). Not evidence either way; to be redone
on the rebuilt APK (02:12, alert strings confirmed inside it).

**Checks:** 235 unit tests pass; lint 0 errors, 23 warnings (unchanged); UI
consistency passes. Pending, needs the phone: bell alert (`printf '\a'` in a
tab left out of sight), finish alert (`sleep 40` out of sight, and `sleep 5`
not alerting), About on device, the 39-route capture, and the app-wide smoke
pass (D). `TerminalSessionManager` is now 332 lines: it owns the sessions and
their observation, which the new "looking" state is; the alert logic lives in
`TerminalAlerts`.
