# Editor — Git Change Markers, Keys Row, Editing Fixes

Status: **built (2026-10-18)**; §8 records what was built and how it was checked. The phone check is §8.3.

Owner ask (2026-10-18): "no git changes lines indicator … just like vs code from where user can see specific line changes diff clicking some actions like see diff revert … need terminal like button and setup from application setting of editor just like terminal … 100% accurate editor behavior for manual coding … other editors refinements that most matter and fixing bugs if any".

The owner chose (2026-10-18):

| Question | Choice |
|---|---|
| Markers | Bars beside the line numbers, VS Code's colours; a tap opens a change sheet |
| Sheet actions | Revert Change, Stage Change, Previous / Next, and a changes count in the bottom bar |
| Keys row | One row above the keyboard, only while it is open, replacing the bottom bar; editing keys, then symbols, scrolling sideways |
| Settings | Keys Row, Keyboard Suggestions (off by default), Git Change Markers, Auto-Close Brackets |

## 1. What stands today (verified)

| Fact | Where it was checked | What follows |
|---|---|---|
| The editor is Sora Editor 0.24.6 with TextMate grammars and each language's `language-configuration.json` (auto-indent on Enter, closing pairs). Since 2026-11-11 `ui/syntax/SyntaxEngine` gives each editor its language, parsed from the catalog when the file first needs it, within `SyntaxLimits` ([syntax-highlighting-plan.md §1.3](./syntax-highlighting-plan.md)) | `app/build.gradle.kts`, `assets/textmate/catalog.json` (the language catalog), `ui/editor/EditorSyntax.kt`, Sora's `TextMateNewlineHandler`, `TextMateSymbolPairMatch` | Editing rules come from the grammars; iCode sets Sora's switches |
| `docs/layout.md` §13.2 lists a "Git change gutter"; none is built | `layout.md`, `CodeEditorSession.kt` | This plan builds it |
| The terminal's keys row is `ExtraKeyRow` of `KeyChip`s, chosen in Terminal Settings › Extra Keys Row (`TerminalSettings.extraKeys`, DataStore `terminal_extra_keys`) | `TerminalControls.kt`, `TerminalSettingsScreen.kt`, `AndroidWorkspacePreferencesRepository` | The editor's row and its setting follow the same shape |
| VS Code's quick diff compares the buffer, unsaved edits included, with the **index** (`git show --textconv :path`); untracked, conflicted, ignored and symlinked files and `.git` contents get none | `extensions/git/src/quickDiffProvider.ts`, `fileSystemProvider.ts` (`sanitizeRef`), `git.ts` (`buffer`) | The same rules |
| VS Code's Stage Change writes the index with `git hash-object --stdin -w --path <p>` and `git update-index --cacheinfo <mode>,<sha>,<p>` | `git.ts` (`stage`) | The same commands |
| `git show :p` gives the index blob in its clean form: LF for a file `.gitattributes` checks out as CRLF. `git cat-file --filters :p` gives it as a checkout writes it | Tested with git 2.43 (Ubuntu 24.04's) | The base is `cat-file --filters`: compared with the file as it is on disk, a CRLF file is not one big change |
| `hash-object --path` applies the clean filter (CRLF→LF) before writing; `update-index --cacheinfo` then stages exactly that blob; `git diff --cached` shows only the staged change | Tested | Stage Change writes the base with one change applied, as the file's text |
| Git rewrites `<gitdir>/index` whenever the index changes; a commit leaves its entries as they were | Git's index format | The base changes only when the index file does: its size and time say when to read it again |
| Taps in the gutter reach Sora's `ClickEvent` with `REGION_LINE_NUMBER`, `REGION_DIVIDER_MARGIN` or `REGION_DIVIDER` and the tapped line; intercepting it stops the caret move and the keyboard. Sora's own side-icon tap reads the row with `offsetX` for `offsetY`, so it is not used | Sora `EditorTouchEventHandler.onSingleTapUp`, `RegionResolver.kt` | The marker's tap is a `ClickEvent` on a changed line |
| Sora draws gutter decorations in `EditorRenderer.drawUserGutterBackground`, in both line-number layouts; `CodeEditor.onCreateRenderer()` is the supported way to supply a renderer | Sora `EditorRenderer.drawView` | Markers are drawn there |
| Sora splits lines at LF, CRLF and a lone CR, as Git counts lines | Sora `LineSeparator`, `Content.insertInternal` | Git's line numbers are the editor's, plus one |
| Each keystroke updates the snapshot's text at once, on the main thread (`UpdateDocumentText` is not queued) | `AndroidICodeGateway.dispatch` | The backend's text is the view's |
| `Content.replace(start, end, text)` is one undoable edit that keeps the caret where the text around it stays | Sora `Content` | Text set from outside goes in as an edit, not `setText` |

## 2. Bugs found, and their fixes

| Bug | Cause | Fix |
|---|---|---|
| An open file changed outside the editor — an assistant edit, a terminal write, Reload from Disk, a restored draft — jumps the caret to line 1 and loses the undo history | `CodeEditorSession.bind` replaces the whole text with `setText` | The view applies the smallest edit between its text and the new one (`EditorTextRules.splice`), never between `\r` and `\n` |
| Backspace on an indented empty line deletes the whole line | Sora's `deleteEmptyLineFast` is on by default | Off |
| Backspace in space indentation removes one space, not one indent step | Sora's `deleteMultiSpaces` is 1 | Backspace in leading whitespace goes back to the previous tab stop, as VS Code's `editor.useTabStops` does (`EditorTextRules.deleteLeftCount`) |
| Enter in a CRLF file inserts a bare LF: the file is saved with mixed line endings | Sora's line separator defaults to LF, and its TextMate Enter handler writes `"\n"` | Sora's separator is the document's; the Enter handler's text and anything typed or pasted take the document's line breaks (`EditorTextRules.withLineBreaks`) |
| The keyboard may autocorrect and suggest inside code | Sora's input type is plain multi-line text | Keyboard Suggestions, off by default, adds `TYPE_TEXT_FLAG_NO_SUGGESTIONS` |
| Editor Settings shows a dead "Format On Save — Unavailable" row | A placeholder | Removed |

## 3. Git change markers

### 3.1 What is compared

The open file's text in the editor, unsaved edits included, with its version in the index as a checkout writes it (`git cat-file --filters :<path>`), decoded as the file is (`DocumentCodec`). Lines are compared with their line breaks, as `git diff` compares them.

A file has markers when Git's index holds it at stage 0 as a regular file (mode `100644` or `100755`). Untracked and ignored files, conflicted files, links and submodules have none, as in VS Code. Each file's repository is the one that holds it (`GitLocator.topOf`), so a file of any repository in the workspace has them.

### 3.2 The change list

`LineDiff.hunks(base, text)`: the changes as `git diff -U0` writes them — `GitDiffHunk`s with their removed lines, then their added lines, numbered as Git numbers them. Myers' shortest edit, in linear space (the middle-snake recursion), after the common start and end are set aside; it stops when the coroutine it runs in is cancelled. `LineChanges.between` (the assistant's counts) takes its counts from it, so one algorithm serves both.

