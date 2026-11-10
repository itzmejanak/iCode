# Real Icons, the Keys Rows, and the Typing Path

Owner's request (2026-10-18): the keys row should give a developer what the Android keyboard lacks,
drawn with real icons; every glyph or emoji standing in for an icon across the app becomes a real
icon, consistently; and the code behind it is held to its performance and design, fixing what is
wrong on the way.

Owner's decisions (2026-10-18):

| Question | Decision |
|---|---|
| Home and End | Added to the editor's keys row, after the arrows |
| Problems file header | The file's type icon, its name, its count; nothing collapses, so no chevron |
| Terminal tab status | Running: the Terminal icon in the accent (owner, 2026-10-20; green before); ended or failed: the error icon in red |
| The editor title's dirty dot | Removed: only the tab shows it |

## 1. What stands today (verified)

- Icons are Material's `Icons.Outlined` throughout (193 uses) and `Icons.AutoMirrored.Outlined` for
  directional ones (18); no Filled, Rounded or drawable icons in screens (files and folders draw
  Material Icon Theme's own icons, [file-kinds-icons-plan.md](./file-kinds-icons-plan.md)). Sizes come from
  `IcodeSizes.IconSm` 16 dp, `IconMd` 18 dp, `IconLg` 22 dp, `IconXl` 24 dp.
- The keys rows' labels live in the domain: `EditorKey("◀")`, `TerminalExtraKey("▶")`, and a symbol
  key repeats its text as its label (`OpenBrace("{", "{")`).
- `KeyChip` draws text only; `BottomBarEntry` draws an icon only for a choice; buttons take text only.

## 2. Audit: every glyph drawn as an icon

Found by scanning `app/src/main` for arrows, geometric shapes, dingbats, `×`, `⊕`, `‹ ›` and emoji
outside comments, then reading each.

| Where | Today | Becomes |
|---|---|---|
| `EditorKey`, `TerminalExtraKey` | `⇤ ◀ ▲ ▼ ▶ ↶ ↷` labels | Icons in the UI (§3); the domain keeps only what a key types |
| `DocumentTabStrip` | `"● "` before a dirty name; `×` close | `StatusDot`; `Icons.Outlined.Close` |
| `TerminalTabStrip` | `●` / `✖` status; `×` close | `Terminal` green / `ErrorOutline` red; `Close` |
| `WorkspaceEditorScreen` title | `"  •"` when dirty | Removed (owner) |
| `EditorChangeSheet` | `‹ Prev`, `Next ›` | `ArrowUpward` Prev, `ArrowDownward` Next |
| `DiffViewScreen` bottom bar | `⌃ Prev`, `Next ⌄` | `ArrowUpward` Prev, `ArrowDownward` Next |
| `EditorFindScreen` | `⌃`, `⌄` chips | `ArrowUpward`, `ArrowDownward` chips |
| `ProblemsScreen` | `"⌄  file"` header | File type icon, name, count (owner) |
| `BranchSelectorScreen` | `"⊕  Create New Branch"` | `Add` icon in the button |
| `CommitPushScreen` | `"✓ Verified"` | `Verified` icon before "Verified" |
| `GitBottomBar` | `"↑2 ↓0"` | `ArrowUpward` 2, `ArrowDownward` 0 |
| `Stepper` | `−`, `+` chips | `Remove`, `Add` chips |
| `WorkspaceSettingsScreen` | `"+ Add Exclude Pattern"` | `Add` icon before the words |

Previous and next are up and down arrows everywhere, as VS Code draws them for both its quick diff
(`gotoPreviousLocation`/`gotoNextLocation` = `Codicon.arrowUp`/`arrowDown`, iconRegistry.ts) and
its find widget (`findPreviousMatchIcon`/`findNextMatchIcon`, findWidget.ts).

Kept as text, because they are text: the ellipsis `…`, `·`, `–`, `—`, `›` in a settings path written
in a sentence, `→` in Git's own rename output, `−` in "+3 −1" counts, ESC/CTRL/ALT, "Aa", "Ab*",
".*" (VS Code's own find toggles are these letters), the symbols a key types, and the `+` a sentence
uses to name the + button.

A bug found on the way: `DiagnosticRow` draws the warning triangle for every severity. It takes the
icon from its caller: `ErrorOutline` for an error, `WarningAmber` for a warning, `Info` for info, as
VS Code's Problems view does.

## 3. The keys rows

`EditorKey` keeps what a symbol key types (`OpenBrace("{")`) and nothing for an action key; the
UI maps each key to its face, in one file per row's keys:

| Editor key | Icon | Does |
|---|---|---|
| Tab | `AutoMirrored.Outlined.KeyboardTab` | `indentOrCommitTab` |
| Outdent | `AutoMirrored.Outlined.FormatIndentDecrease` | `unindentSelection` |
| Left, Up, Down, Right | `KeyboardArrowLeft/Up/Down/Right` | `moveSelection` |
| Home | `Outlined.FirstPage` | `moveSelection(LINE_START)`: the line's first non-blank character, then column 0 (Sora's `enhancedHomeAndEnd`, on by default) |
| End | `AutoMirrored.Outlined.LastPage` | `moveSelection(LINE_END)`: the end of the line's text, then the line's end |
| Undo, Redo | `AutoMirrored.Outlined.Undo/Redo` | `undo`, `redo` |
| A symbol | its text | `commitText` |

`TerminalExtraKey` keeps no labels either: ESC, CTRL, ALT are text; Tab and the arrows are the same
icons as the editor's.

Code and the terminal are drawn left to right in every locale, so the rows are laid out left to
right too: the arrows point the way the caret moves. `FirstPage` has no auto-mirrored version in
Material's set; drawn left to right it needs none.

Each icon key is spoken by its name ("Tab", "Outdent", "Left", "Home", "Undo"). Persisted key lists
store enum names, which do not change; Home and End join the defaults.

## 4. Design system

- `KeyChip(icon, description, …)` beside `KeyChip(text, …)`, one surface for both; a text chip may
  carry a spoken `description` ("Match case"). A chip with no `onClick` is drawn muted: in every
  use it is a key that does nothing now (no matches, the terminal not running).
- `BottomBarItem.icon` is drawn before any entry's label, not only a choice's. `BottomBarEntry`
  takes content of its own (the sync counts) and gives it the entry's state, tap and colour.
- `PrimaryButton` takes a leading `icon`.
- `DetailRow` takes a leading `valueIcon`.
- One tab strip, `IcodeTabStrip`, for documents and terminals: today each screen carries its own copy
  of the same scroll, drag-to-reorder and close code (Document 208 lines, Terminal 159).

## 5. The typing path

Measured by reading what each keystroke runs:

| Finding | Fix |
|---|---|
| Every keystroke writes the whole file's recovery draft to disk (`DocumentRecoveryCoordinator.sync`) | A draft is written 1000 ms after the last edit, VS Code's backup delay (`DEFAULT_BACKUP_SCHEDULE_DELAYS.default`, workingCopyBackupTracker.ts); a file back to clean has its draft removed at once, as VS Code discards its backup; every waiting draft is written when iCode leaves the screen |
| `CodeEditorSession.bind` copies the editor's whole text and compares it with the document's at every recomposition | When the document holds the very string the editor sent, or nothing changed since, nothing is copied or compared |
| The change markers' collector compares the open documents' texts by value at every state change | By identity: a document changes only by a new copy |
| The editor compares change lists by value on every recomposition | By identity |
| The marker renderer walks every change each frame | It stops at the first change below the visible lines |

Not changed: watching folders per keystroke and the auto-save compare cost little next to these.

## 6. Where it goes

| Part | Files |
|---|---|
| Domain | `AppSettings.kt` (`EditorKey`, `TerminalExtraKey`) |
| Data | `DocumentRecoveryCoordinator.kt`, `WorkspaceSessionCoordinator.kt`, `GitLineChanges.kt` |
| Design system | `IcodeButtons.kt`, `IcodePanels.kt`, `IcodeControls.kt`, `IcodeRows.kt`, `IcodeStatus.kt`, new `IcodeTabStrip.kt` |
| UI | new `KeyFaces.kt`; the screens of §2; `CodeEditorSession.kt`, `IcodeCodeEditor.kt` |
| Tests | Draft timing (`DocumentRecoveryCoordinatorTest`) |
| Docs | `layout.md` §13 and §17.3, `editor-plan.md` §4 |

## 7. As built

### 7.1 What changed beyond the plan

- `IcodeTextButton` takes a leading `icon` too, for the change sheet's Prev and Next. `KeyChipRow`
  had one caller left (Terminal Settings, by its text labels) and is gone: both settings pages
  draw the rows' own chips.
- A terminal tab's title takes the documents' colours (the selected one bright, the others
  secondary): its state is the icon's, and is spoken ("running", "ended", "failed to start").
- The Problems header names the file's folder after its name, muted, as VS Code's Problems view
  does: two files of one name in different folders are two groups, and their headers say which.
- The Find toggles "Aa", "Ab*", ".*" are spoken as Workspace Search's are: "Match case", "Whole
  word", "Regular expression".
- Text read as one: "Search branches" and "Search repositories" lose their "...", as every other
  search field's placeholder; "Checking the runtime…" and "Checking available storage…" take the
  ellipsis the app's other progress lines use.
- A file's grammar by its `#!` line reads at most 256 characters before looking for the line
  break, not the whole first line of a one-line file.

### 7.2 How it was checked

| Check | What it showed |
|---|---|
| `DocumentRecoveryCoordinatorTest` | A draft waits for the typing to stop and holds the last text; is written once its delay passes; goes at once when the file is clean, and the waiting one never comes; is not written once its tab closes, it is saved, or its workspace closes. Stable over five runs |
| The whole suite | 824 tests; the one failure is `TreeSizeTest`'s unreadable directory, which root can read |
| Type-checks | Every screen check, the editor against Sora 0.24.6's classes, and a check of the screens no other check compiled (Terminal, Workspace and Branch settings, Runtime Installing, Repository Selector, `KeyFaces`) |
| Guards | Backend and UI consistency; every changed file within its line budget |
| API 30 | No platform reference above API 30 |
| Icons | Every icon used exists in material-icons-extended 1.7.8's classes; none is deprecated (`FirstPage` has no auto-mirrored version; `LastPage`, `KeyboardTab`, `Undo` use theirs) |

### 7.3 On the phone, open

- The keys row: Home twice on an indented line (first character, then column 0), End twice on a
  line ending in spaces; the icons in an RTL language still point the way the caret moves.
- Type, then leave iCode at once: the draft is there (Document Recovery after a forced stop).
- A dirty tab's dot; a terminal tab's icon when its shell is killed; the Problems header after a
  failed build; the sync arrows on Source Control's bar; the Verified icon on a signed commit.
