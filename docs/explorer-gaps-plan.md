# File explorer — selection, safety, archives and the rest (Plan)

Status: **plan** (2026-10-27). It follows [storage-explorer-plan.md](./storage-explorer-plan.md) and
[file-explorer-unification-plan.md](./file-explorer-unification-plan.md), and lifts what both left out:
multi-select, a "show hidden" switch, and one item at a time.

## 1. The owner's request (2026-10-27)

Nineteen problem statements, in six groups:

- **Selection and batch operations:** multi-select, bulk delete, bulk move and copy, select all and
  invert, batch rename. Note: "the behavior of the actions that what matters without any
  limitations".
- **Clipboard and transfer:** a direct "Move to…", Duplicate, and a choice when a paste meets a name
  already there.
- **File information:** a Properties screen, and storage analysis (largest files, duplicates, space
  by category).
- **View and display:** a grid view, a hidden-files switch, sorting by type. Note: "we can do it from
  application setting into storage ensuring that the storage page compact and clean new design
  removing unnecessary things keeping what really matters".
- **Navigation:** favorites, a tappable breadcrumb, recent files.
- **Safety:** undo and redo, and a trash. Note: "show undo/redo as current notification floating
  icon behavior ensuring that no overlap and maintaining consistency".
- **Archives:** compress and extract. Note: "make sure what we have instead of things rebuilding
  from scratch make sure proper research".

Then three screenshots of ZArchiver, "for UI/UX reference":
1. **An item's menu** is a sheet titled with its name: Compress to \*.zip, Compress to \*.7z,
   Compress…, Copy, Cut, Delete, Rename, Information, Add to Favorites.
2. **The top bar's ⋮:** Multi-select, Create ▸, Settings, About, Exit.
3. **The view button's panel:**
   - **View:** Detailed, Compact, Grid, and a Hidden toggle;
   - **Sort:** Name, Size, Date, Type, and a Desc toggle.

## 2. What is there now (read in the code, 2026-10-27)

| Area | Where | Today |
|---|---|---|
| The explorer | `FilesScreen`, `FilesExplorerParts` | One folder at a time, Private (`/workspace`) or Shared (`/storage/emulated/0`). Title ▾ switches place; 🔍 filters by name; ⇅ sorts Name, Date (default) or Size, kept only while the screen lives; flat rows; `..`; a long press opens a dropdown; + for New folder/file; the paste bar |
| An item's actions | `FileItemActions.of` | Open, Open with…, Share…, Open Terminal, Rename…, Cut, Copy, Paste *name*, Copy path, Delete…, one item, in every explorer |
| The IDE tree | `WorkspaceExplorerScreen`, `explorerItemActions` | The same list, from a long press, as a dropdown |
| The clipboard | `ItemClipboard(location, name, cut, folder, workspace)` | **One item** |
| Paste | `WorkspaceItemsCoordinator.paste` (300 lines, the backend budget) | A copy takes a free name ("name copy"); a cut refuses a name already there; across the places, `StorageTransfers` |
| Delete | `items.delete`, `lifecycle.delete` | **Permanent**, after a confirm dialog, as a task |
| Long work | `TaskRunner` | Counted, claimed paths, progress, cancel |
| Listings | `WorkspaceFileSource.listChildren` (Private, the tree), `PhoneStorage.list` (Shared) | Every name shown, dotfiles too; `.git` never listed in a workspace |
| Recent files | `WorkspaceStore` | Files opened in the editor, 100 in all, 20 per workspace; shown only on the editor's empty page |
| Sizes | `data/io/TreeSize.measureTree` | Walks without following links; an unreadable folder makes the size unknown |
| Digests | `data/io/Sha256.hexDigestOf(file)` | SHA-256 of a file |
| Archives | `data/runtime/ArchiveExtractor`, on **Apache Commons Compress 1.27.1** (already a dependency) | Unpacks tar.gz and zip for the runtime's install and its tools, from pinned, checksum-verified archives |
| Settings | DataStore (`AndroidWorkspacePreferencesRepository`) | Appearance, editor and terminal values |
| Settings › Storage | `StorageSettingsScreen` | A usage card, a Workspaces section with a caption, All Files Access, a full-width Clear Cache |
| The bell | `NoticeHost` | Bottom right, a fixed 88 dp above the screen's foot (`IcodeSizes.FabClearance`) |

