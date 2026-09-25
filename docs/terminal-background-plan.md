# Terminal sessions that outlive the screen

Status: **plan** (2026-09-24). Follows [backend-split-rules.md](./backend-split-rules.md);
builds on [terminal-maturity-plan.md](./terminal-maturity-plan.md).

Goal: closing iCode (Back from the launcher, or swiping it from Recents) must not
end terminal sessions. Reopening shows the same tabs, in the same order, with the
same screens and still-running programs, until the user ends them. While sessions
exist, a notification says so, as Termux's does.

## 1. Evidence

**Reference — Termux on this phone** (`dumpsys notification`, 2026-09-24):

| Field | Value |
|---|---|
| Title / text | `Termux` / `1 session` |
| Channel | `termux_notification_channel` "Termux App", importance LOW (silent) |
| Flags | `0x62` = ongoing + no-clear + foreground service |
| Tap | opens the Termux activity |
| Actions | **Exit**, **Acquire wakelock** |

**iCode today (code):**

- `AndroidWorkbenchViewModel.onCleared()` → `gateway.close()` →
  `terminals.closeAll()`, `RuntimeForegroundService.stop()`, `scope.cancel()`
  (`AndroidWorkbenchGateway.kt` 530–535). The ViewModel is cleared when the
  activity finishes — Back from the root, or removal from Recents. **iCode ends
  the sessions itself.**
- `TerminalSessionManager` is constructed by the gateway, so it lives exactly as
  long as the screen's ViewModel. There is no `Application` class.
- `RuntimeForegroundService` owns nothing; the gateway starts and stops it from
  two places (terminal count, runtime install) and re-starts it on every change
  of the session list to update its text.

**Device (Infinix X6881, Android 14 / API 34, XOS; app targets API 36):**

- `POST_NOTIFICATIONS: granted=false`, and nothing in the app requests it. So
  the service's notification has never been visible in the drawer.
- Neither iCode nor Termux is exempt from battery optimisation.

**Platform (developer.android.com, read 2026-09-24):**

- Without `POST_NOTIFICATIONS` a foreground service still runs; its notice shows
  only in the Task Manager, not the drawer. Request the permission from a user
  action.
- Android 14: users can swipe away an `ongoing` foreground notification (not on
  the lock screen, not via "Clear all"); the service keeps running.
- Task Manager **Stop** removes the whole process with no callback; the next
  start sees `ApplicationExitInfo.REASON_USER_REQUESTED`.
- `specialUse` (already declared, with its subtype property): no runtime
  prerequisite, no time limit; the subtype is reviewed by Google Play.
- A service keeps running when its task is removed from Recents unless
  `stopWithTask` is set (not set in our manifest). Whether XOS honours that is
  **not yet measured** — step S0.

## 2. What cannot be preserved, said plainly

A session is a live process. If Android ends iCode's process — Task Manager
Stop, force stop, low memory, an OEM battery manager — the shells end with it
(PRoot takes its programs down: patch 0002 `PTRACE_O_EXITKILL`). Nothing can
bring a killed process back. What iCode owes the user then is the truth on the
next start (S5), not an empty terminal that pretends nothing happened
(architecture.md §540).

## 3. Target design

**Ownership (backend-split-rules §2–4):**

| Fact | Owner | Lifetime |
|---|---|---|
| Terminal sessions (process, screen, order, selection) | `TerminalSessionManager`, created **once per process** by a small holder in `data/runtime` | Process |
| "Keep the process alive and tell the user" | `RuntimeForegroundService`, which **observes** the manager and the install state and stops itself when both are idle | While there is work |
| Notification text and actions | The service, updating its own notification (no restart) | While running |
| Wake lock (opt-in) | The service | Until released, the last session ends, or Exit |
| Screen-level state (open workspace, documents, navigation) | Gateway / ViewModel, unchanged | Screen |

`gateway.close()` stops *observing* terminals; it no longer ends them. Language
servers keep their current behaviour (they stop with the screen) — out of scope.

**Notification (mirrors Termux):** silent LOW channel; title `iCode`; text
`1 session` / `N sessions` (plus the runtime install line while installing);
tap opens iCode on the Terminal panel; actions **Exit** (hang up every session,
then stop) and **Acquire wakelock / Release wakelock**.

