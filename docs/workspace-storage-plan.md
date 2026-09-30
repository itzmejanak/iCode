# Workspaces live in iCode's storage — master plan

Status: **plan, decided by the owner** (2026-09-30). Replaces every earlier version of this document —
the shared-source design with private mounts (S1–S4, commits `8228056` → `b0c585d`) — and the
direction of [storage-logic.md](./storage-logic.md). It is an architecture decision: it changes
[architecture.md](./architecture.md) §26 invariants 1–2, which W1 rewrites.

## 1. The decision

The owner (2026-09-30): the split between shared and private storage "is mismatching and bringing
complexity"; **private storage is the best solution**; the launcher shows the list of workspaces in
private storage, so that one backend serves everything "from the terminal to other areas"; plan so
that no gap remains, then migrate in one clean sweep — rewrite rather than align the current code,
harden it, no duplication, no leftovers, no assumptions.

The owner's answers, the same day:

| Question | Answer |
|---|---|
| Where the list of workspaces lives | **The launcher lists every workspace in iCode's storage**; the phone-storage folder browser (Open Folder) retires |
| All Files Access | **Dropped.** Folders come in by Import, through Android's folder picker; other apps reach workspaces through Android's Files app |
| Workspaces opened from phone storage before | **One notice lists them; each comes in by Import.** Their old shortcuts go; the folders on phone storage are never touched |
| Data safety | **Keep data on uninstall** (`hasFragileUserData`) and **Manage space instead of Clear storage** (`manageSpaceActivity`). Not device-to-device transfer |

**The rule.** A workspace is a folder in iCode's storage, `files/workspaces/<name>`. Every part of
iCode — launcher, Explorer, editor, file operations, terminal, Git, search, language services and
the Files provider — reaches it through one path model (§4.2). Nothing is mounted, mirrored or
synchronised. A folder from phone storage comes in once, by an explicit Import, as a copy; the
original is never written.

## 2. Why — measured on the owner's phone (Infinix-X6881, 2026-09-29/30)

| | Shared storage (`/storage/emulated/0`, FUSE) | iCode's storage (`files/`, ext4) |
|---|---|---|
| 1,000 small files created / deleted | 13.0 s / 6.1 s | 0.07 s / 0.04 s |
| symbolic link | refused ("Permission denied") | works |
| `chmod 755` | ignored (`-rw-rw----`) | kept |
| `A.txt` beside `a.txt` | one file: the second write silently replaced the first | two files |
| running a script (`./run.sh`) | refused: the volume is `noexec` | runs |
| `npm install semver lodash` through iCode's PRoot | **fails**: `EACCES … symlink … .bin/semver`, 0 files | exit 0 in 8 s, `.bin/semver` linked |
| `git clone` with PRoot's `--link2symlink` | cannot finalise a pack ([sudo-capability-plan](./sudo-capability-plan.md) §1) | works |

The split design (S3–S4) made the second column usable for generated folders, at a price paid by
every consumer: two filesystems under one workspace, a mount list per workspace, empty placeholder
folders PRoot leaves in the user's folder, moves that must copy then delete (`EXDEV`), rename and
move refused for mounts and the folders holding them, a compatibility detector still to build for
the source side (links, executable bits, case) — and the source side itself still slow, `noexec`
and case-insensitive. Keeping everything in iCode's storage removes all of it at once.

