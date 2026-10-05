# iCode — Native Mobile Development Environment — User Stories

**Version:** 1.0  
**Status:** Behavioral contract  
**Product name:** iCode  

## 1. Purpose

This document describes product behavior from the perspective of developers and contributors. Stories are grouped by capability, not delivery order. Acceptance criteria define observable outcomes and avoid prescribing internal implementation unless a product boundary depends on it.

Task decomposition, dependency ordering, progress tracking, and release grouping belong in a separate implementation plan.

## 2. Personas

| Persona | Description |
|---|---|
| Developer | Opens, edits, searches, runs, and versions projects on an Android device |
| Power user | Installs and operates arbitrary command-line tools in the Linux runtime |
| AI-assisted developer | Uses a self-installed agent or automation process that modifies workspace files |
| Tablet developer | Uses larger layouts, pointer input, or a hardware keyboard |
| Contributor | Improves the application's native features and foundation |
| Maintainer | Publishes application/runtime releases and documentation |

## 3. Global Acceptance Rules

The following rules apply to every story:

1. A workspace exists in one place, the application's storage: nothing makes a hidden copy, mirror or synchronisation of it.
2. File writes target the workspace file itself.
3. No account, provider, extension, or agent setup is required for local editing.
4. User-installed processes are not limited to a platform-maintained allowlist.
5. The interface is native Android and does not depend on a browser-rendered IDE.
6. Project URLs open in the user's external browser.
7. Git command success, runtime state, and process state are derived from actual results.
8. Android filesystem restrictions are described accurately.
9. Compact and expanded layouts expose the same underlying product state.
10. Destructive actions identify their target and consequence.

## 4. Application Entry

### US-ENTRY-001 — See useful entry actions

**As a developer, I want to see my workspaces and the main workspace actions when the application opens, so that I can start work without navigating a dashboard.**

Acceptance criteria:

- The Launcher shows Import, Clone Repository, and Workspaces.
- The five most recently opened workspaces appear below the primary actions; the Workspaces page lists every one, with search, sort and sizes, and New Folder.
- The screen does not require account, provider, language, or agent configuration.
- Runtime attention appears only when installation, update, or repair affects the requested action.
- Settings and About remain accessible without competing with workspace actions.

### US-ENTRY-002 — Reopen a workspace

**As a returning developer, I want to reopen a workspace, so that I can resume quickly.**

Acceptance criteria:

- Each row identifies the workspace by name and its `/workspace/<name>` path, with its branch when it is a repository.
- Selecting a row opens the workspace.
- The list is the application's workspaces folder itself: nothing in it can be stale, and a workspace made from the terminal in `/workspace` appears.
- Each row's menu, from ⋮ or a long press, offers Open, Open Terminal, Rename, Cut, Copy, Copy path and Delete, and on the home screen Show in Workspaces.
- What is cut or copied pastes on the Workspaces page as a workspace, or in a workspace's Explorer inside the chosen folder, from a bar that names it.

### US-ENTRY-003 — Continue after runtime preparation

**As a first-time developer, I want runtime preparation to continue into my requested workspace, so that setup does not become an unrelated onboarding flow.**

Acceptance criteria:

- Preparation begins only when a runtime-backed capability is required.
- Progress and current operation are visible.
- Failure shows a reason and retry or diagnostic action.
- Successful preparation continues the action the user originally requested.
- No provider or agent selection follows automatically.
- The runtime's tools are offered once after it is built, never forced: Standard preselected, Skip installs nothing, and a feature that needs a tool installs it when used, saying so (owner, 2026-09-30; [runtime-components-plan.md](./runtime-components-plan.md)).

## 5. Where Workspaces Live

### US-STORAGE-001 — Know where my work is

**As a developer, I want to know that my workspaces live in the application's storage and what protects them, so that I never lose work by surprise.**

Acceptance criteria:

- The Workspaces page lists every workspace with its size; Storage settings shows their count and total and says that workspaces live in the application's storage.
- Uninstalling offers to keep the application's data.
- App info offers Android's own Clear storage, which removes the workspaces with the rest of the application's data (owner, 2026-09-30).
- Runtime Repair and Reset state that workspaces are kept, and keep them.
- Workspaces need no storage permission; All Files Access is asked only when I import from phone storage.

### US-STORAGE-002 — Bring a folder in from phone storage

**As a developer with a project on phone storage, I want to import it, so that I can work on it with every tool.**

Acceptance criteria:

- Import opens iCode's browser of phone storage. Without All Files Access it explains why it is needed and opens Android's page; once granted, the folders are listed.
- I choose a folder by going into it and tapping Import; the folder is only read.
- The workspace name is proposed from the folder and can be changed; a taken name is refused inline.
- An import that would not fit in the free space is refused before anything is copied.
- Progress is shown as a task that can be hidden; cancelling or a failure leaves nothing behind.
- The original folder is never written.
- A repository's executable bits and links are restored from Git's own record, and its status reads as before the import; when Git cannot be had, the result says what was not restored.

### US-STORAGE-003 — Reach my workspaces from other applications

**As a developer who also uses other apps, I want my workspaces in Android's Files app and pickers, so that I can open, edit, share, or copy their files elsewhere.**

Acceptance criteria:

- Android's Files app shows iCode, with every workspace as a folder.
- Files can be opened, edited, saved, created, renamed, moved, and deleted there.
- A workspace can be copied out to any folder.
- A link inside a workspace never leads another application out of the workspaces.
- Changes made there appear in iCode like any other outside change.