**Reopen:** the new screen's gateway adopts the existing manager. The terminal
panel shows the same tabs, order and selection; each `TerminalView` re-attaches
to its live session, so its screen and scrollback are exactly as left and
running programs continue. Tapping the notification opens the workspace of the
selected session on the Terminal panel.

## 4. Lifecycle (backend-split-rules §5)

| Stage | Behaviour |
|---|---|
| Enter | Notification permission asked during onboarding (S4). First session opened by the user → service starts (app is in the foreground, so the start is allowed) |
| Act | New tab / close tab / `exit` / signal: manager updates, service re-posts its text |
| Interrupt: Back or Recents | Sessions keep running; notification stays |
| Interrupt: Task Manager Stop, force stop, low memory | Process ends; next start reports it (S5) |
| Interrupt: screen off | Android may suspend the CPU; the wakelock action exists for long work |
| Recover: reopen | Same tabs, screens, programs; no second shell started |
| End | Last session ends, or Exit → wakelock released, service stops, notification removed |

## 5. Steps (each: build, tests, device check)

| Step | Change | Device verification |
|---|---|---|
| **S0 Baseline** | none | Swipe iCode from Recents and press Back: record processes, notification, what reopening shows; measure whether XOS keeps a process with a foreground service alive after removal from Recents |
| **S1 Sessions outlive the screen** | Process-scoped holder for `TerminalSessionManager`; gateway adopts it; `close()` no longer ends sessions | Run a counter loop, swipe away, reopen: same tabs, order, selection, scrollback; the loop kept counting |
| **S2 Service follows the sessions** | Service observes manager + install; updates its own notification; gateway no longer starts/stops it for terminals | Count text follows tabs; last `exit` removes the notification; no start-from-background errors |
| **S3a Wakelock + freeze** | Wakelock toggle in the service (`WAKE_LOCK` permission), released when idle or on destroy | Counter loop in a backgrounded iCode, lock off vs on: `Hiber` freeze lines, counter progress, `dumpsys power` |
| **S3b One row per session** | **Owner's decisions (2026-09-24):** a notification group — iCode's own notification is the group's parent; one row per running session (tab name, workspace, what it runs) with **Exit** and **Acquire/Release wakelock**; tapping a row always opens that session. No "Exit all" row (the owner chose a per-session wakelock over a control row) | Each button with iCode open and closed; rows appear and disappear with tabs; tap opens that session, cold and warm |
| **S3c Icon** | The notification shows iCode's own mark: the drawer header draws the launcher icon, the status bar the small icon — both must be right | Drawer and status bar on the device |
| **S4 Permissions** | **Owner's decisions (2026-09-24): ask during onboarding, with Android's system dialogs — storage, then notifications, then battery optimisation — one at a time on the same permission screen, which keeps its state.** Terminal Settings shows the state and opens the system page | Fresh install: onboarding shows each system prompt in turn; grant shows the notification; deny still keeps sessions alive (Task Manager) |
| **S5 Honest restart** | Persist "sessions were running"; on the next start after an unexpected end, say so once, with `ApplicationExitInfo`'s reason | Force stop, reinstall and a signal with sessions running; a clean `exit`; Dismiss; no repeat |
| **S6 Acceptance + docs** | Update Todo, architecture notes, this record | Full matrix on a fresh install |

### S4 in detail (backend-split-rules §4 ownership map)

