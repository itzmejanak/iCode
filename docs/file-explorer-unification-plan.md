# File Explorer — iCode Explorer vs System Explorer, Unified File Actions (Plan)

Status: **built (2026-10-14), E1–E3; E4 on the device (§7)**

> Followed by [storage-explorer-plan.md](./storage-explorer-plan.md) (2026-10-14). There are no row ⋮ in any explorer
> (a long press opens the menu), + no longer offers Paste (the paste bar does), and the Workspaces page and
> the Import browser are one explorer with two places. Then [explorer-gaps-plan.md](./explorer-gaps-plan.md)
> (built 2026-10-28): the shared actions are one sheet, in one order, in every explorer; Delete goes
> to Trash; and many items are acted on at once.
Owner ask: "plan for the file explorer the current user explorer and ICode explore feels different system which is good somehow but if we make the better ui/Ux and better logic regarding files actions can be done eg: copy cut and other which are matter most if we make them flexible user can perform across the explorer among both by understanding what behavior currently exit and how things need to be aligned and how we can make things better without limitations but 100% accurate design let's plan for it"

## 1. Evidence — current behavior (read, not guessed)

| Area | Where | Behavior |
|---|---|---|
| Workspace Explorer (ICode) | `WorkspaceExplorerScreen.kt:81` — rail Expl/Srch/Git/Assistant; tree `LazyColumn` `state.files` | `FileEntry(path,kind,depth,expanded,gitStatus)`, `depth:0..N`, `.git` filtered (`WorkspaceFileSource`), Git tone: letter for files, dot for folders. Actions: `ExplorerItemMenu` — Rename/Cut/Copy/Paste(=disabled when clipboard null)/Delete. Paste target = `creationParentLocation(selectedPath)` (folder→itself, file→parent). Header `+` → New file/folder at `creationParent`. |
| Workspaces File Explorer (ICode) | `WorkspacesScreen.kt:74` — breadcrumb `FolderBreadcrumb`, search, `FilesSort.Name/Date/Size`, `LazyColumn` `FilesBrowserState` | `BrowsedItem(name,location,folder,bytes,changedAt,branch)`. Actions: `BrowsedItemRow` — Open/Rename/Cut/Copy/CopyPath/Delete. Top `NewItemMenu` + `ChooseBar` (placing clone/import). Sort/size/search local; sizes from `workspaceSizes` map. |
| Import browser (system phone storage) | `ImportBrowserScreen.kt:45` — sdcard breadcrumb, permission gate | Read-only listing of `PhoneFolder`; import via `ImportWorkspace(name, into)` task — no cut/copy/paste there. |
| System Files provider | `WorkspacesProvider.kt:32` — `DocumentsProvider`, authority `${appId}.workspaces`, root `WorkspacePaths.GUEST_ROOT=/workspace` | Exposes `files/workspaces` at `/workspace`. `leadsInside` canonical check guards links. `changed(Context)` broadcasts on every refresh. Whole-workspace move `isChildDocument` → `fileOf`. No staging exposure. |
| Backend file ops | `WorkspaceFileSource.kt:198`, `WorkspaceFiles`, `WorkspaceLibraryCoordinator`, `WorkspaceStaging` | One filesystem (`files/workspaces`, ext4). `WorkspacePaths`/`guestOf`↔`locationOf` single model. Staging: `complete(staged, into, name)` atomic. |
| Clipboard | `ICodeSnapshot.clipboard: ItemClipboard?`, `ClipboardBar.kt`, `WorkspaceItemsCoordinator.clipboard` (`ICodeIntent.CutItem/CopyItem/PasteInto/PasteAsWorkspace/ClearClipboard`) | **One clipboard** shared across WorkspaceExplorer + Workspaces file explorer (`state.clipboard` shown as bar + context menu Paste). Cut=`move()` atomic+clears, Copy=`TaskRunner.copy(count+deduplicate)`. `refusal()` guards: into-self, file→workspace, cut workspace already exists, `.git`, busy task. `WorkspaceUsage` blocks when file task/locked branch open. |
| Intents | `ICodeIntent.kt` — `CreateFile/CreateWorkspaceFolder/Rename/Delete ×Item`, `ClipboardIntent` (Cut/Copy/PasteInto/PasteAsWorkspace/Clear) | All item ops are request-id guarded (`Rename/Create*` success→`WorkspaceItemChanged`, fail→`WorkspaceItemFailed`), except Delete/paste which rely on snapshot refresh. |
| Guard | `tools/check-ui-consistency.sh` | Raw `Color(0x` only in `designsystem`, no screen dup; `ui-lines ≤300` allowed; `IcodeSelect` unified. |

