# File explorer — selection, safety, archives and the rest (Plan)

Status: **built (2026-10-28), F2–F7; the walk-through on the phone is the owner's (§7)**. It follows [storage-explorer-plan.md](./storage-explorer-plan.md) and
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
  by category). The owner then asked for a page to manage storage: Trash and each type of file,
  each listed on its own (§3.10).
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
| Other item menus | editor tabs, Source Control's changes, the Run tab, the assistant's steps and messages, the home screen's workspaces | Dropdowns (`IcodeActionMenu`), from a long press or a row's ⋮ |
| Sheets | `IcodeBottomSheet` over `IcodeSheetOverlay` | The one base of every sheet, in eleven files: title, subtitle, ← back, scrolling, foot |
| Label and value rows | `DetailRow` | The Runtime and Language pages' facts |
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

### 3.1 One sheet for every item's actions, built once (reference 1; owner, 2026-10-27)

The owner, after the plan: "the actions menu need to migrate from drop-down to sheet like zachiver
design: the same sheet handles things like files folder info and others things scalable sheet
(existing base sheet need to make reusable across the variety areas as per basis for 0 duplicacy)".

- **The rule:**
  - **A menu of actions on one thing, or on a selection, is a sheet.** It opens from a long press, a
    row's ⋮, or a selection's ⋯, and is titled with what it acts on, as ZArchiver's.
  - **A menu on a bar's own button stays a dropdown,** anchored to the button, as ZArchiver's ⋮ and
    view panel (references 2 and 3):
    - a top bar's ⋮;
    - ▾;
    - + (the explorer's and the tree's);
    - the view panel;
    - the assistant composer's choosers;
    - the terminal's More… in its text-selection bar.
