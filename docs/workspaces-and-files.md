# Workspaces and files

iCode keeps editable projects in app-private storage and exposes them to its Linux runtime at stable `/workspace` paths. The same explorer also provides an explicitly permissioned view of primary phone storage.

## Status at a glance

| Area | Status | Scope |
|---|---|---|
| App-private workspace and file-operation logic | Done | Implemented with direct unit coverage for paths, transfers, Trash, history, archives, storage scans, drafts, and file classification. |
| Private and Shared explorer behavior on a physical phone | Needs Verification | Android storage permissions and device filesystem behavior vary. |
| Android DocumentsProvider and FileProvider integration | Needs Verification | Implemented, but direct provider instrumentation and recipient-app checks are still required. |
| SD-card and USB explorer places | Not Started | Only primary shared storage is supported. See the [secondary-storage roadmap](roadmap.md#secondary-sd-card-and-usb-explorer-places). |

## Workspace model

A workspace is a real, immediate child directory of the app-private `files/workspaces` directory. That directory is the workspace registry; there is no separate catalog that can drift away from the filesystem. Dot-prefixed entries and top-level symlinks are not listed as workspaces.

Android-side code and persistent stores identify files by their canonical host paths. User-facing runtime paths use a stable prefix mapping:

- The private workspace root is `/workspace`.
- A workspace named `sample` is `/workspace/sample`.
- Descendants retain their relative path, such as `/workspace/sample/src/main.kt`.
- Phone-storage paths remain Android paths.

Every PRoot launch binds the complete private workspace root once at `/workspace` and starts in the requested guest directory. This is direct access to the same files, not a copy or synchronization service. The runtime can also expose primary phone storage at its Android path and through `/sdcard` when Android makes it available.

Workspace names are trimmed and cannot be empty, `.`, `..`, contain `/`, backslash, or NUL, exceed 255 UTF-8 bytes, or begin with `.`. Files and folders inside a workspace may be hidden. Explorer operations protect Private `.git` metadata from ordinary mutation.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/storage/WorkspacePaths.kt`
- `app/src/main/java/dev/icode/mobile/data/runtime/RuntimeLayout.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/WorkspaceLibraryCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/domain/WorkspaceNameRules.kt`
- `app/src/test/java/dev/icode/mobile/data/storage/WorkspacePathsTest.kt`

## Workspace lifecycle

### Create, open, rename, and remove

Creating a folder at the top of Private creates an empty workspace. It stays in the workspace list until the user opens it. The library sorts workspaces by last-opened time, falling back to folder modification time.

Renaming a workspace performs one filesystem move and relocates workspace settings, history, and session references. iCode closes and reopens the workspace around that move. A workspace may refuse rename, move, or deletion while it has active tasks, unsaved or unresolved recovery work, Git activity, or whole-workspace terminal use.

Explorer Delete moves a workspace to Private Trash. Restoring brings its files back as a workspace. Retired per-workspace metadata, such as old tab/session state, is not guaranteed to be reconstructed by restoring the directory. Permanent Delete and Empty Trash are separate confirmed operations.

### Clone and destinations

Clone can create:

- a new top-level workspace; or
- a project folder inside an existing workspace.

A clone is prepared in a hidden staging directory under the private root. iCode validates the URL, branch, name, and destination, runs Git, then moves the completed tree into its final location. Cancellation or failure removes that operation's staging tree. The destination is checked again immediately before the final move, including protection against `.git` destinations and concurrent name collisions.

### Import from phone storage

Import opens the explorer at Shared. The user selects a folder, chooses Copy or Cut, switches to Private, and pastes it:

- Pasting a folder at Private's top creates a workspace.
- Pasting a folder inside a workspace creates a project folder.
- A file cannot become a top-level workspace.

Folders entering Private are staged on the destination volume and become visible at the final name only after a complete copy. Copy keeps the source. Cut removes the source only after the copy finishes successfully. If any selected item cannot be represented at the destination, a cross-place cut is refused before writing; a copy may finish with a notice describing skipped items.

When an imported tree contains a Git repository, iCode asks Git to restore committed executable bits and symlinks while the tree is still staged. If the runtime or Git is unavailable, the ordinary files still arrive and iCode reports that this repair did not run.

### Multiple repositories

A workspace may contain several Git repositories. Source Control chooses the repository containing the selected file or folder. At the workspace root it uses the process-local selected repository, then a root repository, then the first nested repository it discovers.

Automatic discovery searches no more than three directory levels, skips hidden directories and `node_modules`, and does not follow directory symlinks. It recognizes `.git` directories and `.git` files used by worktrees and submodules. The selected nested repository does not survive process death; persistence is tracked in [Source Control requirement completion](roadmap.md#source-control-requirement-completion).

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/workspace/WorkspaceLifecycleCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/git/GitTransferCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/storage/WorkspaceStaging.kt`
- `app/src/main/java/dev/icode/mobile/data/files/StorageTransfers.kt`
- `app/src/main/java/dev/icode/mobile/data/git/GitLocator.kt`
- `app/src/test/java/dev/icode/mobile/data/storage/WorkspaceStagingTest.kt`
- `app/src/test/java/dev/icode/mobile/data/files/StorageTransfersTest.kt`
- `app/src/test/java/dev/icode/mobile/data/git/GitLocatorTest.kt`

## Explorer places

Private and Shared are two places in one explorer. They share folder navigation, search, sort and view settings, hidden-file settings, selection, folder picking, file actions, clipboard status, Recent, Favorites, and storage-management entry points.

### Private

Private is the app-owned workspace root displayed as `/workspace`. Its top level contains workspace folders. Private files can open in the editor or an iCode viewer. Private storage supports the project semantics needed by development tools, including case-sensitive names, executable bits, and internal symlinks.

Private Trash is outside `files/workspaces`. It is not visible under `/workspace`, to the assistant workspace file surface, or through the Android DocumentsProvider.

### Shared

Shared is Android's primary shared-storage root, normally `/storage/emulated/0`. It does not include separate SD-card or USB places.

A Shared file is not edited in place by iCode's text editor. Images can use the in-app image viewer; other Shared files are handed to Android through Open with. Copy a project folder into Private before editing it as a workspace.

Shared treats `.git` as ordinary user data. Its filesystem is treated as case-insensitive and does not reliably preserve Unix executable bits or symlinks.

## Phone-storage permission boundary

**Status: Needs Verification**

iCode requests Android's **All Files Access** only when the user enters Shared or starts an operation involving a Shared path. Without that grant:

- Private remains available.
- Shared shows an explanation and an Allow action that opens Android's permission screen.
- Shared listing, mutation, and cross-place transfer are blocked.

After the grant, iCode can list primary phone storage and perform only the file actions the user requests, including create, rename, copy, cut, paste, Trash, restore, archive, Open with, and Share. Folder navigation canonicalizes its destination and refuses to leave the primary shared-storage root.

Permission grant and revocation, return behavior from OEM settings, free-space reporting, real FUSE case behavior, and `/sdcard` availability need checks on supported phones.

Maintainer entry points:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/dev/icode/mobile/data/storage/PhoneStorage.kt`
- `app/src/main/java/dev/icode/mobile/data/files/FilePlaces.kt`
- `app/src/main/java/dev/icode/mobile/ui/screens/entry/FilesScreen.kt`

## Android document and file integration

### DocumentsProvider

**Status: Needs Verification**

`WorkspacesProvider` exposes one local root named **iCode** with the summary **Workspaces** in Android's system document UI. Its root document ID is `/workspace`. Android apps normally reach it through a system picker and receive only the URI grant chosen by the user.

The provider implements:

- querying the root, documents, and child documents;
- opening files for read and supported write/truncate modes;
- creating files and folders;
- renaming, deleting, and moving items inside a workspace; and
- child-document checks and change notifications.

Creating a folder at the provider root can create a workspace. Whole workspaces do not advertise rename, delete, or move actions through the provider; those lifecycle-sensitive operations belong in iCode. The provider has no direct copy operation. Android may copy by streaming through create and open, but provider-side copy is not a public guarantee.

Provider writes do not use iCode's Trash, conflict sheets, workspace-usage checks, or explorer undo history. An edit from another app is reconciled through iCode's external-change handling. Provider document IDs and symlinks are confined to existing workspace content; links that resolve outside the workspace tree are not listed or opened.

The code needs emulator or phone coverage for discovery, grants, mode handling, mutation, notifications, whole-workspace restrictions, and malicious or symlinked document IDs.

Implementation: `app/src/main/java/dev/icode/mobile/data/storage/WorkspacesProvider.kt` and its declaration in `app/src/main/AndroidManifest.xml`.

### FileProvider, Open with, and Share

**Status: Needs Verification**

A separate non-exported AndroidX `FileProvider` creates temporary content URIs for:

- app-private workspace files;
- primary Shared files; and
- camera files in iCode's temporary assistant-camera cache.

Open with sends an Android view intent with a temporary read grant. Share sends one or several files with read grants and ClipData. Mixed file types use `*/*`. A successful Open with action adds the file to Recent.

URI creation, chooser recipients, MIME handling, grant lifetime, and camera write grants need phone checks against real recipient apps. Maintainers should start at:

- `app/src/main/java/dev/icode/mobile/ui/SharedFiles.kt`
- `app/src/main/res/xml/shared_file_paths.xml`
- `app/src/main/AndroidManifest.xml`

## File operations and clipboard

One process-local clipboard is shared by the Private explorer, Shared explorer, and workspace tree. It can hold several top-level selections; descendants are removed when an already-selected parent includes them. Copy remains available for repeated paste. A successful Cut paste clears the clipboard.

The common action set is context-sensitive and can include Open, Open with, Share, Open Terminal, Show in folder, Cut, Copy, Paste, Move to, Copy to, Duplicate, Rename, archive actions, Favorites, Copy path, Properties, Select, and Delete.

Batch copy and move preflight every selected item. If one refusal invalidates the operation, iCode does not start a partial batch. Name conflicts support Replace, Keep both, Skip, and folder merge, with an option to apply an answer to remaining conflicts. Duplicate always chooses a free sibling name. Batch rename provides a preview and checks case-insensitive collisions for Shared.

Paths are confined to their selected place. Create and rename never overwrite silently. Move and copy refuse a folder's own subtree. Tree walks do not follow symlinks, and a symlink operation acts on the link rather than its target.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/files/FileOperationsCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/files/FileOperator.kt`
- `app/src/main/java/dev/icode/mobile/data/files/PlaceFiles.kt`
- `app/src/main/java/dev/icode/mobile/domain/files/FileClipboard.kt`
- `app/src/main/java/dev/icode/mobile/ui/FileItemActions.kt`
- `app/src/test/java/dev/icode/mobile/data/files/FileOperationsTest.kt`

## Trash, undo, and history

Trash is separate for each place:

- Private Trash is app-private and outside `/workspace`.
- Shared Trash is under the app's `Android/media` directory and includes `.nomedia`.

A Trash item stores an opaque ID, the moved tree, and a record of its original path, name, deletion time, and known size. Restore recreates missing parent folders and never overwrites an existing item. Items at least 30 days old are purged when Trash is initialized or read. Shared Trash is available only while All Files Access remains granted.

Moving to Trash requires an atomic move on the same volume. If Android reports that the move cannot be atomic, iCode refuses it instead of silently falling back to a riskier copy-and-delete. Whether a particular phone treats Shared and its app-specific `Android/media` directory as the same movable volume remains **Needs Verification**.

Explorer undo and redo keep the latest 50 changes in memory. They record concrete creations, moves, Trash IDs, restores, archive results, and empty-folder removals. Before undoing or redoing, iCode checks that the filesystem still matches the recorded post-operation state. If an outside change intervened, it refuses instead of overwriting that change. A new operation clears redo. Permanent Delete and Empty Trash are not undoable.

Clipboard and explorer undo/redo history do not survive process death. Editor text undo is a separate history.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/files/FileTrash.kt`
- `app/src/main/java/dev/icode/mobile/data/files/FileHistory.kt`
- `app/src/main/java/dev/icode/mobile/domain/files/FileHistory.kt`
- `app/src/test/java/dev/icode/mobile/data/files/FileTrashTest.kt`
- `app/src/test/java/dev/icode/mobile/data/files/FileOperationsTest.kt`

## Archives, properties, and storage analysis

Archive work runs as a task and stages output on the destination volume before placing the final result. Extraction supports ZIP/JAR/APK, tar, tar with gzip/bzip2/xz, single gzip/bzip2/xz streams, and 7z with password prompts. Compression supports ZIP, 7z, and tar.gz. RAR, zstd, and encrypted ZIP are not supported.

Private extraction may recreate links only when they point inside the extracted tree. Shared extraction skips links and names that collide only by case. 7z does not preserve links. Failed or cancelled staging is removed, and successful results participate in explorer undo. A whole workspace cannot be compressed into a file at Private's top because that level accepts only workspace directories.

Properties show the item kind, link target where relevant, runtime path, modified time, executable state for Private regular files, and immediate file size. Folder and selection totals are measured asynchronously without following links. If part of a tree is unreadable, the byte total stays unknown instead of being guessed. Android does not provide a reliable creation time, so iCode does not present one as authoritative.

Manage storage scans All, Private, or Shared. It excludes both Trash trees, never follows links, accounts for `.git` bytes separately, and reports categories, largest files, and duplicate groups. Duplicate detection narrows candidates by size, hashes the first 4096 bytes, then hashes the full file. Scan results live only in the process. iCode's own recorded operations update them, while terminal, provider, or other external changes require a new scan.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/files/FileArchives.kt`
- `app/src/main/java/dev/icode/mobile/data/io/Archives.kt`
- `app/src/main/java/dev/icode/mobile/data/files/FileInsightsCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/files/StorageScan.kt`
- `app/src/test/java/dev/icode/mobile/data/files/StorageScanTest.kt`

## File kinds, icons, viewers, and hand-off

`FileTypes` is the shared classification table. It combines whole-name rules, name prefixes and patterns, longest-known extensions, optional first-line rules for text, and up to 512 bytes of signature data for ambiguous or unknown names. The resulting families are Code, Data, Document, Image, Video, Audio, Archive, App, and Binary.

The same classification drives editor eligibility, storage categories, thumbnails, MIME correction, and icon selection. For example, `.ts` is TypeScript unless its bytes identify an MPEG transport stream, while `.d.ts` remains source.

File and folder icons use the bundled Material Icon Theme data through one renderer. Name, parent/name combinations, extension, language ID, folder-open state, and optional sniffed type can all contribute to selection. Rendered SVGs are cached and adjusted for palette contrast.

Image is currently the only dedicated in-app binary viewer. SVG remains editable source. Other text opens in the editor. Binary, oversized, or undecodable content is offered to another Android app. PDF and other binary formats therefore use Open with. Viewer routing by filename means an extensionless image may be recognized for Properties or MIME but still fall through editor rejection to Open with.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/domain/FileTypes.kt`
- `app/src/main/java/dev/icode/mobile/data/io/FileHeads.kt`
- `app/src/main/java/dev/icode/mobile/domain/FileSignatures.kt`
- `app/src/main/java/dev/icode/mobile/ui/FileIcons.kt`
- `app/src/main/java/dev/icode/mobile/domain/FileViewers.kt`

## Drafts and external changes

Unsaved text is stored outside the workspace in app-private `files/document-drafts`, keyed by the document location. Drafts are invisible to builds, Git, workspace search, `/workspace`, and DocumentsProvider clients.

A dirty document is backed up one second after typing becomes quiet. A later edit replaces the pending timer. Leaving the screen flushes pending drafts. Saving, discarding, or returning to clean text removes the draft.

When a leftover draft differs from disk, the document opens read-only until the user chooses Restore or Keep Disk Version. If the disk changed after the draft was recorded, saving restored text requires explicit overwrite consent.

Saves compare against the exact disk version originally opened and do not silently overwrite an external change. Workspace watchers coalesce filesystem events and reconcile open documents:

- A clean open document reloads automatically.
- Dirty text that conflicts with disk asks the user to Reload from Disk, Overwrite Disk, or Compare with Disk.
- An externally deleted file remains open with its current buffer and is reported once.

Provider, terminal, Git, assistant, and other-app changes bypass explorer history and enter through this external-change path. Device-specific provider notifications and inotify behavior remain **Needs Verification**.

Maintainer entry points:

- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentDraftStore.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentRecoveryCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/DocumentSessionCoordinator.kt`
- `app/src/main/java/dev/icode/mobile/data/workspace/WorkspaceWatcher.kt`

## Filesystem boundaries and limitations

- Private is case-sensitive and can preserve executable bits and internal symlinks.
- Shared is treated as case-insensitive and cannot be assumed to preserve Unix links or executable modes.
- Copying to Shared skips symlinks and names that collide only by case and reports them. Cross-place Cut refuses the operation before copying if every item cannot be represented.
- Import back into Private can repair Git-recorded executable bits and committed links when Git is available.
- Tree operations do not follow symlinks. Links that escape a workspace or explorer place are excluded or refused.
- Workspace/runtime prefix conversion is a naming map, not the security boundary. Filesystem operations perform canonical confinement checks.
- Private `.git` is protected in ordinary explorer operations. Shared `.git` is ordinary data.
- Automatic nested Git discovery stops after three levels.
- Only primary shared storage is an explorer place.

Real Shared-storage case handling, symlink behavior, executable preservation, and provider behavior vary by device and remain **Needs Verification**.

## Persistence and data removal

Workspace files, runtime files, drafts, Private Trash, settings, and persisted session metadata survive ordinary process death. In-memory clipboard, explorer undo/redo, storage-scan results, selected nested repository, current tasks, and live subprocesses do not. Android and the device vendor decide when a background process can continue; iCode does not promise that a command resumes after process termination.

App updates normally retain app-private data. iCode disables Android cloud backup and device-transfer backup, so it does not promise recovery through those systems.

**Android App info → Clear storage removes app-private data, including workspaces, runtime files, drafts, Private Trash, settings, and sessions.** Clear cache is narrower and must not be treated as a workspace reset.

On supported Android versions, the system may offer a user choice to retain app data during uninstall because the app declares fragile user data. The prompt and its effect depend on Android, the device vendor, and the user's choice. Uninstalling without retaining data removes app-private workspaces. Ordinary files in Shared remain phone files; app-specific Shared Trash is expected to be removed with the app and needs device verification.