## 6. Workspace Management

### US-WS-001 — Open a workspace

**As a developer, I want to open a workspace, so that I can work on its files with every tool.**

Acceptance criteria:

- The Explorer shows the workspace's files.
- Terminal, Git, search, and language services resolve the same content at `/workspace/<name>`.
- No mirror or synchronised copy is created.

### US-WS-002 — Create a workspace folder

**As a developer, I want to create a new workspace by naming it, so that I can start fresh work.**

Acceptance criteria:

- The user enters a valid folder name; it is created at `/workspace/<name>`, shown as it is typed.
- Invalid or taken names receive inline feedback.
- The created folder becomes the active workspace.

### US-WS-003 — Clone a repository

**As a developer, I want to clone a Git repository into a new workspace, so that I can work on it.**

Acceptance criteria:

- The user supplies a repository URL; the folder name follows Git's rule for the address and can be changed.
- Clone progress and command failure are visible.
- Authentication is handled through the user's Git/runtime configuration.
- A successful clone opens the created workspace.
- A failed or cancelled clone leaves nothing behind and does not report the workspace as ready.

### US-WS-004 — View workspace identity

**As a developer, I want to see the active workspace's name and path, so that I always know which files I am modifying.**

Acceptance criteria:

- The workspace's name and `/workspace/<name>` path are shown.
- Git repository and branch appear when detected.

### US-WS-005 — Switch workspaces safely

**As a developer, I want to switch to another workspace without losing my current context, so that I can work across projects.**

Acceptance criteria:

- Dirty documents are saved, recovered, or explicitly left dirty before switching.
- The current workspace session is persisted.
- The selected target opens using its own document, panel, Git, and language state.
- Returning restores the previous workspace session where possible.

### US-WS-006 — Rename or delete a workspace

**As a developer, I want to rename or delete a whole workspace, so that my list stays the way I want it.**

Acceptance criteria:

- Rename and Delete are refused, with the reason, while a terminal session or a task runs in the workspace, or a file in it has unsaved edits.
- A renamed workspace keeps its settings, recent files, and — when open — its tabs and tree.
- Delete names the workspace and its path, says it cannot be undone, and runs as a task.
- A deleted workspace's settings and recent files are forgotten.

## 7. Explorer and File Operations

### US-FILE-001 — Browse the workspace tree

**As a developer, I want a responsive file tree, so that I can navigate large projects without waiting for a complete recursive scan.**

Acceptance criteria:

- Root entries appear without enumerating every descendant first.
- Directories load when expanded.
- Loading is shown on the affected directory.
- Git and diagnostic markers appear without replacing the filename.
- The selected file is revealed when requested from another surface.

### US-FILE-002 — Create files and folders

**As a developer, I want to create files and folders from the Explorer, so that routine project changes do not require the terminal.**

Acceptance criteria:

- Creation occurs under the selected concrete directory.
- Name validation prevents invalid or conflicting paths.
- The new item appears in the tree without a full workspace reload.
- A newly created text file can open immediately in the editor.

### US-FILE-003 — Rename and move items

**As a developer, I want to rename or move a file or directory, so that I can reorganize a project natively.**

Acceptance criteria:

- The target path is shown before applying the operation.
- Open-document paths update after a successful move or rename.
- Git state refreshes and recognizes eligible renames.
- Failure leaves the original item and document mapping intact.

### US-FILE-004 — Delete an item deliberately

**As a developer, I want deletion to identify what will be removed, so that touch mistakes do not destroy project content.**

Acceptance criteria:

- Deletion identifies the exact path.
- Non-empty directory deletion communicates its breadth.
- Confirmation is required when the operation is not safely recoverable.
- Open documents referencing a deleted path receive a clear state.
- Git state refreshes after successful deletion.

### US-FILE-005 — React to external changes

**As a developer, I want files changed by the terminal, an agent, Git, or another application to update in iCode, so that every tool shares one source of truth.**

Acceptance criteria:

- Explorer state refreshes after detected changes.
- A clean open document reloads or offers a non-disruptive update according to editor settings.
- A dirty open document never silently loses local text.
- Git and diagnostics are invalidated for changed paths.

### US-FILE-006 — Handle binary and large files safely

**As a developer, I want unsupported or oversized files to open safely, so that the editor does not freeze or corrupt them.**

Acceptance criteria:

- The application inspects the file before normal text editing.
- Binary content does not pass through a normal text save path.
- Large-file mode clearly states which expensive features are disabled.
- The user can view metadata, open with another Android application, or cancel.

## 8. Editing

### US-EDIT-001 — Open and edit a text file

**As a developer, I want to open and edit a text file with native touch behavior, so that coding feels intentional on Android.**

Acceptance criteria:

- The editor shows the file's content, line numbers, and syntax highlighting when available.
- Touch selection, cursor placement, scroll, copy, and paste work correctly.
- The software keyboard keeps the active cursor visible.
- Hardware keyboard text entry and navigation work when connected.

### US-EDIT-002 — Work with multiple documents

**As a developer, I want multiple open documents, so that I can move between related files without losing position.**

Acceptance criteria:

- Open documents appear in an ordered tab collection.
- Switching tabs preserves cursor, selection, scroll, and folds.
- Each dirty tab has a visible non-color-only indicator.
- Closing a dirty document requires save, discard, or cancel.
- Close Others and Close Saved are available through contextual actions.

### US-EDIT-003 — Save to the workspace file

**As a developer, I want Save to update the workspace file itself, so that other tools see the same content.**

Acceptance criteria:

- Save writes the canonical workspace path.
- Successful save clears dirty state and its recovery buffer.
- Git state updates from the saved file.
- Save failure retains dirty text and explains the reason.
- The application never substitutes an application-private copy.

### US-EDIT-004 — Configure autosave

**As a developer, I want to choose whether and when documents autosave, so that the editor fits my workflow.**

Acceptance criteria:

- Autosave can be disabled or configured through Editor settings.
- The active setting's scope is clear.
- Autosave does not overwrite an unexpected external revision silently.
- Save failures remain visible and leave the document dirty.

### US-EDIT-005 — Undo and redo reliably

**As a developer, I want document-scoped undo and redo, so that I can reverse editing mistakes.**

Acceptance criteria:

- Undo and redo operate in the correct document.
- Opening another document does not destroy existing history unnecessarily.
- A save does not falsely remove the ability to undo recent edits.
- External-revision decisions create an understandable history boundary.

### US-EDIT-006 — Find and replace within a file

**As a developer, I want file-level find and replace, so that I can edit repeated content quickly.**

Acceptance criteria:

- Find highlights matches and reports current/total position.
- Next and previous navigation wrap predictably.
- Case and regular-expression options are available where supported.
- Replace One and Replace All affect only the open document.
- Invalid expressions show feedback without modifying text.

### US-EDIT-007 — Navigate code structure

**As a developer, I want line navigation, folds, bracket matching, and indentation behavior, so that large files remain manageable.**

Acceptance criteria:

- Go to Line positions and reveals the requested line.
- Fold state can be changed and restored.
- Matching brackets are identifiable near the cursor.
- Tab, indentation width, and spaces-versus-tabs follow active settings.
- Formatting characters and word wrap can be configured.

### US-EDIT-008 — Recover unsaved work

**As a developer, I want dirty text recovered after interruption, so that Android lifecycle events do not destroy work.**

Acceptance criteria:

- Dirty content is written to an application recovery store.
- Restart offers or performs safe restoration according to conflict state.
- A newer on-disk file is not silently replaced by recovered text.
- The user can compare, restore, or discard conflicting recovery data.
- Recovery data is removed after durable save or explicit discard.

### US-EDIT-009 — Resolve an external-edit conflict

**As a developer, I want clear choices when another process changes my dirty file, so that neither version disappears silently.**

Acceptance criteria:

- The editor detects that disk content differs from the saved revision.
- Reload, Compare, and explicit Overwrite choices are available.
- Compare shows both relevant versions.
- Cancel preserves dirty local text.
- The chosen result updates Git and language-service state.

## 9. Quick Open and Commands

### US-NAV-001 — Quickly open a file

**As a developer, I want to search workspace filenames and paths, so that I can navigate without expanding directories manually.**

Acceptance criteria:

- Quick Open is reachable by touch and hardware shortcut.
- Results update as text is entered.
- Filename and path context are distinguishable.
- Selecting a result opens the document and restores editor focus.
- Recent files appear before input when useful.

### US-NAV-002 — Run an application command

**As a developer, I want a searchable Command Palette, so that infrequent actions do not clutter the interface.**

Acceptance criteria:

- Commands are searchable by label and category.
- Unavailable commands explain or reflect why they cannot run.
- Selecting a command executes it against the correct workspace or focused object.
- Dismissing the palette restores previous focus.

## 10. Workspace Search

### US-SEARCH-001 — Search across files

**As a developer, I want fast project-wide content search, so that I can find usages and configuration across a repository.**

Acceptance criteria:

- Search supports plain text and regular expressions.
- Case, include, and exclude controls are available.
- Results stream and remain cancellable.
- Results group by file and show line number and excerpt.
- Selecting a result opens the exact range.

### US-SEARCH-002 — Respect project exclusions

**As a developer, I want search to avoid irrelevant generated or ignored content by default, so that results remain useful.**

Acceptance criteria:

- Repository ignore rules are respected by default where applicable.
- The user can explicitly include excluded content.
- Active include and exclude patterns are visible.
- Search behavior does not permanently change project files.

### US-SEARCH-003 — Replace across files deliberately

**As a developer, I want to replace matches across the workspace with a clear scope, so that large edits remain controlled.**

Acceptance criteria:

- The interface previews affected files and match count.
- Individual matches or files can be excluded when supported.
- Replace All requires explicit confirmation.
- Modified files become normal workspace-file and Git changes.
- Failure reports affected and unaffected paths without claiming complete success.

## 11. Source Control

### US-GIT-001 — Recognize repository state

**As a developer, I want iCode to recognize my Git repository, so that I can understand version state immediately.**

Acceptance criteria:

- The active repository, branch, and detached-head state are accurate.
- Staged, unstaged, untracked, renamed, deleted, ignored, and conflicted paths are distinguished.
- Ahead and behind counts appear when an upstream is configured.
- Explorer decorations and Source Control derive from the same repository snapshot.

### US-GIT-002 — See editor change markers

**As a developer, I want Git change markers beside edited lines, so that I can understand modifications while coding.**

Acceptance criteria:

- Added, modified, and deleted-line positions have distinct markers.
- Markers update after save and repository refresh.
- State is not communicated by color alone.
- Activating a marker can reveal the related change where supported.

### US-GIT-003 — Inspect an unstaged diff

**As a developer, I want to compare my working file with the index, so that I can review an unstaged change before staging it.**

Acceptance criteria:

- The diff is generated from Git state, not from an unrelated editor snapshot.
- Inline presentation is readable on compact screens.
- Wider layouts may show side-by-side comparison.
- Added, removed, and unchanged regions are distinguishable.
- Next and previous hunk navigation are available.

### US-GIT-004 — Inspect a staged diff

**As a developer, I want to review the staged version against the current commit, so that I know what the next commit will contain.**

Acceptance criteria:

- Staged diff remains distinct from unstaged diff.
- A file with both states can expose each comparison.
- The UI labels which versions are being compared.
- Refresh after staging or unstaging produces current content.

### US-GIT-005 — Stage and unstage a file

**As a developer, I want to stage or unstage selected files, so that I can prepare a focused commit.**

Acceptance criteria:

- Stage acts on the selected concrete path.
- Unstage preserves the working-tree content.
- Operation progress and failure are visible.
- The file moves to the correct group only after Git succeeds.
- Filenames containing spaces or special characters work correctly.

### US-GIT-006 — Discard a working-tree change

**As a developer, I want to discard a selected change with a clear warning, so that I do not remove work accidentally.**

Acceptance criteria:

- The confirmation identifies the exact path and irreversible consequence.
- Dirty open-document state is reconciled before or after the Git operation without silent loss.
- Cancellation makes no repository change.
- Success refreshes the editor and Source Control state.

### US-GIT-007 — Commit staged changes

**As a developer, I want to enter a message and commit staged changes, so that I can version my work without leaving iCode.**

Acceptance criteria:

- The commit action requires eligible staged changes.
- The message is validated without inventing repository policy.
- Git hooks and configured identity behave as they do in the runtime.
- Failure preserves the message and displays the Git error.
- Success refreshes branch, status, and ahead/behind state.

### US-GIT-008 — Work with branches

**As a developer, I want to see, create, and switch branches, so that I can manage parallel work.**

Acceptance criteria:

- Local branches are searchable.
- The active branch is unmistakable.
- Creating a branch validates its name through Git.
- Switching respects Git's dirty-worktree behavior and reports blockers.
- Detached-head state is not mislabeled as a normal branch.

### US-GIT-009 — Fetch, pull, and push

**As a developer, I want to synchronize through my existing Git configuration, so that the application does not require a proprietary repository account.**

Acceptance criteria:

- Fetch, pull, and push use the runtime's Git and credential configuration.
- Operation output and errors are available.
- A missing upstream or authentication failure offers actionable information.
- The UI updates remote and ahead/behind state after success.
- The application does not claim network success while offline.

### US-GIT-010 — Resolve merge conflicts

**As a developer, I want conflicted files grouped and editable, so that I can complete merges on the device.**

Acceptance criteria:

- Conflicted files appear before normal changes.
- Conflict ranges and current/incoming sections are identifiable.
- Accept Current, Accept Incoming, Accept Both, and manual editing are available where applicable.
- Mark Resolved is a separate explicit action.
- The file leaves the conflict group only after Git reports it resolved.

### US-GIT-011 — Understand an in-progress operation

**As a developer, I want to know when a merge, rebase, cherry-pick, or revert is in progress, so that I do not treat the repository as a normal clean state.**

Acceptance criteria:

- Detectable in-progress state appears prominently in Source Control.
- Conflicts and required next actions are visible.
- The application does not hide relevant Git errors.
- Completion or abort occurs through explicit Git-backed actions where supported.

## 12. Terminal and Runtime Tools

### US-TERM-001 — Open a workspace terminal

**As a developer, I want a real terminal in the workspace, so that I can use normal development commands.**

Acceptance criteria:

- The terminal connects to a PTY in the Linux runtime.
- Its initial directory maps to the active workspace.
- ANSI colors, cursor movement, interactive prompts, and signals work.
- The terminal does not receive a restricted command allowlist.

### US-TERM-002 — Use multiple sessions

**As a developer, I want multiple terminal sessions, so that I can keep a server, tests, and an interactive shell separate.**

Acceptance criteria:

- The user can create, name, switch, and close sessions.
- Each session preserves its own scrollback and working process while alive.
- Closing a session with a running process communicates the consequence.
- A stopped session is not shown as running.

### US-TERM-003 — Use terminal controls on touch

**As a mobile developer, I want accessible control keys, so that shell and terminal applications remain usable without a hardware keyboard.**

Acceptance criteria:

- Escape, Tab, Ctrl, Alt, arrows, and configured extra keys are available.
- Keys can be reordered or hidden.
- Ctrl+C and other combinations reach the PTY correctly.
- The accessory row does not cover active output.

### US-TERM-004 — Install an arbitrary tool

**As a power user, I want to install a compatible tool through the runtime's package managers, so that the application does not decide my toolchain.**

Acceptance criteria:

- The user can invoke available package managers from the terminal.
- Installed files persist across application restarts and normal updates.
- The application does not require a tool-specific adapter or registration.
- Installation errors remain ordinary command output and exit state.

### US-TERM-005 — Run a self-installed AI agent

**As an AI-assisted developer, I want to run my chosen agent as a normal command, so that I retain provider and workflow choice.**

Acceptance criteria:

- The application does not present a supported-agent list.
- The process inherits the terminal's accessible runtime and workspace paths.
- Files changed by the agent update Explorer, editor, and Git state.
- Agent authentication and configuration remain owned by that CLI.
- The application does not add per-command approvals around the process.

### US-TERM-006 — Continue a long-running command

**As a developer, I want an eligible command to continue while I switch applications, so that builds or servers are not stopped merely because the UI is hidden.**

Acceptance criteria:

- The runtime uses an Android foreground service when continued execution requires it.
- A visible notification identifies that development processes are active.
- Returning shows verified process state.
- If Android terminated the process, the application reports it as lost or stopped rather than running.

### US-TERM-007 — Open a development URL externally

**As a developer, I want a URL printed by my development server to open in my installed browser, so that the application does not duplicate a browser.**

Acceptance criteria:

- HTTP and HTTPS URLs are recognizable in terminal/output text.
- Activating a URL sends it through Android's external browser handling.
- The application does not create an internal preview tab or browser panel.
- Returning to the application preserves the editor and terminal session.

### US-TERM-008 — Inspect process completion

**As a developer, I want clear process exit state, so that I know whether a command succeeded.**

Acceptance criteria:

- Exit code is retained when available.
- Failed, cancelled, killed, and normally exited states are distinguishable.
- Output remains readable after completion.
- Restart creates a new verified process lifecycle.

## 13. Language Intelligence

### US-LANG-001 — Add a generic language service

**As a developer, I want to configure any compatible language server command, so that language support does not depend on an application release.**

Acceptance criteria:

- Configuration includes language/file matching, command, arguments, environment, root markers, and initialization options.
- Required fields are validated.
- Saving does not install or pretend to install the server command.
- A configured service appears in the language-service list with status.

### US-LANG-002 — Receive completion

**As a developer, I want context-aware completion from my language server, so that native editing remains productive.**

Acceptance criteria:

- Completion is requested only when supported by the server.
- Results can be navigated by touch or keyboard.
- Inserting a completion applies the server-provided edit correctly.
- Server failure leaves normal text editing available.

### US-LANG-003 — See diagnostics

**As a developer, I want errors and warnings near affected code, so that I can fix issues quickly.**

Acceptance criteria:

- Diagnostic ranges render in the editor.
- File and workspace counts appear in Explorer and Problems.
- Severity, message, source, and location are accessible without relying on color alone.
- Stale diagnostics clear after relevant server or document state changes.

### US-LANG-004 — Navigate definitions and references

**As a developer, I want to jump to definitions and references, so that I can understand unfamiliar code.**

Acceptance criteria:

- The action appears only when the service reports support.
- A single target opens directly.
- Multiple targets appear in a navigable result list.
- Cross-file navigation preserves origin so Back returns correctly.

### US-LANG-005 — Rename a symbol

**As a developer, I want a language-aware rename preview, so that I can refactor across files safely.**

Acceptance criteria:

- The server validates the position and new name.
- A multi-file edit identifies affected files before application.
- Applying edits updates workspace files and normal Git state.
- Partial failure is reported and never represented as complete success.

### US-LANG-006 — Format code

**As a developer, I want document or selection formatting from my configured language service, so that style remains consistent.**

Acceptance criteria:

- Formatting is shown only when supported.
- Returned edits participate in document undo.
- The document becomes dirty until saved according to active settings.
- Formatting failure leaves existing text unchanged.

### US-LANG-007 — Use code actions

**As a developer, I want relevant code actions at a diagnostic or cursor position, so that supported fixes are available without a custom extension.**

Acceptance criteria:

- Actions are requested and displayed only when supported.
- The action title and scope are clear.
- Workspace edits and commands are applied through validated product contracts.
- Resulting file changes appear in Git.

### US-LANG-008 — Recover a failed language server

**As a developer, I want to inspect and restart a failed service, so that language intelligence can recover without closing the workspace.**

Acceptance criteria:

- Status distinguishes starting, ready, stopped, failed, and restarting.
- Failure includes useful exit or protocol information.
- Restart terminates stale state before establishing a new session.
- Normal editor and terminal functions remain available during failure.

## 14. Problems and Output

### US-PROB-001 — Review workspace problems

**As a developer, I want a consolidated Problems surface, so that I can move through errors and warnings across files.**

Acceptance criteria:

- Problems group by file and severity.
- Filters include severity, active file, and source where available.
- Selecting a problem opens the exact document range.
- Empty state accurately states that no current diagnostics exist.

### US-PROB-002 — Inspect structured output

**As a developer, I want bounded application-owned output channels, so that I can diagnose Git or language-service behavior without mixing it with interactive terminal sessions.**

Acceptance criteria:

- Output channels are clearly named.
- Interactive input is not expected in Output.
- Output remains bounded and clearable.
- Sensitive values controlled by the application are sanitized where practical.

## 15. Settings and Personalization

### US-SET-001 — Navigate settings hierarchically

**As a developer, I want settings organized by category, list, and detail, so that configuration remains understandable as the product grows.**

Acceptance criteria:

- Root Settings shows categories rather than every leaf control.
- Category pages preserve their own navigation state.
- Item collections, such as language services, open dedicated details.
- Back returns to the correct parent and scroll position.
- Compact and expanded layouts represent the same route hierarchy.

### US-SET-002 — Search settings

**As a developer, I want to search setting names and descriptions, so that I can find configuration without knowing its category.**

Acceptance criteria:

- Results show their category path.
- Selecting a result opens its real location.
- Search does not create a duplicated setting with separate state.
- Invalid or unavailable settings do not appear as editable controls.

### US-SET-003 — Use application and workspace scope

**As a developer, I want selected settings to differ by workspace, so that project needs do not alter every project.**

Acceptance criteria:

- The active scope is visible.
- Workspace override shows the inherited application value.
- Reset to Inherited removes the override.
- Settings that cannot be workspace-scoped do not offer the choice.

### US-SET-004 — Customize editor appearance

