# Long operations — one task model, dialog, background and notification — plan

Status: T1–T2 done (2026-09-29); **§6, the background foundation, replaces T3–T4** (2026-09-30). Owner's request, after deleting a large
folder: "the delete functionality lacks the proper real-time dialog and background notification
process as the ref app [MT Manager]; make sure the design [is right] not just for this
functionality — this kind of aspect needs better design". It is the long-running half of item L
("one loading behaviour for the whole app, backend and frontend aligned",
[code-separation-plan.md](./code-separation-plan.md) §8); L's short actions are measured apart.
Follows [backend-split-rules.md](./backend-split-rules.md).

## 1. Measured (code, 2026-09-29)

| Operation | What the user sees today | Serialised by | Kept alive in the background |
|---|---|---|---|
| **Delete** (Explorer) | a confirmation; on Delete it closes and **nothing shows** until the whole walk ends — a message only if it failed | the gateway's mutation lock: open, save, Git, create… all wait behind it | **no** — Android may stop it midway; a partial folder is left |
| **Copy** (Explorer paste) | nothing until done | mutation lock | no |
| Move, rename | nothing (a rename on one volume: instant) | mutation lock | no |
| Clear cache (Storage) | nothing until done | mutation lock | no |
| Package install (git, openssh-client, ripgrep, first use) | the asking feature's own busy state | `dpkg` mutex, own job | no |
| Git clone, fetch, pull, push | a progress screen, cancellable; a notice if the process died | own job, one at a time | no (a ledger reports a transfer cut off) |
| Runtime install, repair, reset | a full progress screen | own flow | **yes** — `RuntimeForegroundService` watches `RuntimeWork.installing` |
| Terminal sessions | the terminal; a notification group | — | **yes** — the same service |

Evidence: `WorkspaceItemsCoordinator.delete` → `WorkspaceFiles.delete` (a recursive
`walkFileTree`, no count, no progress, no cancel), routed inside `AndroidICodeGateway`'s
locked `when`; `DeleteWorkspaceItemDialog` dispatches and dismisses; `RuntimeForegroundService`
is idle when there are no sessions and no install (`idle = sessions.isEmpty() && !installing`).

So one operation (runtime install) already has the whole shape — progress, background, service —
and each of the others has a part of it or none. Nothing is shared.

## 2. The reference (MT Manager, on the owner's phone)

- Measured (its notification channels, read-only): **"Task Running Notification"** (default
  importance), **"Task Progress Notification"** (minimum importance — silent, no sound or
  pop-up), **"Task Error Notification"** (high importance — pops up). File work runs as tasks of
  one service, with progress kept quiet and failures loud.
- Measured (the owner asked for `node_modules` in RevPlayer to be deleted with it, 2026-09-29,
  recorded frame by frame, ~30 MB in ~5 s): a confirmation "Delete — Do you want to delete
  node_modules? — CANCEL / OK"; then a dialog **"Deleting…"** with **HIDE** at its top right,
  the item being deleted as a path from the chosen folder on one line, cut off at its end
  (`node_modules/is-callable/.editorconfig`), and a determinate progress bar. Its first frame
  shows "…" and an empty bar — it counts before it deletes. **No Cancel.** When it ends the
  dialog closes and the list refreshes without the folder.

## 3. Design — one path for every long operation

1. **One model** (`domain`): a `WorkTask` — id, kind, title ("Deleting node_modules"), the
   current item, done and total (items and bytes when known), cancellable, and its state:
   Running, Succeeded (a one-line summary), Failed (the reason and what is left), Cancelled
   (what was done before). The snapshot carries the list; intents: cancel a task, show it,
   send it to the background.
2. **One runner** (`data/tasks`): starts a task on its own job, **outside** the mutation lock —
   a long walk no longer holds up open, save or Git; it takes the lock only to apply the result
   (tree, tabs, recents). A task claims its paths: another change to them waits with a reason
   ("node_modules is being deleted"). Progress is published at most a few times a second.
   Cancellation is checked between items; nothing is claimed about work not done.
3. **One service**: `RuntimeForegroundService` already exists to keep the user's work alive; it
   watches the runner as it watches the runtime install, so a task continues with iCode in the
   background, and stops itself when nothing runs. Notifications mirror the reference's three
   kinds: running (group), progress (silent, with a progress bar and Cancel), failed (pops up).
4. **One dialog** (`designsystem`): title, progress bar (determinate when the total is known),
   the current item, counts and size, **Background** and **Cancel**. Background closes it and the
   notification carries the task; a small running-tasks indicator reopens it. The result is said
   once: a short message on success, the dialog's own failure state otherwise.
5. **Every long operation uses it**: delete, copy, move across folders, clear cache, package
   installs; Git transfers and the runtime install keep their screens, and become tasks for the
   service and the indicator.