Verified against Android's documentation (2026-09-24): `areNotificationsEnabled()`
is the check for "may post"; without `POST_NOTIFICATIONS` a foreground service's
notice appears only in Task Manager; the fallback page is
`ACTION_APP_NOTIFICATION_SETTINGS`. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
(system dialog, needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is, under Google
Play policy, for listed use cases only — a terminal is not listed (see §7).

- **Facts and owner.** One `SystemAccess` (domain): storage, notifications,
  unrestricted battery — read from Android, never stored — plus which optional
  permissions onboarding has already offered (so each is offered once).
  `AndroidSystemAccess` (data/system) reads them and persists "offered"
  (app-private `SharedPreferences`, as `LanguageServiceStore` does). The
  gateway holds it in the snapshot as `systemAccess` (replacing
  `storageAccessGranted`), re-reads it on start, after every prompt and on every
  resume (a setting changed elsewhere is shown truthfully).
- **Onboarding's steps** (`SystemAccess.onboardingSteps`, pure, tested): storage
  while it is missing (as today), notifications and battery while missing and
  not yet offered. `OnboardingRoute` enters the permission screen when that list
  is not empty.
- **One request path**, generalising today's storage one: intent
  `RequestSystemPermission(p)` → effect → the composition root launches the
  system prompt (all-files page; notification dialog, or its settings page
  below Android 13; battery dialog) → `SystemPermissionAnswered(p)` → the
  gateway re-reads, marks p offered, and emits `SystemPermissionResolved(p,
  granted)`; storage keeps its pending-folder-browser behaviour. "Not now" →
  `DeclineSystemPermission(p)` (marks offered). Terminal Settings →
  `OpenSystemSettings(p)` (notification settings page; battery: the prompt
  while optimised, the system list once exempt). Replaces
  `RequestAllFilesAccess`, `StorageAccessChanged`, `StorageAccessResolved`.
- **Permission screen.** Same layout as today (heading, numbered reasons,
  primary, "Not Now"); the content is the current step's. The steps are fixed
  on entry and the position saved (`rememberSaveable`); it advances on that
  step's `SystemPermissionResolved` (storage: only when granted — otherwise the
  existing "not granted" message) or on "Not Now", and at the end leaves for the
  launcher (empty launcher while storage is off, as today). Event-driven rather
  than snapshot-driven because the static demo reports everything granted and
  must still show the storage frame (screens carry no `staticDemo` branch).
- **Terminal Settings.** A "Background" section: Notifications and Battery
  optimisation, each with its current state and a tap to the system page.
- **Cycles.** (1) domain, adapter, contract, routing, root prompts + tests;
  (2) permission screen; (3) Terminal Settings; (4) device: permissions reset
  over adb to a fresh install's state (runtime kept), walk every branch.

### S5 in detail

Promise (architecture.md §14.4, §20): after Android kills the process, report
terminal processes as stopped, never as resumed. Evidence read 2026-09-24:

- `ApplicationExitInfo` (SDK sources): public `getReason()`; `getSubReason()` is
  hidden. Since Android 14 an update is `REASON_PACKAGE_UPDATED` (16); before,
  `REASON_USER_REQUESTED` (10) covered both "stopped by the user" and "updated".
  `getHistoricalProcessExitReasons(package, pid, n)` finds one process's record.
- This phone's own records: every reinstall → 16; `am force-stop` → 10
  (sub-reason FORCE STOP); a swipe from Recents while no session ran → 10
  (REMOVE TASK, XOS "SwipeUpClean"). `Settings.Global.BOOT_COUNT` = 74.
- `setProcessStateSummary` (≤128 bytes attached to the exit record) cannot hold
  a session list, and a phone restart leaves no record to attach it to; so the
  list is kept by iCode itself.

Design:

- **Record** — `TerminalSessionLedger` (data/runtime, one per process, owned by
  `TerminalSessionManager`): whenever the running sessions change it writes their
  tab names and workspaces, this process's pid and the boot count
  (app-private `SharedPreferences`); no running session → the record is removed.
  A new process takes (reads and removes) the previous record at once, so it is
  reported once and never overwritten unread.
- **Cause** — off the main thread, once per process: a different boot count → the
  phone restarted; otherwise that pid's exit record → a cause (pure mapping,
  tested); no record → "ended without a reason".
- **Show** — the snapshot's `lostTerminalSessions`; the launcher (the first screen
  of every cold start) shows a warning notice: what ended iCode, when, which
  sessions, and **Dismiss** (`DismissLostTerminalSessions`). A normal end — `exit`,
  Exit on the notification, closing tabs — leaves nothing to report.
- **Device** — a running session, then each of: `am force-stop`; reinstall; the
  process killed by its own uid; no session running (nothing shown); Dismiss and
  relaunch (not shown again). A real phone restart is covered by tests only.

## 6. Record

**S0 (2026-09-24, 17:04).** Before: one session (PRoot 8123 → bash 8128);
`RuntimeForegroundService` foreground (id 4711) but its notification hidden by
the missing permission; Recents said "2 apps are active". Swiped iCode from
Recents → 3 s later the **app process was still alive** (PID 31932), while
PRoot and bash were gone and the service was stopped. Reopened from the launcher:
same process, launcher screen, no sessions. Confirms §1: XOS did not end the
sessions; `gateway.close()` did.

