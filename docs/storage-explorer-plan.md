# One Explorer for Private and Shared Storage (Plan)

Status: **built (2026-10-14), X1–X4; the walk-through on the phone is the owner's (§7)**. It follows [file-explorer-unification-plan.md](./file-explorer-unification-plan.md)
(shared item actions, E1–E3) and changes [workspace-storage-plan.md](./workspace-storage-plan.md) §1 and §4.5,
where Import was the only way between phone storage and iCode's and phone storage was never written.

Owner ask (2026-10-14, verbatim, with three ZArchiver screenshots): "Do that plan cover: user can perform
action eg: copy, cut from private to shared ? And do we design ui with consistency of both explore something
like reference design so both explore have constant design I think it's better to integration of native
library need research instead of building backend from scratch maintaining the consistency of ui.. also make
sure remove the components of the ui whi make bloat on ui make everything clean accurate and my intended scope
as I wanted to look like and real explorer for home screen we can just update navigation path as reference for
storage selection options we can keep shared and private as options ensure the real explorer... Tha ss is
zachiver app.. the goal is to make things better not over helming and no overengenering and bringing
limitations"

## 1. The answers

| Question | Answer |
|---|---|
| Does the plan cover copy and cut between Private and Shared? | **No.** The unification plan works inside iCode's storage only. Phone storage has its own browser, which only reads and lists folders. Import is its one way in, and there is no way out at all. This plan adds both directions (§4). |
| Do both explorers share one design? | **No, today they are two.** The Import browser is a list of folder cards with a breadcrumb and an Import button. The Workspaces page is a list of file cards with a breadcrumb, a search field and a Name/Date/Size switch. This plan makes them **one explorer**, modelled on ZArchiver, where Private and Shared are two places of the same list (§3). |
| A library instead of a backend built from scratch? | **The backend is already the platform's own library: `java.nio.file`**. It is Android's file API from API 26, and every copy, move, walk and delete in iCode already goes through it. No library on offer does what is left, and two cannot be used (§2). |
| Home screen | **Only where its tiles lead changes**: Workspaces opens the explorer at Private, and Import opens it at Shared (§3.5). |

## 2. Research — libraries (read, not assumed)

| Candidate | Licence | What it is | Verdict |
|---|---|---|---|
| `java.nio.file` (Android API 26+) | platform | `Files.walkFileTree`, `copy(COPY_ATTRIBUTES, NOFOLLOW_LINKS)`, `move`, `createDirectory`, `isSymbolicLink` | **Used, as today.** With All Files Access, `/storage/emulated/0` is an ordinary path, so one API serves both places |
| [SimpleStorage](https://github.com/anggrayudi/SimpleStorage) (`com.anggrayudi:storage`) | Apache-2.0 | A Storage Access Framework (`DocumentFile`) wrapper with copy/move/zip and conflict callbacks; minSdk 23 | **Not adopted.** It exists for apps *without* All Files Access. iCode has it, and wrapping plain paths in `DocumentFile` makes each file a ContentResolver query. It also knows nothing of what iCode must decide (below) |
| Apache Commons IO `FileUtils` | Apache-2.0 | `copyDirectory`, `moveDirectory` | **Not adopted.** It adds nothing over `java.nio.file`, and not following links, skipping and counting are still iCode's to do |
| [Material Files](https://github.com/zhanghai/MaterialFiles), [Amaze](https://github.com/TeamAmaze/AmazeFileManager) | GPL-3.0 (or later) | Whole file-manager apps | **Cannot be used.** iCode has no LICENSE (all rights reserved), and GPL code cannot be embedded in it |

What no library knows, so it stays iCode's own code (small, and already written and tested for Import):

- **Shared storage is FUSE.** It refuses symbolic links, ignores `chmod`, is `noexec` and case-insensitive
  ([workspace-storage-plan.md](./workspace-storage-plan.md) §2, measured).
- So a copy **to** Shared skips links and names that differ only by case, and counts them, instead of failing
  halfway.
- A copy **from** Shared is Import. A repository's checkout is restored as Git recorded it (`+x`, links).
- A workspace's rules apply: `.git` belongs to Git, an item in use is not moved, and a task holds its paths.

## 3. The explorer — ZArchiver's, in iCode's design system

One screen, `FilesScreen`, replaces both `WorkspacesScreen` and `ImportBrowserScreen`. Choosing a clone's
place is the same screen, Private only.

### 3.1 Top bar

```
 ←  app ▾                         🔍  ⇅
    /workspace/RevPlayer
```

- **Title**: the folder shown. At a place's top it is the place's name, **Private** or **Shared**.
- **Subtitle**: where that folder is, which is its parent's path, as ZArchiver shows it. At the top it is the
  place's own path: `/workspace` (the runtime's name for it), or `/storage/emulated/0`.
- **▾ on the title** opens the places menu, ZArchiver's dropdown, with the two places the owner chose:
  - **Private** (`/workspace`): iCode's storage, where workspaces live.
  - **Shared** (`/storage/emulated/0`): phone storage, which other apps see.

  The place shown is checked. Each place reopens where it was last.
- **🔍** opens a search field under the bar, for the folder shown; ✕ or Back closes it.
- **⇅** opens Sort: Name, Date or Size, with the current one checked. Folders always come first.

### 3.2 The list

- **`..`** is the first row whenever there is a folder above. A tap goes up, as Back does.
- **Flat rows**, not cards: a 24 dp type icon, the name, then a detail line. The detail is "12 items",
  "Empty" or a file's size; a workspace adds its branch ("12 items · main"). The date sits on the right.
- **A tap** goes into a folder. A file in Private opens in the editor. A file in Shared has no editor to open
  in, since iCode edits workspaces, so the tap opens its menu.
- **A long press** opens the item's menu, the same one in every explorer (`FileItemActions`): Open, [Open
  Terminal], Rename…, Cut, Copy, Paste *name*, Copy path, Delete…. Shared items show the same menu except
  Open, which exists only where there is something to open.
