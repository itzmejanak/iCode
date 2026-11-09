# One loading behaviour — plan and as built

Status: B0–B4 done in code (2026-11-09); the phone checks in §7 are the owner's. Owner's request
(Todo.md #12, item L of [code-separation-plan.md](./code-separation-plan.md) §8): "one loading
behavior for whole app (make sure state, skeleton loading, performance, stability, accuracy, 0
duplicacy); actions buttons and areas should not feel fire and forget type; proper state management
and feels intended from the user's perspective". Follows [backend-split-rules.md](./backend-split-rules.md).
The long-running half of item L — tasks with progress, background and notification — is
[long-operations-plan.md](./long-operations-plan.md); this plan is the short actions and the content
that is not read yet.

## 1. Measured (code, 2026-11-09)

How a tap reached the screen before this plan:

- **The gateway's locked actions** (open a workspace or a file, save, stage, commit, branch, discard,
  abort, add a remote, resolve a conflict, Git identity and token, runtime repair and reset, clear
  cache, open or close a terminal, close a tab) were `scope.launch { mutations.withLock { … } }`.
  Nothing was published until the work had the lock and finished. Behind the last session's restore
  at launch, a refresh or a runtime install, a tap showed nothing at all; a second tap ran it twice.
- **Language services** (start, stop, restart, save, remove, install) and the Run tab's ▶ were
  launched and forgotten: the button gave no sign until the service's or the process's own state
  changed.
- **Startup**: the Workspaces list was empty until the library's first read, and said "No
  workspaces yet" meanwhile; the first read also waited on the staging sweep.
- **Questions answered** (discard changes, restore or discard a draft, overwrite a newer file, a file
  changed on disk, clear cache) closed at the tap; what the answer started then ran unseen.
- **Pages opened before their content** (Diff, Conflict) showed the last file's content, or "No file
  selected" / "This file is no longer conflicted", until the new one was read.
- **Settings pages** drew their controls with default values before the settings were read; a tap
  then was dropped by `SettingSaver` with no sign (a font size step computed from the default would
  otherwise have replaced a value never shown).

The duplication ledger — one meaning, written several ways:

| Meaning | Ways it was written |
|---|---|
| "this button is working" | a label swap ("Cloning…", "Testing…", "Creating…", "Changing…", "Cancelling…", "Generating…"); disabled with no sign |
| "a spinner" | the design system's, and a private `CircularProgressIndicator` in four screens (Source Control, the phone bubble, model and report sheets) |
| "waiting, with words" | text alone in eight places ("Loading settings…" ×3, "Loading workspace settings…", "Reading…", "Reading what is installed…", "Reading the licence…", "Searching this workspace…"); a spinner beside text, built in six |
| "a value not read yet" | "Checking…" written in two screens |
| "progress with no share yet" | `IndeterminateProgressTrack` beside `ProgressTrack(0f)`, each caller choosing; a clone with no percentage froze its bar at 0 |
| "a sheet waits for its answer" | `PendingRequest` (create, rename) disabled the button with no sign; the other sheets did not wait |
| "search is not ready" | its wording written in Find and again in the assistant's attach sheet |

## 2. Fire-and-forget, classified

Every dispatch site was sorted into one of these; the third column is what it does now.

| Kind | Examples | Now |
|---|---|---|
| Instant: the state changes in the same frame | select a tab, toggle a folder, choose a provider, a search query | unchanged |
| Short action behind the lock | open, save, stage, commit, branch, discard, close a tab | busy from the tap (§4) |
| Short action off the lock | language service changes, Run ▶ | busy from the tap until done (§4.1) |
| Long work with its own state | Git transfers, runtime install, file tasks, apt, MCP servers, the assistant, voice | its own state, already published at the tap (§4.3) |
| A question's answer | discard, draft, overwrite, external change | held open, busy, until carried out (§4.2) |
| Content not read yet | Workspaces, Diff, Conflict, settings, properties, licences | skeleton or a wait with words (§5) |

## 3. Rejected

