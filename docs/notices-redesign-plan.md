# Notices that say what happened, and where — plan

Status: **proposed**, waiting for the owner's answers (§7). Nothing is built until then.

Owner's request (2026-11-11): deleting a file made a pill blink, but no notification was made, and
tapping the pill showed none. A pull's notification does not say which workspace or project it was
about. Research why, where and when, for every kind of notice, and redesign the whole notification
design and architecture — in iCode and in Android's shade — for accuracy, scalability,
maintainability and clean abstraction. Propose the message design first, for approval.

Follows [notices-plan.md](./notices-plan.md) (the bell, the page, the sheet, the shade), which this
plan supersedes where it says so, and [dock-navigation-plan.md](./dock-navigation-plan.md) D11–D14
(the floating pills).

## 1. Measured (main 3aba804)

### 1.1 One funnel, three outcomes

Every notice but two start-up kinds goes through one call:
`ICodeStateHolder.notify(tone, title, detail)` (`data/workspace/ICodeStateHolder.kt:50`). It emits
`ICodeEffect.Notice` with a new id and the time. `report(subsystem, fallback, error)` and
`failed(title, words)` wrap it as Problems. Then:

| Tone | The bell | The Notifications page | Android's shade |
|---|---|---|---|
| Problem | Rings red, 15 s | Kept (100 at most) | Posted; pops up only while iCode is out of view |
| Heads-up, Info | Rings amber or accent, 10 s | Kept | Posted, silent |
| Done | Rings green, 3 s | **Not kept** (`NoticeRules.kept`, `domain/AppNotice.kt:118`) | **Not posted** |

A notice has a tone, a title, a detail and, for the website's only, a link. **It has no field for
where it came from**: no workspace, repository, branch, file or tool.

### 1.2 Why deleting a file showed no notification

Traced end to end:

1. Move to Trash (`FileOperationsCoordinator.trash`, `data/files/FileOperationsCoordinator.kt:168`)
   runs as a task. On success, `FileHistory.record` publishes the change's line, "Moved notes.md to
   Trash" (`domain/files/FileWords.kt:13`), as `FileNews` (`data/files/FileHistory.kt:66`).
2. **No notice of any tone is raised.** The bell does nothing.
3. `FileNews` wakes the **undo pill** for 10 s (`ui/UndoPill.kt`). That is the pill that blinked.
4. The undo pill's line is said to TalkBack only. On screen it is an icon.
5. **A tap on the undo pill undoes**: it runs `FilesIntent.Undo` at once, so a tap made to see what
   happened puts the file back, and says nothing.
6. Even a Done notice, such as "Saved", cannot be seen again: it is not kept, and the bell's tap
   opens only a kept notice (`ui/NoticeHost.kt`: the one unread, else the page).

Delete for good and Empty Trash record no change at all: nothing is said on success.

### 1.3 What is said today

About 150 places raise a notice (53 Problem, 83 Heads-up, 3 Info, 8 Done, 3 that choose by the
outcome). Each builds its own words where it stands; there is no catalogue.

**Successes that say nothing, or only Done for 3 s:**
- Files: Move to Trash, delete for good, copy, move, rename, new file and folder, duplicate,
  compress, extract, restore, undo, redo — only the undo pill, or nothing.
- Git: clone, stage, branches, discard, init, credentials — nothing. Pull, push and fetch — Done for
  3 s: "Pulling complete", or Git's own "Already up to date." or "Everything up-to-date". Commit —
  Done "Commit created".
- Runtime and tools install, language servers, MCP servers, workspaces, run commands — nothing.

**Failures that are no notice at all:**
- Git clone, pull, push and fetch failures (`GitTransferCoordinator.kt:236`): kept only in
  `gitOperation`, shown on the progress page and in Output. Leave that page, and nothing tells you.
  An automatic fetch's failure, on opening a workspace, shows only in Workspace settings.
- File task failures: the task's sheet, and Android's "Task failures" while iCode is out of view.
- Runtime tools: their page only.

### 1.4 Where: almost never said

- No Git notice names the repository, the branch or the remote. "Git operation failed" covers twelve
  operations (stage, commit, branch, discard…) and never says which.