### The "different systems" feeling (today)

* Two explorers: **tree** (depth-indented, expandable) vs **flat list** (bread-crumb folder, no depth). Two row primitives (`ExplorerRow` `IcodeListRow(Rectangle)` vs `BrowsedItemRow` `IcodeListRow(card)` + badge size).
* Two menus that do the same work but diverge: `ExplorerItemMenu` (5 items) vs `BrowsedItemRow` Overflow (4 + CopyPath). Same `ItemClipboard` underneath, but discovery differs.
* `Paste` appears in three places: row menu `Paste ${name}`, header `+` menu `Paste ${name}`, bottom `ClipboardBar Paste` — consistent data, but multiplicity feels ad-hoc.

## 2. Perception vs architecture (100% accurate)

* Perception: "user explorer" (ImportBrowser = system storage) vs "ICode explorer" (workspaces) feels separate — **intended** (private storage decision, Import copies). Family resemblance already: same `FolderBreadcrumb`, same `PrimaryButton Accent` after import.
* Architecture: there is **one clipboard topology** and **one path model** (`WorkspacePaths`). The Explorers are **two views of one file surface**, not two systems. System Files provider is the third view (read-only from Android's picker) over the same `files/workspaces`. So unification is UI polish + menu/bar alignment, not a data-model fork.

## 3. Target design — flexible, but without limitations

Principle: **one file-action model, every row pastes the same way, no action fires spuriously**.

* **Top level:** `ui/screens/entry/WorkspacesScreen.kt` stays the *system-level* explorer (browsing `files/workspaces`, choosing clone/import destination). Keeps breadcrumb, search, sort, choose bar, workspace-size summary.
* **Workspace level:** `ui/screens/workspace/WorkspaceExplorerScreen.kt` stays the *IDE tree* (coding nav, depth, Git dots). Keep `ActivityRail` + `SectionLabel`.
* **Actions unify:** single item-action definition used by both file explorers, exposed uniformly as **Row `⋮` overflow** + **long-press menu** with the same order:
  1. Open
  2. Rename…
  3. Cut — `ItemClipboard(location,name,cut=true)`
  4. Copy — `cut=false`
  5. Copy path (`clipboard` runtime guest path)
  6. Delete… (destructive)
  Workspace rows add `Open Terminal` only where `workspace==true` (already present). No action is shown disabled by accident; **Paste** is the only item that embeds the held name: `Paste ${name}` (via `pasteLabel/clipboard.name`), enabled iff `clipboard != null`.

* **Paste rules stay:** Paste target = the folder the row sits in (`creationParentLocation` for trees, `browser.path` for file explorer). Pastes never duplicate silently: copy auto-renames (`"copy"` suffix/`name (1)` pattern via `WorkspaceStaging`), cut into existing name = refusal. Whole-workspace cut → Paste-as-workspace variant already handled (`PasteAsWorkspace`). All guarded by `WorkspaceUsage` + `TaskRunner.waitReason`.

* **Clipboard surface single:** one `ClipboardBar` composable used by **both** explorers (already shared) plus header `Paste ${name}`. Floating over-screen bar is not duplicated.

## 4. Decisions (no assumptions)

* No background sync, no second clipboard, no storage migration, no `WorkspacesProvider` move/rename for workspaces (kept refuse on root — §4.6).
* No limits on copy/move (4096-entry guard removed already); Name Rules remain (`WorkspaceNameRules`).
* Import stays read-only; phone storage writes remain Import-copied.
* No new packages/modules; `domain` stays tokens-only.

## 5. What we will NOT do (out of boundary)

* Do not change the private-storage invariant, `hasFragileUserData`, or `WorkspacesProvider` security/leadsInside.
* Do not introduce multi-select/bulk actions — one item at a time (future follower).
* Do not change snapshot shape — `clipboard`, `filesBrowser`, `files`, `filesTree` etc. already there.

## 6. Steps (each: gate, commit — `check-ui-consistency + check-backend-consistency` + snapshot review)

| Step | What |
|---|---|
| E1 | Shared item actions: factor `FileItemActions` (pure `IcodeMenuAction` list + `pasteLabel`) and `CopyPath` helper, reuse in `ExplorerItemMenu` + `BrowsedItemRow`. No visual change, one owner. |
| E2 | Header parity: move ad-hoc header Paste into `FileItemActions`' "Paste ${name}" and ensure `IcodeSelect` menus all share the same `SelectMenu` shape (already done in C1). |
| E3 | Clipboard UX polish: confirm `ClipboardBar` appears identically in Workspaces + Explorer (bar color/subtitle consistency) and that row Paste names the held item; no action disables incorrectly when `clipboard==null`. Remove any duplicated bar logic. |
| E4 | Verification: walk-through — file↔folder cross-paste, workspace cut→file-explorer paste-as-workspace, copy with free-rename, import→workspace cycle; 37-route capture unchanged; guards PASS. |

Every step keeps the runtime contract: tasks off the mutation lock, `release/retire` on whole-workspace move, single `ItemClipboard`, single `WorkspacePaths`.

## 7. As built (2026-10-14)

Each decision below rests on the code read for §1; where the plan left a choice open, it was taken
for consistency with what iCode already does elsewhere.

### What every explorer now shares

| | Explorer (tree) | Workspaces page — files | Workspaces page and home — workspaces |
|---|---|---|---|
| Opened by | ⋮ **and** long press (was: long press only) | ⋮ and long press | ⋮ and long press |
| Actions | Open (a file) / **Expand · Collapse** (a folder), Rename…, Cut, Copy, Paste *name*, **Copy path**, Delete… | Open, Rename…, Cut, Copy, **Paste *name***, Copy path, Delete… | Open, Open Terminal, Rename…, Cut, Copy, **Paste *name***, Show in Workspaces (home only), Copy path, Delete… |
| Paste goes | into a folder row; beside a file row, into its folder | into a folder row; beside a file row, into the folder shown | into the workspace |
| + menu | New file, New folder, Paste *name* | New file, New folder (at the top: New folder), **Paste *name*** | — |
| Paste bar | **says where**: "Moves into /workspace/app/src" | **says where**; at the top "Copies here as a workspace" | — |
| Delete names | the runtime path (was: relative to the workspace) | the runtime path | the runtime path |

- **One owner** — `ui/FileItemActions.kt`: `of(…)` builds every row's list in one order; `creation(…)`
  every + menu; `copyPath` every Copy path; `NewItemButton` the + itself. `ExplorerItemMenu`, the two
  `pasteLabel`s and the two `copyPathAction`s are gone. A folder in the tree says what Open does
  there — Expand or Collapse; everywhere else Open is Open.
- **Paste** is in every row's list, named by what it holds, and greyed — not hidden — with nothing
  held. Its place follows the rule the tree already had (`creationParentLocation`) and that desktop
  file managers use: a folder takes it, a file passes it to the folder it is in.
- **The tree's rows** are compact, as Source Control's tree: the ⋮ is a full touch target and the row
  its height (48 dp; before, 12 dp of padding around the text — about the same). The long-press
  menu opens at the ⋮, as on the Workspaces page.
- **Copy path** copies the runtime path everywhere (`WorkspaceRuntimePath.within`), the tree's too —
  and the assistant's step menu, which copied a path relative to the workspace, labelled "Copy Path".
  Below Android 13 a toast says so; from 13 the system does (`ui/CopyText.kt`, moved from the
  assistant's steps so every copy behaves alike).
- **A paste that can never work is not offered** (the plan's "no action fires spuriously"): at the top
  of the workspaces, a file or a cut workspace cannot become a workspace. One rule says so —
  `ItemClipboard.notAWorkspace` — read by the paste itself (`WorkspaceItemsCoordinator.refusal`) and
  by the bar, which says why and greys Paste; the + menu leaves Paste out there. Every other refusal
  stays the paste's own, said when it is tried.

### Found with it, on main

- `AssistantRetryPolicy.stepText` did not compile: `AssistantRetryKind.Server` (`fc3c3d3`) had no
  branch in its `when`. It reads "Server error — retrying in 7s (1/5)…" now; `AssistantRetryPolicyTest`.
- `AssistantComposer` used `Icons.Outlined.KeyboardArrowDown` without its import: it did not compile.

### Verified

- Unit tests: `FileItemActionsTest` (the order, Paste named and greyed, a workspace's extras, Cut
  and Copy hold the item, the + menu), `ItemClipboardTest` (the rule, the runtime path),
  `AssistantRetryPolicyTest`; the routes, settings catalogue and the tree's creation parent as before.
- The screens type-check against Compose (desktop jars): both explorers, the shared actions, the
  paste bar, the assistant's screens. Guards; Android 11 API check.
- Not checked here: the device — the walk-through of E4 (cross-paste, a workspace cut and pasted as
  one, a copy's free name, Import into a workspace) is the owner's on the phone.