- **A flag per coordinator** (`opening`, `committing`, `saving`…): the duplication the request
  forbids, and each would be set at a different moment.
- **Pending state in the UI only**: a screen left and opened again forgets it, and it cannot know
  when the backend took the work. `PendingRequest` stays where it was right — the create and rename
  sheets, whose failure text comes back by request id — and now shows busy.
- **A global overlay spinner**: it says nothing about what is working, and blocks what is not.
- **Label swaps** ("Cloning…"): the button changes width and its words, and a screen reader hears a
  new button. The label stays; the state is said as "Working".

## 4. The design: one busy, published at the tap

`ICodeSnapshot.busy: Set<Busy>` (domain, `Busy.kt`): what the user asked that runs now, as
`Busy(action, subject)` — `OpenWorkspace` of a path, `Stage` of a file (or `BusyRules.ALL`),
`CloseTab` of a terminal id or a document location. `BusyRules.of(intent)` is the one mapping, pure,
in the domain; an intent it does not name makes no busy. Screens read it with
`isBusy(action, subject?)`, `busySubjects(action)` and `isOpening(location)`.

### 4.1 One owner: `ActionTracker`

`data/workspace/ActionTracker.kt` is the only writer of `busy`. On the dispatching thread, before the
work is launched — so before it waits on the mutation lock, the disk or the runtime — it publishes
the busy; it takes it away in `finally`, however the work ends. A repeat while one runs is refused
there (`Repeat.Drop`), so nothing runs twice; `SaveDocument` and `OpenFile` queue instead
(`Repeat.Queue`): a newer text, another line of the same file. An automatic save makes no busy.

The gateway routes through it: every locked intent (`actions.launch(intent) { withLock { … } }`),
language changes (`actions.route` — instant ones such as Select stay synchronous, so a page's
selection lands before the page), and Run ▶. `LanguageServices.perform` and
`RunCoordinator.startRun` are suspend, so busy lasts until the service or the process has started.

At launch the library is read before the staging sweep, and `workspacesLoaded` says it was.

### 4.2 The vocabulary (design system)

`designsystem/IcodeLoading.kt`, used everywhere, never re-made:

- **Buttons** — `PrimaryButton`, `SecondaryButton`, `IcodeTextButton`, `DangerButton` and
  `IcodeIconButton` take `busy`: disabled from the tap, label and colour kept (dimmed), the spinner
  after `BUSY_SHOW_DELAY_MILLIS` (150 ms — most actions end before, and a one-frame flash reads as a
  glitch), said as "Working" (`busySemantics`).
- **Rows and choices** — the spinner in the icon's place: a workspace row (in place of ⋮), an
  Explorer or Files row while its file or workspace opens, a rail entry, a tab's × while it closes,
  the bottom bar's branch while it changes, a section header's action (Stage All).
  `BusyIndicator` for a row's trailing place (a language service acting).
- **A wait with words** — `IcodeLoadingLine(text)`: the spinner beside what is happening, a polite
  live region.
- **Long work with a measure** — `ProgressTrack(progress: Float?)`: null sweeps; the one track.
  `GitOperationState.fraction` is null while Git has given no percentage.
- **A question held open** — `IcodeConfirmSheet(confirmBusy)`: its button busy, the sheet not
  dismissed (Cancel would not stop it). `HeldAnswer` (ui) sends the answer with
  `ICodeGateway.dispatchAndWait`, which returns once the work it started has ended — the busy is
  published before `dispatch` returns, so the wait cannot miss it — then closes the question.
  The discard question is held by the backend: `pendingDiscard` is cleared when the discard ends.