- No `report()` failure names the file or the workspace it is about.
- Of the 150, 26 name anything: a file (9), a server, tool, session or model (15), a workspace (2).
- Where it happened is often not the open workspace by the end: a pull runs on, and you may have
  opened another.

### 1.5 Wording

- Failures are said six ways: "Unable to …" (in 90 places), "Could not …", "… failed", "Not …",
  "… was not …", "… cannot …".
- Near twins: "Unable to start a terminal session" and "Unable to start terminal"; "Open a workspace
  first", "Open a workspace in iCode first", "Open a workspace before searching".
- Successes mix "Saved", "Commit created", "Pulling complete" and Git's raw lines.
- Details mix a sentence, an exception's message, Git's output, and (MCP) the server's name again.
- Clauses joined by ". ", "; " or ": " at random; jargon such as "dirty tabs".

### 1.6 Android's shade

Five classes post notifications, each with its own channels, ids and builder:

| Poster | Channels |
|---|---|
| `TerminalNotifications` | Development runtime (LOW): the foreground notification and a row per session |
| `TerminalAlerts` | Terminal alerts (DEFAULT): a program rang the bell; a long command finished |
| `WorkNotifications` | Task progress (MIN), Task failures (HIGH), Needs your answer (HIGH) |
| `PhoneForegroundService` | Phone control (LOW) |
| `NoticeNotifications` | Messages (LOW), Problems (HIGH) |

Faults found:
- **Ids are hand-kept ranges** (4711, 5000+n, 6000+n, 7000s–9000s, 10000, 10500). A session numbered
  1000 or more, or a hashed id, lands in another range. Clone and Sync share one number, so they share
  their progress, failure and question notifications.
- **A runtime install failure is posted twice** while iCode is out of view: a Problem notice, and
  "Installing the Linux runtime stopped" on Task failures.
- **A swiped-away Problem comes back.** With no delete intent, it stays unread, and the next change
  to the notices posts it again, popping up again out of view. (Read from the code, not yet seen on a
  phone.)
- No channel groups, no workspace in the header, and no notice's place shown.
- Done is never posted, so a pull that finished while you were away says nothing anywhere.

### 1.7 How others do it

