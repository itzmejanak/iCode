# S6 Terminal and Background — Separation and Close-out Plan

Status: **plan for approval** (2026-09-28). The S6 step of
[code-separation-plan.md](./code-separation-plan.md), which stays authoritative. Evidence and
earlier records: [terminal-maturity-plan.md](./terminal-maturity-plan.md) (T1–T5, 2026-09-24),
[terminal-background-plan.md](./terminal-background-plan.md) (S0–S5, 2026-09-24),
[sudo-capability-plan.md](./sudo-capability-plan.md) (superseded by passthrough `sudo`). Same rules
as S5: one owner per lifecycle, ~90–270 lines (300 budget), a move never shares a commit with a
behaviour change, both consistency guards in every gate, a fresh APK for every device check.

## 1. What S6 must deliver

Most of the terminal is already real and was verified on the device in September: Termux's
emulator and view, sessions that outlive the screen, a foreground service with one notification row
per session, per-session wake lock, bell and long-command alerts, and an honest report when Android
ended the process. S6 therefore is a close-out, not a rebuild:

1. Take the terminal cluster out of the gateway into one owner, and bring the two over-budget
   terminal files under 300 lines along their real seams.
2. Fix the gaps found in the current code (§3).
3. Make the `libtermux.so` packaging a checked fact instead of a merge-order accident.
4. Run the terminal's open device items, and close the three older plans with a record.

## 2. Lifecycle and owners today

| Stage | Surface | Owner today | Owner after S6 |
|---|---|---|---|
| Enter | Tools panel Terminal tab; "+"; "Open Terminal" on a recent workspace; notification row tap | gateway (open, show, select, reorder, close; settings applied) + `TerminalSessionManager` | `TerminalCoordinator` + manager |
| Load | shell checked, prompt installed, PRoot argv built, Termux session started | `TerminalSessionManager.prepare/start` (341 lines) | `TerminalLaunch` (preparation) + manager (sessions) |
| Act / react | typing, keys, selection, URLs, zoom, cursor style; exit and signals; commands finishing refresh the workspace | Termux view + manager; gateway observes `commandsFinished` | unchanged; the refresh trigger moves to the coordinator |
| Background | service, parent notification, session rows, Exit, wake lock, bell and finish alerts | `RuntimeForegroundService` (354 lines) + `TerminalAlerts` | service (lifecycle, wake lock) + `TerminalNotifications` (channel, parent, rows, intents) |
| Interrupt / recover | process ended by Android: ledger → Notifications page | `TerminalSessionLedger`, `NoticeStore` | unchanged |
| Settings | shell, font, scrollback, extra keys, cursor, alerts, background permissions | `TerminalSettingsScreen` + preferences repository | unchanged |

## 3. Confirmed gaps

1. **Two sessions from one request.** The panel's automatic first open dispatches the same
   `OpenTerminalSession` as the "+" button, which always makes a new session. `open()` checks for a
   running session, prepares on a background thread, then starts on the main thread; nothing covers
   an open still in flight. A re-created panel (a new back-stack entry forgets `attemptedFirstOpen`)
   or a second request during preparation starts a second shell — the likely cause of "Open Terminal
   once produced two sessions" (Todo, 2026-09-24, not reproduced).
2. **Stale alerts.** A bell or "npm finished" alert is cleared only when the user looks at its
   session. When the session is closed or its shell exits, the alert stays; tapping it now says
   "That terminal session has ended".
3. **Unchecked `libtermux.so`.** Ours (16 KB aligned, built from the vendored `termux.c`) ships only
   because `pickFirsts` meets it before Termux's 4 KB prebuilt; nothing fails if that order changes,
   and lint warns three times about the prebuilt that is not shipped.
4. **Over budget.** `RuntimeForegroundService` 354 and `TerminalSessionManager` 341 lines (allowlisted
   in `tools/backend-debt-allowlist.txt`); each holds a second concern that is its own lifecycle.
5. **Gateway still owns terminal glue** (~70 lines): observing the snapshot and endings, applying
   settings, the five session intents, the workspace-terminal hand-off and the refresh trigger.
6. **Docs.** Three plans still say "plan" while recording finished work; Todo #0 is open.

## 4. Steps

Each step: debug and release compile, all unit tests, lint, UI guard, backend guard; a device check
of its own lifecycle stage; one commit.

### S6.1 — Move the gateway's terminal cluster into `TerminalCoordinator` (behaviour unchanged)

Open, ensure-free open, show (with the workspace hand-off through a callback), select, reorder,
close, applying settings, reporting endings, and the "command finished in the open workspace"
trigger. The gateway keeps only routing and the terminal-session source for the view. A narrow
`TerminalSessions` seam over the manager lets the coordinator run in JVM tests with a fake.