**S1 (17:06–17:07).** `TerminalSessionManager` is one per process
(`TerminalSessionManager.get`); the gateway adopts it; `close()` no longer ends
sessions and keeps the service while any runs. Counter loop in tab "bash", second
tab "bash 2", swiped from Recents: 20 s later the activity was gone (0 records),
both shells alive, service foreground. Reopened: same two tabs, same order,
"bash" still selected, no new shell (still PRoot 9748 and 9808), scrollback
intact. **Correction (17:15):** the counter did *not* keep running. The system
log shows XOS froze the whole app, shells included, from 17:06:50 until the
reopen at 17:07:27 (`Hiber/sceneManager: freeze uid 10327 … pids:[7859, 10018,
9814, 9808, 9753, 9748]`); tick 47 at reopen fits a loop that paused ~37 s.
S1 keeps sessions **alive**; keeping them **running** in the background is a
separate problem (below).

**Finding — XOS Hiber freezes background apps despite a foreground service.**
Every time iCode left the screen, `Hiber/sceneManager` froze its uid within
~5 s, service or not; Termux (uid 10305, with its own foreground service) is
frozen the same way (17:10:13). Unfreeze reasons seen: `activity` (reopened),
`removeTask`, `transBinder`, `bumpService`, `wakelock`. Whether holding a
partial wake lock keeps the processes unfrozen is **not yet measured**; it is
the first thing S3 must establish, before any design relies on it.

**S2 (17:10–17:13).** The service observes the sessions and a process-wide
install flag (`RuntimeWork`), updates its own notification, and stops itself when
both are idle; the manager starts it when a session starts; the gateway no longer
starts or stops it. With `POST_NOTIFICATIONS` granted over adb for the test:
"iCode / 1 session" → "+" → "2 sessions" → iCode swiped away (0 activity
records), still "2 sessions" → that tab's `sleep 8; exit` ended it in the
background → "1 session", no start-from-background errors → reopened (no new
shell) → `exit` → notification removed, service stopped, no PRoot left.
Notification flags `0x6a`: ongoing, alert once, no-clear, foreground service.

**S3a (17:17–17:29) — what keeps a backgrounded session running on this XOS
phone.** Wakelock toggle added to the service (`WAKE_LOCK`; released when idle
or on destroy); `dumpsys power` shows `PARTIAL_WAKE_LOCK 'iCode:terminal-sessions'`
only while toggled; the notification reads "1 session (wake lock held)". Test:
a loop printing the time each second, iCode sent Home for 40 s, then the system
log and the gaps in the printed times read.

| Condition | XOS froze iCode? | Loop |
|---|---|---|
| Foreground service only | yes, 5 s after Home | paused 36 s |
| + wakelock held | yes, 5 s after Home | paused ~21 s |
| + battery exemption (adb, then reverted) | yes | — |
| + battery **Unrestricted** (Settings) | yes | paused 36 s |
| + **Lock** in Recents + Unrestricted | yes | paused 38 s |