- Android: a channel per kind of notification; once made, its behaviour is the user's, though its
  name and description can still change ([Android: channels](https://developer.android.com/develop/ui/compose/notifications/channels)).
  Channels and categories let the system and the user decide what reaches them
  ([Android: notifications](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications)).
- Material 3: snackbars tell of a process the app performed, briefly, and may offer Undo or Retry
  ([Material: snackbar](https://m3.material.io/components/snackbar/guidelines)).
- VS Code: send few notifications, out of respect for attention
  ([VS Code UX: notifications](https://code.visualstudio.com/api/ux-guidelines/notifications)); each
  carries a "Source" line, which users rely on when several stack up
  ([vscode#136989](https://github.com/microsoft/vscode/issues/136989)).

Content was rephrased for compliance with licensing restrictions.

## 2. Decisions proposed

**D1 — One notice, which knows where it is from.** `domain/notices/Notice.kt` replaces
`AppNotice`'s three kinds with one:

```kotlin
data class Notice(
    val id: String,
    val at: Long,
    val read: Boolean,
    val tone: NoticeTone,            // Problem, HeadsUp, Info, Done: unchanged
    val source: NoticeSource,        // Files, Editor, Git, Terminal, Run, Languages, Search, Assistant,
                                     // Mcp, Phone, Voice, Runtime, Workspaces, Storage, Settings, Website
    val title: String,               // what happened, to what: one line
    val place: NoticePlace? = null,  // where (D2)
    val detail: String? = null,      // why, and what to do: sentences
    val output: String? = null,      // the tool's own words as printed: Git, a server, an exception
    val actions: List<NoticeAction> = emptyList(),  // at most two, the first the main one
    val keep: NoticeKeep,            // D3
    val repeatKey: String? = null,   // the same event again folds into one row (D6)
    val repeats: Int = 1,
)
```

- `NoticeAction` is a closed set, stored by name: open a file, a route, a terminal session, a chat, a
  workspace, a URL, Git's output; Undo; Retry (only while iCode still runs the thing it retries).
- The two start-up kinds (terminal sessions that ended with iCode, a Git transfer that did not
  finish) become ordinary notices from their builders. The website's notices become notices of
  source Website with an open-URL action.
- `NoticeStore` reads the old JSON and writes the new: nothing kept today is lost.

**D2 — Where, captured when the work starts.** `NoticePlace(workspace, workspaceLocation,
repository, branch, remote, subject)`:
- **Workspace**: always, for anything that happened in one — even the open one.
- **Repository**: only when the workspace holds more than one (`gitRepositories`), by its folder.
- **Branch and remote**: for Git, as `main → origin/main`.
- **Subject**: the file or folder (relative to the workspace), the terminal tab, the chat, the
  language server, the MCP server, the model.
- Outside a workspace: "Phone storage · Download", "iCode storage", "Linux runtime", "iCode website".
- Read from the snapshot **when the operation starts** (`NoticePlaces.of(snapshot, location)`) and
  carried with it, so a pull still says the workspace it ran in after you open another.
- Shown as one line, `phone · src`, `phone · main → origin/main`, and in full in the sheet.

**D3 — Three kinds of keeping** (`NoticeKeep`), decided by each builder, not by the tone alone:

| Keep | What | Bell | Page | Badge count | Shade |
|---|---|---|---|---|---|
| **NeedsYou** | Problems and Heads-up | Rings | Kept, 100 at most | Counted while unread | Posted (D7) |
| **Done** | Info, and confirmations of real changes: files, saves, Git, installs | Info rings; a success (tone Done) does not (§7, Q1) | Kept, 150 at most, apart from NeedsYou | Never | Only when finished while you were away (D7) |
| **Moment** | Confirmations of a tap only: "Copied" | Rings: it is the tap's only answer | Not kept | Never | Never |

So a delete is kept and can be found again; "Saved" on every save never pushes a problem off the
page, and never counts.

**D4 — One way to raise a notice; the words in one place per area.**
- **Builders**, in `domain/notices/`, one file per area: `FileNotices`, `EditorNotices`, `GitNotices`,
  `TerminalNotices`, `RunNotices`, `RuntimeNotices`, `LanguageNotices`, `AssistantNotices`,
  `McpNotices`, `PhoneNotices`, `WorkspaceNotices`, `SettingsNotices`, `WebNotices`. Each turns an
  event and its place into a notice draft. They use the words iCode already has — `FileWords`,
  `McpFailures`, `LanguageFailure` — and are tested on the JVM.
- **One sink**, `ICodeStateHolder.tell(notice)`, stamps the id and time and emits it. A failure
  is told with `tell(notice, subsystem, error)`: logged once, credentials masked, the error's words in
  `output`, the builder's sentence in `detail`. (Built on the state holder, which every coordinator
  already has, rather than a new `data/notices/Notices` that each would need passed in; and with no
  separate draft type: a `Notice` without an id is one.)
- `notify`, `report` and `failed` on `ICodeStateHolder` are **removed**, every call site migrated,
  not wrapped. `failureText` stays, for what is shown in state.
- A consistency rule, `notice-text`: a notice's words are written only in `domain/notices/`.

**D5 — The bell and the undo pill.**
- **The bell** rings for every notice but a success (§7, Q1): a file change, a save, a pull done are
  kept on the page and ring nothing. A Moment ("Copied") still rings. Its count is unread NeedsYou
  only. Its colour is the most serious unread; otherwise, while awake, the tone that rang it.
- **A tap on the bell**, while it is awake, opens **the notice that rang it**, when it is kept. At rest:
  the one unread NeedsYou notice, else the page.
- **The undo pill** (Q2, recommended): a tap opens a small card over it, which says what changed and
  where — "Moved notes.md to Trash · phone · src" — with **Undo**, and **Redo** when there is one. It
  never undoes on a tap alone, so a look never changes a file. The same change is kept on the page as
  Done, with Undo while it can still be undone.

**D6 — The page and the sheet.**
- **Filters** over the list: All · Needs you · Done, then one chip per workspace present, and Other.
- **A row**: the tone's icon, the title (two lines), the place line, the time; a dot while unread
  NeedsYou. A repeat within 10 minutes folds into the row before ("Saved notes.md · 3 times").
- **The sheet**: the title; the place in full (Workspace, Repository, Branch, File); when, the kind
  ("Problem", "Heads-up", "Info", "Done") and the source ("Git"); the detail; the output, mono and
  folded, with its own Copy; the actions; Copy and Delete; All Notifications.
- **Clear all** clears what the filter shows.
- When Android's notifications are off for iCode, the page says so in one line, with Turn On.

**D7 — Android's notifications: one registry, one poster.**
- `data/notify/IcodeChannels` makes every channel and its group once, when iCode starts, and deletes
  those no longer used. Channel ids that keep their meaning stay, so the user's choices for them stay
  (Android lets a channel's name and description change, not its behaviour).

| Group | Channel (id) | Importance | Holds | Change |
|---|---|---|---|---|
| Running | Running in the background (`icode.runtime`) | LOW | The foreground notification, a row per terminal session | Renamed |
| Running | Progress (`icode.work-progress`) | MIN | Each long operation's progress | Kept |
| Running | Phone control (`icode.phone-control`) | LOW | While the assistant can use the phone | Kept |
| Needs you | Needs your answer (`icode.work-questions`) | HIGH | The assistant's, MCP's and GnuPG's questions | Kept |
| Needs you | Problems (`icode.problems`) | HIGH | Problems while out of view, **and the work failures** that were Task failures | Takes failures (Q5) |
| Needs you | A program asks for you (`icode.terminal-alerts`) | DEFAULT | A terminal program rang the bell | Renamed; finish alerts leave it |
| Updates | Finished while you were away (`icode.finished`) | DEFAULT (Q4) | A long command, a Git transfer, a clone, a runtime install, an assistant turn, that ran 30 s or more and ended out of view | New |
| Updates | Messages (`icode.messages`) | LOW | NeedsYou notices, silent | Kept |
| — | Task failures (`icode.work-failures`) | — | — | Deleted (Q5) |

- `data/notify/IcodeNotifications` is the one builder all five posters use: icon, colour, channel,
  group, **the workspace as the header's sub-text**, the time, and a tap that opens an `Arrival`
  through one encoding.
- **One key per notification**: a constant id per kind and a tag of the thing's own id (a session,
  a task, a notice, `git:clone`, `git:sync`). The hand-kept ranges and their collisions go.
- Each notice's notification carries its main action as a button where it is an Open.
- **One event, one notification**: a work failure that is also a Problem notice is posted once.
- **A swipe** marks the notification dismissed (a delete intent): it is not posted again, and stays
  unread in iCode.

**D8 — How a notice is written.**
1. **Title** — what happened, to what, in one line of at most about 60 characters, sentence case, no
   final period.
   - Problem: `Couldn't <verb> <object>` (Q3) — "Couldn't pull into main".
   - Heads-up: what to do, first — "Save notes.md first".
   - Done: past tense, with what and how many — "Moved 3 items to Trash", "Pulled 2 commits into main".
   - Info: a plain statement — "notes.md was deleted outside iCode".
2. **Place** — always in the place line (D2), never squeezed into the title unless it is the object.
3. **Detail** — why, and what to do next, in one or two full sentences ending with a period. Never the
   tool's raw text.
4. **Output** — the tool's own words, as printed: Git's lines, a server's message, an exception's.
5. **Names** as they are, without quotes: files, branches, servers. Counts as words do it:
   "1 file", "3 files".
6. **One word per thing**, the screen's own: Source Control, Trash, Workspace settings; "unsaved
   tabs", not "dirty tabs".

**D9 — The catalogue: before and after.** The main events, written to D8:

| Event | Today | After: title | Place | Detail / output | Action | Keep |
|---|---|---|---|---|---|---|
| Move to Trash | Undo pill only | Moved notes.md to Trash | phone · src | — | Undo | Done |
| Delete for good | Nothing | Deleted 3 items for good | Phone storage · Download | — | — | Done |
| Copy, move, rename, new, compress, extract | Undo pill only | Copied 2 items to assets (`FileWords`) | phone · app/src | — | Undo | Done |
| Move to Trash failed | Task sheet; shade if away | Couldn't move notes.md to Trash | phone · src | Why, in a sentence; Android's error as output | Try Again | NeedsYou |
| Save (by hand) | "Saved", 3 s | Saved notes.md | phone · src | — | — | Done, folds |
| Save failed | "Unable to save document" | Couldn't save notes.md | phone · src | The error's words | Save Again | NeedsYou |
| Deleted outside iCode | "notes.md was deleted outside iCode; its text stays open here" | notes.md was deleted outside iCode | phone · src | Its text stays open in the editor. Save it to keep it. | Open | Done (Info) |
| Pull | "Pulling complete" or "Already up to date.", 3 s | Pulled 3 commits into main · or · main is already up to date | phone · main → origin/main | — | Open Source Control | Done |
| Pull failed | Nothing (progress page only) | Couldn't pull into main | phone · main → origin/main | Git's reason in a sentence; Git's lines as output | Open Source Control | NeedsYou |
| Push | "Pushing complete" or "Everything up-to-date" | Pushed 2 commits to origin/main · or · Nothing to push to origin/main | phone · main | — | — | Done |
| Push rejected | Nothing | Couldn't push main to origin | phone · main → origin/main | origin has commits main does not. Pull first. | Pull | NeedsYou |
| Fetch (by hand) | "Fetching complete" | Fetched origin: 4 new commits for main | phone | — | — | Done |
| Fetch on open (failed) | Workspace settings only | Couldn't fetch origin (Q6) | phone | Git's reason | Fetch Again | Done (Info) |
| Clone | Nothing | Cloned superpowers | superpowers | From github.com/acme/superpowers. | Open | Done |
| Clone failed | Clone form only | Couldn't clone superpowers | — | Git's reason; Git's lines as output | Try Again | NeedsYou |
| Commit | "Commit created" | Committed 3 files to main | phone | The message's first line | — | Done |
| Switch branch failed | "Git operation failed" | Couldn't switch to feature-x | phone | Git's reason | — | NeedsYou |
| Transfer cut short | "Pulling did not finish" and a path | Pull into main didn't finish | phone · main → origin/main | iCode closed while Git worked. Pull again: Git goes on from what it has. | Pull | NeedsYou |
| Shell exited with an error | "bash exited with code 1" | bash ended with exit code 1 | phone | — | — | Done (Info) |
| Long command ended away | Shade only | npm finished after 2 min | phone · bash | — | Open | Done |
| Sessions ended with iCode | "2 terminal sessions ended" | 2 terminal sessions ended | phone, superpowers | Why, and the sessions | Open Terminal | NeedsYou |
| Language server crashed | "pylsp stopped" | pylsp stopped | phone · Python | Its last words as output | Restart | NeedsYou |
| Assistant turn failed, chat not shown | "The assistant's turn failed" | The assistant stopped | phone · Fix login bug | The failure | Open Chat | NeedsYou |
| MCP sign-in failed | "github did not answer in time", detail "github" | Couldn't sign in to github | MCP server · github | github did not answer in time. | Try Again | NeedsYou |
| Runtime install failed | "Runtime installation failed" (and a second shade notification) | Couldn't install the Linux runtime | Linux runtime | The reason | Try Again | NeedsYou, once |
| Copied (Android 12 and below) | "Copied" | Copied | — | — | — | Moment |

Counts ("3 commits", "2 commits") are measured: HEAD before and after a pull, what main was ahead by
before a push. Where Git does not say, the title says less, never more.

**D10 — Faults fixed on the way.**
1. A Done notice could never be seen again (§1.2).
2. A tap on the undo pill undid without a word (§1.2; D5).
3. Git transfer and clone failures were never notices (§1.3).
4. "Git operation failed" never said which operation (§1.4).
5. A runtime install failure was posted twice; a swiped Problem came back (§1.6).
6. Notification ids could collide; Clone and Sync shared theirs (§1.6).
7. MCP notices said the server's name twice (§1.5).
8. notices-plan.md still describes the bell's line and ×, a 10 s Done and "iCode · Messages"; it is
   corrected where this plan replaces it.

## 3. Where the code goes

| Layer | File | What |
|---|---|---|
| domain | new `notices/Notice.kt`, `NoticePlace`, `NoticeAction`, `NoticeKeep`, `NoticeSource` | D1–D3 |
| domain | new `notices/*Notices.kt`, one per area | The words (D4, D8, D9) |
| domain | `NoticeRules` (moved to `notices/`) | Keeping, counting, folding, caps, what is posted |
| data | new `notices/Notices.kt`; `NoticeBoard`, `NoticeStore` (v2, reads v1) | The sink and the keeping |
| data | every coordinator that raises a notice | Builders and places in place of `notify` |
| data | new `notify/IcodeChannels.kt`, `IcodeNotifications.kt`; the five posters | D7 |
| ui | `NoticeHost`, `NoticeViews`, `NotificationsScreen`, `UndoPill` | D5, D6 |
| tools | `check-backend-consistency.sh` | The `notice-text` rule |

## 4. Acceptance criteria

[T] is a JVM test, [P] a check on the owner's phone.

- **A1** [T] Each builder gives a source, and a place for anything that happened in a workspace.
- **A2** [T] Move to Trash gives Done "Moved notes.md to Trash", place "phone · src", kept, not
  counted, not posted, with Undo while it can be undone.
- **A3** [T] A pull's success and failure give D9's titles, with workspace, branch and remote, even
  when another workspace was opened meanwhile.
- **A4** [T] The badge counts unread NeedsYou only; Done and NeedsYou are capped apart; repeats fold.
- **A5** [T] A store written by today's build reads back whole.
- **A6** [T] Every notification has a registered channel and a key no other has.
- **A7** [T] No notice's words are written outside `domain/notices/` (the guard).
- **A8** [P] Delete a file: the undo pill's tap shows what changed and where, and changes nothing
  until Undo. The page has it under Done.
- **A9** [P] A pull while away: the shade's header says the workspace; tapping opens its notice.
- **A10** [P] Swipe a Problem away: it does not come back. A runtime install failure away: one
  notification.

## 5. Cycles

| Cycle | What | Exit gate |
|---|---|---|
| **R1** — the model | D1–D4's types, store v2, rules, the sink; no call site yet | A4, A5 |
| **R2** — the surfaces | D5, D6: the bell, the undo pill's card, the page's filters, the sheet | Builds; A8 on the phone |
| **R3** — Android | D7: registry, groups, one builder, keys, delete intent, Finished; the five posters | A6, A10 |
| **R4** — files, editor, workspaces, storage | Their builders and call sites | A1, A2 |
| **R5** — Git | Transfers, clone, commit, branches, credentials; counts; cut-short transfers | A3, A9 |
| **R6** — the rest | Terminal, Run, Runtime, Languages, Search, Assistant, MCP, Phone, Voice, Web, Settings, Copy; `notify`, `report` and `failed` removed; the guard | A1, A7 |
| **R7** — docs | This record, notices-plan.md and layout.md where replaced, CHANGELOG | — |

Each cycle is its own commit, with the full gate run before it.

## 6. Not in this plan

- The website's notices' own text.
- Translations.
- Notification settings inside iCode (Android's per-channel settings serve).
- Snoozing.

## 7. Questions for the owner

1. **Confirmations kept** — keep Done on the page (D3), apart from problems, never counted, folded
   when repeated; "Copied" only rings? *Recommended: yes.*
2. **The undo pill's tap** — (a) as today, it undoes at once, and the bell also rings with the
   change; or (b) it shows the change and where, with Undo and Redo, and a file change does not ring
   the bell. *Recommended: (b): a look never changes a file, and one pill wakes per change.*
3. **How a failure reads** — "Couldn't pull into main" (Android's own voice), or "Unable to pull
   into main" (iCode's most used today). *Recommended: "Couldn't".*
4. **Finished while away** — post work that ran 30 s or more and ended while iCode was out of view:
   with a sound and no pop-up (DEFAULT), or silently (LOW)? *Recommended: DEFAULT.*
5. **Merging two channels** — Task failures into Problems, and long-command alerts into Finished.
   Android keeps a channel's settings per channel, so anyone who silenced one of the two loses that,
   once. *Recommended: yes.*
6. **A fetch that fails on its own** (when a workspace opens or iCode comes back) — keep it on the
   page as Info, once until a fetch succeeds, never posted; or stay quiet as today? *Recommended:
   keep it, once.*

**The owner's answers (2026-11-11):** "1 yes, 2b, 3 Couldn't, 4 sound, 5 yes, 6 keep", with Q1
restated as "keep them in the page history but don't ring the bell". So:

1. Done is kept on the page, never counted, and a success does not ring the bell (D3, D5). A
   Moment still rings, being the only answer to its tap.
2. (b): the undo pill's tap opens its sheet; Undo and Redo are buttons in it.
3. "Couldn't …".
4. Finished while away: DEFAULT, a sound and no pop-up.
5. Task failures go to Problems, long-command alerts to Finished.
6. An automatic fetch that fails is kept on the page as Info, once until a fetch succeeds, and never
   posted.

## 8. Record

- **R1 — the model.** `domain/notices/`: `Notice`, `NoticeTone` (moved here), `NoticeKeep`,
  `NoticeSource`, `NoticePlace`, `NoticeAction` (OpenFile, Show, OpenSession, OpenUrl, Undo, Run),
  `NoticeRules` (rings, kept, counted, posted, available, added, the filters), `NoticePlaces`, and
  the first builders (Terminal, Git, Web, Settings, Files, Assistant). `NoticeIntent` in `domain`
  (it must share `ICodeIntent`'s package). `ICodeEffect.Notice` is now `ICodeEffect.Told`. The sink
  on `ICodeStateHolder` (D4). Store v2 writes kind `notice` and reads the three v1 kinds.
  `AppNotice` and `NoticeLink` are gone. `isAtOrUnder`/`relocated` moved to `domain/PathRules.kt`
  so places can be read in the domain. `FetchRemote(automatic)` marks iCode's own fetches for R5.
  `notify`, `report` and `failed` stay on the state holder only until R6 removes them.
- **R2 — the surfaces.** The bell rings by `NoticeRules.rings`; a tap while awake opens the notice
  that rang it. The page: All · Needs you · Done, a place chip, Clear by filter, and a line with Turn
  On when Android's notifications are off. The sheet: when · kind · source, the place in full, the
  detail, the output folded, the actions available now, Copy, Delete. The undo pill opens
  `UndoSheet`: the change last made or undone and its place (`FileNews.place`, from
  `FileNotices.placeOf`), what Undo and Redo would do, and their buttons; the pill's tap and hold no
  longer undo or redo.
- **R3 — Android.** `data/notify/IcodeChannels` registers the three groups and eight channels in
  `ICodeApplication.onCreate` and deletes `icode.work-failures`; the ids users set choices for are
  kept. `data/notify/IcodeNotifications` is the one builder (icon, colour, channel, the place as
  sub-text), with `NotificationKey` (one id per kind, tagged by the session, the work's key or the
  notice) and `NotificationLink` (the one tap encoding; MainActivity reads it back). All five posters
  use it. The work's failure notification is gone, and with it `BackgroundItem.failure`, `watched`
  and `number`: work that stopped short is not carried, and is told as a Problem notice — the
  runtime install's already, a task's in R4, a Git transfer's in R5, a tools run's in R6 (until
  then, on its own page only). So an install failure out of view posts once (D10.5). A long
  command's end is `TerminalNotices.finished`, told by `TerminalCoordinator` from the sessions'
  `longCommandsEnded`, unless the user watched it end; out of view and with the user's setting on, it
  is posted to Finished. `TerminalAlerts` keeps only the bell, on "A program asks for you". A
  notice's notification says where in its header, has its first action as a button when that only
  opens something, and a swipe sends `NoticeSwipedReceiver`, which marks it dismissed in the store
  itself (not through the gateway, which a process started for the swipe alone would have to build);
  `NoticeIntent.Dismissed` is not needed and is gone. Git's work has one key, `git`: a clone and a
  pull never run at once.
- **R4 — files, editor, workspaces, storage.** Every notice of `data/files`, `data/workspace` and
  `data/tasks` is built in `FileNotices`, `EditorNotices`, `WorkspaceNotices` or `StorageNotices`,
  with its place. `FileHistory` tells each change made, undone or redone in the undo pill's words, as
  Done with Undo (A1). Delete for good says what went, from where. A file task is a `FileTask`
  (domain/files/FileWords.kt): its sheet's title and what it stopped short of — "Couldn't move
  notes.md to Trash" — come from it; `TaskRunner.start` takes that notice, tells it with the error's
  words as output, and keeps them in the sheet. A refusal is told near what it was about. Saves fold
  per file, failed saves apart from them, and a refresh that keeps failing folds per workspace. When
  a notice has no detail, the shade shows its output. The refusal sentences (`FileRefusals`,
  `WorkspaceUsage`, `PlaceFiles`) are shared with the sheets and kept as they are.
