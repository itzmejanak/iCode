# The tool dock: one tap between tools, each keeping its place — plan

Status: **plan, for the owner's approval** (2026-11-10). Nothing is built. The owner's choices in
§8 come first.

Owner's request: an intelligent, docked navigation manager, the best for iCode's UI and UX and for
what a user needs. Today the user has to go Back to get anywhere.

Owner's decisions (2026-11-10):
- an expandable floating pill docked on the edge, on the same edge as the bell and the undo pill;
- six destinations: Explorer, Editor, Search, Source Control, Assistant and Terminal;
- the dock shows only on those six tools' main screens;
- each tool keeps its own navigation history;
- Back first moves within the current tool, then returns to the tool used before, then the
  Launcher, then exits;
- all four intelligent features: each tool restored as left; live badges; context suggestions; a
  quick switch to the last tool;
- phone first; tablet and landscape later.

The owner's three hardest journeys are the first acceptance criteria (§5.1):
1. Editor → Assistant → Back returns to the same file, caret and scroll position.
2. Editor → Terminal → Editor → Terminal keeps both, sessions and state.
3. Assistant → open or edit a file → back to the Assistant with its conversation and context.

The owner's other requirements are criteria too:
- switching never recreates screens needlessly or loses their state;
- the dock is smooth, unobtrusive and easy to use with one hand;
- no duplicate screens and no loops;
- existing UI and functions are kept;
- Back is always predictable.