**As a developer, I want to configure editor font, size, wrapping, indentation, and theme, so that code remains readable on my device.**

Acceptance criteria:

- Changes preview or apply without corrupting document state.
- Editor and terminal font settings remain independent.
- Theme tokens preserve Git and diagnostic distinguishability.
- Reset restores documented defaults.

### US-SET-005 — Inspect runtime health

**As a developer, I want hierarchical runtime information and diagnostics, so that advanced details do not clutter normal coding.**

Acceptance criteria:

- Runtime category summarizes current health.
- Details expose version, architecture, paths, storage, and relevant process facts.
- Diagnostics are explicit user actions.
- Repair and Reset remain separate and state their consequences.

## 16. Adaptive Layout and Input

### US-LAYOUT-001 — Use a compact phone layout

**As a phone developer, I want one dominant coding surface with contextual tools, so that code is not squeezed by permanent desktop panes.**

Acceptance criteria:

- The editor uses the main available region.
- Explorer, Search, and Source Control open as compact tool surfaces.
- Selecting a file or result returns focus to the editor.
- Terminal and Problems expand from a remembered bottom panel.
- No essential action depends on hover or precise pointer input.

### US-LAYOUT-002 — Use an expanded tablet layout

**As a tablet developer, I want persistent navigation and resizable panes, so that larger width improves productivity.**

Acceptance criteria:

- Activity navigation can remain visible.
- A sidebar can coexist with the editor.
- The bottom panel is resizable and stateful.
- Settings may use list-detail presentation.
- Wider diff surfaces may use side-by-side mode.

### US-LAYOUT-003 — Rotate or resize without losing context

**As a developer, I want layout adaptation to preserve my work, so that rotation and multi-window changes are not disruptive.**

Acceptance criteria:

- Selected workspace, document, cursor, scroll, and panel state remain.
- Routes transform between pushed and list-detail presentation without duplication.
- The software keyboard does not leave the editor in an invalid layout.
- A resized terminal receives updated PTY dimensions.

### US-LAYOUT-004 — Use a hardware keyboard

**As a developer with a hardware keyboard, I want predictable shortcuts and focus, so that tablet and desktop-mode workflows are efficient.**

Acceptance criteria:

- Save, Find, Quick Open, Command Palette, tab navigation, and Terminal toggle have documented shortcuts.
- Terminal focus passes expected control sequences to the PTY.
- Editor focus preserves normal selection and navigation.
- Reserved Android/system shortcuts are not captured unexpectedly.

### US-LAYOUT-005 — Use accessibility services

**As a developer using accessibility features, I want meaningful labels, focus order, scaling, and non-color state, so that iCode remains operable.**

Acceptance criteria:

- Interactive icons expose accessible names.
- Focus order follows visible task order.
- Status includes text or icon semantics in addition to color.
- Font scaling preserves primary actions and navigable content.
- Reduced-motion preference is respected.

## 17. Persistence, Updates, and Recovery

### US-STATE-001 — Restore a workspace session

**As a returning developer, I want my workspace session restored, so that I continue from the previous context.**

Acceptance criteria:

- Open-document order and selected document restore where paths still exist.
- Cursor, scroll, folds, and panel state restore.
- Missing files receive a clear state rather than blocking the workspace.
- Dirty recovered documents follow recovery rules.

### US-STATE-002 — Preserve tools across normal updates

**As a power user, I want my runtime home and installed tools preserved during normal application updates, so that updates do not erase my environment.**

Acceptance criteria:

- Normal application update does not reset runtime home.
- Tool configuration and shell files remain.
- A runtime migration reports failures without pretending the previous state vanished safely.
- Any operation that could remove installed tools states that consequence beforehand.

### US-STATE-003 — Repair the runtime

**As a developer with a broken runtime, I want a repair action with explicit preservation behavior, so that I can restore functionality knowingly.**

Acceptance criteria:

- Diagnostics can be run before repair.
- Repair identifies what it checks, replaces, and preserves.
- Active processes are addressed before repair proceeds.
- Success is determined by health verification.
- Failure leaves actionable logs and retry options.

### US-STATE-004 — Reset the runtime deliberately

**As a developer, I want runtime reset separated from repair, so that destructive reinitialization cannot be triggered accidentally.**

Acceptance criteria:

- Reset states that runtime home, installed tools, credentials, or configuration may be removed according to the chosen reset scope.
- External workspaces are explicitly excluded from deletion.
- Confirmation names the destructive scope.
- Cancellation leaves the runtime unchanged.

### US-STATE-005 — Survive application process recreation

**As a developer, I want Android process recreation to restore truthful state, so that the interface does not misrepresent files or processes.**

Acceptance criteria:

- Workspace and documents restore from persisted state.
- Recovery buffers protect dirty text.
- Terminal processes are reattached only when verified alive.
- Lost processes are marked stopped or lost.
- Git and language state refresh from their real sources.

## 18. Assistant

Owner's decision, 2026-10-01 ([assistant-koog-plan.md](./assistant-koog-plan.md); PR-ASSIST-01–17); its runtime foundation, 2026-10-02 ([runtime-foundation-plan.md](./runtime-foundation-plan.md); PR-ASSIST-18–21).

### US-ASSIST-001 — Set up the assistant

**As a developer, I want to add my providers and their models once, so that I can ask any of them about any workspace.**

Acceptance criteria:

