# Workspaces — the home screen shows the recent ones, one page manages them all — plan

Status: **plan** (2026-10-01). Owner's request: "the home screen shows only recent workspaces; for
better CRUD of workspaces make a Workspaces page; on the home screen the New Folder tile becomes
the workspace navigator, and inside it the features that really matter — respecting the
workspace's lifecycle, without duplication or inconsistency." Then: "the user can cut, copy and
use other features on a long press on the workspace list's items; UI consistent, and the best, cool
UI."

## 1. Measured (code, 2026-10-01)

| What | Where | Problem |
|---|---|---|
| Every workspace, most recently opened first | the launcher (`LauncherScreen`) | grows without end; the home screen is a list to scroll |
| Every workspace again, with its size | Storage settings (`StorageSettingsScreen`) | a second full list of the same thing |
| One set of actions — Open, Open Terminal, Rename, Show in Files, Copy path, Delete | `WorkspaceActionsMenu`, on both lists | right: one menu. Keep it |
| The list itself | `WorkspaceLibraryCoordinator.workspaces()` — the workspaces folder is the registry; sorted by last opened, a folder never opened by its date | right: one source. Keep it |
| Sizes | `RefreshStorage` measures the runtime, the cache **and** every workspace together (`StorageDetails.workspaceSizes`) | a page that needs workspace sizes pays for measuring the whole runtime |
| Making one | New Folder, Import, Clone: three tiles on the launcher | New Folder has no place of its own to live once the tile goes |
| Lifecycle | `WorkspaceLifecycleCoordinator`: create, rename (refused while a task holds it; history, tabs and session follow), delete (a task; refused while in use; history forgotten) | right: every page uses these intents, none of its own |

## 2. Design

1. **Home: the recent five.** The launcher lists the five most recently opened workspaces under
   **Recent**, with the letterpress behind. The tiles become **Import · Clone Repo · Workspaces**:
   the third, where New Folder was, opens the Workspaces page. First run (none yet) keeps its empty
   state, worded for the new tiles.
2. **The Workspaces page** (new route `Workspaces`, no design frame, built from the design system's
   parts) — the one place that manages them:
   - **Create:** **New Folder** in its top bar (the existing New Folder page). Import and Clone
     stay on the home screen, where they already are: one entry each, never two.
   - **Read:** every workspace — name, `/workspace/<name>`, branch, last opened, and **size** once
     measured; a **search** field (name or path) and **sort**: Recent · Name · Size.
   - **Update / Delete:** the same `WorkspaceActionsMenu` (Open, Open Terminal, Rename…, Show in
     Files, Copy path, Delete…); a tap opens it.
   - **Summary:** "N workspaces · total size" at the top.
3. **One list, not three.** Storage settings keeps what is its own — device, runtime and cache —
   and its Workspaces section becomes one row, "N workspaces · total size", opening the Workspaces
   page. The home screen shows a window on the same list (the recent five). All three read the one
   `workspaces` list.
4. **Sizes on their own.** Workspace sizes move out of `StorageDetails` into their own snapshot
   field, measured by their own intent (`MeasureWorkspaces`), which the Workspaces page and Storage
   settings ask for. The runtime and cache measurement stays with `RefreshStorage`. A size is
   measured, bounded, and "…" until then — never guessed.
5. **Rules in one place** (`domain`, pure, tested): the recent window, the search, the sort
   (unmeasured sizes last), the summary line.
6. **A long press is the menu, as in the Explorer.** A long press on a workspace row opens its
   actions where the finger is, in the same `IcodeActionMenu` the Explorer's long press uses; the
   ⋮ button opens the same list. One list, built once: Open, Open Terminal, Rename…, **Cut**,
   **Copy**, Show in Files, Copy path, Delete…. On the home screen's recent rows as well as on the
   page.