Delete itself: count first (bounded), then delete bottom-up, reporting as it goes; tabs of
deleted files close; a cancelled delete says how much is gone — deletion cannot be undone.

## 4. Steps (each: gate, commit; device where it touches a screen)

| Step | What |
|---|---|
| T1 | `WorkTask` + task runner off the lock with path claims, progress and cancel; delete and copy on it; JVM tests on a temporary tree (progress counts, cancel midway, claim conflict, failure keeps what is left) |
| T2 | The task dialog, Background, the running-tasks indicator; Explorer delete and paste use them |
| T3 | The service keeps tasks alive; the three notification kinds |
| T4 | Clear cache and package installs as tasks; Git transfers and runtime install listed as tasks |
| T5 | Device: a large scratch folder (created for the test, in the scratch workspace) deleted and copied — progress, cancel, background, notification, iCode sent away and back |

## 5. Decisions (2026-09-29), from the reference — the owner may overrule

- The dialog is the reference's: title, current item, progress bar, **Hide**. Delete offers no
  Cancel, as the reference: a delete stopped midway leaves a half folder, worse than finishing.
  Copy offers Cancel — stopping it loses nothing but the partial copy, which is removed.
- On success nothing new is posted: the dialog (or the progress notification) ends and the list
  refreshes; a failure is said in the dialog, or by the pop-up kind when hidden — the
  reference's three channels have no "done" kind.

## 6. Background — the foundation (owner, 2026-09-30)

The owner's request, with MT Manager's notifications as the reference ("1 task is running";
"Extracting…(1207/11813)" with the item in hand and a bar; "Deleting…"): a hidden dialog goes to
the background as the reference does — carried by a notification, not by a row inside iCode —
on a foundation with no gaps in how the features are wired.

### 6.1 Measured (code, 2026-09-30)

| Gap | Where |
|---|---|
| **The backend belongs to one screen.** `AndroidICodeViewModel` builds its own `AndroidICodeGateway` and `onCleared` calls `close()`, which cancels the gateway's scope. Leaving iCode with Back, or swiping it from Recents while work runs, cancels a running delete (half a folder left), an import, a clone or a sync, and the runtime install | `AndroidICodeViewModel`, `AndroidICodeGateway.close` |
| **Nothing keeps the process for a task.** `RuntimeForegroundService` runs while a terminal session runs or the runtime installs (`RuntimeWork.installing`), nothing else: a task, a Git transfer or an apt install in the background dies with the process Android reclaims | `RuntimeForegroundService.idle` |
| **A hidden task has no notification.** It shows as a row in the Explorer and the launcher (`BackgroundTasks`) — inside iCode only | `TaskSheets.kt` |
| **Two keep-alive signals.** Sessions, and `RuntimeWork` for the install alone | `RuntimeWork.kt` |
| **A failure while away is silent** until iCode is opened again | — |

### 6.2 Design

1. **One backend per process.** `AndroidICodeGateway.attach(context)` returns the process's
   one gateway, as `TerminalSessionManager.get` does: work belongs to the process, screens attach
   and detach. When the last screen detaches, only what serves a screen stops — the folder
   watcher and language servers. Every long operation keeps running on the gateway's scope.