- Until a provider has a model, the Assistant page explains what the assistant does and what it sends, and offers its settings; nothing else asks.
- **Assistant** settings list the providers — kind, models, which one the chat uses, a missing key — and **+** adds one; each opens its own page.
- A provider's page offers OpenAI, Anthropic, Google Gemini, OpenRouter, Ollama and OpenAI-compatible servers when it is added; it has a name, a server's address only where it is needed, its own API key, and its models.
- **Add Models** lists the server's real models with the address and key as typed — or says why it could not — then the models iCode knows for that kind; any ID can be typed. A model iCode does not know can be given its context window, output limit, whether it reads images and whether it reasons.
- The API key is masked and never shown again once saved; Replace changes it.
- Save checks the form; Back with unsaved changes asks first; Remove asks first.

### US-ASSIST-002 — Ask about the workspace

**As a developer, I want to ask the assistant to explain, find or change something in the open workspace, so that I can work on a phone without doing every step by hand.**

Acceptance criteria:

- An empty chat shows the iCode mark over its letterpress, as the home screen does, with a few things to ask.
- Each reply shows as Markdown when it comes; each step shows what it did ("Read src/App.kt", "Ran `npm test`") and how it went; its output is a tap away.
- The composer chooses the mode (Agent, or Ask: it reads and changes nothing), the model — every provider's, listed under its name — and its reasoning effort where the model has one ("Auto" sends the provider's default).
- **+** opens **Attach**: Camera, Photos and Files (the phone's own pickers, no permission asked), then the workspace's files, the open one first; up to five in all. Text goes as its text then; a photo or a PDF goes to a model that reads it, and the composer says when the chosen model cannot. Photos show as thumbnails; a photo alone can be sent.
- The answer a turn ends on can be copied; a message that failed before any reply can be sent again with **Try Again**.
- Two fingers pinch the chat's text larger or smaller.
- Edits reach the Explorer, Source Control and clean open tabs as a terminal's edits do.
- A file with unsaved edits is never written; the assistant is told to ask for a save.

### US-ASSIST-003 — Approve a change or a command

**As a developer, I want to see each change and command before it happens, so that the assistant never surprises me.**

Acceptance criteria:

- A change shows its file and the text it replaces; a command shows its command line.
- Allow runs it; Deny tells the assistant it was refused; Allow All stops this chat's questions.
- The permissions are chosen on the chat page: "Ask before changes" (the default) or "Allow all changes"; reads never ask.

### US-ASSIST-004 — Stop the assistant

**As a developer, I want to stop a turn at any point, so that a wrong direction costs nothing more.**

Acceptance criteria:

- Stop ends the stream and a running command with everything it started.
- What was said and done stays; steps that had not run are marked stopped, and the next message continues the chat.

### US-ASSIST-005 — Leave while it works

**As a developer, I want the assistant to keep working when I switch apps, so that a long turn is not lost.**

Acceptance criteria:

- The turn continues with a notification naming its step, with Cancel.
- A question it waits on pops up while iCode is out of view; a tap opens the Assistant.
- A workspace the assistant is working in cannot be renamed or deleted until it stops.

### US-ASSIST-006 — Keep a workspace's chats

**As a developer, I want each workspace to keep its chats, so that I can come back to any of them.**

Acceptance criteria:

- **Chats** lists the workspace's chats newest first, each titled by its first message with when it was last used; a tap opens one, and one can be deleted after a confirmation.
- **New chat** starts an empty chat; it is listed once something is said in it.
- Each chat survives the app being closed or killed, as last saved; all of them follow a rename and go with a delete.

### US-ASSIST-007 — A long chat

**As a developer, I want a long chat to go on within the model's limits, so that I never have to start again to continue.**

Acceptance criteria:

- Under the message box, at the right, the page always shows how full the model's context is after the last request ("84k / 200k", "0 / 200k" before the first), moving with every step; **Compact Chat** is a tap away.
- Near the limit (80 %), the chat is compacted before the next message: the model writes a summary, and later turns send the summary and what came after it; the page shows where, and keeps everything.
- **Compact Chat** does the same on request.

### US-ASSIST-008 — Run a project's server

**As a developer, I want the assistant to start my project's server and leave it running, so that I can open it in my browser and keep working.**

Acceptance criteria:

- The assistant starts a server, a watcher or a worker as a process that keeps running; its answer names the process, the ports its output named and whether they answer.
- A row above the message box says what runs ("1 running"); it opens **Processes**: each process's name, command, state ("Running · 4m", "Exited 1", "Stopped"), its last output, and its ports — a tap opens `http://127.0.0.1:<port>` in the browser. Stop ends one; Remove clears one that ended.
- Processes keep running while iCode is out of view, with a notification whose Cancel stops them all; Android may still end them when many processes run on the phone, and the sheet says so.
- A workspace with a running process cannot be renamed or deleted until it is stopped.

### US-ASSIST-009 — Commands that behave as in my terminal

**As a developer, I want the assistant's commands to find what my terminal finds and remember where they ran, so that a setup I did once works for it too.**

Acceptance criteria:

- Tools installed through the profile (nvm, a conda init, `~/.local/bin`) are found without setting anything by hand.
- A `cd` carries to the chat's next command; an `export` does not, and the assistant is told so.
- A failed command says why in words — "command not found" with the path searched, "ended by SIGTERM" — and where it ran.
- The assistant's commands see this workspace alone under `/workspace`.

### US-ASSIST-010 — Know how a reply ended

**As a developer, I want to know when a reply did not finish, so that I never act on half an answer.**

Acceptance criteria:

- A reply cut short at the output limit, or stopped by the provider's content filter, is kept, and the page says so under it.
- A reply whose connection broke, or whose provider failed while writing it, ends the turn with those words; **Try Again** is offered where nothing was answered.
- The model's reasoning shows above its answer, folded; with OpenRouter, the reasoning and the answer appear as they are written.

## 19. Public Website and Distribution

### US-WEB-001 — Learn about the product

**As a prospective user, I want a clear public landing page, so that I understand the product's native, local-first purpose and Android boundaries.**

Acceptance criteria:

- The page describes direct workspace editing, native UI, Linux runtime, Git, terminal, and language services accurately.
- It does not claim universal tool, extension, root, or device compatibility.
- Documentation, source, downloads, and release notes are discoverable.

### US-WEB-002 — Download a release

**As a user, I want to identify and download an appropriate application release, so that installation is trustworthy and understandable.**

Acceptance criteria:

- Version, architecture, minimum Android requirement, checksum, and release notes are available.
- The download source and installation guidance are clear.
- Runtime compatibility information accompanies relevant releases.

### US-WEB-003 — Use iCode without the website

**As an existing user, I want local capabilities to continue when the public website is unavailable, so that hosted infrastructure is not a runtime dependency.**

Acceptance criteria:

- Existing workspaces open without website access.
- Editing, search, Git local operations, terminal, and installed language services continue.
- Network-dependent update checks fail non-destructively.

## 20. Community Contribution

### US-COMM-001 — Locate feature ownership

**As a contributor, I want clear module and contract ownership, so that I can modify one capability without navigating an application-wide class.**

Acceptance criteria:

- Repository documentation maps features to modules.
- Dependency rules identify allowed module direction.
- Editor implementation, runtime, Git, search, language, settings, and UI concerns are separated.
- Cross-module changes explain why a contract change is necessary.

### US-COMM-002 — Add a built-in capability coherently

**As a contributor, I want to register routes, settings, commands, and UI through stable product patterns, so that additions do not crowd root navigation.**

Acceptance criteria:

- New settings fit an existing category or justify a documented category.
- New screens identify their parent hierarchy and responsive presentation.
- New commands identify scope and availability.
- The feature uses existing file, runtime, persistence, and design-system contracts.

### US-COMM-003 — Validate a contribution

**As a maintainer, I want parser, state, integration, and device behavior covered by appropriate tests, so that community changes preserve foundation guarantees.**

Acceptance criteria:

- Machine-output parsers include fixtures for edge cases and unusual paths.
- State transitions have deterministic tests.
- File-writing changes prove that writes land in the workspace file itself.
- Runtime-facing features test success, failure, cancellation, and process death.
- Responsive UI changes validate compact and expanded layouts.

### US-COMM-004 — Publish release information

**As a maintainer, I want versioned application and runtime release metadata, so that users can verify compatibility and integrity.**

Acceptance criteria:

- Application and runtime versions are independently identifiable.
- Required architecture and compatibility fields are explicit.
- Checksums and supported signing information are published.
- Release notes distinguish application, runtime, migration, and known-limit changes.

## 21. Cross-Capability Scenarios

### Scenario A — Edit and commit an existing repository

1. Developer opens a workspace — cloned, or imported from phone storage.
2. Explorer reveals the repository tree and Git branch.
3. Developer opens and edits files.
4. Saved files receive Git decorations.
5. Source Control lists unstaged changes.
6. Developer reviews diffs, stages selected files, writes a message, and commits.
7. Repository state refreshes from Git.

Expected outcome: the workspace's repository contains the edit and commit; no other copy of it exists.

### Scenario B — Agent modifies the project

1. Developer installs an agent CLI through the runtime.
2. Developer launches it in the workspace terminal.
3. The agent edits several project files.
4. Explorer, clean open documents, Search, diagnostics, and Git react to external changes.
5. Dirty conflicting documents request a compare/reload decision.
6. Developer reviews all agent changes through Git diff.

Expected outcome: the product provides full visibility without knowing or restricting the agent by name.

### Scenario C — Run a web project

1. Developer opens a repository and starts its development server in Terminal.
2. The server prints an HTTP URL.
3. Developer activates the URL.
4. Android opens the installed browser.
5. Developer returns to iCode with editor and terminal state preserved.

Expected outcome: no internal preview browser or port dashboard is required.

### Scenario D — Recover after Android interruption

1. Developer has dirty documents and an active terminal.
2. Android recreates the application process.
3. The workspace and document session restore.
4. Dirty recovery buffers are reconciled with disk state.
5. Terminal processes are shown running only if verified.
6. Git and language state refresh.

Expected outcome: user work is protected and process state remains truthful.

### Scenario E — Configure an unfamiliar language

1. Developer installs a compatible language server through the terminal.
2. Developer opens Settings → Languages → Language services.
3. Developer supplies file matching and launch configuration.
4. The server starts when a matching document is opened.
5. Reported completion, diagnostics, navigation, and formatting capabilities become available.

Expected outcome: a new language workflow is possible without an application-specific extension or adapter.

## 22. Story Completion Standard

A story is behaviorally complete only when:

- all acceptance criteria are demonstrable;
- compact and applicable expanded layouts are verified;
- success, empty, loading, failure, and recovery behavior are defined;
- the one-copy workspace rule is preserved;
- accessibility labels and focus behavior are present;
- process and Git results are based on actual exit/state evidence;
- relevant persistence and process-recreation behavior is tested;
- documentation reflects Android or runtime limitations accurately;
- no unrelated root navigation or tool-specific restriction was introduced.
