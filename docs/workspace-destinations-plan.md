# Where a project goes — Clone (and Import) into a workspace, several projects in one — plan

Status: **plan** (2026-10-01). Owner's request: "on Clone Repository the user can select the
workspace folder where they want to organize it — the vision is that a user can have multiple
projects in the same workspace, as we now provide file operations too." Then: "New Folder — instead
of a dedicated create-folder page, make it like a real file explorer: clean, the best UI/UX", and:
"the root of the workspaces, in private storage, is a real file explorer — it must feel as a file
explorer does, with no gaps"; "no limits on folders and repositories side by side; do not
over-engineer".

## 1. Measured (code, 2026-10-01)

| What | Where | Today |
|---|---|---|
| Where a clone lands | `GitTransferCoordinator.clone` → `WorkspaceStaging.complete(staged, name)` | always a new workspace, `/workspace/<name>`; staged in `.clone-…`, renamed into place in one step, then opened |
| Where an import lands | `WorkspaceImportCoordinator` → the same `WorkspaceStaging.complete` | always a new workspace |
| The name and its check | `WorkspaceNameFields` (Clone, Import, New Folder) | a workspace's name, checked against the workspaces |
| A folder browser | `ImportBrowserScreen`: breadcrumb (sdcard › …), folders with their counts, "Import <folder>" | phone storage only |
| Which repository Source Control shows | `CliGitService.workingFolderOf(root)` = the repository **at or above** the workspace's root (`GitLocator.topOf`) | **one** repository per workspace. A workspace holding several projects, each its own repository, shows "This folder is not a Git repository — Initialize Git": none of them can be staged, committed, pulled or pushed from Source Control (the terminal can) |

The last row decides the vision: cloning into a workspace works as files today, but a workspace of
several repositories is not yet a place to work on them.

## 2. Design

1. **A destination, chosen where the project is made.** Clone (and Import, §4 D1) gains
   **Where**: *New workspace* (as today, `/workspace/<name>`) or *In a workspace* — a folder
   chosen in the workspaces, shown as its path (`/workspace/tools/cli/<name>`), with Change.
2. **The Workspaces page is the file explorer of the workspaces** (owner: "no gaps"). It opens at
   `/workspace` and browses down, as MT Manager does:
   - **Where it is:** the breadcrumb (`workspace › tools › cli`), each step a tap back; Back goes
     up a folder, and leaves only at the top.
   - **What is there:** folders, then files, each with its size or count and when it changed; a
     workspace at the top shows its branch. Sort by Name · Date · Size, and search in the folder
     shown. `.git` is Git's and not listed (as in the Explorer); iCode's staging never is.
   - **Open:** a folder goes in; a file opens in the editor, its workspace opened for it (the
     lifecycle's `reveal`); a workspace opens from its menu, or by a tap at the top.
   - **Make:** New folder and New file where it is (at the top, New folder is a new workspace).
   - **Long press, or ⋮:** Open, Rename…, Cut, Copy, Copy path, Delete…; on a workspace also Open
     Terminal. Paste from the clipboard bar, into the folder shown. One action list, one clipboard.
   - **Choose mode:** Clone's and Import's **Where** is this page with **Place here** at its foot
     (**New workspace** at the top), not a second browser.
   The home screen's recent five, and the Explorer inside a workspace (the tree for coding), stay
   as they are; the file explorer is for organizing, the tree for working.
   The backend closes one gap for it: create, rename and delete of an item work in any workspace,
   not only the open one (the paste already does); the open workspace's tree reads again when a
   change touched it. The listing is one read of a folder with each entry's size, date and kind.
3. **One completion.** `WorkspaceStaging.complete(staged, name)` becomes
   `complete(staged, into, name)`: a staged folder becomes `into/name` in one rename — the
   workspaces folder by default. Clone and Import both pass the destination; nothing else learns
   about it. The name is checked where it will be: a workspace's name for a new workspace, a
   folder's name and a free place in the chosen folder otherwise (`WorkspaceNameRules`, both
   already there).