7. **One clipboard across iCode** (D5, the owner's choice). What is cut or copied — a whole
   workspace, or a folder or file in one — pastes wherever folders are: on the Workspaces page it
   becomes a workspace (a copy as "<name> copy"; a folder cut from a workspace becomes its own), and
   in a workspace's Explorer it goes inside the folder pasted into. A cut item moves once; a copied
   one pastes again. The clipboard is iCode's (`clipboard` in the snapshot), not a
   screen's: the Explorer and the Workspaces page show the same one.
8. **The backend, without a second copy of anything** (owner: "no duplication in the backend;
   abstractions where needed"):
   - **The file work is `WorkspaceFiles`**, rooted at the workspaces folder: its copy (free names,
     nothing half-copied), its move (never overwrites, never into itself), its confinement (never
     outside, never `.git`). No new file code.
   - **One answer to "may this change now?"** — `WorkspaceUsage`: a task holding it, unsaved edits
     or recovery drafts under it, Git working in it, and for a whole workspace a terminal running
     in it. Today the lifecycle (rename, delete of a workspace) and the Explorer (rename, move,
     delete of an item) each ask part of this in their own words; both ask `WorkspaceUsage`.
   - **One paste**, where cut and copy already live (`WorkspaceItemsCoordinator`): move or copy
     (a task) from anywhere in the workspaces into a folder or the workspaces folder, then its
     consequences through what exists — tabs and recents follow a move inside the open workspace
     (`followMove`), a folder that became a workspace shows in the library, a workspace that stopped
     being one is **retired** (closed if open before, its settings and history gone after) by the
     lifecycle's own step, which Delete uses too.
   - The Explorer's `MoveWorkspaceItem` / `CopyWorkspaceItem` intents give way to `CutItem`,
     `CopyItem`, `PasteInto(folder)` and `PasteAsWorkspace`: one way in.
   - Refused, with the reason: a file pasted as a workspace (a workspace is a folder), a name a
     workspace cannot have (`WorkspaceNameRules`), a cut onto a name that is taken, anything pasted
     into itself.

## 3. Steps (each: gate, commit)

| Step | What |
|---|---|
| P1 | Domain: `WorkspaceListRules` (recent, search, sort, summary); workspace sizes as their own snapshot field and intent; the library measures them apart from the runtime; tests |
| P2 | The Workspaces page and its route; New Folder reached from it; route and capture fixtures |
| P3 | Home: Recent (five), the Workspaces tile, the empty state's words |
| P4 | Storage settings: the summary row in place of the second list; docs (layout, user stories, architecture) |
| P5 | One clipboard: `WorkspaceUsage` (both callers moved onto it), the lifecycle's retire step (Delete moved onto it), the clipboard in the snapshot and the paste (the Explorer moved onto it); tests on real folders |
| P6 | The long-press menu on workspace rows (one action list for long press and ⋮: Open, Open Terminal, Rename…, Cut, Copy, Show in Workspaces on the home screen, Copy path, Delete…), the clipboard bar with Paste on the Workspaces page and the Explorer; Show in Files removed (owner); device check |

## 4. Decisions — the owner may overrule

- **D1** Five on the home screen, as VS Code's welcome page lists its recent folders.
- **D2** Import and Clone stay home tiles; the Workspaces page creates with New Folder only.
- **D3** Sort by Recent by default; Size sorts measured sizes first, largest on top.
- **D4** No bulk selection yet: every action is per workspace, through the one menu.
- **D5** (owner, 2026-10-01): **one clipboard across iCode**, not "duplicate only" (§2.7).

## 5. Record

- **P1** (2026-10-01): `WorkspaceListRules` (domain): the recent five, search by name or path, sort
  by Recent · Name · Size (the unmeasured last), and the summary, which totals only when every
  workspace is measured whole. The size label ("Measuring…", "Size exceeds scan limit") moved from
  the Settings screens into the domain: one wording for every page. Workspace sizes left
  `StorageDetails` for their own snapshot field (`workspaceSizes`), measured one workspace after
  another by their own intent (`MeasureWorkspaces`), each shown as it lands; the device, runtime
  and cache stay with `RefreshStorage`. The two measurements are one intent family
  (`StorageIntent`, routed to the library off the lock as tasks and components are), which keeps
  the gateway at 372. 5 tests. Gate: 562 tests; lint 0 errors / 12 warnings; both guards pass.
- **P2 + P3** (2026-10-01), together: the route needs its way in. The Workspaces page
  (`WorkspacesScreen`, route `Workspaces`, no frame): the summary under its title, New Folder in its
  top bar, search, Recent · Name · Size, and every workspace with its size once measured (measured
  on arrival and whenever the list changes). The home screen's row moved out of the launcher into
  `ui/WorkspaceRow` — one row for the home screen and the page, the size shown where a list
  measures it. Home: **Recent** lists the five; the tiles are Import · Clone Repo · **Workspaces**;
  the empty state names them. Gate: 562 tests; lint 0 errors / 12 warnings; both guards pass.
- **P4** (2026-10-01): Storage settings' second list of workspaces became one row — "N workspaces ·
  total" — opening the Workspaces page; it keeps what is its own (device, runtime, cache, All Files
  Access, Clear Cache) and its sentence on where workspaces live. The actions menu is now used by
  the two lists that remain. Layout §9 and US-ENTRY-001 / US-STORAGE-001 say so.
- **P5** (2026-10-01): one clipboard, no second copy of anything. `WorkspaceUsage` answers "may this
  change now?" for everyone: a task (its dialog shows), a file with unsaved edits or an unanswered
  recovery question (the Explorer's own rule, which the lifecycle's plainer one gave way to),
  recovery drafts, and for a whole workspace a terminal or Git in it. The lifecycle's `inUse` and
  the Explorer's `blocked` / `busy` are gone. The lifecycle's **release** (close it if open) and
  **retire** (settings and history go) are Delete's steps, and the paste uses them for a cut
  workspace. The clipboard is the snapshot's (`ItemClipboard`); `CutItem`, `CopyItem`,
  `PasteInto(folder)` and `PasteAsWorkspace` replace the Explorer's move and copy intents, routed in
  one line. The paste reuses `WorkspaceFiles` rooted at the workspaces folder: a cut moves in one
  step and leaves the clipboard; a copy is the cancellable task and stays for another paste. What
  follows a move is one rule: inside one workspace tabs, tree and recents follow; out of it tabs
  close and recents go (the paths' `workspaceOf` is `WorkspacePaths`' own). Refused with the
  reason: gone, into itself, a file as a workspace, a workspace pasted as one, a name a workspace
  cannot have, a cut onto a taken name, anything held. The Explorer shows the one clipboard and names what Paste brings
  ("Paste RevPlayer"). The gateway came down to 367 (budget lowered). 4 usage tests, 1 file test on
  real folders. Gate: 567 tests; lint 0 errors / 12 warnings; both guards pass.
- **P6** (2026-10-01): one action list for a whole workspace, shown by ⋮ and by a long press on
  the row alike (`IcodeOverflowMenu` and `IcodeActionMenu` with the same list, as the editor's tabs
  do): Open, Open Terminal, Rename…, **Cut**, **Copy**, **Show in Workspaces** (the home screen's
  recent rows: the Workspaces page), Copy path (its icon a link now: Copy is the clipboard's),
  Delete…. **Show in Files** is gone at the owner's word — the intent, its effect, the prompt and
  the provider's link builder with it; the Files app still lists the workspaces. `ClipboardBar`
  (ui): what waits on the clipboard — its name, cut or copied, **Paste**, ✕ (`ClearClipboard`) —
  at the foot of the Workspaces page (paste as a workspace) and of the Explorer (paste into the
  selected folder); the Explorer's Paste items name what they bring. The gateway came down to 364
  (budget lowered). Gate: 567 tests; lint 0 errors / 12 warnings; both guards pass.