The background freeze measured on the same phone (Transsion's Fast Freezer; R1 in the record of
this plan's previous version, commit `b0c585d`) is independent of storage and stays parked by the
owner's decision; nothing here changes it.

## 3. The whole lifecycle — today, and after this plan

| # | Step | Today (code as of `b0c585d`) | After |
|---|---|---|---|
| 1 | First launch | Splash → runtime install → **All Files Access (required)** → notifications → battery → launcher | Splash → runtime install → notifications → battery (both optional, each offered once) → launcher |
| 2 | Home | Tiles Open Folder, Clone Repo, New Folder; **Recent Workspaces**: up to 20 shortcuts to phone-storage paths, which can go stale or unavailable (Remove from recents, Remove Stale); a banner while storage access is off | Tiles **Import**, Clone Repo, New Folder; **Workspaces**: every folder in iCode's storage, most recently opened first. Nothing can be stale: the list is the folder. A workspace made in `/workspace` from the terminal appears in it |
| 3 | New workspace | Choose a parent anywhere on phone storage through the folder browser, name it, open it | Name it; it is made at `/workspace/<name>` and opened |
| 4 | Clone | Choose a destination through the folder browser; Git clones into it | Clones into `/workspace/<folder>`: Git's name for the address, editable; nothing to choose |
| 5 | Open an existing folder | Open Folder: browse phone storage, open any folder in place | Open a workspace from the list. A folder elsewhere comes in by **Import** — a copy; the original is untouched |
| 6 | Explorer, editor, file operations | `java.io` on phone storage (13 ms a file operation); S4's logical tree over two filesystems | `java.io` on ext4 in iCode's storage; one filesystem, no boundary rules |
| 7 | Terminal, Git, search, language services | Each launch binds the open folder at `/workspace/<name>` (a `:` in the name becomes `-`); Git binds the repository top it finds itself (`GitMountResolver`, same-path binds); S3b adds each mount | **Every launch binds `files/workspaces` at `/workspace`**; a command runs in `/workspace/<name>`; the terminal sees every workspace |
| 8 | Switch workspace | From the launcher; unsaved edits are asked about | Same |
| 9 | Leave iCode, process death, restore | Sessions under the foreground service; the freeze (R1, parked); the session restored by location | Same |
| 10 | Other apps | See the folder by its `/sdcard` path (MT Manager, Files) | Android's Files app and every app's Open/Save picker show **iCode → workspaces**, read and write. Not by `/sdcard` path |
| 11 | Taking work out | It was already on phone storage | Copy from **iCode** in the Files app to any folder; open or share a file from there |
| 12 | Rename or delete a workspace | Only "Remove from recents" (files untouched) | **Rename…** and **Delete…** on the workspace's menu; a delete is confirmed and runs as a task |
| 13 | Storage management | Settings → Storage: device usage, runtime and cache sizes, recent workspaces' sizes, Clear Cache, Remove Stale | Same page: every workspace's size, Clear Cache; Remove Stale goes. App info's **Manage space** opens this page |
| 14 | Runtime Repair, Reset | Touch `files/runtime` only (rootfs, home backup) | Same; the confirmations say workspaces are kept |
| 15 | App update | Stores carry over | This version migrates once (§5) |
| 16 | Uninstall, Clear storage | Workspaces on phone storage survive both | Android asks whether to keep iCode's data on uninstall; App info offers Manage space instead of Clear storage |

## 4. Design

### 4.1 What lives where (replaces architecture.md §12)

| Data | Location | Lifetime |
|---|---|---|
| Workspaces | `files/workspaces/<name>/` | Until the user deletes the workspace, or uninstalls iCode without keeping its data |
| Runtime root filesystem and home | `files/runtime/` | Across app updates; replaced only by Repair or Reset |
| Settings, workspace history, recent files, session | DataStore and SharedPreferences | Removed with the app's data |
| Recovery drafts | `files/document-drafts/` | Until saved or discarded |
| Downloads, PRoot temporary files | `cache/` | Rebuildable; Clear Cache |

### 4.2 One path model

`WorkspacePaths` (`data/storage`, pure, tested) is the only code that knows how a workspace is
reached:

| Form | Example | Used by |
|---|---|---|
| host path — the **location**, the identity every store already keys by | `<files>/workspaces/RevPlayer/src/App.tsx` | Explorer, editor, file operations, stores, watcher |
| guest path | `/workspace/RevPlayer/src/App.tsx` | terminal, Git, ripgrep, language services; **also the path people see** (launcher, Explorer header, Copy path) |
| document ID | `RevPlayer/src/App.tsx` | the Files provider |

- `guestOf(location)` / `locationOf(guestPath)`: prefix exchange, both ways, null outside the root.
- `workspaceOf(location)`: the workspace holding a location — its first path segment under the root.
- Every PRoot launch binds `files/workspaces:/workspace` once. The only per-launch fact is the
  working directory. So: no per-launch mount decision, no name mangling (a `:` never reaches PRoot's
  `host:guest` argument), no Git mount resolver — every repository's top is inside `/workspace` and
  Git finds it itself, linked worktrees included, since Git writes guest paths and every launch
  sees the same `/workspace`.
- Names: `WorkspaceNameRules` (no `/`, `\`, NUL, `.`, `..`; at most 255 bytes), and a workspace's
  name cannot start with `.`: at the top of the root such names are **iCode's staging** — an import
  or a clone in progress (§4.5). Staging is never listed, never offered by the provider, and what a
  stopped process left of it is removed at the next start.
- The launcher's branch comes from `.git/HEAD` read on the host; a linked worktree's `.git` file
  names its Git directory by guest path, which `locationOf` turns back into a location.

### 4.3 The workspace list and identity

- **The directory is the registry.** The launcher lists the folders in `files/workspaces`; there is
  no separate list to go stale. The store keeps only when each was last opened (keyed by location);
  a folder never opened in iCode sorts by its modification time.
- iCode watches `files/workspaces` while the launcher is shown: a workspace made, renamed or removed
  from the terminal appears or goes.
- **Rename** is refused while a terminal session or a task runs in the workspace, or a file in it
  has unsaved edits; its language services are stopped first. The folder is renamed; its settings,
  last-opened time and recent files follow; if it is the open workspace, its tabs, tree, session
  and search index follow, as for a folder renamed in the Explorer.
- **Delete** is confirmed (the name, its path, "this cannot be undone") and runs as the existing
  delete task. It is refused while a terminal session runs in the workspace or its recovery drafts
  wait; the open workspace is closed first, asking about unsaved edits as a switch does. Its
  settings, last-opened time and recent files are forgotten.

### 4.4 Screens

Frames are the Figma frames `Route.kt` names; changes use only the existing kit.

| Screen | Change |
|---|---|
| Launcher (3:124, empty 3:202) | Tiles **Import**, Clone Repo, New Folder. List **Workspaces**: name, `/workspace/<name>`, branch, last opened. Menu: Open, Open Terminal, Rename…, Show in Files, Copy path (the guest path), Delete…. Empty state: "Create a folder, clone a repository, or import one from phone storage". The storage banner goes. Hidden tasks (an import, a delete) show as in the Explorer |
| New Folder (2003:77) | Folder Name; the path row reads `/workspace/<name>` as it is typed, with no Change; "already exists" is said inline; the storage card stays |
| **Import** (new route; no frame yet) | Built from New Folder's parts: **From** (the picked folder's name; Change reopens the picker), Folder Name (prefilled), the path row, the storage card, **Import** |
| Clone (3:239), Clone progress (3:278) | Folder Name (Git's name for the address, editable) and the path row replace Destination; progress reads `Dest: /workspace/<name>` |
| Open Folder (2003:117) | **Retired**, with its route and the folder browser |
| Permission (3:90) | Notifications and Battery only |
| Explorer (3:456), Editor empty (2003:19) | Subtitle `/workspace/<name>` |
| Storage settings (2003:575) | "Workspaces" with every workspace's size and menu; Clear Cache; Remove Stale goes; one line: workspaces live in iCode's storage — uninstalling removes them unless Android is told to keep iCode's data |
| Notifications | The one-time migration notice (§5) |
| Android's Files app | Root **iCode**, summary "Workspaces" (§4.6) |

### 4.5 Import

1. **Import** opens Android's folder picker (`ACTION_OPEN_DOCUMENT_TREE`). The grant is used for this
   import only; nothing is persisted.
2. The Import screen proposes the folder's name; it must not exist yet. Picking a folder from
   iCode's own provider is refused: it is already here.
3. The import runs as a task: counted first (entries and bytes — an import that would not fit in
   the free space is refused before anything is copied), then copied file by file through the
   picked folder's provider into the staging folder `files/workspaces/.import-<n>`, with each file's
   modification time kept, and renamed to `<name>` only when complete. A stopped or failed import
   leaves nothing behind, and no half workspace ever appears in the list. Documents without bytes
   (a cloud file) are skipped and counted in the result. The source is never written. On success
   the workspace opens.
4. **Git repair.** Git on phone storage recorded `core.fileMode=false` and `core.symlinks=false`, and
   checked out executables without `+x` and links as plain files. For an imported repository iCode
   restores what Git itself records: both settings to `true`; `+x` on the files the index lists as
   `100755`; a link for each file it lists as `120000` — only where the working file still matches
   the index, so nothing the user changed is touched. `git status` must read as it did before the
   import (W5 proves this with real Git before relying on it). Git is installed for it as Clone
   installs it; if the runtime or Git cannot be had, the import completes without the repair and
   its result says so.
5. Android's picker does not offer the root of internal storage, `Download` itself, `Android/data` or
   `Android/obb` (device check D6).

**Clone** follows the same staging: Git clones into `/workspace/.clone-<n>`, renamed to the chosen
name on success. A failed or cancelled clone's folder is iCode's own and is removed — the
"Git left … part-written" notice (`GitOperationState.Cancelled.leftover`) goes.

### 4.6 The Files provider

A `DocumentsProvider`, authority `<applicationId>.workspaces`, exported only to the system's
documents UI (`android.permission.MANAGE_DOCUMENTS`): another app reaches exactly what the user
picks for it, through Android's grants. One root, **iCode**:

- Document ID = the path relative to `files/workspaces`; the root is `/`.
- Operations: roots, a document, children; open for read, write and truncate; create a file or
  folder (a taken name becomes "name (1)", as Android does); rename; delete (a folder with
  everything in it); move within the root; `isChildDocument`, so an app can be given one workspace
  as a tree.
- **Security.** An ID is normalised and must stay inside the root; the canonical path must stay
  inside the canonical root. A link can therefore never lead another app out of the workspaces into
  the runtime (its home holds SSH keys) or iCode's other data: such links are neither listed nor
  opened. Nothing else of iCode's storage is exposed.
- **Changes.** Cursors carry notification URIs; the provider notifies after its own writes; iCode's
  own file operations notify their folder. iCode's watcher sees writes made through the provider
  as any other write, so an open file changed by another app follows the existing
  external-change flow.
- **Show in Files** opens Android's Files app at the workspace, else at the root (device check D8).

### 4.7 Data safety

- `android:hasFragileUserData="true"`: uninstalling asks whether to keep iCode's data.
- `android:manageSpaceActivity`: a non-exported alias of the main activity that opens Settings →
  Storage. App info then shows **Manage space** instead of **Clear storage** (device check D10).
- Backups stay off (cloud and device transfer exclude everything) — the owner's answer.
- A workspace is deleted only by its confirmed Delete…; the runtime's Repair and Reset say that
  workspaces are kept.

### 4.8 Permissions and onboarding

`MANAGE_EXTERNAL_STORAGE` leaves the manifest. `SystemPermission.Storage`, `SystemAccess.storage`,
its prompt, its onboarding step, the launcher banner and every `isExternalStorageManager()` check
go. Onboarding asks for notifications and battery, each once; the launcher follows.

### 4.9 Unchanged

Per-workspace settings (keyed by location), recovery drafts, the atomic save and changed-on-disk
checks, the task model (dialog, Hide, background row), terminal sessions and their notifications,
Git credentials, the capture pipeline (fixtures updated), the background freeze (R1, parked), and
PRoot's patches (0001 keeps a failed link from losing the file on any filesystem).

## 5. Migration of the current install (once)

At the first start of this version, before the launcher reads the list; marked done in its own
preferences file (`storage_layout = 2`); idempotent; a failure is logged, retried at the next
start, and never blocks the launcher.

1. `files/workspaces` holds only S3b's experimental mount folders at that moment — its only writer
   was `WorkspaceStorage` (`e7c5329`), deleted in W2 — so it is deleted and the new root made.
2. The old recent workspaces (`recent_workspaces_v1`, all on phone storage) become **one notice**:
   "Workspaces now live in iCode", each name with its path, and "Bring each in with Import. The
   folders on phone storage are untouched." The key is removed.
3. Recent files, the saved session and per-workspace settings keyed by those old locations are
   removed: iCode no longer opens those folders in place.
4. Recovery drafts for files on phone storage stay in Document Recovery. Restore says the file is
   outside iCode's workspaces (import its folder, or Discard). No draft is deleted by the migration.
5. The empty mount points old launches made in the runtime (`rootfs/workspace/<name>`) are removed;
   `/workspace` is the root's bind point from now on.

## 6. Ledger — deleted, rewritten, changed

Nothing of the shared-storage model survives: every item is removed or rewritten in the cycle
named, and the guards' allowlists shrink with it.

| Item | Fate | Cycle |
|---|---|---|
| `domain/LogicalWorkspace.kt`, `data/storage/WorkspaceStorage.kt`, `data/storage/LogicalFiles.kt` and their tests | deleted: the mount model | W2 |
| `data/git/GitMountResolver.kt`, `data/git/GitHeadReader.kt`, `data/runtime/GuestMount.kt` | deleted: the root is bound whole. `GitLocator` finds a repository's top (never above the workspaces) and reads its branch; commands take a location, which `WorkspacePaths` turns into the guest working directory | W2 |
| `RuntimeLayout.prootCommand` (`workspaceHostPath`, `samePathBinds`), `RuntimeLayout.guestWorkspace` | rewritten: one bind of the root, a guest working directory | W2 |
| `TerminalLaunch`, `GuestCommand`, `CliGitService`, `GitInspectParser`, `RipgrepService`, `LanguageServiceProcessManager` | changed: paths through `WorkspacePaths` | W2 |
| S4's logical tree in `DirectFileWorkspaceSource`, `WorkspaceFiles`, `WorkspaceTree`, `WorkspaceWatcher` and the coordinators | removed: back to one filesystem | W2 |
| `DirectFileWorkspaceSource` | rewritten as the workspace file source: listing, reading, saving and creating under the root; the phone-storage root and the folder browser go with the screen that uses them | W4 |
| `WorkspaceStore`, `WorkspaceLibraryCoordinator` | rewritten: the list from the root, last-opened times, create, rename, delete, sizes | W3 |
| `WorkspaceSummary.available` and the launcher's UNAVAILABLE state | deleted: a folder in iCode's storage cannot be unavailable | W3–W4 |
| `AndroidWorkspacePreferencesRepository` | gains move (rename) and forget (delete, migration) of one workspace's overrides | W3 |
| `GitTransferCoordinator.clone`, `GitOperationState.Cancelled.leftover` | clone into staging; the leftover notice deleted | W3 |
| `FolderBrowserCoordinator` | deleted | W3 |
| `domain`: `FolderBrowserPurpose`, `FolderBrowserEntry`, `FolderBrowserState`; intents `StartFolderBrowser`, `BrowseFolder`, `BrowseParentFolder`, `ConfirmFolderSelection`, `RequestCreateFolder`, `ShowWorkspaceLocation`, `RemoveRecentWorkspace`, `RemoveStaleRecents`; effects `FolderBrowserOpened`, `FolderDestinationSelected`; snapshot `folderBrowser`, `createFolderDestination`, `createFolderDestinationDisplay` | deleted, replaced by `CreateWorkspace`, `RenameWorkspace`, `DeleteWorkspace`, `ShowWorkspaceInFiles`, the import intents, and `CloneRepository` without a destination | W3–W5 |
| `SystemPermission.Storage`, `SystemAccess.storage`, `AndroidSystemAccess`, `SystemPrompts`, `PermissionScreen`'s storage step, `IcodeApp`'s storage branch, `OnboardingRoute` | storage removed | W4 |
| `OpenFolderScreen.kt`, `Route.OpenFolderBrowser` | deleted | W4 |
| `LauncherScreen` (with the background-task row), `CreateFolderScreen`, `CloneRepositoryScreen`, `CloneProgressScreen`, `WorkspaceActionsMenu`, `StorageSettingsScreen`, `SettingsCatalog`, `EntryScreenHost` | changed as §4.4 | W4 |
| Capture fixtures (`app/src/debug`) | phone-storage paths, the folder browser and its destination replaced by `/workspace` workspaces and an import source | W4–W5 |
| Manifest | `MANAGE_EXTERNAL_STORAGE` removed; provider, alias, `hasFragileUserData`, `manageSpaceActivity` added | W4, W6, W7 |
| New: `WorkspacePaths`, the import copier and its picker source, the Git repair, the provider and its ID logic, the migration | tested where pure; each with its device check | W2–W7 |
| architecture.md §2, §3, §4, §5.5, §6–8, §10.1, §10.2, §11, §12, §13, §21, §23–27 · prd.md §3, §4, §5.1, §9.2, §10.1, §10.2 (PR-WS-01–07), §12.1, §13, glossary · user-stories.md §3, US-ENTRY-001/002, §5 (US-STORAGE-001–003 rewritten), US-WS-001–004, new US-WS-006, US-EDIT-003 · layout.md §6, §9, §10 · README · Todo · plan.md · storage-logic.md (superseded) | rewritten to this plan | W1 |

Dated records ([integration-plan](./integration-plan.md), [code-separation-plan](./code-separation-plan.md),
[sudo-capability-plan](./sudo-capability-plan.md), [terminal-background-plan](./terminal-background-plan.md))
keep describing what was true when they were written, as their convention is.

## 7. Cycles

Each cycle is one commit, or a few, and passes its gate before the next begins. **Here** (a cloud
session without the Android SDK or the phone): `data/` and `domain/` type-checked against
Robolectric's `android-all`, the backend unit tests, both guards. **On the owner's machine and
phone**: `./gradlew :app:compileReleaseKotlin :app:testDebugUnitTest :app:lintDebug`, the capture,
and the device checks the cycle names. A screen cycle cannot be compiled here, so it is written
against the existing kit only and reviewed line by line; the owner's build is its first compile.

| Cycle | What | Done when |
|---|---|---|
| **W1** | This plan; the architecture decision and every stale document rewritten (§6) | Documents agree with each other and with this plan |
| **W2** | One path model and one bind: `WorkspacePaths`; every PRoot launch binds the root; terminal, Git, ripgrep and language services through it; the mount model (S3–S4), the Git mount resolver and `GuestMount` deleted | Type-check and tests pass; D1, D2 |
| **W3** | The workspace list and lifecycle: store and library rewritten; New Folder and Clone into the root; rename; delete as a task; open, switch, restore; the root watched; the migration (§5) | Tests pass; D3, D4, D11 |
| **W4** | Screens (§4.4), onboarding and permission removal, Open Folder retired with the folder browser, the workspace file source rewritten for the root, capture fixtures, the UI guard | Gradle gate, capture; D5 |
| **W5** | Import: picker, screen, the copier as a task, the Git repair measured with real Git first | Tests pass; D6, D7 |
| **W6** | The Files provider, Show in Files, change notifications; the security tests | Tests pass; D8, D9 |
| **W7** | Data safety (§4.7) and the Storage page's wording | D10, D12 |
| **W8** | Device acceptance of the whole lifecycle (§3), the 40-frame capture, and the list of Figma frames to update | Every D passes or is written down with what it showed |

## 8. Device checks — measured, not assumed

| # | Check | Expected |
|---|---|---|
| D1 | Terminal in a workspace: `pwd`, `ls /workspace`, `npm install` in a Node project with the screen on | `/workspace/<name>`; every workspace listed; the install finishes, `.bin` links made |
| D2 | Git: status, commit, a linked worktree made from the terminal (`git worktree add ../x-feature`) | Status and decorations right; the worktree appears as a workspace and Git works in it |
| D3 | `mkdir /workspace/made-in-terminal`, then the launcher | Appears while the launcher is shown |
| D4 | Rename and delete a workspace; both refused while a terminal session runs in it | As §4.3 |
| D5 | Fresh install: onboarding | No storage step; notifications and battery once each; the launcher |
| D6 | Import from `Internal storage/Projects/RevPlayer` and from a folder inside `Download`; cancel one midway | Progress; the workspace opens; a cancelled import leaves nothing; the picker's refusals as §4.5 |
| D7 | An imported repository with `gradlew` and a committed link | `./gradlew` runs; the link is a link; `git status` as before the import |
| D8 | Android's Files app | **iCode** listed; browse, open, edit and save a file in another editor; copy a workspace to `Download`; Show in Files lands on the workspace |
| D9 | MT Manager | Whether it can add iCode's provider; written down either way |
| D10 | Uninstall dialog; App info → Storage | "Keep app data" offered; Manage space opens Storage settings (XOS may differ: written down) |
| D11 | The upgrade from `b0c585d` on the owner's phone | One notice naming the old workspaces; no shortcut to phone storage left; `files/workspaces` holds only real workspaces |
| D12 | Runtime Repair, then Reset | Workspaces untouched |

## 9. Not built

Synchronisation of any kind; mounts; a compatibility detector (ext4 holds whatever a repository
holds; Import repairs from Git's own record); opening a phone-storage folder in place; several
workspaces in one Explorer; an in-app Export (the Files app copies out); device-to-device transfer;
a terminal view of `/sdcard`; opening a single file from another app ("Open with iCode"). The
background freeze (R1) stays parked as the owner decided.