Facts the decisions stand on, each checked:

- **The bell can already sit on the +.** With the paste bar shown, the + floats over it. The bar
  (`IcodeActionBar`) is at least 64 dp: a 48 dp touch target with 8 dp above and below. The + is
  56 dp, 16 dp above the bar. So the + reaches at least 136 dp up, where the bell sits at 88 dp.
- **Android gives apps no creation date.** In Android's libcore (read at LineageOS 21),
  `UnixFileAttributes.creationTime()` returns the modified time unless birth time is supported, and
  `UnixNativeDispatcher.c` turns that on only for macOS (`_DARWIN_FEATURE_64_BIT_INODE`).
  `android.system.Os.stat` has no birth time either.
- **Commons Compress reads and writes zip, tar, gzip and bzip2 by itself.** For xz and 7z's LZMA it
  needs its own optional dependency, `org.tukaani:xz`. Its POM names 1.10: 0BSD, 168 KB, pure Java.
  Read in its 1.27.1 jar:
  - it decrypts 7z with a password (`SevenZFile.Builder.setPassword`, `AES256SHA256`);
  - it cannot decrypt an encrypted zip (`UnsupportedZipFeatureException.Feature.ENCRYPTION`);
  - its archivers are ar, arj, cpio, dump, jar, 7z, tar and zip: no rar;
  - a 7z entry has Windows attributes only: no Unix mode, so no link and no executable bit.
- **minSdk is 30.** `ThumbnailUtils.createImageThumbnail` and `createVideoThumbnail` (API 29) are
  there, Android's own.
- **The workspaces live in `files/workspaces`.** So `files/trash` is on the same filesystem, and a
  move between them is a rename.
- **Phone storage is FUSE** ([workspace-storage-plan.md](./workspace-storage-plan.md) §2, measured).
  It refuses symbolic links, ignores `chmod` and ignores case.
- **No more lines fit the gateway and the intents.** The gateway (339 lines) is already over the
  300-line budget on the allowlist, and `ICodeIntent.kt` is at 296.

## 3. Decisions

### 3.1 One action sheet, for one item or many (reference 1)

- **A long press opens a sheet**, not a dropdown, in every explorer.
  - Titled with the item's name, its detail under it ("12 items · main", "1.2 MB").
  - For a selection: "3 selected", with "2 folders, 1 file" under it.
- It is one component for both explorers and both cases, built by `FileItemActions`.
- **Its rows, in this order:**
  1. Open, Open with…, Share…, Open Terminal — where each applies, as today;
  2. Cut, Copy, Paste *name*, Move to…, Copy to…, Duplicate;
  3. Rename…;
  4. Compress to *name*.zip, Compress to *name*.7z, Compress… — or, on an archive, Extract here,
     Extract to…;
  5. Add to Favorites (a folder);
  6. Copy path, Properties;
  7. Select;
  8. Delete, last and apart.
- The workspaces at Private's top use the same sheet, with a workspace's extras. The home screen's
  workspace ⋮ is not an explorer and stays a dropdown.

### 3.2 Selection

- **Into selection:**
  - a tap on an item's icon (ZArchiver's way);
  - Select in its sheet;
  - ⋮ › Select.
- **In selection:**
  - a tap on a row ticks it or unticks it;
  - `..` and the + are hidden;
  - Back, or ✕ where ← was, leaves selection.
- **Top bar:** where you are, as always, with Select all and Invert.
  - Select all ticks every item shown, so with a search open, those it found.
  - Invert ticks those shown and not ticked.
- **Foot bar** (`IcodeActionBar`, as every foot bar):
  - "3 selected", with "2 folders, 1 file · 12.4 MB" under it;
  - Cut, Copy and Delete, and ⋯ for the selection's sheet.
- **A selection belongs to the folder shown.** Going elsewhere ends it.
- **In the IDE tree:** Select in the sheet does the same, with ticks on its rows. A folder ticked
  covers what is in it: its items are dropped from the selection, so nothing is acted on twice.

