# Where a project goes — Clone (and Import) into a workspace, several projects in one — plan

Status: **plan** (2026-10-01). Owner's request: "on Clone Repository the user can select the
workspace folder where they want to organize it — the vision is that a user can have multiple
projects in the same workspace, as we now provide file operations too." Then: "New Folder — instead
of a dedicated create-folder page, make it like a real file explorer: clean, the best UI/UX."

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
2. **One folder browser, two sources.** The Import browser's page — breadcrumb, folders with their
   counts, the choose button at the foot — becomes a shared `FolderBrowser` (ui) over a small model
   (the crumbs, the folders here, what choosing means). Phone storage (Import's source, rooted
   "sdcard") and the workspaces (the destination, rooted "workspace") each fill it; the crumbs are
   one rule (`PhoneStoragePaths.crumbs` generalised: a root's name and path).
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
| G2 | `FolderBrowser` out of the Import browser (Import unchanged to the eye); the crumbs rule for any root; the workspaces as a second source |
| G3 | Clone's **Where** (New workspace · In a workspace, with the browser to choose), the name checked where it will land; what follows (open, select, refresh) |
| G4 | Import's **Where**, the same parts |
| G5 | Source Control over several repositories: the scan, the picker, every Git action on the chosen one; tests with nested repositories |
| G6 | Device: clone two repositories into one workspace; stage, commit and push each from Source Control |

## 4. Decisions

- **D1** (owner, 2026-10-01): Import gets the same **Where** as Clone.
- **D2** (owner, 2026-10-01): Source Control over several repositories is in this plan.
- **D3** A new workspace made by New Folder appears in the list and is not opened — a file
  manager's behaviour; a tap opens it. Clone and Import still open what they made, as today.

## 5. Record

(empty)