### S6.2 — Split the two over-budget owners along their seams (behaviour unchanged)

- `TerminalLaunch`: everything done before a session starts — shell path checked inside the rootfs,
  prompt installed, supplementary groups, PRoot argv and environment. Pure enough to test the shell
  checks on the JVM. `TerminalSessionManager` keeps the sessions.
- `TerminalNotifications`: the channel, the group parent, one row per session and its text, the
  pending intents. `RuntimeForegroundService` keeps start/stop, the observation of sessions and
  install state, sampling, and the wake lock.
- Both allowlist entries leave `tools/backend-debt-allowlist.txt`.

### S6.3 — One request, one session

- New `EnsureTerminalSession` for the panel's automatic first open: reuse a running session of the
  workspace, or the open already in flight for it; never start a second. "+" keeps
  `OpenTerminalSession` (always new, which is what the user asked for).
- The coordinator shares one in-flight open per workspace, so concurrent requests from the panel and
  "Open Terminal" meet in one session. JVM tests with a fake that holds the open in flight.

### S6.4 — Alerts follow their session

A bell or finish alert is removed when its session is closed or its shell exits, as the session's
notification row already is. Tested at the seam that decides what to cancel.

### S6.5 — `libtermux.so` checked, not assumed

A build step after native-library merging reads the packaged `libtermux.so`'s ELF program headers
and fails the build unless every `PT_LOAD` segment is 16 KB aligned. With that guarantee in place,
lint's `Aligned16KB` for Termux's unshipped prebuilt is ignored by path in `lint.xml`, with the
reason (lint 14 → 11). Proven by pointing the check at the prebuilt once: the build must fail.

### S6.6 — Device acceptance on the current install

Through the real app and terminal, the owner's phone idle, nothing written outside throwaway
folders: `vim` and `nano` (edit, save, quit), `git log` with its pager, a multi-line paste,
rotation both ways (the user's rotation setting restored after), each cursor style, the Light
palette, notification rows and Exit, per-session wake lock, a bell alert and a finish alert (and
S6.4 removing them), a force stop with a session running (Notifications page), and the S6.3 race
(panel open + "Open Terminal" together → one session). Pinch zoom cannot be driven by adb: the
owner tries it.

### S6.7 — Records

`terminal-maturity-plan.md` and `terminal-background-plan.md` marked done with a pointer to this
record; `sudo-capability-plan.md` marked superseded by passthrough `sudo`; Todo #0 closed; the
S6 record in `code-separation-plan.md` §7.

## 5. Decisions for the owner

Carried over from the older plans; each is a behaviour or distribution choice, not a bug. The
recommendation is what S6 does if the owner does not choose otherwise.

| # | Decision | Options | Recommendation |
|---|---|---|---|
| 1 | `/proc` entries Android denies (`stat`, `loadavg`, `uptime`, …); `htop` stops at "Cannot open /proc/stat" | static stand-ins as proot-distro binds (shows invented numbers) / keep the honest error | **keep the honest error** for now; revisit with real values if a tool the owner needs depends on it |
| 2 | Link-emulation bookkeeping (`.l2s.*`) sits beside the files it serves; `rm -rf` of a tree holding an emulated hard link fails "Directory not empty" | `PROOT_L2S_DIR` (one directory, as proot-distro), with our patch 0001 falling back to the real `link()` for the workspace's refusal / leave as is | **do it, as its own step after S6** — a PRoot C patch with an A/B on the device, too large to share S6's commits |
| 3 | Pinch-zoom level is per screen and resets on reopen | persist it / keep (the font-size setting persists) | **keep** |
| 4 | Fresh-install acceptance (Todo #0) wipes the installed runtime, the guest home and its SSH key if run on this debug app | skip / a second debug-signed variant with its own package name installed beside it / uninstall this one | **a second debug-signed variant** (one build-type block, never shipped), so the matrix runs on a truly empty app while the owner's runtime stays |
| 5 | Google Play would refuse the direct battery-optimisation prompt for a terminal | keep the direct prompt (builds outside Play, as Termux) / the list page for a Play build | **keep**; revisit with the distribution decision (About/licences, Todo #4) |

## 6. Done when

- The gateway has no terminal logic beyond routing; no terminal file is over 300 lines; both
  allowlist entries are gone.
- One request makes one session, however the requests race; alerts leave with their session.
- The build fails if a 4 KB `libtermux.so` would ship; lint 11 warnings.
- The §4 S6.6 matrix is recorded, with what only the owner can try.
- The three older plans are closed; Todo #0 is closed.