### 3.3 Every action on many (the owner's "without any limitations")

| Action | One | Many |
|---|---|---|
| Open, Open with… | as today | not offered: Android opens one thing at a time |
| Share… | a file | every file, in one share (`ACTION_SEND_MULTIPLE`); not offered with a folder ticked |
| Cut, Copy | held | all held, in one clipboard |
| Move to…, Copy to… | the folder picker (§3.5) | all, in one operation |
| Duplicate | "name copy" beside it | each beside itself |
| Rename… | the rename dialog | batch rename (§3.4) |
| Compress… | *name*.zip | one archive named after the folder shown ("Archive" at a place's top) |
| Extract | into its folder | each archive into its own |
| Add to Favorites | the folder | every folder ticked |
| Copy path | the path | the paths, one per line |
| Properties | §3.9 | the selection's count and total size |
| Delete | to Trash | all to Trash, one step to undo |

### 3.4 Batch rename

- **One rule at a time**, chosen with a segmented control:
  - **Replace:** text found becomes other text, matching case or not.
  - **Add:** text before or after the name, before the extension.
  - **Number:** a name and a number ("Photo 1", "Photo 2"…), from a start you choose, padded to
    the widest. The order is the one shown.
  - **Extension:** the extension becomes another, or goes.
- **A preview** shows each old name beside its new one, and why a new name cannot be:
  - empty, or a character a name cannot hold;
  - two items given the same name;
  - a name already taken by something not being renamed.
- Rename is offered only when no name is refused.
- The rules are pure domain code, tested.
- **One step to undo.**

### 3.5 Clipboard, Move to, Copy to, Duplicate

- **The clipboard holds many.** Its items, and whether they are cut. The paste bar says "3 items" and
  where Paste puts them. The rules of today hold for each item: not into itself, a file never a
  workspace, phone storage needs All Files Access, an item in use is not moved, a task holds its
  paths. Across the places, `StorageTransfers` carries each item, as today.
- **Move to… and Copy to…** open the explorer as a folder picker: folders only, both places.
  - Its foot bar says "Move here" or "Copy here", and what goes ("3 items move into
    /workspace/app/src").
  - At Private's top, "Move here as workspaces", for folders.
  - This is today's clone-destination picker, generalized. The clone's "Place here" becomes one of
    its uses.
- **Duplicate** is a copy into the item's own folder, under the name it already takes ("name copy").

### 3.6 When a name is already there

Today a copy takes a free name without asking, and a cut refuses. Instead, the operation asks, in a
sheet, for each item whose name is taken: the two side by side, with size and date.

| Meeting | Choices |
|---|---|
| A file, or a file and a folder | **Replace** (the one there goes to Trash), **Keep both** ("name copy"), **Skip** |
| A folder and a folder | **Merge** (what is in it goes in, asking the same way), **Keep both**, **Skip** |

- **"Do this for the other N"** answers the rest.
- **✕ stops** the operation. What it did stays, as one step to undo.
- **The task waits** while the sheet is open, its step saying "Waiting for your answer".
- **In a workspace, `.git` stays Git's.** A merge leaves an existing `.git` as it is and skips the one
  coming, and the result says so.
- **Duplicate never asks.** Its name is always a free one.

### 3.7 Trash

- **Delete moves to Trash, without a dialog.** It can be undone (§3.8). This keeps the design
  system's rule: a destructive action gets a confirm "rather than a hidden gesture: … no way back".
  There is a way back now. **Delete permanently** and **Empty Trash** still ask first.
- **One trash per place,** so a delete is a rename on the same volume, never a copy.

  | Place | Trash | Why there |
  |---|---|---|
  | Private | `files/trash`, beside `files/workspaces` | the same filesystem; outside `/workspace`, so the runtime, the assistant and Android's Files app do not see it |
  | Shared | `Android/media/<package>/Trash` on phone storage, with a `.nomedia` in it | Android removes it with iCode; `.nomedia` keeps trashed pictures out of galleries; a move there should be a rename on phone storage's own volume |

- **Each trashed item:** `files/<id>`, the item itself, and `info/<id>.json`, which holds:
  - its name and where it was;
  - when it was deleted;
  - whether it is a folder, and its size;
  - whether it was a workspace.
- **The Trash view** lists both places' items, newest first: the name, where it was, how long ago,
  and the size.
  - A tap opens the item's sheet: Restore, Delete permanently, Properties.
  - Selection works as everywhere: Restore and Delete permanently, for many.
  - Its top bar has Empty Trash.
- **Restore** puts an item back where it was:
  - folders gone meanwhile are made again;
  - a name taken there asks, as §3.6;
  - a workspace comes back a workspace.
- **30 days.** An item is removed after 30 days in Trash, at start and when Trash opens. The page says
  so.
- **A whole workspace** goes to Trash as it is deleted today: released, its tabs closed, its recent
  files forgotten.
- **To confirm on the phone** (§7, step 1): Android 11 and later mount `Android/data` and
  `Android/obb` apart for each app; `Android/media` is not among them, so a move into Shared's trash
  should be a rename, as a move between two folders of phone storage is. If the phone shows otherwise
  (an `EXDEV`), the delete fails and says so, with nothing removed. The fix is then to put Shared's
  trash in a hidden folder at phone storage's top, which is surely the same volume.
- **Shared's trash needs All Files Access,** as everything on Shared does. Without it, Trash lists
  Private's items and a line asks for access.
- **Not iCode's to trash:** deletes by the terminal, the assistant, Git, or Android's Files app.

### 3.8 Undo and redo

- **What can be undone.** Every file change made in iCode's explorers and their dialogs:
  - new folders and files;
  - renames, one or many;
  - moves;
  - copies, Duplicate and Copy to;
  - deletes to Trash, and restores;
  - compress and extract.
- **What cannot.** Delete permanently and Empty Trash, which ask first. The editor keeps its own undo.
  Changes by the terminal, the assistant or Git are not iCode's explorer's.
- **How it works:**
  - Each operation is kept as what it did: "moved a to b", "made c", "trashed d as id".
  - Undo reverses it. Moved items go back; trashed items are restored. Made items go to Trash, so
    undoing a copy can be undone too and nothing is ever lost.
  - Redo does it again.
  - A change made after an undo clears redo.
- **Checked first.** Undo and redo first check that what they touch is as it was left: the names, the
  places. If not, they say what changed ("README.md was moved since") and the history stays.
- **The last 50 steps, while iCode runs.** History is not saved; Trash keeps what was deleted anyway.
- **Where it shows:**
  - **The floating stack.** The bell and the undo pill share one column at the bottom right: the
    pill above the bell, or alone in the bell's place.
    - The column sits above the highest of the screen's + and foot bars, and at least 88 dp up as
      today.
    - Each + and foot bar of the design system (`IcodeFab`, `IcodeActionBar`, `IcodeBottomBar`)
      reports its top edge to the stack. So no screen wires it, and the bell's overlap with the +
      (§2) goes too.
  - **The pill**, as the bell (owner, "notification floating icon behavior"):
    - after a change: ↶, the change's line ("Moved 3 items to Trash") and **Undo**;
    - after 6 s, the line slides away and the pill holds ↶, and ↷ when there is something to redo,
      with × beside them;
    - 10 s later it goes, as the bell does.
    - Undone, its line says what came back ("Restored 3 items"), with **Redo**.
    - Not shown while a sheet or the Notifications page is open.
    - TalkBack reads its line as the bell's.
  - **The explorer's ⋮** keeps "Undo *what*" and "Redo *what*" for later.

### 3.9 Properties

- **One item:**
  - **Name.**
  - **Kind:** Folder, Workspace (with its branch), Link (with where it points), or the file's type
    and extension.
  - **Location:** the runtime path, with Copy.
  - **Size:**
    - a file: "1.2 MB (1,234,567 bytes)";
    - a folder: measured as it is opened, "340 MB · 1,234 files, 56 folders". Unknown when a folder
      inside cannot be read, as `measureTree` says.
  - **Modified:** date and time, and how long ago.
  - **Executable,** for a file in Private that is.
- **Many:** "3 items · 2 folders, 1 file", their total size, measured, and the folder they are in.
- **No "Created".** Android does not give it (§2). Showing the modified time under that name would be
  untrue.
- It opens from the sheet and the Trash view (Properties), as a sheet over the explorer.

### 3.10 Storage analysis

- **A page.** It opens from Settings › Storage (Analyze storage) and from the explorer's ⋮ (Analyze
  this folder). It works on the folder shown, or a place's top.
- **One walk:** links not followed, `.git` counted (it takes space). It shows "Scanned 12,340 files"
  as it goes, and stops when the page is left. The result stays until the next run.
- **What it shows:**
  - The total, and a bar of **space by category**, with a legend: Images, Videos, Audio, Documents,
    Archives, Apps, Code, Other.
    - The categories come from one domain table, built on `FileTypes`, by extension. The archive
      formats there are the ones Extract reads (§3.11).
    - Their colours are design-system tokens.
  - **The largest files,** the first 20. A tap shows the file in its folder, ticked.
  - **Duplicates:** files of the same size, then the same SHA-256 (`hexDigestOf`), grouped, with the
    space the extra copies take.
    - A group opens its copies, to tick some and Delete them, to Trash.
    - `.git` is left out: it is Git's.

### 3.11 Archives — on what iCode has

- **Reused:**
  - **Commons Compress,** already a dependency;
  - **`ArchiveExtractor`'s tar and zip reading,** moved to `data/io` as one archive reader that the
    runtime's install and the explorer share. It gains what untrusted archives need:
    - every entry must land inside the destination (its canonical check, kept);
    - a link is made only in Private, and only when it points inside what is extracted; any other
      is skipped and counted;
    - device nodes are ignored, and hard links copied (as now);
    - on Shared, links and names that differ only in case are skipped and counted, as transfers do.
- **Added:** `org.tukaani:xz:1.10`, Commons Compress's own optional dependency: 0BSD, 168 KB, pure
  Java. It is what opens .7z and .xz.
- **Extract reads:**
  - .zip, and .jar and .apk, which are zips;
  - .tar, and .tar.gz/.tgz, .tar.bz2/.tbz2 and .tar.xz/.txz;
  - .gz, .bz2 and .xz holding one file;
  - .7z, asking for its password when it has one.
- **Extract does not read,** and says so on the file:
  - **.rar:** Commons Compress has no rar, and the free decoders carry UnRAR's licence limits.
  - **.zst:** it needs zstd-jni, a native library, not added.
  - **An encrypted zip:** Commons Compress cannot decrypt one.
- **Extract here:**
  - An archive whose entries all sit in one folder gives that folder.
  - Any other goes into a folder named after the archive.
  - At Private's top, either becomes a workspace.
- **Extract to…:** the folder picker (§3.5).
- **How it runs:**
  - **As a task,** into a staged folder, put in place only when complete. Stopped or failed, nothing
    is left.
  - **Counted first:** a zip or a 7z from its own index, with the space it needs checked first. A
    tar is a stream: it is counted as it is read, and free space is checked as it goes.
  - **A name already there** asks, as §3.6.
- **Compress:**
  - **"Compress to *name*.zip"** and **"Compress to *name*.7z":** one tap, as the reference.
  - **"Compress…":** a dialog with the name, and the format: Zip, 7z or Tar.gz.
  - It goes into the folder shown, under a free name.
  - **Links and executable bits:** zip and tar.gz keep both. A 7z entry can hold neither (§2), so a
    7z skips links and says so.
  - Compressing a workspace includes its `.git`: reading is not changing it.
- **Undo:** an archive made goes to Trash; what was extracted goes to Trash.

### 3.12 View and sort (reference 3)

- **One button, ☰, opens a panel**, as ZArchiver's:
  - **View:** Detailed, Compact, Grid, and **Hidden**;
  - **Sort:** Name, Size, Date, Type, and **Descending**.
- **The views:**
  - **Detailed:** today's row.
  - **Compact:** one line, a smaller icon, the size or date at its end.
  - **Grid:** tiles in as many columns as fit (at least 104 dp each), the icon or a thumbnail, the
    name on two lines.
- **Thumbnails, in every view:**
  - images and videos, by Android's `ThumbnailUtils` (§2);
  - an APK's own icon, by `PackageManager.getPackageArchiveInfo`.
  - They are made off the main thread and kept in memory by path and modified time.
- **Sorting:**
  - **Name:** A→Z. **Size:** largest first. **Date:** newest first. **Type:** by extension, then
    name, with a label over each group (".kt · 12"), so files of one kind come together.
  - Folders always come first.
  - **Descending** flips the order of each.
- **Remembered:** view, sort and order are kept app-wide in DataStore, beside the other settings. Now
  they last only while the screen does.

### 3.13 Hidden files

- **One setting, "Show hidden files",** on by default, as today. Two places show the same value:
  - Settings › Storage (the owner's ask);
  - the panel's Hidden (the reference).
- **Hidden** is a name starting with ".".
- **It applies** to both explorers, the folder picker, and what Select all ticks.
- **It does not apply** to Recent, Trash or storage analysis, which are about what you opened,
  deleted or stored.
- **`.git` in a workspace stays unlisted,** whatever the setting: it is Git's.

### 3.14 Navigation

- **The breadcrumb.** The subtitle becomes the path, in segments ("/workspace › RevPlayer › src").
  - A tap on a segment goes there.
  - A long path scrolls, its end in view.
  - The title stays the folder shown, and ▾ stays on it.
- **▾ becomes "go to":**
  - **the two places:** Private, Shared;
  - **Favorites:** each pinned folder, with its path;
  - **Recent;**
  - **Trash,** with how many it holds.
- **Favorites:**
  - Add to Favorites, or Remove from Favorites, in a folder's sheet.
  - Kept app-wide.
  - A favorite follows a rename or move made in iCode, as recent files do.
  - One whose folder is gone is not shown. It is kept, so a restore brings it back.
- **Recent:**
  - **What it holds:** the files opened in iCode — the editor, the image viewer, Open with — across
    workspaces and phone storage, newest first.
  - **Where it comes from:** the recent-files store there is now. It also records phone storage's
    opens, and a save in the editor moves a file to the top.
  - **What it leaves out:** a file that is gone.
  - **Using it:** a tap opens the file, as in its folder; its sheet adds Show in folder.
- **⋮** (reference 2): Select, Undo *what*, Redo *what*, Analyze this folder, Storage settings. Create
  is the +'s.

### 3.15 Settings › Storage, compact

```
 STORAGE
 [██████████░░░░░░]  57.3 GB used · 48.7 GB free of 106 GB
 Workspaces                2.1 GB  ›    removed with iCode unless you keep its data
 Runtime                   3.4 GB
 Trash                     120 MB  ›
 Cache                      80 MB  Clear
 Analyze storage                   ›
 FILES
 Show hidden files                [on]
 PHONE STORAGE
 All Files Access            ALLOWED ›
```

- **The usage card and its sizes become one list.**
  - Workspaces opens Private.
  - Trash opens Trash.
  - Cache's Clear asks first, as it does now.
- **Removed:**
  - "Device storage, including other apps and files": the bar's line says it.
  - The Workspaces section's caption: its words are now the row's.
  - Cleanup Operations and its full-width button: Clear is on the Cache row.

## 4. Backend

- **New, each under the 300-line budget:**

  | File | What |
  |---|---|
  | `data/files/FileTrash.kt` | Put, list, restore, delete, empty, the 30 days; both trashes |
  | `data/files/FileBatch.kt` | Copy, move, Duplicate, Move to and Copy to of many, with the §3.6 question; across the places, `StorageTransfers` per item |
  | `data/files/FileHistory.kt` | The steps, undo, redo, the check before each |
  | `data/files/FileArchives.kt` | Compress and extract, as tasks |
  | `data/io/Archives.kt` | Reading and writing; from `ArchiveExtractor`, which the runtime keeps using |
  | `data/files/FileFacts.kt` | Properties |
  | `data/files/StorageAnalyzer.kt` | The walk: categories, the largest, duplicates |
  | `data/files/ExplorerPreferences.kt` | Hidden, view, sort, order, favorites, in DataStore |

- **Coordinators:**
  - `WorkspaceItemsCoordinator` keeps new and rename.
  - A new `FileOperationsCoordinator` takes:
    - the clipboard;
    - paste, Move to, Copy to, Duplicate;
    - Delete to Trash, and Trash's own actions;
    - undo and redo;
    - the question.
  - `FileInsightsCoordinator` takes properties, analysis and favorites.
  - The gateway hands them one intent group, `FilesIntent`, in its own file, in one line.
- **Listings:** the hidden filter goes where each place is listed: `WorkspaceFileSource.listChildren`
  for Private and the tree, `PhoneStorage.list` for Shared.
- **Snapshot:**
  - the clipboard, with its items;
  - the explorer's settings and favorites;
  - Recent;
  - Trash;
  - the question waiting;
  - undo and redo, each with what it would do;
  - properties being measured;
  - the analysis.
- **Removed:**
  - `DeleteWorkspaceItemDialog` and `DeleteWorkspace`'s confirm: Delete is to Trash now.
  - The single-item `CutItem` and `CopyItem`: they take a list.
  - `ChooseDestination`'s own picker: it is one use of the folder picker.

## 5. Not done, and why

- **.rar, .zst, encrypted zips:** §3.11.
- **A creation date:** §2 and §3.9.
- **Android's own trash.** MediaStore's trash works on media files only, asks the user each time
  (`createTrashRequest`), and holds no folders.
- **Undo across a restart:** history lives in memory. Trash keeps what was deleted for 30 days.
- **Other places:** SD cards, USB, the network. The two places stay the owner's.
- **Trash for deletes by the terminal, the assistant or Git:** they are not the explorer's.

## 6. Cycles

| Cycle | What | Done when |
|---|---|---|
| **F2** Domain | The many-item clipboard; selection rules (ticks, a folder covering its items); batch rename rules; sort by Type with groups, and Descending; categories; explorer settings; the question; Trash and history models; `FilesIntent` | Tests: each rename rule and its refusals; covering; Type groups; both orders; categories |
| **F3** Operations | `FileTrash`, `FileBatch`, `FileHistory`, `FileOperationsCoordinator`; Delete to Trash in every explorer, workspaces too; the question; the 30 days | Tests on temporary Private and Shared folders: many copied, moved and duplicated; each answer to the question; a merge leaving `.git`; trash and restore with folders gone and names taken; undo and redo of each operation, and the refusal when something changed |
| **F4** Archives and facts | `Archives` (moved, with the guards), `FileArchives`, xz; `FileFacts`; `StorageAnalyzer`; `ExplorerPreferences`, favorites following moves; Recent across places; the hidden filter | Tests: every format both ways where §3.11 says so; an entry escaping, a link leading out, case twins on Shared; a 7z with its password; facts; categories, the largest and duplicates; favorites after a rename |
| **F5** The explorer | The sheet; selection and its bars; the panel, three views, thumbnails; the breadcrumb; ▾ with Favorites, Recent, Trash; ⋮; the folder picker; the batch rename, compress, question and properties sheets; the Trash and Recent views | UI type-check; touch-driven checks in a desktop scene: select, select all, invert, cut and paste many, the question, rename many, the panel's choices; renders of each |
| **F6** Everywhere else | The IDE tree's sheet and selection; the floating stack (the undo pill, the bell, the floors); Settings › Storage; the analysis page | UI type-check; the stack over a + and a foot bar; renders |
| **F7** Gate | Docs as built (PRD, user stories, layout, the two plans this follows); the full gate | Push the branch and `main` |

## 7. On the phone (the owner's walk-through, after F7)

1. Delete a folder on Shared and in a workspace. Undo from the pill, then restore one from Trash.
   This proves the move into `Android/media/<package>/Trash` is a rename on the phone.
2. Select ten photos and share them; see their thumbnails in Grid.
3. Paste a folder onto one of the same name: Merge, then Keep both.
4. Extract a .7z and a .tar.gz; compress a workspace to .zip and open it in another app.
5. Turn Show hidden files off in Settings › Storage, and see the panel's Hidden follow.