- **One base, made reusable, nothing drawn twice:**
  - **`IcodeBottomSheet` over `IcodeSheetOverlay` stays the one base.** It already gives every sheet
    its title, subtitle, ← back, scrolling and foot. Every sheet in the app is drawn on it, from
    eleven files: the assistant's chats, models, attachments, processes and steps; a notice; a
    picture; the editor's changes; the branch and repository selectors; Git history; the
    assistant's model settings.
  - **New in the design system, on that base: `IcodeActionSheet`.**
    - Its head: the item's icon or thumbnail, its name, and its detail ("12 items · main",
      "1.2 MB"). For a selection: "3 selected", with "2 folders, 1 file".
    - Then the actions, as rows: icon, label, a detail where one helps ("/workspace/app"), greyed when
      not possible, ✓ when chosen.
    - Groups have a line between them, and the destructive action comes last, in red.
    - It takes the same `IcodeMenuAction` lists the dropdowns take: one action model for both, so
      a list is never written twice.
  - **Pages within the sheet.** An action that needs more than a tap opens its page in the same
    sheet, with ← back to the actions:
    - Properties (§3.9);
    - Rename…, and batch rename (§3.4);
    - Compress… (§3.11);
    - Select text (the assistant's message).

    Back steps back a page, then closes the sheet. One helper, `rememberSheetPages`, holds the page
    stack and the Back handler. The assistant's run sheet does this by hand today; it moves onto the
    helper.
  - **Info pages** are `DetailRow`s, the design system's label and value row, already used by the
    Runtime and Language pages. A path has Copy beside it.
- **The forms that stay dialogs:**
  - New file and New folder: they come from +, not from an item;
  - the confirm before Delete permanently and Empty Trash: the design system's rule for what cannot
    be undone.
- **Every item menu moves to the sheet:**

  | Where | Today | The sheet |
  |---|---|---|
  | The explorer's rows, the workspaces at Private's top | long press, dropdown | the item's actions (§3.3) |
  | The home screen's workspace rows | ⋮ and long press, dropdown | the same workspace sheet as the explorer's |
  | The IDE tree's rows | long press, dropdown | the item's actions |
  | A selection, in either explorer | — | its ⋯ (§3.2) |
  | Trash and Recent | — | Restore, Delete permanently, Properties; Show in folder and the item's actions |
  | The editor's tabs | long press, dropdown | the tab's actions |
  | Source Control's changes | long press, dropdown | Open file, Discard changes… |
  | The Run tab's commands | long press, dropdown | Edit, Delete |
  | The assistant's step rows | long press, dropdown | Open in Editor, Show Changes, Copy path |
  | The assistant's message | long press, dropdown | Copy, and Select text as its page |

  The `IcodeActionMenu` dropdowns those used are left only where the rule keeps them.
- **The explorer's sheet, in this order:**
  1. Open, Open with…, Share…, Open Terminal — where each applies, as today;
  2. Cut, Copy, Paste *name*, Move to…, Copy to…, Duplicate;
  3. Rename…;
  4. Compress to *name*.zip, Compress to *name*.7z, Compress… — or, on an archive, Extract here,
     Extract to…;
  5. Add to Favorites (a folder);
  6. Copy path, Properties;
  7. Select;
  8. Delete, last and apart.

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
| Rename… | the sheet's Rename… page | batch rename (§3.4) |
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
- It is the selection sheet's Rename… page (§3.1), the preview scrolling above the rule's fields.

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
sheet on the same base (§3.1), for each item whose name is taken: the two side by side, as
`DetailRow`s of size and date.

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
- **The Trash view** is one of the explorer's views (§3.10). It lists both places' items, newest
  first: the name, where it was, how long ago, and the size. It opens from ▾, from Manage storage,
  and from Settings › Storage.
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

- **What can be undone.** Every file change made in iCode's explorers and their sheets:
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
    - after a change: ↶, and ↷ when there is something to redo, each saying what it would do, with
      × apart at its top end — icons only since 2026-11-04 (owner's request: a long name stretched
      the pill);
    - 10 s later it goes, as the bell does.
    - Not shown while a sheet or the Notifications page is open.
    - TalkBack reads the change ("Moved 3 items to Trash", "Restored 3 items") as the bell's.
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
- It is the action sheet's Properties page (§3.1), with ← back to the actions; from Trash and Recent
  too. Its rows are `DetailRow`s.

### 3.10 Manage storage, and the explorer's views (owner, 2026-10-27)

The owner, after the plan: "design the page for manage storage where user can see trash list and all
and each type individually maintaining file explorer consistency and better consistent ui/ux and
ensuring best user experience and application behavior".

**The explorer's views.** The explorer shows a folder, or one of these lists of files gathered from
anywhere. Each view is the explorer itself, not a page of its own.

| View | What it lists |
|---|---|
| **Recent** | the files opened in iCode (§3.14) |
| **Trash** | what was deleted, from both places (§3.7) |
| **A category** | every file of one kind: Images, Videos, Audio, Documents, Archives, Apps, Code, Other |
| **Largest files** | the 100 largest, the largest first |
| **Duplicates** | files with the same contents, in groups |

- **The same as a folder:**
  - its rows and tiles;
  - the ☰ panel's views and sorts (§3.12), so Images and Videos can be a grid of thumbnails;
  - 🔍;
  - selection and its bars;
  - the action sheet;
  - the floating stack.
- **Different from a folder,** because a view gathers files from many folders:
  - **Its title** is the view's name. Its subtitle says where it looked, and how much it found
    ("All · 1,204 files · 3.2 GB").
  - **Each row's detail** is the folder the file is in, with its size.
  - **Not shown:** `..`, the +, the breadcrumb and the paste bar. Nothing is made or pasted into a
    view.
  - **The action sheet adds Show in folder,** which opens the folder with the file ticked.
- **Each view keeps its own sort and view.** Categories open sorted by size; Images and Videos open
  as a grid; Recent and Trash open newest first. Changing one is kept for that view only.
- **Duplicates** shows each group under a label ("3 copies · 24 MB each"), the oldest first.
  - Its ⋮ adds **Select extra copies**: every copy but the oldest in each group, ready to Delete.
- **Trash** keeps §3.7's own actions: Restore and Delete permanently, and Empty Trash in its top bar.
- **They update with iCode's own changes.** A file deleted, moved or renamed in iCode leaves or
  changes its rows at once. A change made elsewhere (the terminal, another app) shows after ↻ on
  Manage storage.

**The Manage storage page:**

```
 ←  Manage storage                                   ↻
    [  All  |  Private  |  Shared  ]
    [█████████████▒▒▒▒▒▒▒░░░░░]
    9.4 GB in 12,340 files                    Scanned now
 CATEGORIES
  ▣ Images        1,204 files      3.2 GB  ›
  ▶ Videos           48 files      2.4 GB  ›
  ♪ Audio           310 files      1.1 GB  ›
  ▤ Documents       220 files      340 MB  ›
  ▥ Archives         35 files      2.0 GB  ›
  ▦ Apps             12 files      800 MB  ›
  ⟨⟩ Code         8,000 files      600 MB  ›
  ⋯ Other         2,100 files      1.5 GB  ›
    Git history (.git)             700 MB
 CLEAN UP
  ⇣ Largest files                  9.8 GB  ›
  ⧉ Duplicates    14 groups   1.2 GB extra  ›
  🗑 Trash          23 items       120 MB  ›
```

- **Where it opens:**
  - Settings › Storage (Manage storage);
  - the explorer's ⋮ (Manage storage).
- **Where it looks.** A segmented control: **All**, **Private** or **Shared**, All first.
  - Without All Files Access, Shared's part is a line asking for it, as Shared asks in the
    explorer.
- **The bar** stacks the categories in their colours, design-system tokens, with the total and the
  file count under it.
- **Each row** is the view of the same name: its icon in the category's colour, how many, how much,
  and ›.
  - A category with no files is shown greyed, with no ›.
  - **Git history** is the space `.git` takes in the workspaces. It is shown for the total to add
    up, but opens nothing: it is Git's, and its files are in no category.
- **Clean up:**
  - **Largest files.**
  - **Duplicates,** with the space the extra copies take.
  - **Trash,** with how many it holds and their size.
- **One scan for all of it.**
  - One walk of where it looks, links not followed; the hidden-files setting does not apply.
  - **It leaves out Trash's folders,** which have their own row. Shared's trash lies inside phone
    storage, so otherwise trashed files would count twice.
  - **`.git` is in no list:** not a category, not Largest files, not Duplicates. Its space is the Git
    history row.
  - It runs when the page first opens, and again on ↻.
  - The line under the bar says "Scanning… 4,210 files" as it goes, with Stop; then when it last
    scanned.
  - The result stays, the views built from it, while iCode runs.
- **How a file finds its category.** One domain table, built on `FileTypes`, by extension. The
  archive formats in it are the ones Extract reads (§3.11).
- **How duplicates are found.** Files of the same size are hashed, a first block and then the
  whole, with SHA-256 (`hexDigestOf`). Empty files are left out.

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
  - **"Compress…":** the sheet's page (§3.1), with the name, and the format: Zip, 7z or Tar.gz.
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
- **Remembered** in DataStore, beside the other settings. Now they last only while the screen does.
  - Folders share one view, sort and order, app-wide.
  - Each view of §3.10 keeps its own.

### 3.13 Hidden files

- **One setting, "Show hidden files",** on by default, as today. Two places show the same value:
  - Settings › Storage (the owner's ask);
  - the panel's Hidden (the reference).
- **Hidden** is a name starting with ".".
- **It applies** to both explorers, the folder picker, and what Select all ticks.
- **It does not apply** to the views (§3.10) or Manage storage's scan, which are about what you
  opened, deleted or stored.
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
- **⋮** (reference 2): Select, Undo *what*, Redo *what*, Manage storage, Storage settings. Create is
  the +'s.

### 3.15 Settings › Storage, compact

```
 STORAGE
 [██████████░░░░░░]  57.3 GB used · 48.7 GB free of 106 GB
 Workspaces                2.1 GB  ›    removed with iCode unless you keep its data
 Runtime                   3.4 GB
 Trash                     120 MB  ›
 Cache                      80 MB  Clear
 Manage storage                    ›
 FILES
 Show hidden files                [on]
 PHONE STORAGE
 All Files Access            ALLOWED ›
```

- **The usage card and its sizes become one list.**
  - Workspaces opens Private.
  - Trash opens the Trash view.
  - Manage storage opens its page (§3.10).
  - Cache's Clear asks first, as it does now.
- **Removed:**
  - "Device storage, including other apps and files": the bar's line says it.
  - The Workspaces section's caption: its words are now the row's.
  - Cleanup Operations and its full-width button: Clear is on the Cache row.

## 4. Backend

- **Design system** (§3.1), on the one base, `IcodeBottomSheet` over `IcodeSheetOverlay`:
  - `IcodeActionSheet`: the head, the grouped `IcodeMenuAction` rows, the destructive last;
  - `rememberSheetPages`: a sheet's page stack and its Back;
  - the floating stack's floors (§3.8).
- **New, each under the 300-line budget:**

  | File | What |
  |---|---|
  | `data/files/FileTrash.kt` | Put, list, restore, delete, empty, the 30 days; both trashes |
  | `data/files/FileBatch.kt` | Copy, move, Duplicate, Move to and Copy to of many, with the §3.6 question; across the places, `StorageTransfers` per item |
  | `data/files/FileHistory.kt` | The steps, undo, redo, the check before each |
  | `data/files/FileArchives.kt` | Compress and extract, as tasks |
  | `data/io/Archives.kt` | Reading and writing; from `ArchiveExtractor`, which the runtime keeps using |
  | `data/files/FileFacts.kt` | Properties |
  | `data/files/StorageScan.kt` | Manage storage's walk: each category's files, the largest, duplicates, `.git`'s size; kept, and changed by iCode's own operations |
  | `data/files/ExplorerPreferences.kt` | Hidden, view, sort, order, favorites, in DataStore |

- **Coordinators:**
  - `WorkspaceItemsCoordinator` keeps new and rename.
  - A new `FileOperationsCoordinator` takes:
    - the clipboard;
    - paste, Move to, Copy to, Duplicate;
    - Delete to Trash, and Trash's own actions;
    - undo and redo;
    - the question.
  - `FileInsightsCoordinator` takes properties, Manage storage's scan and the views' lists;
    `FileShortcuts`, Recent and Favorites.
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
  - the explorer's location: a folder, or a view (§3.10);
  - Manage storage's scan.
- **Removed:**
  - `DeleteWorkspaceItemDialog` and `DeleteWorkspace`'s confirm: Delete is to Trash now.
  - `RenameWorkspaceItemDialog`: Rename… is the sheet's page.
  - The item menus' dropdowns, and `WorkspaceActionsMenu`'s: the sheet draws their actions.
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
| **F4** Archives and facts | `Archives` (moved, with the guards), `FileArchives`, xz; `FileFacts`; `StorageScan`; `ExplorerPreferences`, favorites following moves; Recent across places; the hidden filter | Tests: every format both ways where §3.11 says so; an entry escaping, a link leading out, case twins on Shared; a 7z with its password; facts; categories, the largest, duplicates and `.git` apart; a scan changed by a delete and a rename; favorites after a rename |
| **F5** The explorer | `IcodeActionSheet` and `rememberSheetPages` (the assistant's run sheet onto the helper); the explorer's sheet and its pages; selection and its bars; the panel, three views, thumbnails; the breadcrumb; ▾ with Favorites, Recent, Trash; ⋮; the folder picker; the sheet's pages (Rename…, batch rename, Compress…, Properties) and the question's sheet; the Trash and Recent views | UI type-check; touch-driven checks in a desktop scene: select, select all, invert, cut and paste many, the question, rename many, the panel's choices; renders of each |
| **F6** Everywhere else | The IDE tree's selection (built: F6c; its sheet, and the home screen's: `91f0177`); every other item menu onto the sheet (built: F6c; §3.1: editor tabs, Source Control, the Run tab, the assistant's steps and message); the floating stack (built: F6b); Settings › Storage and the Manage storage page (built: F6a) | UI type-check; the stack over a + and a foot bar; touch-driven checks of Manage storage into a category, a grid of Images, Select extra copies, Show in folder; renders |
| **F7** Gate | Docs as built (PRD, user stories, layout, the two plans this follows); the full gate | Push the branch and `main` |

## 7. On the phone (the owner's walk-through, after F7)

1. Delete a folder on Shared and in a workspace. Undo from the pill, then restore one from Trash.
   This proves the move into `Android/media/<package>/Trash` is a rename on the phone.
2. Select ten photos and share them; see their thumbnails in Grid.
3. Paste a folder onto one of the same name: Merge, then Keep both.
4. Extract a .7z and a .tar.gz; compress a workspace to .zip and open it in another app.
5. Turn Show hidden files off in Settings › Storage, and see the panel's Hidden follow.
6. Open Manage storage on All. Open Images as a grid and delete two; see the row's count follow.
   In Duplicates, Select extra copies, then Delete.

## 8. As built

### F2 (domain, `1df72ba`)

`domain/files`: `FileSelection`, `BatchRename`, `ArchiveFormats`, `FileCategories`, `ExplorerSettings`,
`FileConflicts`, `FileHistory`, `FileWords`, `Trash`; `FilesBrowser` moved there; sort by Type and
Descending.

### F3 (operations)

- **Domain:**
  - `FileClipboard` of `HeldItem`s replaces `ItemClipboard`.
  - The snapshot's `clipboard` became `explorer: ExplorerState`: the clipboard, the question
    waiting, undo and redo, and Trash.
  - `FilesIntent` (`domain/FileIntents.kt`) replaces `ClipboardIntent`, `DeleteWorkspaceItem` and
    `DeleteWorkspace`.
  - `RenameWorkspace` went: `RenameWorkspaceItem` renames a whole workspace too, through the
    lifecycle.
  - `FileChange.refusal` is the check before an undo or redo.
  - `FileStep.Removed` and `Reversal.MakeFolder` cover an empty folder taken away: a merge's
    emptied source, or a new folder undone. An empty folder is removed, not put in Trash, since it
    holds nothing to lose.
- **`data/files`:**
  - `PlaceFiles` (was `WorkspaceFiles`): one per place, Private rooted at the workspaces'
    folder. Copy and move take an exact target, and a move makes the folders that went.
  - `StorageTransfers.carry`: one item between the places, inside the operation's task.
  - `FileTrash`, `FileHistory`, `FileQuestions`, `FilePlaces`.
  - `FileOperator`: one step, its follow-ups under the lock.
  - `FileBatch`: many items, the question, merges, renames.
  - `FileRefusals`: everything checked before anything starts.
  - `FileOperationsCoordinator`: one line in the gateway; the answer goes off the lock.
- **`data/workspace`:**
  - `FileChangeFollower` holds what followed a move or delete in the items coordinator. It
    implements `FileFollower`, so the operations are tested on temporary folders.
  - The lifecycle's `release` / `stayed` / `renamed` / `retire` are the one way a workspace moves
    or goes.
  - `WorkspaceUsage.edited` is asked by a running operation before Replace.
  - The items coordinator keeps new and rename. Both are kept to undo.
- **`data/io/PathRules.kt`:** `isAtOrUnder` and `relocated`, out of the workspace package.
- **Every task starts `quiet`:** no dialog and no notification for its first 500 ms, so a rename
  or a move to Trash never flashes one.
- **Rules settled while building:**
  - Names swapped by one rule go through a passing name, "name (renaming 1a2b3c4d)". It is never
    a dot name, so it stays seen if iCode stops midway.
  - A restore meeting a folder can Merge: back beside it as "name copy", then merged in.
  - Trash's own folders, and a folder holding one, are never moved or put in Trash.
  - Delete for good and Empty Trash take an item's record first, so an item is never removed twice.
  - A record without its item goes when Trash is read. An item without its record goes at start.

### F4a (archives)

- **`data/io/Archives.kt`** replaces `ArchiveExtractor`. It is the one reader, for the runtime's
  pinned downloads and the explorer's Extract alike: zip (and .jar, .apk), tar, .tar.gz, .tar.bz2,
  .tar.xz, .gz, .bz2, .xz, and 7z with its password.
  - It takes `ArchiveLinks`: `AsWritten` for a pinned download, `Inside` for the explorer in
    Private, `None` on Shared.
  - Case twins are left out on Shared. What was left out is counted.
  - An encrypted zip says so.
- **`data/io/ArchiveWriting.kt`** writes zip and tar.gz, which keep links and executable bits, and
  7z, which leaves links out and counts them.
- **`org.tukaani:xz:1.10`** is added; its checksum and 0BSD licence were checked against Maven
  Central.
- **`data/files/FileArchives`:** Compress and Extract as tasks.
  - What is made is staged in `<trash>/staging/<id>` on the destination's volume, swept at start,
    then put in place by `FileBatch.settle`: the question for Extract, a free name for Compress.
    Both are kept as made, to undo.
  - A 7z locked by a password sets `ExplorerState.password` (`PasswordAsk`) and waits for Extract
    again with it. The password is never kept.
  - The default name comes from `ArchiveFormats.archiveName`.
- **`FileTasks`:** running an operation as a task, keeping it, refreshing. It is shared by
  `FileOperationsCoordinator` and `FileArchives`.
- **`StorageTransfers.roomRefusal`:** one room check, on the workspaces' volume, which phone storage
  shares.

### F4b (facts and the scan)

- **`data/io/TreeSize.kt`:** `tallyTree`, one walk giving bytes, files and folders, and stopping
  when told. `measureTree` is a view of it.
- **Domain:**
  - `FileFacts` and `TreeFacts`: Properties.
  - `StorageInsights`: the overview, Largest, the same-size candidates, Select extra copies, and
    `after`, where a path is once iCode's own steps are done.
- **`data/files/StorageScan`:** the walk and the copies (same size, then the first 4 KB, then the
  whole, by SHA-256).
  - `.git` is apart; Trash is left out by `FileTrash.isTrash`, which unlike `holds` is false for
    Trash's ancestors.
  - It follows every change kept in `FileHistory`: what went, moved or arrived.
- **`data/files/FileInsightsCoordinator`:** Properties and the scan (`FilesIntent.Insight`).
  - Requests and changes are taken in order, off the main thread, through one channel. A failure in
    one never stops the next.
  - The snapshot gets `ExplorerState.facts` and `ExplorerState.storage`.
- **The view lists** (a category's files, Largest, Duplicates) are published with the views in F6.

### F4c (settings, Recent, Favorites, hidden files)

- **Settings:** `WorkspacePreferences.explorer`, kept by `AndroidWorkspacePreferencesRepository`.
  - Keys: `explorer_show_hidden`; `explorer_folders_look`; `explorer_look_<view key>`, one per
    view changed.
  - A look is kept as text (`FilesLook.encoded`: "Grid,Size,desc"). One that cannot be read is no
    choice, and the view opens its own way.
  - `FileOperationsCoordinator.start` follows them into `ExplorerState.settings`. Hidden files
    shown or not, `FileFollower.relist` reads the explorer's folder and the open tree again.
- **Hidden files:** filtered where each place is listed. `WorkspaceFileSource.listChildren` and
  `PhoneStorage.list` take `showHidden`. The tree, the explorer and opening a workspace pass the
  setting. `.git` stays unlisted either way.
- **`data/files/FileShortcuts`:** Recent and Favorites, owned by the library (`library.shortcuts`)
  and kept in the workspace store.
  - **Recent:** the editor's opens and saves, and `FilesIntent.Opened` (a viewer, Open with), in
    workspaces and on phone storage.
    - At most 20 per workspace, 20 for phone storage, 100 in all.
    - Each is stored by its location alone: its name, workspace and runtime path are derived.
  - **Favorites:** `FilesIntent.Favorite(location, pinned)`, kept as `favorites_v1`, in the order
    pinned.
  - **Following changes:** both follow every move kept in `FileHistory`, in order, under the
    mutation lock. What went to Trash is kept, not shown, so a restore or an undo shows it again.
  - **Publishing:** to `ExplorerState.recent` and `ExplorerState.favorites` (what is there now), at
    start, after each change, and on every library refresh. A refresh after a watcher event or a
    terminal delete is one.
- **Migrated:**
  - `ICodeSnapshot.recentDocuments`, the library's recent-file code and `FileChangeFollower`'s
    recent calls are gone.
  - The editor's empty page reads `explorer.recent` for its workspace. A file gone leaves the list
    there too, instead of showing "Missing".
  - `WorkspaceStore.relocate` and `forget` no longer touch recent files: the moves do.

### F5a (the action sheet, the item's sheet, the question)

- **Design system:**
  - `IcodeActionSheet`: a sheet of `IcodeMenuAction`s, the dropdowns' own model, with a head
    (`IcodeBottomSheet`'s new `leading`, then the title and detail).
  - `IcodeMenuAction` gained `beginsGroup`, a line before it in both the sheet and the dropdown, and
    `opensPage`: in a sheet, it opens a page instead of closing.
  - `rememberSheetPages`: a sheet's page stack and its Back, kept as the screen turns. The
    assistant's steps sheet moved onto it.
- **One list of an item's actions,** `FileItemActions.of(target, explorer, …)`, in §3.1's order and
  groups:
  - Duplicate, Compress to *name*.zip and *name*.7z, Extract here, and Add to or Remove from
    Favorites are added, for the tree's and the home screen's dropdowns too.
  - With `SheetPageActions`, the list is a sheet's: Compress… and Properties, and Rename… as a page.
  - A whole workspace is not compressed at Private's top. Archives may not sit there:
    `FileRefusals.archiveInto` refuses it, with a test.
- **The explorer:**
  - A long press opens the item's sheet (`FilesItemSheet` on `ItemSheet`), which has three pages:
    - Rename…;
    - Compress…, with the format, what it keeps, and the archive's name;
    - Properties: `FileProperties.rows` from `FileFacts`, measured while it is open.
  - The workspaces at Private's top have the same sheet, with Open (in the IDE) and Open Terminal.
- **Renaming:**
  - `RenameForm` holds one rename's rules and request, for the Rename… page and the dialog left to
    the tree and the home screen (`RenameDialog`).
  - The two former rename dialogs are gone. New workspace shares `NameFields`.
- **Asked anywhere:** `FileAsks`, over every screen.
  - The question (§3.6) shows both sides, the answers, and "Do this for the other N". While it is
    open, `TaskDialogs` steps aside.
  - A 7z's password is held in `remember` only, never saved.
- **Recent:** Open with… (`SharedFiles.open`) and the viewer, from Shared, record the file
  (`FilesIntent.Opened`).
- **Domain:** `FileProperties`, `PropertyRow`, `FileCategory.one`, `ConflictSide.said`,
  `FileConflict.forOthers`, `CompressFormat.note`, and a `Long` `countOf`.

### F5b (selection, the folder picker, batch rename)

- **Selection:**
  - **Starting:** a tap on an item's icon, the sheet's Select, or ⋮ › Select.
  - **While selecting:**
    - a tap or a long press ticks;
    - `..` and + go;
    - ✕ (`BarLeave.Selection`, the top bar's new `leaving`) or Back leaves;
    - the top bar adds Select all and Invert, on what is shown.
  - **Foot bar** (`SelectionBar`): the count and `FileSelection.summary` ("2 folders, 1 file ·
    12.4 MB"), with Cut, Copy, Delete and ⋯.
  - **Ending:**
    - Going to another folder ends it: its state is keyed by the folder shown.
    - Every action from its bar or sheet ends it.
    - It ends once all it ticked has gone.
- **One list for one item or many:**
  - `FileItemActions.of(targets, …)` follows §3.3: Share… sends every file in one share
    (`SharedFiles.share`, `ACTION_SEND_MULTIPLE`).
  - **Compress:** many are named after the folder shown, or "Archive" at a place's top.
  - **Archives:** extracted all at once.
  - **Favorites:** every folder ticked is added.
  - **Copy paths:** one per line.
  - **The explorer's own** (`ScreenActions`): Move to…, Copy to…, Extract to… and Select.
- **The sheet:** `FilesSheet`, for an item or a selection, on `ItemSheet`, which takes many
  `ActionTarget`s and a `SheetFolder`:
  - Rename… for many is `BatchRenamePage`: the rule, its fields, and the preview with each refusal.
  - Compress… and Properties take many.
- **The folder picker** (`FolderPick`: Clone, Transfer, Extract):
  - **Its bar:** `PickBar`, on `FolderPicks.bar`, says where, what goes there, and why it cannot:
    - inside itself;
    - already there;
    - a file at Private's top.
  - **What it lists:** the explorer, folders only, in both places. A clone stays in Private.
  - **Choosing a clone's place** is this picker, in place of `ChooseBar`.
- **⋮** (`ExplorerMenu`): Select, Undo and Redo with what each would do, and Storage settings.
- **Moved out of `FilesScreen`** to `FilesBars.kt`: the places' menu, the sort button and the search
  field.

### F5c (the view panel, the grid and thumbnails, the breadcrumb, go to)

- **☰, the view panel** (`ViewPanelButton`), in place of the sort button:
  - **View:** Detailed, Compact, Grid, and Hidden files;
  - **Sort:** Name, Size, Date, Type, and Descending; a new sort reads its own way first.
  - **Kept:** the look by `setFilesLook(null, …)`, Hidden by `setShowHidden`, read back from
    `explorer.settings`.
- **The listing,** `FilesListing.kt`:
  - **Detailed and Compact:** a list. **Grid:** `LazyVerticalGrid`, tiles of at least
    `IcodeSizes.GridTileMin`.
  - **Sorted by Type:** a label over each group, "*label* · *count*".
  - **One item's taps** (`ListingTaps`, `itemActs`) are shared by its row and its tile.
- **Thumbnails** (`Thumbnails.kt`), in every view and in the assistant's attachments:
  - pictures, videos and an APK's own icon (`ThumbnailKind`);
  - made off the main thread, four at a time;
  - kept in memory, 200 of them, by path and modified time.
- **The breadcrumb:** `FilesBrowserState.crumbs`, with a test, drawn by the design system's
  `IcodeBreadcrumb` in the top bar's new `subtitleContent`. It scrolls with its end in view.
- **▾, go to** (`goTo`): the two places, then each favorite with its path. Favorites are now
  `FavoriteFolder`s (name, location, runtime path), published by `FileShortcuts`.

### One item sheet everywhere, and the option panel (the owner, `91f0177`)

- **The item sheet** is the only place file actions are shown:
  - on the home screen (`WorkspaceSheet`);
  - in the IDE's tree (`ExplorerItemSheet`), where Expand and Collapse stay the tree's own;
  - in the file explorer.
- **What was removed:**
  - `WorkspaceActionsMenu`, `RenameDialog`, and the "Compress to *name*.zip / .7z" shortcuts.
  - Compress… is the one entry, and its page chooses the format and the name.
  - `FileItemActions.of` always takes the sheet's pages.
- `behindSheet()`: the screen under an item sheet is blurred, as under every sheet.
- **The option panel kit** (`IcodeOptionPanel`) carries the ☰ View and Sort panel, with `ViewPanelTest`.
- **Rows:** ticked rows are square and edge to edge, and nothing is reserved under the last row.

### F5d (Recent and Trash, the explorer's views)

- **One row model** for folders and views:
  - `BrowsedItem` now carries its runtime `path` and its `place`.
  - Recent is a list of rows (`RecentDocument` is gone); the editor's empty page reads the same list.
  - Trash's rows are `TrashedItem.row`. Each is listed by where Trash holds it (`heldAt`), so a
    trashed picture has a thumbnail. Restore and Delete permanently go by its id.
  - `FileTrash.locationOf` gave way to `heldAt`.
- **One path exchange:** `WorkspacePaths.runtimePathOf` serves listings, Recent, Favorites and
  Trash (a trashed item's `path` is where it was).
- **Domain rules:**
  - **Views:** `ExplorerView` moved to `ExplorerViews.kt`, which also holds:
    - `encoded`/`decode`, so the screen keeps its view;
    - `ExplorerViews.rows`;
    - `ExplorerViews.found`, the view's subtitle ("1 item · 3.0 KB · removed after 30 days").
  - **Rows:**
    - `FilesRules.gatheredDetail`: the folder a row is in, then its detail.
    - `FilesRules.sizeOf`: one size rule, for sort and summary.
    - `sorted(foldersFirst = false)` for views.
  - **Trash:** `FileProperties.trashed` and `TrashRules.removedAt`. Properties of many gathered from
    several folders have no "In" row.
- **The screen:**
  - **`FilesScreen`** holds:
    - the view shown, kept as the screen turns;
    - the folder picker, which either side can start;
    - the file Show in folder asked for.
  - **`FolderExplorer`** is the folder. The file Show in folder asked for comes ticked, and the
    list scrolls to it (`FilesListing`'s `focus`).
  - **`ViewExplorer`** (`FilesViewScreen.kt`) is the view:
    - **Shared with folders:** the same listing, ☰ (each view's own look, no Hidden), 🔍, ⋮,
      selection, and the item's sheet.
    - **Left out:** `..`, +, the breadcrumb and the paste bar.
    - **Back:** leaves the selection, then the search, then the view.
- **Shared pieces:**
  - **`Opening`** (replacing `openOf`): what a tap opens, by the row's own place, so a Recent
    file opens as in its folder.
  - **`ListingTaps.onOpen`** decides a tap; `FilesListing` takes its `detail`, its `empty` state
    (`FolderEmpty`, `ViewEmpty`) and `up`.
  - **`SelectionBar`** takes its actions: `selectionActions` for files, `trashActions` in Trash.
  - **`SheetIcon`** leads every item sheet: the explorer's, Trash's, the tree's and the home screen's.
- **Recent's sheet** is `FilesSheet` with no folder:
  - "Show in folder" uses `showInList`, which a workspace calls "Show in Workspaces".
  - `ItemSheet` offers Rename… and Compress… for many only when they share one folder, so a view's
    selection gets neither.
- **Trash:**
  - **On open:** it is read again (`ReadTrash`).
  - **Its sheet** (`TrashSheet`): Restore, Properties as it was deleted, and Delete permanently.
  - **Its selection:** Restore and Delete permanently.
  - **Its top bar:** Empty Trash.
  - **Asking:** both deletes ask first (`ForGoodDialog`).
- **▾ go to:** the places, Favorites, then Recent, and Trash with how many it holds.
- **For Manage storage (F6):** `ExplorerView.icon` and `FileCategory.icon`. Its views list what its
  scan will publish.

### F6a (Manage storage and its views; Settings › Storage, compact)

- **Where the explorer is,** a folder or a view, is in the snapshot (§4), so other pages can open a view:
  - `ICodeIntent.BrowseView` sets `ExplorerState.view`, and any browse — `BrowseFiles`, `BrowsePlace` —
    leaves it.
  - **What this replaced:** the screen's own saved view, with `ExplorerView.encoded`/`decode`.
  - **Back from a view:**
    - opened onto from Manage storage or Settings › Storage, Back returns there;
    - entered from a folder, Back returns to the folder.
  - Choosing a clone's place is always among folders.
- **The scan's views** — a category, Largest files, Duplicates — are listed while shown, the way
  Properties is:
  - `FilesIntent.ListView` / `CloseView`, and `FileInsightsCoordinator` publishes
    `ExplorerState.scanned` (`ScannedView`).
  - The rows are listed again as the scan finishes, or as iCode's own changes move or remove files.
  - `StorageInsights.listed` picks each view's files.
- **One grouping rule:**
  - `RowGroup`, with `FilesRules.grouped` (Type) and `ExplorerViews.grouped` (Duplicates, set by set,
    under "2 copies · 1.0 KB each", the oldest first).
  - `FilesListing` takes the groups.
  - Duplicates keeps its own order: its ☰ has no Sort (`ExplorerViews.sortable`), and its ⋮ has
    Select extra copies.
- **⋮** adds Manage storage, as §3.14 says.
- **Manage storage** (`ManageStorageScreen`, `Route.ManageStorage`):
  - **Where it looks:** All, Private or Shared.
  - **When it scans:** when the page first opens, and on ↻.
  - **The bar:** `ShareTrack`, a new design-system track of coloured shares. Each category is in its
    colour (`categoryTint`, from the file icons' Seti palette), Git's history last.
  - **Under the bar:** the total, and when it scanned or how far it has got, with Stop
    (`StorageInsights.total`, `status`, `shares`).
  - **The rows:** each category, then Largest files, Duplicates and Trash. Each opens its view; one
    with nothing in it is greyed. Git's history is counted but opens nothing.
  - **Without All Files Access,** Shared's part is a line asking for it.
- **Settings › Storage** is §3.15's one list:
  - **Storage:** the device's bar; Workspaces (opens Private); Runtime; Trash (opens its view); Cache
    (Clear asks first); Manage storage.
  - **Files:** Show Hidden Files.
  - **Phone Storage:** All Files Access.
  - The old caption, the "Device storage…" line and Cleanup Operations are gone.
- **Shared pieces:**
  - `SettingRow` takes its icon's tint.
  - `FileCategory.icon` and `categoryTint` sit beside the file-type icons, which share `setiColor`.

### F6b (the floating stack: the undo pill above the bell)

- **The design system's stack** (`IcodeFloatingStack.kt`):
  - **`IcodeFloors`:** each `IcodeFab`, `IcodeActionBar`, `IcodeFootBar` and `IcodeBottomBar`
    reports its top edge (`floor()`), and each `IcodeSheetOverlay` that it is open (`SheetOpen`).
    No screen wires either.
  - **`IcodeFloatingStack`:** a column at the bottom end, above the highest floor, at least
    `FabClearance` up, and gone while a sheet is open. The bell no longer meets the explorer's +.
  - **The pill's parts,** shared by the bell and the undo pill: `IcodeFloatingPill`,
    `IcodeFloatingLine` (with an optional action), `IcodeFloatingAway` and `FLOATING_SLIDE_MILLIS`.
  - **Tested:** `IcodeFloorsTest`.
- **The bell:**
  - `NoticeHost` keeps the bell's state, its timing and its sheet.
  - `NoticeBell` draws it in the stack, on the shared parts.
- **The undo pill** (`UndoPill.kt`):
  - **Its state:** `UndoPillState`, with `rememberUndoPill`, lives at the app's root, like the bell's.
  - **Each change** in `FileHistoryState.news` is told once, and is not told again as the screen
    turns.
  - **Shown:** ↶, ↷ when something can be redone, and × apart (`IcodeFloatingAway`).
  - **After `FileNews.HOLD_MILLIS`:** it goes.
- **Its place:** `IcodeApp` provides `LocalIcodeFloors` and draws the stack beside `NoticeHost`,
  over every screen but the Notifications page.

### F6c (every item menu on the sheet; the IDE tree's selection)

- **Item menus, now sheets** (§3.1). Each is an `IcodeActionSheet` over the screen's own
  `IcodeSheetOverlay`, with the screen blurred behind it (`behindSheet`):
  - **The editor's tabs** (`TabSheet`): titled with the file, and where the runtime has it.
    - Actions: New file, Close other tabs, Close saved tabs, and Close *name*, last.
    - Close other tabs keeps the tab pressed, not the one selected.
    - `IcodeTabStrip`'s `menu` slot is gone.
  - **Source Control's changes** (`GitChangeSheet`), from a long press:
    - A file: Open file, Stage or Unstage, and the discard that fits it.
    - A folder: Stage or Unstage, and Discard changes.
    - The rows keep only + or −. The screen holds the row by its list key (`changeRowKey`).
  - **The Run tab's commands** (`RunCommands.kt`):
    - One value, `RunCommandAsk`, holds what the tab asks: a new command, a command's sheet,
      its edit or its delete.
    - The tools panel draws it over the whole panel.
    - The dialogs moved there from `RunPanel`.
  - **The assistant** (`AssistantActionsSheet`):
    - A step about a file: Open in Editor, Show Changes and Copy path (`fileActions`).
    - The user's message: Copy, and Select text, which is the sheet's own page.
    - The entry sheet no longer opens a message.
- **One blur:** the assistant, the navigation host, the file viewer and two settings pages used
  `behindSheet` by hand. They now call it.
- **The IDE tree's selection** (§3.2):
  - **One state for every explorer** (`Selection.kt`):
    - `ExplorerSelection` holds what is ticked and whether its sheet is open, with
      `rememberSelection` and `among`. The selection ends once what was ticked has gone.
    - The file explorer, its views and the tree share it, with `SelectionActions`,
      `SelectionBar` and `selectionActions`, which moved here from `FilesBars`.
  - **Starting and using it:**
    - Select in a row's sheet starts it. Rows then tick with a tap, their icon becomes the tick,
      and a ticked row is drawn as the selected one.
    - The + gives way to Select all and Invert, and ✕ or Back leaves the selection.
    - The foot bar says what it acts on: a folder ticked covers what is in it (`FileSelection.covering`).
  - **One sheet** (`ExplorerItemSheet`), for a row or for the selection. Many in one folder are
    renamed and compressed in it; many across folders are not.
  - **The folder picker is the file explorer's alone:** `ScreenActions.pick` is optional, so the
    tree has no Move to…, Copy to… or Extract to….
- **Fixed:** a sheet closes before its action runs. A selection's sheet read `sheet` as already
  false, so Copy or Duplicate from ⋯ left the selection on. `ExplorerSelection.acted` decides
  as the sheet is drawn. `ItemSheet.onActed` wraps every action but a page, so no screen repeats
  that wrapper.
- **One path rule:** `ICodeSnapshot.runtimePathOf` gives the open workspace's runtime path. The
  tree, its sheet and the tab sheet once wrote it out each.
- **Checked:**
  - Tests: `FileItemActionsTest` (the tree's Select, with no folder picker), and
    `WorkspaceNameRulesTest` (`runtimePathOf`).
  - The UI type-check now covers the tools panel, with its terminal view stubbed.
  - By touch, in a desktop scene:
    - Holding a row opens its sheet, and Select ticks it.
    - Ticking a folder and its file counts one folder and one file.
    - ⋯ › Copy sends both locations and ends the selection.
    - Select all, then ✕, leaves the selection.

### F7 (docs as built; the gate)

- **[prd.md](./prd.md):**
  - §5.1, PR-ENTRY-01, PR-WS-01 to 04 and §13 say what is there: Private and Shared; a folder from
    phone storage copied or moved in; Delete to Trash, which can be undone.
  - PR-FILE-02 and 03 are rewritten, and PR-FILE-07 to 13 added: the one sheet, selection, undo,
    archives, Properties, Manage storage, and view and sort.
  - The glossary's Import is gone; Private, Shared and Trash replace it.
- **[user-stories.md](./user-stories.md):**
  - Entry, storage and workspace stories as built.
  - US-FILE-003 and 004 rewritten for Rename…, Move to… and Trash.
  - US-FILE-007 to 013 added: the one sheet, many at once, undo, archives, properties, Manage
    storage, and the views.
- **[layout.md](./layout.md):**
  - The sitemap, and §9.3 the file explorer, in place of the Workspaces page.
  - §12.2 the sheet's order, and the tree's selection.
  - The tab and Run tab sheets.
  - §19.6 Settings › Storage and Manage storage.
  - §21 sheets and menus, and the floating stack with the undo pill.
- **The two plans this follows** point here, and say what changed.
- **Gate:** the full gate, the UI type-check, the UI tests and the guards pass.
