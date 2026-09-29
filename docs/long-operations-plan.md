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
- **Pending:** its delete dialog — the frames were missed on 2026-09-29 (the delete had ended);
  a capture loop records the next demonstration. Nothing below copies its dialog until that is
  seen.

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

## 5. For the owner

- Show MT Manager's delete once more (a large, disposable folder) while the capture runs, so its
  dialog is seen before T2's design is fixed.
- Cancel a delete midway — offered, as the reference's Cancel is? (What is gone stays gone.)
- On success in the background: a short "done" notification, or only the ongoing one ending?