Follows [layout.md](./layout.md):
- §7 navigation layers and Back order;
- §8.2, where compact screens may use "a narrow dock";
- §21 sheets;
- the floating stack and docked pills (`IcodeDockedPill`, the owner's choices of 2026-11-04).

## 1. Measured (main d992742)

### 1.1 One back stack for everything

- **The history.** `NavigationState` (`ui/NavigationState.kt`) holds one list, `historyNames`.
  `IcodeApp` has one `BackHandler`, which removes the top entry. Sheets, selections and three
  settings pages with unsaved fields have their own `BackHandler`s first.
- **Actions** (`navigation/NavigationActions.kt`). Every screen receives these. Their call sites:
  - `navigate` (74) pushes;
  - `back` (73) pops;
  - `replace` (11) swaps the top;
  - `openPanel` (8) shows Terminal's Terminal, Run, Problems or Output tab;
  - `returnTo` (1) goes back if the route is just below, else swaps.
- **Kept state.** `NavigationHost` keeps each entry's saved state under the key
  `"<place in the list>:<route>"`. That state is forgotten the moment the entry leaves the list.
- **Switching and going back are the same act.** To reach another tool, a screen is pushed on top.
  Getting to a third tool usually means Back first.

### 1.2 How the tools are reached today

| From | To | How |
|---|---|---|
| Explorer | Terminal, Search, Source Control, Assistant, Workspace settings | Its rail (`ActivityRail`), the only one: no other screen has it |
| Explorer | Editor | Opening a file; the Editor has no rail entry |
| Editor | Terminal, Problems | Its bottom bar (`BottomPanelBar`), 1 tap |
| Editor | Assistant, Source Control, Search | ⋮ › Command Palette › a command, or Back to the Explorer and its rail |
| Terminal | Editor | Its bar's "Editor" (`returnTo`), 1 tap |
| Assistant | Explorer | "Workspace" pushes **a second Explorer** (`AssistantScreen.kt:170`) |
| Empty editor | Launcher | Its ← pushes **a Launcher on top of the workspace** (`EditorEmptyScreen.kt:57`) |
| Settings without a workspace | Launcher | "Open a Workspace" pushes **a second Launcher** (`SettingsRootScreen.kt:139`) |

The three bold rows are today's loops. After Explorer → Assistant → Workspace, Back goes
Explorer → Assistant → Explorer.

### 1.3 What survives leaving a tool today

| State | Where it lives | Survives leaving and coming back? |
|---|---|---|
| Open files, text, dirty marks | Data layer (`ICodeSnapshot.openDocuments`) | Yes |
| A file's caret, selection, scroll, folds, undo | Its Sora view, kept per file (`CodeEditorSessions`: "Navigation swaps Compose hosts but retains this editor") | Yes |
| Terminal sessions, their processes, scrollback, typed line | `TerminalSessionManager`, a service | Yes |
| Terminal tab (Terminal, Run, Problems, Output) | `NavigationState.panelTab` | Yes |
| **Terminal's scrolled-back position** | The `TerminalView`, made anew each time the screen is shown (`TerminalHost.kt`, `factory`) | **No**: it comes back at the latest output |
| Assistant conversation, steps, the running turn | Data layer | Yes |
| Assistant draft and attached files | `rememberSaveable` in its entry | Yes, while its entry is in the history |
| **Assistant chat scroll position** | `remember { LazyListState(last) }` (`AssistantChatView.kt:77`), not saved | **No**: it comes back at the end |
| Explorer selection and tree scroll, Search query and options, commit message | `rememberSaveable` and lazy-list state in their entry | Yes, while the entry is in the history; lost if it is replaced or popped |

The editor's caret and scroll already survive. Journey 1's real problem is the route there and
back: there is no one-tap way to the Assistant. Journey 2 loses the terminal's scroll position.
Journey 3 loses the chat's scroll position.

### 1.4 What the dock can be built from

- **The floating stack** (`IcodeFloatingStack`) sits at the bottom end, above the highest bottom
  bar (`IcodeFloors`) and at least 88 dp up. It holds the undo pill over the bell, steps aside
  while a sheet is open, and moves above the keyboard.
- **`IcodeDockedPill`** is flush on the edge with a grip. It tucks into the edge and comes out on a
  tap or a pull, with the swipe, spring and TalkBack actions already done. The dock is one more
  pill of this kind.
- **The rail's entry** (`ActivityRailEntry`): badge, accent while working, and busy spinner.
- **What badges need:**
  - Changed files: `gitChanges`.
  - Unsaved files: `openDocuments.dirty`.
  - Problems: `problems`.
  - Assistant working: `assistant.running`.
  - Assistant waiting for an answer or an approval: `assistant.questions`, `assistant.approval`.
  - A terminal command running: `TerminalCommands.running`, per session, from the shell's OSC 133
    marks. This is known but **not yet published** to the UI. Its end is published
    (`commandsFinished`).

### 1.5 How others do it

- **Android's Navigation library** keeps one back stack per bottom-navigation item, saved and
  restored as the user switches (`saveState` / `restoreState`). Each tab's screen leaves
  composition and comes back from its saved state.
- **iOS tab bars** keep each tab's stack as well.
- **VS Code's activity bar** keeps each view's state while another shows.
- **The owner's rule differs from Material's** in one place. Back at a tool's start goes to **the
  tool used before**, not to a fixed start tool. This is the order Android's Recents gives apps.

## 2. The model: one history per tool

```text
App layer:   Launcher › Workspaces › …                     (onboarding, launcher, settings from it)
Workspace:   Explorer [Explorer]
             Editor   [Editor, Find in file]
             Search   [Search]
             Source   [Source Control, Diff]
             Assistant[Assistant]
             Terminal [Terminal(tab)]
Tool order:  Explorer → Source → Editor → Assistant        (most recent last; each tool once)
On screen:   the top of the most recent tool's history: Assistant
```

## 3. Decisions

**D1 — The six tools, and what belongs to each.**

| Tool | Its main screen (the dock shows here) | Pages that open inside it (dock hidden) |
|---|---|---|
| Explorer | `WorkspaceExplorer` | Workspace settings, settings pages, Runtime Tools, Notifications |
| Editor | `WorkspaceEditor` (the empty editor when no file is open) | Find in file, Command Palette, External file change, Document recovery |
| Search | `WorkspaceSearch`, `QuickOpen` (its two modes) | — |
| Source Control | `SourceControl` | Diff, Commit and push, Pull progress, Git credentials, Conflicts; Branch, Repository and History sheets |
| Assistant | `Assistant` | Diff, assistant settings, providers |
| Terminal | `TerminalExpanded` with its Terminal, Run, Problems and Output tabs | Runtime installing |

Rules:
- A page opens in **the tool it was opened from**. A diff from Source Control is Source Control's,
  and a diff from the Assistant is the Assistant's. The table names where each is usually opened
  from.
- The app layer keeps onboarding, the Launcher, Workspaces, Clone and Settings when they are opened
  from the Launcher.

**D2 — Kept as a model, not as a screen's code.**
- `navigation/NavigationModel.kt` is plain Kotlin with no Compose. It holds the app stack, the six
  tools' histories and the tool order, and makes every decision of D3 and D4. It is tested on the
  JVM with no device (§5.3).
- `NavigationState` becomes a thin Compose wrapper around it, with its saver.
- **All 167 existing call sites stay as they are.** The model tells a switch from a push by the
  route named (D3). So every existing button, rail entry, bottom bar, palette command and effect
  keeps working, now without stacking.

**D3 — What each action does.**

| Action | Rule |
|---|---|
| `navigate(a tool's main screen)` | **Switch** to that tool: its history as it was left, or its main screen the first time. Nothing is pushed. |
| `navigate(any other page)` | **Push** it on the current tool's history, or on the app stack outside a workspace. A page already in that history is gone back to instead: screens take no arguments, so the same route is the same screen and is never stacked twice. |
| `replace(page)` | The top of the current history is swapped. `replace(a tool's main screen)` closes the top page, then switches. This is the Command Palette's way. |
| `returnTo(main screen)`, `openPanel(tab)` | Switch to that tool, and for `openPanel` select the tab. |
| Something opened for the user (a file, a new terminal, a Git repository just made) | Switch to its tool **and show its main screen**: the file is what was asked for, so a Find-in-file page left open over it closes. A dock tap, by contrast, shows a tool exactly as it was left. |
| A workspace opens | Its six tools start empty, at the Explorer, over the app stack. |
| The open workspace is opened again (from the Launcher) | **Back where you were**: its tools as they were left. |
| Another workspace opens | Its tools start anew: files and branches differ. |
| `navigate(Launcher)` from inside a workspace | Go to **the** Launcher: the one in the app stack, never a second. The workspace's tools are kept for its return. |

**D4 — Back, always in this order (the owner's).**
1. Something open over the screen closes first: a menu, a sheet, a selection, or the expanded dock.
   These are the existing handlers, unchanged, plus the dock's.
2. A page inside the current tool: the one below it in that tool.
3. At a tool's main screen: **the tool used before it**, as it was left. The tool just left keeps
   its history for the dock, but **leaves the Back order**. Back therefore never comes back to a
   tool it has left, so it cannot loop.
4. No tool before it: the app stack's top, which is the Launcher or Workspaces. If the app stack is
   empty, for example after arriving from a notification, the Launcher.
5. At the Launcher with nothing below: Android's own Back, which leaves the app.

Back never closes or discards a file, as layout.md §7.2 requires. Unsaved files stay open and
dirty. The pages that ask before leaving unsaved fields (provider, speech provider, MCP server)
still ask.

Worked example:

| Steps | Back, Back, Back… shows |
|---|---|
| Explorer › open `a.kt` › dock: Assistant › dock: Terminal | Assistant › Editor `a.kt` › Explorer › Launcher › exit |
| Editor › dock: Terminal › dock: Editor › dock: Terminal | Editor › Explorer › Launcher › exit |
| Source Control › Diff › dock: Assistant | Source Control **Diff** › Source Control › Explorer › Launcher |

In the second row the order holds each tool once, so Editor, Terminal, Editor, Terminal leaves only
Terminal over Editor.

**D5 — Nothing a tool holds is lost by switching.**
- **Stable keys.** Each page's saved state is kept under its tool and its place in that tool's
  history, not its place in one list: for example `Assistant/0`, `Source/1:DiffView`. It lives as
  long as the page is in its tool's history, while other tools show, and goes when that page is
  closed.
- **Terminal.** Each session's scrolled-back position is kept when its view goes and set again when
  it returns. The output is still there, so the same lines show. A text selection is a gesture in
  progress and is not kept, as Android drops one on leaving.
- **Assistant.** The chat's position is kept per conversation. A conversation opened for the first
  time still starts at its end, as today. The draft and attached files are kept as today, now
  under a stable key.
- **Editor.** It needs nothing more: its views are kept per file today (§1.3).
- **How a tool comes back.** A tool not on screen leaves composition, and returns drawn from what
  it keeps: data, kept views, saved state. This is Android's own way with several back stacks. The
  editor's and terminal's native views and processes are not remade.
  - **Gate:** returning to a tool must show its kept content in the first frame, with no blank
    screen, no jump in scroll and no flash of the wrong file.
  - If the owner's phone shows otherwise for a tool, that tool is kept composed but undrawn while
    another shows (§8, question 1).

**D6 — The dock.**
- **Where.** In the floating stack, on the bell's edge, at the **bottom** of the stack, nearest the
  thumb. The undo pill and the bell stand above it, one step higher than today (40 dp and an 8 dp
  gap). The dock is always there, so its place never moves, while the undo pill comes and goes.
  (§8, question 2.)
- **Tucked**, as it is by default and again after each switch: only its grip shows in the edge, as
  the bell's does when tucked. The grip takes the tone of what needs you most (D8).
- **Out**, on a tap on the grip or a pull from the edge: one row of the six tools' icons slides out
  from the edge. Each icon is a 48 dp touch target, in a fixed order, with the nearest to the thumb
  first (§8, question 3).
  - The tool on screen has the rail's accent line.
  - A tap switches and tucks the dock.
  - Back, a tap outside, or a swipe back into the edge tucks it.
- **Hidden** on every page that is not a tool's main screen (D1), while a sheet is open, on the
  Notifications page and outside a workspace.
- **With the keyboard up** it stays tucked unless pulled out, and moves above the keyboard as the
  bell does. It never covers the editor's key row or the terminal's extra keys, because those are
  floors of the stack.
- **Motion and access.**
  - It slides in about 200 ms and follows Android's animation scale; with animations off it simply
    appears.
  - TalkBack reads "Tools" with a custom action per tool, its badge said ("Source Control, 3
    changes"), and the quick switch as an action.
- **Kept as they are.** The Explorer's rail, the Editor's Terminal and Problems bar, the Terminal's
  Editor button and the Command Palette all stay. Their taps now switch tools instead of stacking.

**D7 — Badges** (feature b). Each badge is computed from the snapshot by a plain rule in `domain`
and tested.

| Tool | Count | State |
|---|---|---|
| Editor | Unsaved files | — |
| Source Control | Changed files, counted as the rail counts them | — |
| Terminal | Errors in Problems | A dot while a command runs, in any terminal, from the shell's marks (needs publishing, §1.4) |
| Assistant | — | Accent while it works, as on the rail; amber while it waits for your answer or approval |
| Explorer, Search | — | — |

**D8 — Suggestions** (feature c). These follow fixed rules, never guesses. One suggestion is shown
at a time: the newest. It is shown as the grip's tone and a mark on that tool's icon, said by
TalkBack. **It never switches by itself.**

| When this happens while another tool shows | Suggested | Until |
|---|---|---|
| The assistant asks a question or an approval | Assistant | It is answered |
| The assistant's turn ended having changed files (in a Git repository) | Source Control | Source Control is opened |
| A run or build ended with errors in Problems | Terminal, on its Problems tab | It is opened |
| A terminal command that ran 30 s or more ended (the existing alert rule) | Terminal, on that session | It is opened |
| A pull or merge left conflicts | Source Control | They are resolved, or it is opened |

**D9 — Quick switch** (feature d).
- A swipe up or down along the tucked grip switches straight to the tool used before. This is the
  same gesture Android uses to switch to the last app.
- In the opened row, that tool carries a small "last" mark.
- TalkBack offers "Switch to Editor", naming the tool used before.

**D10 — Restored where it was left** (feature a). This is D5. It also holds across a process
restart: the model's saver keeps all six histories, the tool order and the app stack.

## 4. Where the code goes

| Layer | File | What |
|---|---|---|
| navigation | new `Tool.kt` | The six tools, and each route's tool or app layer (D1) |
| navigation | new `NavigationModel.kt` | D3 and D4, pure, with its saved form |
| navigation | `NavigationActions.kt` | Unchanged for screens; adds `switchTo(tool)` and `previousTool` for the dock |
| ui | `NavigationState.kt`, `NavigationHost.kt` | Wrap the model; the stable keys of D5 |
| ui | `IcodeApp.kt` | The root `BackHandler` asks the model; the effects as D3; the dock in the floating stack |
| domain | new `DockRules.kt` | D7 and D8 from the snapshot, pure |
| data | `TerminalSessionManager` | Publishes whether a command runs, per session (§1.4) |
| designsystem | `IcodeDockedPill.kt` | The quick-switch swipe along the edge, as an option |
| ui | new `ui/dock/ToolDock.kt` | The dock (D6, D9) |
| ui/terminal | `TerminalHost.kt` | The scroll position kept per session (D5) |
| ui/screens/tools | `AssistantChatView.kt` | The chat position kept per conversation (D5) |

Every file stays at or under 300 lines. `IcodeApp.kt` is at 293 now, so the dock's wiring goes in
its own file.

## 5. Acceptance criteria

Each criterion is checked by a JVM test (**[T]**) or on the owner's phone (**[P]**).

### 5.1 The owner's journeys

**J1. Editor → Assistant → Back.** Open `a.kt`, scroll to line 300, put the caret at line 312 col 8,
select a word, type without saving. Dock › Assistant, then Back.
- [T] The model shows Editor with `a.kt` after one Back.
- [P] `a.kt` is the selected tab. Line 312 col 8 has the caret and the selection is the same. The
  first visible line is the same. Undo still undoes the typing, and the tab is still dirty. Taps:
  2 (grip, Assistant) to go and 1 Back, against 3 or more today.

**J2. Editor → Terminal → Editor → Terminal.** In Terminal tab 2, run `top`, then scroll back 50
lines in tab 1. Go Editor, then Terminal, then Editor, then Terminal, by the dock or the bars.
- [T] The history never grows: two Backs reach the Explorer.
- [P] Both sessions still run, and `top` is still updating in tab 2. Tab 1 is still 50 lines back.
  The same terminal tab is selected. Each Editor visit is as J1 leaves it. No screen ever shows
  twice.

**J3. Assistant → file → Assistant.** Write a draft with one attached file, scroll the chat up to
an earlier answer, then tap "Open in Editor" on a step. Edit the file, then Back, or Dock ›
Assistant.
- [T] Opening the file switches to Editor. Back shows the Assistant.
- [P] The conversation is the same, and so are the draft, the attachment, the scroll position and a
  turn still running. The edited file stays open and dirty in Editor.

### 5.2 Back is predictable

- **B1** [T] Back follows D4's five steps, in order, in every state.
- **B2** [T] From any state reachable by any sequence of actions, repeated Back reaches Android's
  exit. It takes at most the number of pages in all histories plus the six tools plus the app stack
  (no loop), and never shows the same screen twice in a row.
- **B3** [T] Back from a tool goes only to a tool still in the Back order: never one Back already
  left.
- **B4** [T] The worked examples of D4 give exactly the screens listed.
- **B5** [P] Sheets, menus, the Explorer's selection, the Files pages' own Back and the "discard
  changes?" pages behave exactly as today.
- **B6** [T] Back never closes or discards an open file.

### 5.3 No duplicates and no loops

- **N1** [T] No history holds the same route twice, and no tool's main screen is ever a pushed page.
- **N2** [T] The tool order holds each tool at most once.
- **N3** [T] Today's three loops (§1.2) are gone: Assistant › Workspace switches to the Explorer;
  the empty editor's ← and Settings' "Open a Workspace" go to the one Launcher.
- **N4** [T] 10 000 random sequences of 60 actions each keep N1, N2 and B2. The random actions are
  every action of D3, the effects, Back and the dock.

### 5.4 Nothing lost by switching

- **S1** [T] A page's saved state survives any switches away and back while it is in its tool's
  history, and goes when it is closed.
- **S2** [P] Each tool comes back as left:
  - Explorer: expanded folders, selection, scroll.
  - Search: query, options, results, scroll.
  - Source Control: the commit message being written, the open sections, scroll.
  - Terminal: tab, session, scroll position.
  - Assistant: as in J3.
  - Editor: as in J1.
- **S3** [P] Returning to a tool shows its kept content in the first frame: no blank, no scroll
  jump, no flash of another file (the D5 gate).
- **S4** [T] After a process restart, the six histories, the tool order and the app stack are as
  they were.

### 5.5 The dock

- **K1** [P] It shows on the six main screens only. It is hidden on every other page, under
  sheets, on Notifications and outside a workspace.
- **K2** [P] One-handed: every icon can be reached with the right thumb on a 6.1–6.7" phone held in
  one hand. A switch takes 2 taps, or 1 swipe for the last tool.
- **K3** [P] Tucked by default, and only its grip shows. It never covers a bottom bar, the key
  rows or the keyboard. It moves with the keyboard as the bell does.
- **K4** [P] It slides smoothly, with no dropped frames on the owner's phone, and follows
  Android's animation scale.
- **K5** [P] TalkBack reads each tool, its badge, the suggestion and the quick switch.
- **K6** [P] The bell and the undo pill work as before. They stand one step higher, and still
  step aside under sheets.

### 5.6 The four features

- **F1** [T] Restored as left: S1 and S4.
- **F2** [T] Each badge of D7 is right for its snapshot: counts, the working and waiting states,
  and a command running.
- **F3** [T] Each suggestion of D8 starts when its event happens out of sight, ends as its row
  says, and never switches by itself. The newest wins.
- **F4** [T] The quick switch goes to the tool used before, from every tool.

### 5.7 Existing UI kept

- **E1** [P] The rail, bottom bars, Terminal's Editor button, the Command Palette, notifications'
  arrivals and file links all reach the same places as before, with no stacking.
- **E2** [T] The 167 call sites compile unchanged.
- **E3** The existing unit tests pass, and so do both consistency guards and both builds.

## 6. Cycles

| Cycle | What | Exit gate |
|---|---|---|
| **N1** — the model | `Tool`, `NavigationModel`, the wrapper, the root Back, the effects; no dock yet | §5.2, §5.3, S1, S4 and E2 tested. The app works as today, but every existing way to another tool switches without stacking, and the three loops are gone. |
| **N2** — nothing lost | Stable keys, the terminal's scroll position, the chat's position | S1 tested; J1–J3 checked as far as the JVM allows |
| **N3** — the dock | `ToolDock` in the floating stack: tucked, out, switch, hidden rules, keyboard, TalkBack, the quick switch | Guards and builds; K1–K6 and J1–J3 on the phone |
| **N4** — badges and suggestions | `DockRules`; the terminal's running state published | F2 and F3 tested; on the phone |
| **N5** — docs as built | This plan, layout.md §7 and §8.2, CHANGELOG | — |

Each cycle is its own commit, with the full gate run before it.

## 7. Not in this plan

- The rail for wide screens and landscape (layout.md §8.2): a later cycle, as the owner asked.
- A dock on the other edge for the left hand.
- Removing the Explorer's rail.
- Jumping between open files from the dock.

## 8. Questions for the owner

1. **A tool coming back.** Should it be drawn again from what it keeps, as Android's own several
   back stacks do (recommended, D5)? Or should the tool used before also stay composed but undrawn,
   so going back to it costs nothing? That uses memory and battery for a screen not seen, and an
   editor or terminal kept that way could keep the keyboard. The recommended way has a gate (S3):
   any tool that fails it on your phone moves to the second way.
2. **The dock's place in the stack.** At the bottom, nearest the thumb and never moving, with the
   undo pill and the bell one step higher (recommended)? Or above them, where it moves as the undo
   pill comes and goes?
3. **The order of the six icons, from the edge outward.** Recommended: Editor, Terminal, Assistant,
   Source Control, Search, Explorer, so your three journeys' tools are nearest the thumb. Or name
   your own order.
4. **A file opened for you shows the Editor's main screen**, closing a Find-in-file page left open
   over it (recommended, D3). Or should the Editor come back as left, Find page included?