XOS's `Hiber` classifies apps: its own list (`fast-white`: system providers,
Play Store, Transsion apps), the default keyboard and SMS app (`default-app`),
the launcher (`resume`); every user-installed app — iCode **and Termux** — is
`pre` and is frozen ~5 s after leaving the screen. No app-side API or user
setting found so far changes that. On this phone a session is **kept** (never
killed) and **paused** while iCode is off screen, resuming the moment iCode
returns; Termux behaves identically here. The wakelock and the battery
exemption still matter on Android builds without such a freezer (they are what
stock Android's Doze and standby honour), which this phone cannot verify.

Also found while testing: relaunching with `adb shell monkey` stacked five
`MainActivity` instances in one task (a launcher tap brings the existing one
forward). The notification's "open this session" must reuse the existing
screen — part of S3b.

**S3b (17:36–17:52).** Implemented as decided, then verified on the device:

- Rows: "bash · superpowers · running sleep" / "bash 2 · superpowers · idle",
  the program read from `/proc` (PRoot's `tpgid`; the group leader's `comm`;
  `ProcStat` parser with 4 unit tests), refreshed every 5 s and on every session
  change, re-posted only when the text changes.
- Layout, as shown by XOS: header "iCode"; rows in tab order (fixed row times,
  since XOS ignores sort keys and lists newest first); each row **Exit** and
  **Acquire wakelock**. XOS still prints "now"/"1m" on grouped rows although the
  app asks for no time.
- Two designs were tried and dropped on evidence: a control row titled "iCode"
  appeared as a duplicate child (owner's report); making that row the group's
  parent removes the duplicate but its buttons are then never shown (UI dump of
  the expanded group: rows only).
- Exit on "bash 2" → its row gone, one PRoot left. Wakelock on "bash 2" → row
  "idle · wake lock held" / "Release wakelock", `dumpsys power` shows it; Exit
  on that session → lock released (0 holders), "bash" row unchanged. Exit on
  the last session → no session, no service, no iCode notification.
- Tap a row with iCode open → Terminal panel, that tab, one activity. Tap
  "bash" while "bash 2" was selected and iCode swiped away (0 activities) →
  iCode started, waited out onboarding, opened the Terminal panel on "bash".
- `MainActivity` handles the tap both on creation and in `onNewIntent`; the
  pending intent reuses the existing screen (`CLEAR_TOP | SINGLE_TOP`).
- Checks: 210 unit tests pass; lint 0 errors (new warnings addressed: the
  singleton holds only the application context; the wake lock is released by
  design outside the acquiring method); UI consistency passes.

**S3c (20:10–21:13) — the notification icon was a black square.** Owner's
report: the drawer showed a black square where Termux shows its icon.

- What draws what, measured: a shell notification posted with a stock small
  icon (`cmd notification post -i @android:drawable/ic_dialog_email`) showed the
  Shell app's robot in the drawer header. XOS's drawer header draws the **app's
  launcher icon**; the status bar draws the notification's **small icon**.
- iCode's launcher icon was an adaptive icon whose foreground was an `<inset>`
  around one 1254 px `drawable-nodpi` bitmap. The XOS launcher and Recents drew
  it; XOS's drawer drew only its background layer (`#000000`): the black square.
- Tried and ruled out, each with a new `versionCode` so no cached icon was
  shown: an accent `setColor`; no monochrome layer; a stock small icon; a
  transparent bitmap in the same `<inset>` form. All stayed black.
- Fix: the standard foreground layer — `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png`
  on the 108 dp canvas, made from the owner's transparent logo export aligned
  to the old art. The drawer then showed the logo. Not isolated: whether XOS
  fails on the `<inset>` or on the single oversized bitmap; the standard form
  avoids both.
- Owner asked for the logo a little larger: 1.25× (60 dp art frame); its
  farthest point is 26.8 dp from the centre, inside the 33 dp safe zone, so no
  launcher mask clips it. The launcher icon grows with it (same resource).
- Owner's report: the status-bar glyph did not match the logo. The hand-drawn
  `ic_notification` and `ic_launcher_monochrome` were a right-pointing chevron,
  the logo's mirror image. Both are now traced from the logo's own silhouette
  (potrace), with a cut along the blue/orange seam so the ribbon and the "i"
  stay apart; every path is under lint's 800-character limit. Seen on the
  device next to Termux's `>_`.
- Kept `setColor` (iCode's accent): stock Android's templates use it; XOS
  ignores it. Notification text is drawn by the system in its own font; the
  standard templates cannot use iCode's Chakra Petch or IBM Plex Mono.

**S4 (21:15–21:37) — permissions asked one at a time.** Built as planned above:
`SystemAccess`/`SystemPermission` (domain, 5 tests), `AndroidSystemAccess`
(data/system), the snapshot's `systemAccess`, one request path
(`RequestSystemPermission` → `SystemPrompts` in the composition root →
`SystemPermissionAnswered` → `SystemPermissionResolved`), the stepped
permission screen, Terminal Settings "Background", `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
in the manifest. The storage-only intents and effects are gone.
Device checks, each after resetting permissions over adb to a fresh install's
state (all-files appop `default`, `POST_NOTIFICATIONS` revoked with its user
flags cleared, iCode off the Doze whitelist, "offered" marks deleted; the
runtime kept):

- All three missing → "Step 1 of 3" All Files Access. The owner walked it:
  all-files page (21:30:39) → back, step 2 → Android's notification dialog
  (21:30:43) → Allow, step 3 → Android's battery dialog (21:30:48) → Allow →
  launcher. After: storage allowed, notifications granted (`USER_SET`), iCode
  on the Doze whitelist, both marked offered.
- Gap found there: the launcher listed the workspace as **UNAVAILABLE** after
  storage was granted — a workspace's availability follows storage access, and
  nothing recomputed it (the old storage path had the same gap). Fixed: every
  re-read that finds storage access changed refreshes the recent workspaces.
  Re-checked: storage alone missing → the storage step without a counter →
  granted on Android's page → Back → launcher with the workspace available.
- Notifications and battery missing → "Step 1 of 2" Notifications → Android's
  dialog → **Don't allow** → "Step 2 of 2" Battery Optimization → **Not Now** →
  launcher; both marked offered, permission denied, not exempt. Cold relaunch →
  straight to the launcher: nothing asked again.
- Terminal Settings → Background showed "OFF" and "OPTIMIZED". Notifications row
  → Android's notification page for iCode. The owner then: allowed
  notifications there; Battery row while optimized → Android's battery dialog →
  Allow; Battery row again, now exempt → Android's battery optimization list.
  Back in iCode the rows read "ALLOWED" and "UNRESTRICTED" — the refresh on
  resume, since a settings page reports no result.
- Static demo: the permission frame renders as before (storage content, no step
  counter).
- Not re-measured here: a denied notification permission hiding the service's
  notice (S0 recorded it: service foreground, notice hidden). Not verifiable on
  this Android 14 phone: the settings-page path used below Android 13.
- Checks: 217 unit tests pass; lint 0 errors, 23 warnings (25 before: the
  obsolete `mipmap-anydpi-v26` folder and a `Uri.parse` in the replaced storage
  code are gone); UI consistency passes.

**S5 (21:44–21:54) — an honest restart.** Built as planned above:
`TerminalSessionLedger` (record, take-once, cause off the main thread),
`ProcessEnds` (reason → cause, 5 tests), `LostTerminalSessions` (domain), the
snapshot's `lostTerminalSessions`, `DismissLostTerminalSessions`, and the
launcher's `LostSessionsNotice` (2 tests). Device, each on the installed build:

| Case | Record written | Next start |
|---|---|---|
| Two tabs, `am force-stop` | `bash`, `bash 2` · superpowers, pid, boot 74 | "2 terminal sessions ended — iCode was stopped from Android (Force stop, Task Manager or Recents) at 9:49 PM — bash · superpowers, bash 2 · superpowers" |
| Force stop again, notice not dismissed, no session | (taken: empty) | nothing — reported once |
| One tab, reinstall | `bash` · superpowers | "iCode was updated at 9:50 PM" |
| Dismiss | — | notice gone |
| One tab, SIGKILL from the app's own uid | `bash` · superpowers | Android recorded reason 2 (signal 9) → "iCode's process was killed at 9:51 PM" |
| `exit` in the tab, then force stop | removed on exit | nothing |
| Reinstall with no session | none | nothing |

- Corrected on evidence: the signal kill first read "Android ended iCode", which
  the record does not say (the kill came from the app's own uid). Signals now
  have their own sentence; other reasons say only "iCode's process ended", and a
  missing record says so.
- Found while testing: `adb shell input text` reaches the terminal only once it
  has focus. The first `exit` attempt never arrived, the session still ran, and
  the notice that followed was therefore true; with focus, `exit` removed the
  record.
- Not measured on the device: a real phone restart (boot count differs → "The
  phone restarted"; covered by tests), low memory, crash, ANR.
- `TerminalSessionManager` is 304 lines (ownership review, backend-split-rules
  §4): it still owns only the sessions; the ledger's storage and Android lookup
  live in `TerminalSessionLedger`, and the manager's share is the one call in
  `publish()` that reports the running sessions.
- Checks: 224 unit tests pass; lint 0 errors, 23 warnings (none new); UI
  consistency passes.

## 7. Decisions for the owner

1. Wakelock action — recommended **yes**, opt-in, as Termux does (a foreground
   service does not stop the CPU sleeping with the screen off).
2. Notification tap target — recommended: the selected session's workspace,
   Terminal panel.
3. Pinch zoom level is screen-local today and resets on reopen; the font size
   setting persists. Keep as is, or persist the zoom? Recommended: keep.
4. Battery prompt and Google Play: the owner chose the direct system dialog
   (S4). Google Play allows `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only for its
   listed use cases, which do not include a terminal; a Play build would need
   the policy-safe list page (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`)
   instead. Builds outside Play (as Termux ships) are unaffected.