- **No ⋮ on rows**, in any explorer, the Explorer's tree included: a long press is how a file manager
  opens an item's menu. The home screen's workspace rows keep their ⋮; the home screen is not an explorer.

### 3.3 Floating +

- **+**, bottom right, offers New folder and New file, where a file can go.
- At the top of Private it offers New folder, which is a new workspace.
- Paste is not on +. The paste bar holds it, and the bar is always shown while something is held.

### 3.4 Bottom bars

- **The paste bar** (`ClipboardBar`, unchanged) says what is held and where Paste puts it. For example,
  "Copies into /storage/emulated/0/Download" or "Moves here as a workspace".
- **Choosing a clone's place** shows `ChooseBar`, as today.

### 3.5 Shared without All Files Access

The list area shows the permission's explanation, as the Import browser did. Allow goes to Android's page,
and Not now leaves. The wording now says what is true: iCode reads phone storage, and changes it only when
you paste, rename, delete or create something there.

### 3.6 Removed as bloat

- **The breadcrumb row.** The title, the subtitle and `..` replace it.
- **The search field always on screen.** It now opens from 🔍.
- **The Name · Date · Size switch.** It is now behind ⇅.
- **Cards, badges and ⋮ on rows.**
- **The + in the top bar.** It is now the floating +.
- **Import's own screens:**
  - its browser (folders only);
  - its "Import <folder>" button;
  - its name-and-destination page.

  Import is now **Copy in Shared, then Paste in Private**. At Private's top the folder becomes a workspace;
  in a folder it goes there. It keeps its own name, and Rename is in the menu.

## 4. Copy and cut between the places — the backend

### 4.1 The rules

| From → to | Copy | Cut |
|---|---|---|
| Private → Private | as today: walk and copy, links as links, "name copy" when taken | as today: one rename |
| Shared → Shared | the same walk (FUSE holds no links) | one rename |
| **Shared → Private** | **Import's engine**, described below | the same copy; the source is deleted only once all of it is copied |
| **Private → Shared** | counted, then copied. Links and names that differ only by case are skipped, counted and named in the result | **refused before anything is written** if anything would be skipped ("…copy it instead"); otherwise copy, then delete the source |