What a button says while busy is its own label: "Clone Repository" stays, its spinner beside it. A
button whose work shows its own progress keeps that progress as its label ("Installing SSH client ·
Setting up 3 of 6") and is busy as well. Language Restart and Stop on a running server are only
disabled: the state turns to Stopping at once, and a spinner beside it would say less.

### 4.3 What keeps its own state

These already publish at the tap and keep their own model, which says more than "working":
Git transfers (`gitOperation`, the progress screens), the runtime (`runtimeInstallReason`, its
screen), file operations (tasks, `long-operations-plan.md`), apt (`runtimeTools`), MCP servers
(`McpStatus.Starting` — Restart shows it busy), the assistant's turn, voice, Git auth work
(`GitAuthWork` — Test, Generate, Create Key, Change Passphrase show it busy).

## 5. Content not read yet

Never an empty state that reads as "there is none", nor the last content as if it were the new:

- **A list** — `IcodeSkeletonRows(count)`: its rows drawn as they will be, pulsing, said once
  ("Loading workspaces"). The Workspaces list until `workspacesLoaded`.
- **A page** — `IcodeLoadingLine`: Diff ("Reading the changes…") and Conflict ("Reading the
  conflicts…") while their `OpenDiff` / `OpenConflict` busy runs; Properties; the runtime tools'
  plan and licences; Find while it searches or its engine installs; settings pages.
- **A value** — `CHECKING` ("Checking…") in its row's place: runtime version, runtime details,
  update check.
- **Settings** — `settingsShown(preferences, saver)`: the wait, or why they could not be read, in
  place of the controls; the controls only once the values are the user's. Storage's Show Hidden
  Files shows its spinner until then.

## 6. Cycles, as built

- **B0 — the model.** `Busy`, `BusyRules`, `ActionTracker`, `ICodeSnapshot.busy` and
  `workspacesLoaded`; the gateway routes through the tracker; language changes and Run ▶ suspend
  to their end. Tests: `ActionTrackerTest` (busy from the dispatch until the end, however it ends;
  repeats refused then accepted again; saves queued; instant intents unchanged; a held answer
  waits for its work, one with none does not).
- **B1 — the vocabulary.** `IcodeLoading.kt`; `busy` on every button kind and the rail, section
  header, bottom-bar choice and tab strip; `ProgressTrack(Float?)`; `IcodeConfirmSheet(confirmBusy)`.
- **B2 — the actions.** Launcher rows and skeleton; Explorer and Files rows; editor Save and tab
  close; terminal + and tab close; Run ▶; Source Control (Refresh, Commit, Commit & Push guarded,
  stage rows, Stage All, Abort, Initialize Git, Check again); branches (bottom bar, Create, Delete);
  Add Remote; conflict choices and Mark as Resolved (staged: its row shows it); Clone and Cancel;
  Git credentials (identity, token, SSH key, signing key, passphrase, Test); language services
  (list rows, Start, Stop, Save, Remove, Install); runtime Repair and Reset; Clear cache; MCP
  Restart.
- **B3 — questions and pages.** Held answers: discard changes, draft recovery (restore, keep
  disk), recovered overwrite, external change (reload, overwrite), new folder, new file, rename;
  the recovery centre's rows. Diff and Conflict read before shown.
- **B4 — one wording.** Every wait with words is `IcodeLoadingLine`; every bare spinner
  `IcodeSpinner`; "Checking…" one constant; search's waiting one wording
  (`SearchEngineState.waiting`); settings' loading and failure one function.

Gate: compile, unit tests (only `TreeSizeTest`'s "a directory that cannot be listed" fails, as on
main: the sandbox runs as root), assembleDebug and assembleRelease, both consistency guards; lint
has only the error already on main (`SpeechProviderParts`, NonObservableLocale).

## 7. Phone checks (owner)

1. Cold start: the Workspaces list shows its skeleton, then the workspaces; a tap on one in the
   first second shows its spinner and opens once.
2. A double tap on Commit, Save, a branch or a workspace runs it once.
3. Discard a large folder: the sheet stays with Discard working, then closes.
4. A file changed outside iCode under unsaved edits: Reload keeps the sheet, working, until done.
5. Diff and Conflict from Source Control never show the previous file.
6. TalkBack on a busy button says "Working".
7. Settings › Terminal on a cold start: the loading line, then controls with the saved values.