A change is **Added** (no base lines), **Deleted** (no new lines) or **Modified**, as VS Code classes them.

### 3.3 When it is computed

| When | What |
|---|---|
| A file opens, or the open files change | Bases are read for the files that have none: one script per repository (`ls-files -s -z`, then `cat-file --filters` per file into the guest's `/tmp`) |
| Source Control reads the repository again (after any Git action in iCode, a save, coming back to iCode) | Each repository's `<gitdir>/index` size and time are compared with the ones its bases were read at; when they differ, its bases are read again |
| Stage Change | The bases of that repository are read again |
| The text or a base changes | 200 ms after the last change, as VS Code waits, the changes are computed again, off the main thread; a newer text cancels the computation in hand |
| Git Change Markers off | Nothing is read or computed; the markers go |

The snapshot holds, per open file with a base, `DocumentLineChanges(base, changes)`.

### 3.4 The markers

A 3 dp bar at the line numbers' right edge, as VS Code's `scm.diffDecorationsGutterWidth` default: green for added lines, blue for modified ones, a red wedge at the line boundary where lines were deleted. Drawn by iCode's renderer in Sora's gutter pass; with Line Numbers off there is no gutter, and the bottom bar's count still leads to the changes.

A tap anywhere in the gutter of a changed line (or on either side of a deletion's wedge) opens the change sheet at that change.

### 3.5 The change sheet

A bottom sheet: "Change 2 of 3 · Lines 13–15" and its kind; the removed lines in red and the added lines in green, numbered (`DiffRow`, as the diff screen draws them); then **Revert**, **Stage**, **Prev** and **Next** (the up and down arrows, [icons-plan.md §2](./icons-plan.md)).

| Action | What happens |
|---|---|
| Revert | The base's lines go back in place of the change: an edit in the editor, so Undo brings the text back. Refused when the text no longer holds that change |
| Stage | The index gets the base with only this change applied, encoded as the base was (`hash-object --path`, `update-index --cacheinfo` with the entry's mode). Source Control reads the repository again; the change leaves the markers, as it is staged |
| Prev / Next | The sheet moves to the previous or next change, and the editor shows it |

The bottom bar shows "3 changes" while the file has any; a tap opens the sheet at the first change at or after the caret's line, else the first.

## 4. Keys row

`EditorKey`: **Tab**, **Outdent**, **Left**, **Up**, **Down**, **Right**, **Home**, **End**, **Undo**, **Redo**, drawn as their icons ([icons-plan.md §3](./icons-plan.md)), then the symbols `{ } ( ) [ ] ; = " ' < > / \ | : , . ! & _ - + * # $ % ? @ ~ ^` and the backtick.

| Key | Does |
|---|---|
| Tab | Sora's `indentOrCommitTab`: indents the selected lines, or the line when the caret is in its indentation, else inserts one indent |
| Outdent | `unindentSelection` |
| The arrows | `moveSelection` |
| Home, End | `moveSelection(LINE_START)`, `(LINE_END)`: Sora's enhanced Home and End — the first non-blank character, then column 0; the end of the text, then the line's end |
| Undo, Redo | `undo`, `redo` |
| A symbol | Typed as from the keyboard (`commitText`): Auto-Close Brackets applies |

One row of `KeyChip`s, scrolling sideways, between the code and the keyboard, shown only while the keyboard is open, in place of the bottom bar. Not shown for a read-only file or the Markdown preview. Editor Settings › Keys Row chooses its keys and their order, as Terminal Settings › Extra Keys Row does for the terminal's (§4.1).

### 4.1 Arranging a keys row (2026-10-22)

The owner asked for the keys to be put in order by holding and dragging them, for every key card to be the same, and for the section to open and close. Both settings pages now use one card, `KeyRowSettings`, and the row above the keyboard shows the keys in the order it keeps.

| | Before | Now |
|---|---|---|
| Order | Fixed: a tap rebuilt the list in the built-in order | The user's. `editor_keys` and `terminal_extra_keys` already stored an ordered list, so nothing changes in storage |
| Key cards | `FlowRow` chips as wide as their labels (ESC and CTRL wider than an arrow), rows ending ragged | `IcodeKeyGrid`: as many 48 dp columns as fit, stretched to fill the width, every key the same size, the columns lined up |
| The page | 42 editor keys always shown | `IcodeExpandableCard`, closed at first, drawn as a settings select (arrow at its end, accent edge while open). Closed it reads "42 of 42 keys" over the row as it stands |

Open, the card holds:
- **The row's keys, in order:**
  - **Hold** one: it lifts (larger, accent edge, a haptic tick) and follows the finger, and the others slide out of its way. Letting go saves the order once.
  - **Hold and let go without moving:** nothing changes.
  - **Tap** one: it is taken out.
  - **TalkBack:** Move earlier and Move later on each key.
- **More keys:** the keys left out, in the built-in order. A tap adds one at the row's end.
- **Reset to Default:** every key, in the built-in order. It is enabled only when the row differs.

An empty row says "No keys: the row is not shown", which is what the editor and the terminal already do with no keys.

**Built as:**
- `KeyRows.toggled` and `KeyRows.others` (domain).
- `IcodeKeyGrid` and its pure geometry `KeyGridLayout` (design system). The keys are composed in a fixed order and placed by offset, so a held key stays the same node while the others move.
- `IcodeExpandableCard` (design system).
- `KeyRowSettings` (settings), used by both pages with their own chips.

**Checked here:**
- `KeyRowsTest` and `KeyGridLayoutTest`.
- Every data and domain test: 902, with the one known root-only failure.
- The UI type-check (Terminal Settings now in it too), and both guards.
- Extra Keys Row driven by touch in a Compose Desktop 1.7.3 scene, the saved row read after each step:
  1. The card opens on a tap.
  2. A tap takes ESC out, and a tap under More keys puts it back at the end.
  3. Holding CTRL and dragging it onto the first key saves `Ctrl Tab …`.
  4. Holding ALT and letting go changes nothing, and saves nothing.
  5. Move later moves the first key.
  6. Reset to Default restores the built-in order.
- Both cards drawn, closed and open.

## 5. Settings

Editor Settings gains, in `EditorSettings` (DataStore):

| Setting | Default | Applies |
|---|---|---|
| Keys Row (`editor_keys`) | every key | The row |
| Keyboard Suggestions (`editor_keyboard_suggestions`) | off | Sora's input type; the keyboard is restarted |
| Git Change Markers (`editor_change_markers`) | on | The backend reads and computes nothing while off |
| Auto-Close Brackets (`editor_auto_close_brackets`) | on | Sora's `symbolPairAutoCompletion` |

Format On Save's placeholder row is removed.

## 6. Where it goes

| Where | What |
|---|---|
| `domain/LineDiff.kt` | `LineDiff`: hunks, revert, stage; `LineChangeKind` |
| `domain/LineChanges.kt` | Counts from `LineDiff` |
| `domain/EditorTextRules.kt` | `splice`, `withLineBreaks`, `separatorOf`, `deleteLeftCount` |
| `domain/GitLineChanges.kt` | `DocumentLineChanges`, `StageLineChange` |
| `domain/AppSettings.kt`, `WorkspacePreferences.kt` | `EditorKey`, the four settings |
| `data/git/GitLineChanges.kt` | Bases, index stamps, the computation, Stage; owned by `GitWorkspaceCoordinator` |
| `data/git/GitIndexVersions.kt` | The script that reads index versions, and its reader |
| `data/git/GitLocator.kt`, `GitOperations.kt`, `GitRepositoryClient.kt`, `CliGitService.kt` | Index file, versions, staging a version |
| `data/workspace/WorkspaceConflictFiles.kt` | The conflict owner's file access, out of the gateway (which may not grow) |
| `data/settings/AndroidWorkspacePreferencesRepository.kt` | The four settings |
| `ui/editor/IcodeCodeEditor.kt` | Sora's `CodeEditor` with iCode's renderer (markers), Backspace and line breaks; `FileLineBreaksLanguage`, the Enter handlers' line breaks |
| `ui/editor/CodeEditorSession.kt` | The splice, the separator, the settings, the marker tap, Revert, the keys |
| `ui/editor/CodeEditorSessions.kt` | The sessions and `EditorCanvas`, out of the session's file (the 300-line budget) |
| `ui/GitDiffLineRow.kt` | One diff line, for the diff screen and the change sheet alike |
| `ui/screens/workspace/EditorChangeSheet.kt`, `EditorKeyRow.kt`, `WorkspaceEditorScreen.kt` | The sheet, the row, the count; Find shows the markers too |
| `ui/screens/settings/EditorSettingsScreen.kt` | The settings |

## 7. Not done, and why

| | Why |
|---|---|
| Deleting an auto-closed pair with one Backspace (VS Code's `editor.autoClosingDelete`) | Sora does not track which closing characters it inserted; deleting every adjacent pair would differ from VS Code |
| Markers for staged changes (VS Code's secondary quick diff) | Source Control shows them; the gutter shows what is not yet staged, as VS Code's primary quick diff does |
| Markers in the scrollbar | The bottom bar's count and Prev / Next find the changes on a phone |

## 8. As built

### 8.1 What changed beyond the plan

- `GitIndexOperations` is the change markers' own interface, which `GitWorkspaceOperations` extends: their test fake implements four methods, and the gateway passes nothing new — `GitWorkspaceCoordinator` owns `GitLineChanges`.
- The gateway shrank from 352 lines to 348 (the ratchet now says 348): the conflict owner's file access left it for `WorkspaceConflictFiles`.
- `LineChanges.between` keeps its rule that line endings do not count (a model's `\n` against a `\r\n` file), by comparing both texts with LF breaks; the markers compare them as they are, as Git does.

### 8.2 How it was checked

| Check | What it showed |
|---|---|
| `LineDiffTest` | Git's `-U0` numbering for each case; 2 000 random pairs: every edit is a shortest one (against the textbook LCS table) and turns the base into the text; revert and stage, one change at a time; a stale change refused; the checkpoint stops the search |
| 400 random file pairs, against `git diff --no-index --minimal --numstat` (git 2.43) | The same counts for every pair, files without a final line break among them |
| `EditorTextRulesTest` | Line breaks; 20 000 random splices that rebuild the text and never split or make a CRLF at their edges; Backspace's tab stops |
| `GitIndexVersionsTest` | The listing git 2.43 printed for a repository mid-merge read as the rules say; both scripts handing an odd path (`'`, `$`, `"`, a space, a backtick) to Git as one argument, through a real `sh` |
| The scripts on a real repository (git 2.43) | Versions as a checkout writes them (CRLF for `eol=crlf`); none for a conflict, an untracked file or a link; `--literal-pathspecs` matching `we ird*.txt` alone; staging cleaned CRLF to LF and kept a mode of `100755` |
| `GitLineChangesTest` | Read once per file and again only when the index changed; changes following edits; none for an untracked file or one outside a repository; the setting; staging the base with one change, its BOM and CRLF kept; a stale change refused |
| Type-checks | The editor (`ui/editor`) against Sora 0.24.6's and its TextMate module's own classes; the editor, Find, change sheet, keys row, diff and Editor Settings screens with them; every other screen check; API 30 |

### 8.3 On the phone, open

On a clean install, in a repository:
- edit a tracked file: green, blue and red markers; the bottom bar's count; a tap opens the sheet; Revert, then Undo; Stage, and Source Control shows the change staged;
- the keys row while typing: Tab, the arrows, Home and End (twice each), Undo, Redo and a symbol; Auto-Close Brackets on and off;
- Backspace on an indented empty line, and in four spaces of indentation;
- Enter in a CRLF file, then `git diff` in the terminal shows no `^M` change;
- an assistant edit to the open file: the caret stays, and Undo takes the edit back;
- Keyboard Suggestions off: no autocorrect in the editor.

