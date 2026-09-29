# Long operations — one task model, dialog, background and notification — plan

Status: **plan, for the owner's review** (2026-09-29). Owner's request, after deleting a large
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
`walkFileTree`, no count, no progress, no cancel), routed inside `AndroidWorkbenchGateway`'s
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

## 6. Record

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