Import's engine (Shared → Private) works like this:

- it counts the source first, then refuses it if there is not room;
- a folder is staged in `.import-<n>` and renamed into place only once complete;
- links are skipped and counted, and times are kept;
- a repository's checkout is restored as Git recorded it.

The common rules for both directions between the places:

- **Counted first.** The walk lists what will be copied and what cannot be, and the space it needs. Nothing is
  written until the walk is complete.
- **A cut is all or nothing.** It is a copy, then a delete of the source, run without Cancel once the copy is
  whole. Stopped during the copy, it removes what it made, and the source is untouched. A whole workspace cut out
  of Private is released first and retired after, as a delete of it is.
- **Nothing is overwritten.** A copy takes a free name ("name copy"). A cut refuses a name already taken, and on
  Shared that check is case-insensitive because the filesystem is.
- **Everything a paste checks today still applies:** an item cannot go inside itself, an item in use is
  not moved, a task holds its paths, a file never becomes a workspace, and phone storage needs All Files
  Access.
- **The result** names what was not carried, if anything: "Copied RevPlayer — 3 items not copied (links, or
  names that differ only in case)".
- `+x` cannot exist on Shared (`noexec`, `chmod` ignored). It is not a loss for a repository: Git's repair restores
  it on the way back, as it does for links.

### 4.2 Shared storage in the explorer