2. **One list of background work, derived** (`domain/BackgroundWork.kt`, pure, tested):
   `BackgroundWorkRules.of(snapshot, installingPackage)` names everything running — each task, the
   Git transfer, the runtime install, a runtime package install — with a title, the item or step
   in hand, its counts or share, whether it may be cancelled (the intent Cancel sends), where a
   tap leads (the task's dialog, the clone or sync progress page, the runtime install page), and,
   once it stops short, why. The gateway publishes it (`data/tasks/BackgroundWork`); it is the only
   thing the service watches besides terminal sessions. `RuntimeWork` goes.
3. **The service keeps the process while anything runs**, and shows the reference's three kinds:
   - its own notification: "iCode — 1 task running", with sessions ("2 sessions · 1 task running");
   - per item, a silent progress notification (its own channel, minimum importance, as the
     reference's): "Deleting… (1207/11813)", the item in hand, a bar (sweeping while counting),
     **Cancel** where the work allows it; a tap opens iCode on that work;
   - a failure that pops up (its own channel, high importance) when the user was not watching:
     the task was hidden, or iCode was not on screen. A tap opens iCode on it.
   Progress notifications are posted at most twice a second: Android drops faster updates.
4. **Hide = the notification carries it.** No row inside iCode. A hidden task comes back into
   view when it fails, when its notification is tapped, or when the user runs into it — acting on
   a path it holds shows its dialog with the reason.
5. **The dialog**: title with CANCEL (where allowed) and HIDE; the item in hand on one line; the
   bar; the counts and share under it ("1,207 / 11,813", "10%"), so the dialog says what the
   notification says.

On success nothing new is posted (§5): the dialog or notification ends and the list refreshes.

### 6.3 Device checks (T5)

| # | Check | Expected |
|---|---|---|
| B1 | Delete a folder of ~10,000 entries; HIDE; Home | "iCode — 1 task running"; "Deleting… (n/N)" with the item and a bar, updating; it finishes; the folder is gone |
| B2 | As B1, then swipe iCode from Recents | The delete continues and finishes; reopening iCode shows the Explorer without the folder |
| B3 | Import a large folder; HIDE; tap its notification | iCode opens with the Import dialog; Cancel in the notification stops it and leaves nothing |
| B4 | Clone a repository; leave iCode | "Cloning…" with Git's phase and share; a tap opens Clone progress |
| B5 | A failing task while iCode is away (delete a folder another app holds read-only) | A pop-up names the failure; a tap shows it in iCode |
| B6 | Notifications refused | Work continues; a hidden task comes back when it fails or when the user acts on its folder |

## 7. Record

- **T1** (2026-09-29): `WorkTask` (domain) and the task intents — Hide, Show, Cancel, Dismiss —
  which the compiler made the router take (the locked `when` must cover every intent);
  `TaskRunner` (`data/tasks`) runs each task on its own job outside the mutation lock, claims its
  paths (a folder around them or inside them is refused, "Deleting node_modules — wait for it to
  finish"), publishes progress at most every 150 ms, applies the result under the lock, keeps a
  failure (with the error's words) until dismissed and brings a hidden one back to say it.
  `WorkspaceFiles` counts, and its delete and copy report each entry as a path from the folder
  the item is in; a copy that stops removes what it made. Explorer delete (no Cancel) and paste
  (Cancel) run as tasks; rename and move wait on a claim. The gateway stays within its budget
  (the clone-form branch on one line; two constructors' plain arguments on one line, as the
  rest). 8 tests. Gate: 474 tests; lint 0 errors / 11 warnings; both guards pass. The dialog
  (T2) is next: until it lands, a delete runs with no dialog, as before, but no longer blocks
  the workspace.
- **T2** (2026-09-29): `IcodeProgressSheet` (design system): the title with HIDE at its right
  (and CANCEL where the task allows it), the item in hand on one line, cut at its end, and a bar
  that sweeps while counting (a bar frozen at zero reads as a hang — the app's rule, where the
  reference shows an empty bar) and fills after; a tap outside does nothing, Back hides.
  `IcodeProgressFailedSheet`: why it stopped, and OK. `TaskSheets` (ui) draws the oldest
  visible task over any screen; `BackgroundTasks` shows each hidden one in the Explorer as a thin
  row with its share, and a tap brings its dialog back. On the phone (fresh APK; a scratch
  folder of 12,061 entries in the scratch workspace): the dialog within 0.8 s of Delete, "…"
  while counting, then `delete-test/pkg-5/f151.js`…; HIDE → "Deleting delete-test 28%" in the
  Explorer; a tap → the dialog again; the delete finished with iCode out of sight. It deletes
  ~80 entries a second — the storage's own pace (1,000 deletes by the shell: 6.1 s on shared
  storage, 0.04 s in app storage). Not checked: opening a file during the delete (the owner took
  the phone). Gate: 474 tests; lint 0 errors / 11 warnings; both guards pass.
- **§6, the background foundation** (2026-09-30), replacing T3–T4. The backend is one per
  process (`ICodeProcess`: screens attach and detach; the last to leave stops the watcher and
  language servers only), so a delete, an import, a clone or the runtime install goes on when
  iCode is left with Back or swiped from Recents. `BackgroundWorkRules` (domain, pure) names all
  background work from the snapshot and the package installer's `installing`; the gateway
  publishes it (`BackgroundWork`), and it is the one signal `RuntimeForegroundService` watches
  besides sessions — `RuntimeWork` is gone. `WorkNotifications`: per item a silent progress
  notification (minimum importance: "Deleting… (1207 / 11813)", the item, a bar, Cancel where
  allowed; a tap opens iCode on the work — the task's dialog, Clone or Sync progress, the runtime
  install) and a failure that pops up (high importance) when the user was not watching; posted at
  most twice a second; a failure read, dismissed or run again leaves the drawer. The in-app
  background rows are gone; a hidden task comes back when it fails, when its notification is
  tapped, or when the user acts on a path it holds (`TaskRunner.waitReason`, one message for
  Explorer and workspace actions). The dialog shows counts and share. Found and fixed on the way:
  a task appeared before its job was registered, so a Cancel in that instant was lost, and a job
  cancelled before its body ran never freed its paths — the job is now registered first and
  settled on completion, however it ends (tested 50 times in a row). Transfer labels have one
  source (`GitTransferLabel`); notification arrivals one model (`Arrival`). Gate here: `data/` and
  `domain/` type-checked, 429 tests (the one failure is `TreeSizeTest`, which fails only as root),
  both guards. Not compiled here: the screens, the service and the manifest — the owner's build
  is their first compile; then B1–B6.