4. **What follows, by the rules that exist.** A new workspace opens, as today. A project made in a
   workspace opens that workspace with the new folder selected in the Explorer; if it is the open
   one, its tree reads again (the paste's `refreshOpen`). The chosen destination is held by
   `WorkspaceUsage`'s answer for the time of the clone: a folder being deleted is not cloned into.
5. **New Folder as a file explorer makes one.** The dedicated New Folder page goes: on the
   Workspaces page, **New Folder** opens a small dialog — the name, the `/workspace/<name>` it will
   have and whether it can be used, as it is typed — and the new workspace appears in the list, as
   a file manager's new folder does, without opening it. The dialog is the Explorer's own "New
   folder" dialog's shape (`IcodeConfirmDialog`, answered by request id: a refusal is said in the
   dialog, as Rename's is). Its Figma frame (2003:77) becomes a state of the Workspaces page. The
   destination browser (2) has the same **New folder**, into the folder it shows: a place can be
   made while one is chosen.
6. **Several repositories in one workspace (§4 D2).** Source Control finds the repositories in the
   open workspace — its own and those in its folders, a few levels down, as VS Code scans — and
   shows one at a time with a picker in its header ("RevPlayer ▾"), remembering the choice per
   workspace while iCode runs. Status, diff, stage, commit, branch, pull and push work on the chosen
   repository; the Explorer's badges are the chosen repository's (D6); the launcher's branch stays
   the workspace root's.

## 3. Steps (each: gate, commit; device where it touches a screen)

| Step | What |
|---|---|
| G0 | New Folder as a dialog on the Workspaces page, answered by request id; the New Folder page and route go (its frame a state); capture count |
| G1 | `WorkspaceStaging.complete(staged, into, name)`; Clone and Import pass their destination (default: the workspaces); tests on real folders |
| G2 | Backend: a folder's listing (entries with size, date, kind), items made, renamed and deleted in any workspace, a file opened from anywhere (`reveal`); tests on real folders |
| G3 | The Workspaces page as the file explorer: breadcrumb, Back up, entries, sort and search, open, New folder / New file, the long-press and ⋮ menu, the clipboard bar |
| G4 | Choose mode: Clone's and Import's **Where** (New workspace · a folder), **Place here**; the name checked where it lands |
| G5 | Source Control over several repositories: the scan, the picker, every Git action on the chosen one; tests with nested repositories |
| G6 | Device: clone two repositories into one workspace; stage, commit and push each from Source Control |

## 3b. After the device check (G6, owner 2026-10-01)

What the phone showed, and the owner's asks: the branch selector is a page of its own that paints
a copy of Source Control behind its sheet, and the repository picker is a dropdown — both should
open over the real Source Control, a little blurred, and be chosen from one bar at the bottom.

| Step | What |
|---|---|
| G7 | Navigation: each screen keeps what it holds (a typed commit message, a search) while another is opened over it, and forgets it when it leaves the history. A route can be a **sheet**: drawn over the live screen below it, blurred and dimmed; a tap outside or Back closes it. The branch selector becomes one; its painted backdrop goes |
| G8 | Source Control's **bottom bar**, as VS Code's status bar: the repository (when there are several) and the branch, each opening its sheet, and ↑↓ opening the sync screen; the top bar says Source Control. The repository chooser is a sheet like the branch selector (search, rows, the chosen one ticked); the dropdown goes |
| G9 | **Commit & Push** commits what is staged with the message, then pushes — one transfer, which the sync screen follows (Committing, then Pushing); the sync screen follows only the transfer it was opened for, never the one before; the transfer intents are one group |
| G10 | Choose mode's empty folder says what can be done there (New folder), not New file and Paste |
| G11 | Device: the sheets over Source Control and the sync screen; a typed message kept across the sync screen; Commit & Push on icodeTest |
| G12 | (owner) The search keeps what was typed — its query, options and results — while the workspace is open, as VS Code's search view: leave it and come back, and it is there; the workspace's close resets it. Quick Open starts empty and no longer wipes it |
| G13 | (owner) In Import's browser, Back goes up a folder, as in the Workspaces page's explorer; at sdcard it leaves. One rule for the folder above, for both |

## 4. Decisions

- **D1** (owner, 2026-10-01): Import gets the same **Where** as Clone.
- **D2** (owner, 2026-10-01): Source Control over several repositories is in this plan.
- **D3** A new workspace made by New Folder appears in the list and is not opened — a file
  manager's behaviour; a tap opens it. Clone and Import still open what they made, as today.
- **D4** One item at a time, as today's menus: no multi-select yet (a later step if wanted).
- **D5** In the file explorer, Back goes up a folder, as in MT Manager; at the top it leaves.
- **D6** The Explorer's badges come from the repository Source Control shows, not from every
  repository in the workspace: each more would be one more Git process in the runtime at every
  refresh. A later step if wanted.
- **D7** (owner, 2026-10-01): the branch and repository are chosen from a bar at the bottom of
  Source Control, as VS Code's status bar; each opens its sheet from the bottom.

## 5. Record

- **G0** (2026-10-01): New Folder is a dialog on the Workspaces page, as a file manager makes a
  folder: the name, its `/workspace/<name>`, whether it can be used as it is typed, and the
  backend's answer by request id (a refusal said in the dialog). The new workspace appears in the
  list, unopened (D3). Rename's dialog and New Folder's are one (`WorkspaceNameDialog`, `ui/
  WorkspaceDialogs.kt`). The New Folder page and route are gone; its frame (2003:77) is a documented
  state of the Workspaces page (RouteTest), and the capture counts 38 routes. PR-ENTRY-01 and the
  layout say so. Gate: 567 tests; lint 0 errors / 12 warnings; both guards pass.
- **G1** (2026-10-01): `WorkspaceStaging` owns the one name check (`nameProblem`: a new
  workspace's name, or a folder's name not yet in the chosen folder) and completes into any folder
  of the workspaces — a workspace's own top or anything in it; refused only outside the workspaces
  or inside `.git`. Clone and Import take `into` (none: a new workspace, as ever) and ask the same
  check; both clear the chosen destination when done. What they made is shown by one step
  (`WorkspaceLifecycleCoordinator.reveal`: a new workspace opens; a project in a workspace opens it
  with the project selected, or refreshes the open one) — in the lifecycle, since the session
  coordinator is at its 300-line budget. Domain: `ProjectDestination` (location, `/workspace/…`
  path, the names it held), `WorkspaceNameRules.projectNameError`, `WorkspaceRuntimePath.of(name,
  destination)`. Owner (2026-10-01): no limits beyond those — folders and repositories side by side
  in one workspace, a clone anywhere in it; nothing over-built. 2 tests. Gate: 569 tests; lint 0
  errors / 12 warnings; both guards pass.
- **G2** (2026-10-01): the file explorer's backend. `FilesBrowserState` (where it is, what is
  there, the breadcrumb from `workspace`), `BrowsedItem` (folder or file, size or count, when it
  changed, a workspace's branch), `FilesRules` (folders first; Name · Date · Size; search; what a
  folder holds). The breadcrumb is one rule for any root (`FolderCrumbs`; Import's sdcard uses it).
  The library lists a folder (`BrowseFiles`, with the storage reads, off the lock): the workspaces
  at the top, else the folder's entries as the Explorer lists them; it lists again on every
  refresh, and the watcher follows the folder shown, so a change from the terminal appears. Create
  file and folder, rename and delete work in any workspace — the workspace the item is in, not
  only the open one; a dialog that named a workspace still guards it — and each change re-reads
  the explorer, and the open workspace's tree when touched. `reveal` opens what is anywhere: a
  file in the editor with its workspace opened first; `OpenFile` goes through it (the same for
  anything in the open workspace). 4 tests. Gateway 363 (budget lowered). Gate: 573 tests; lint 0
  errors / 12 warnings; both guards pass.
- **G3** (2026-10-01): the Workspaces page is the file explorer. The breadcrumb from `workspace`
  (one component now, `FolderBreadcrumb`, which Import's browser uses too); Back — the system's and
  the top bar's alike — goes up a folder and leaves only at the top; Name · Date · Size (Date first:
  at the top it is when a workspace was last opened, as the old Recent) and search in the folder
  shown; a tap goes into a folder — a workspace at the top too, as a file explorer does; its menu
  has Open — or opens a file in the editor. **+** makes a New folder (at the top, a new workspace)
  or, inside one, a New file. Each file and folder: its icon (the Explorer's file-type icons), name,
  size or count and age, and one menu from ⋮ or a long press (Open, Rename…, Cut, Copy, Copy path,
  Delete…); each workspace at the top keeps its own (Open, Open Terminal…). The clipboard bar pastes
  into the folder shown, or as a workspace at the top. The Explorer's create, rename and delete
  dialogs take a location, not a tree row, and serve both; Copy path is one action. The list rules
  the file explorer replaced (search and sort of workspaces) are gone. Gate: 571 tests; lint 0
  errors / 12 warnings; both guards pass.
- **G4** (2026-10-01): Clone and Import have **Where** — "New workspace", or the path of the folder
  chosen — with Change, which opens the same file explorer in choose mode (route
  `ChooseDestination`, the same screen): folders only, New folder still there, and at its foot the
  place it will be with **Place here**, or at the top **New workspace**. The choice is the
  snapshot's `projectDestination` (the library reads the folder's names for the live check);
  the name's check and the path shown follow it (`/workspace/tools/<name>`, "already in
  /workspace/tools"); Clone and Import send it, and it is cleared once they finish. Gate: 571
  tests; lint 0 errors / 12 warnings; both guards pass.
- **G5** (2026-10-01): Source Control over several repositories. `GitLocator` finds a workspace's
  repositories on disk at each read — the one it is in, then each folder with a `.git` of its own,
  three levels down, not in hidden folders, `node_modules` or through a link — and holds the one
  chosen per workspace while iCode runs. Every Git command already asks it where to run
  (`workingFolderOf`): for the workspace, the chosen repository while it is still there, else the
  one the workspace is in, else the first found; for anything in it, the repository holding it —
  so status, diff, stage, commit, branches, conflicts, pull and push are the chosen one's. What Git
  says reaches the workbench by one copy (`withGit`, `GitPicture.kt`), for a workspace opened and
  for one refreshed; the snapshot carries `gitRepositories`. Source Control shows the picker
  (`RepositoryPicker`: each named by its folder in the workspace, the chosen one ticked) when there
  is more than one; `ChooseGitRepository` chooses and refreshes. Found on the way: a task's Hide
  that lands after its failure no longer hides it — the failure stays until dismissed (a timing
  flake in `TaskRunnerTest`, now asserted). 2 tests. Gateway 361 (budget lowered). Gate: 573
  tests; lint 0 errors / 12 warnings; both guards pass.
- **G6** (2026-10-01, device, fresh APK): a workspace `multi` made by New folder; `icodeTest` (SSH)
  and `Extension-List` (HTTPS) cloned into it through Where → `multi` → Place here, side by side.
  Source Control showed the picker ("2 repositories in this workspace"); each repository showed
  only its own change, with its own paths; a commit in Extension-List made it ↑1 and left
  icodeTest ↑0; switched to icodeTest, its commit was pushed and GitHub's `main` is that commit
  (`d378e0b`); Extension-List kept its own ↑1. Found and fixed: when a clone finished, the progress
  screen gave way before the clone was opened, so the workspace open before showed for a few
  seconds — it is now opened first, then said to have succeeded, and Cancel no longer applies once
  the clone is whole (1 test). Found, for G7: **Commit & Push** only opens the sync screen — it
  commits nothing (a label from the demo frames); what is typed on a screen (the commit message) is
  lost when another screen is opened on top and closed — the navigation keeps no screen's state;
  the empty folder's words in choose mode speak of New file and Paste, which are not there. Gate:
  574 tests; lint 0 errors / 12 warnings; both guards pass.
- **G7** (2026-10-01): `NavigationHost` draws the history. Each entry keeps what its screen holds
  (`SaveableStateHolder`, keyed by place and route) while another is opened over it, and forgets it
  once it leaves the history — a typed commit message survives the sync page (device). A route can
  be a sheet (`Route.sheet`): drawn over the live screen below it, which stays composed as it was,
  blurred (`IcodeSizes.SheetBackdropBlur`, Android 12+) and dimmed (`IcodeColors.SheetScrim`);
  a tap outside or Back closes it; a tap on the sheet stays on it. The branch selector is one; its
  painted copy of Source Control is gone (device: opens over the real Source Control). 1 test.
- **G8** (2026-10-01): `GitBottomBar`, the same on Source Control and its sync page, in the
  editor's bottom-bar style (`IcodeBottomBar`): the repository when there are several, the branch,
  each opening its sheet, and ↑↓ — the sync page, lit on it. Source Control's top bar says Source
  Control, with Refresh; the sync page's "Open Branch Selector" button is the bar's branch now, and
  it scrolls above the bar. The repository chooser is a sheet as the branch selector is
  (`RepositorySelectorScreen`, route `RepositorySelector`, owner-asked, frameless): search, rows,
  the chosen one ticked; one row for both sheets (`IcodeChoiceRow`); the dropdown is gone.
- **G9** (2026-10-01): **Commit & Push** commits what is staged with the message, then pushes —
  one transfer (`CommitAndPush`), followed on the sync page (Committing, then Pushing); a commit Git
  refuses is not pushed and the page says why. A transfer is running from the moment it is
  accepted and always ends succeeded, failed or cancelled — a refusal ("Check out a branch first")
  or no workspace ends it failed, in its words, where it is followed — so the page opened with it
  never shows the one before. The transfer intents are one group (`TransferIntent`, one gateway
  line). `act` returns what went wrong, so Commit & Push shows the commit's failure itself. Source
  Control clears a message once the commit it was sent for exists — found on return too, as the
  "commit created" event is not heard while the sync page covers it. 3 tests, 2 changed.
  Gateway 358 (budget lowered).
- **G10** (2026-10-01): choose mode's empty folder says what can be done there — Place here, or
  New folder; at the top, New workspace below — and goes by what it can list: a folder of files
  only is "No folders in …", not "Nothing here matches “”".
- **G12** (2026-10-01, owner): the search keeps what was typed while the workspace is open, as
  VS Code's search view — the page no longer clears it on leaving; it opens with the last query,
  its options and results, and runs it again. The workspace's close resets it, as before. Quick
  Open starts empty and no longer clears the text search.
- **G13** (2026-10-01, owner): in Import's browser, Back — the system's and the top bar's — goes up
  a folder, as in the Workspaces page's explorer; at sdcard it leaves. The folder above is one rule
  for both browsers (`FolderCrumbs.parentOf`). 1 test. Gate (G7–G13): 579 tests; lint 0 errors /
  12 warnings; both guards pass.
- **G8, revised** (2026-10-01, owner: "branch as status and button both", ↑↓ on the right): the
  repository and the branch are chips on the left of the bar — icon, name, ▾ — each saying which
  it is and opening its sheet; ↑↓ sits at the bar's right end. The chip is the bar's own
  (`BottomBarItem.choice`, one component for every bar). Gate: 579 tests; lint 0 errors / 12
  warnings; both guards pass.