Shared supports the same operations as Private: New folder, New file, Rename, Delete, Cut, Copy, Paste and
Copy path. One operations class serves both places (`WorkspaceFiles`, given the place's top). On Shared:

- `.git` is ordinary data;
- the top itself is never changed;
- nothing leaves it.

Every entry is listed, including those whose names start with a dot, as in Private. A link that leads out
of Shared is not followed.

### 4.3 Browsing

- **Navigation is by path.** `BrowseFiles(path)` goes to a folder, and `BrowsePlace(place)` goes to where that
  place was last.
- **Browsing is ordered.** The newest request is the one shown, because the folder is decided when the intent
  arrives and an older listing still running is cancelled. Before, two browse requests could cross.
- **All Files Access is followed.** Shared lists its folder once access is granted, and shows the explanation
  again once access is taken back.

## 5. Not done

- **No second clipboard, and no multi-select.** One item at a time, as the unification plan decided.
- **No editor outside a workspace.** A file on Shared is copied in to be edited.
- **No more places than the two the owner chose.** SD cards and USB drives are not added.
- **No "show hidden" switch.** Everything is shown, in both places.
- **No new dependency.**

## 6. Steps

| Step | What |
|---|---|
| X1 | This plan |
| X2 | Backend: `StoragePlace`; `FilesBrowserState(place, access)`; Shared listed and changed; the transfer engine (Import's, both ways); the cross-storage paste; the Import flow's intents, state, coordinator and routes removed |
| X3 | UI: `FilesScreen` (§3); `IcodeFab`; the top bar's title menu; a checked item and a detail line in the shared menu; tree rows without ⋮; the home tiles; the permission's wording; `ImportBrowserScreen`, `ImportScreen` and `FolderBreadcrumb` removed |
| X4 | Tests (the transfer's plan and copy both ways, the cross paste's rules, the places), guards, type-checks, Android 11 API check, docs as built, push |

## 7. As built (2026-10-14)

### Backend

| Piece | What |
|---|---|
| `domain/FilesBrowser.kt` | `StoragePlace` (Private, Shared); `FilesBrowserState(place, rootPath, path, items, access, sharedRoot)`. The title is `name` (the place at its top), the subtitle is `where`, and `..` is `parent`. `runtimePathOf` gives `/workspace/…` in Private and Android's own path in Shared. `FilesRules.detail` gives "12 items · main", "Empty", "1.2 KB" or "Folder" (a folder that cannot be read) |
| `data/storage/PhoneStorage.kt` | `contains`, `list` (every entry, links listed and never followed), `folder` (canonical, never outside) |
| `data/storage/StorageTransfer.kt` | Import's engine, renamed and made two-way. `plan(tree, caseInsensitive)` skips links, names a folder cannot hold, and, for Shared, a name that differs from another only in case. `copy` writes a file or a folder at a path that must not exist; stopped or failed, it removes what it made and never touches what was there |
| `data/workspace/StorageTransfers.kt` | The cross-storage paste (§4.1), as a task. Into Private a folder is staged (`.import-<n>`), Git's checkout is restored, and it is put in place with `WorkspaceStaging.complete`. A cut is refused before anything is written when it cannot carry everything, and the original is removed without Cancel only once the copy is whole |
| `data/workspace/WorkspaceFiles.kt` | `create`; `gitOwnsDotGit` (false on Shared); `freeName` shared with the transfers |
| `WorkspaceItemsCoordinator` | New file, New folder, Rename, Delete and Paste on Shared as in a workspace, each refused without All Files Access. A paste between the places goes to `StorageTransfers` |
| `WorkspaceLibraryCoordinator` | Lists both places. `BrowsePlace` reopens where a place was last. Browsing is ordered: the newest request is the one shown. It follows All Files Access |
| Removed | `ImportBrowserState`, `PhoneFolder`, `ImportSource`, `FolderCrumb(s)`, `PhoneStoragePaths`; the intents `OpenImportBrowser`, `BrowseImportFolder`, `ChooseImportFolder`, `ImportWorkspace`; the effect `ImportSourceChosen`; the snapshot's `importBrowser` and `importSource`; `WorkspaceImportCoordinator` |

### UI

| Piece | What |
|---|---|
| `ui/screens/entry/FilesScreen.kt` (was `WorkspacesScreen`) | §3: the title ▾ places menu, 🔍, ⇅, the floating +, the paste bar, the permission's explanation for Shared; choosing is Private only |
| `FilesExplorerParts.kt` | `..`, flat rows (icon, name, detail, date), a long-press menu; a file on Shared opens its menu on tap; workspaces at Private's top use the workspace menu without ⋮ |
| Design system | `IcodeTopBar(onTitleClick, titleMenu)`, `IcodeMenuAction(detail, checked)`, `IcodeFab`, `IcodeSizes.FabClearance` |
| Every explorer | No ⋮ on rows, the Explorer's tree included (its rows are their padded height again); + offers New folder and New file, and Paste is the bar's |
| Home | Import → `BrowsePlace(Shared)`; Workspaces → `BrowsePlace(Private)`; a recent workspace's "Show in Workspaces" opens the folder it is listed in. Settings › Storage › Workspaces opens Private |
| Routes | `ImportBrowser` and `ImportWorkspace` are gone. `Workspaces` draws Open Folder's frame (2003:117), so the design's 40 frames are still accounted for |
| Removed | `ImportBrowserScreen`, `ImportScreen`, `FolderBreadcrumb`, `NewWorkspacePage` (used by Import only) |
| Wording | All Files Access says iCode reads phone storage and changes it only when you paste, rename, delete or create there |

### Verified

- **Unit tests.** These ran here:
  - `StorageTransfersTest`: both directions; links and case-twins left out and named; a refused cut; a cut that
    removes the original; a file under a free name; no room; no access; a cancelled cut.
  - `StorageTransferTest` and `PhoneStorageTest`.
  - `WorkspaceFilesTest`: create, Shared mode and free names.
  - `FilesRulesTest`, `FileItemActionsTest`, `RouteTest` and `WorkspaceWatcherTest`.

  Of the 700 data and domain tests, 699 pass. The one failure is `TreeSizeTest`'s unreadable-folder case: this
  container runs as root, which can read the folder anyway. That code is untouched.
- **Type-checks.** The screens type-check against Compose's desktop jars: the explorer, the home screen, the
  permission's explanation and the Explorer's tree.
- **Other checks.** The guards pass, and the gateway's line allowlist is ratcheted to 352. The Android 11 API check
  shows nothing new.
- **Not checked here.** The phone itself has not been tested. The owner walks it through:
  1. Copy a folder on Shared and paste it at Private's top.
  2. Cut a file from a workspace into Download.
  3. Copy a repository out to phone storage and back. Its links and `+x` should return.
  4. Rename and delete on Shared.
  5. Turn All Files Access off, then on again.
