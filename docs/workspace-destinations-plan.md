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
   workspace. Status, diff, stage, commit, branch, pull and push work on the chosen repository; the
   Explorer's badges come from each file's own repository; the launcher's branch stays the
   workspace root's.

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

## 4. Decisions

- **D1** (owner, 2026-10-01): Import gets the same **Where** as Clone.
- **D2** (owner, 2026-10-01): Source Control over several repositories is in this plan.
- **D3** A new workspace made by New Folder appears in the list and is not opened — a file
  manager's behaviour; a tap opens it. Clone and Import still open what they made, as today.
- **D4** One item at a time, as today's menus: no multi-select yet (a later step if wanted).
- **D5** In the file explorer, Back goes up a folder, as in MT Manager; at the top it leaves.

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

