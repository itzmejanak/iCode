# The tool dock: one tap between tools, each keeping its place — plan

Status: **built** (N1–N5), waiting for the owner's phone checks ([P] in §5). Approved 2026-11-10: the
owner chose every recommendation in §8 and asked for the whole plan, end to end: the architecture
migrated cleanly rather than wrapped, no duplication, and faults found on the way fixed. What was
built, and where it differs from §3–§4, is the record in §9.

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
  bar (`IcodeFloors`) and at least 88 dp up. It holds the undo pill over the bell, is hidden
  while a sheet is open (its pills kept as they were), and moves above the keyboard.
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
- `navigation/Navigation.kt` is plain Kotlin with no Compose. It holds the app stack, the six
  tools' histories and the tool order, and makes every decision of D3 and D4. It is tested on the
  JVM with no device (§5.3). Its saved form is `navigation/NavigationSaving.kt`.
- `NavigationState` holds it for Compose, with its saver, and decides nothing. The old one-list
  history (`historyNames`) is gone, not wrapped.
- **The existing call sites stay as they are**, but two. The model tells a switch from a push by the
  route named (D3), so every existing button, rail entry, bottom bar, palette command and effect
  keeps working, now without stacking. `returnTo` is gone: its one call, Terminal's Editor, is a
  `navigate`. The empty editor's ← is Back, as every other screen's is.

**D3 — What each action does.**

| Action | Rule |
|---|---|
| `navigate(a tool's main screen)` | **Switch** to that tool: its history as it was left, or its main screen the first time. Nothing is pushed. |
| `navigate(any other page)` | **Push** it on the current tool's history, or on the app stack outside a workspace. A page already in that history is gone back to instead: screens take no arguments, so the same route is the same screen and is never stacked twice. |
| `replace(page)` | The top page of the current history closes, and the route is gone to as `navigate` goes. A main screen is never replaced, only left: `replace(a tool's main screen)` closes the top page, then switches. This is the Command Palette's way. |
| `openPanel(tab)` | Switch to Terminal and select the tab. |
| A tool's other face | Search has two main screens, finding text (`WorkspaceSearch`) and finding files (`QuickOpen`). Asking for the face it does not show starts it on that face. The Editor's are `WorkspaceEditor` and, for the design's captures, `EditorEmpty`. Terminal's tab frames (`ProblemsPanel`, `OutputPanel`) are its one screen on that tab. A dock tap keeps the face as left. |
| Something opened for the user (a file, a new terminal, a Git repository just made) | Switch to its tool **and show its main screen**: the file is what was asked for, so a Find-in-file page left open over it closes. A dock tap, by contrast, shows a tool exactly as it was left. |
| A workspace opens | Its six tools start empty, at the Explorer, over the app stack. The page that made it (Clone progress) gives way, so Back does not show it again. |
| The open workspace is opened again (from the Launcher) | **Back where you were**: its tools as they were left. |
| Another workspace opens | Its tools start anew: files and branches differ. This holds however it became the open one, a file opened in it included: the tool on screen stays on screen, on its main screen. |
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
  thumb. The undo pill and the bell stand above it, one step higher than before (a pill's height
  and an 8 dp gap). The dock is always there, so its place never moves, while the undo pill comes and goes.
  (§8, question 2.) The user can move each pill elsewhere (D11).
- **Closed**, as it is by default and again after each switch: its own icon, the tools' grid, as a
  see-through button (D12, D13), in the tone of what needs you most (D8).
- **Open**, on a tap: one row of the six tools' icons grows from its icon, which stays where it was,
  now at the row's docked end. Each icon is 44 dp wide and the pill's 48 dp tall, in a fixed order,
  with the nearest to the thumb first (§8, question 3). A count or a mark on an icon is drawn whole,
  never cut by the pill's round.
  - The tool on screen has the rail's accent line, and its icon the accent.
  - A tap switches and closes the dock. A tap on the suggested tool goes where the suggestion is
    (D8): Terminal on its Problems tab, or on the session whose command ended.
  - Its icon, Back, a touch outside, or 6 seconds untouched close it. A touch outside does only
    that, as one outside a menu does.
- **Hidden** on every page that is not a tool's main screen (D1), while a sheet is open, on the
  Notifications page and outside a workspace. Inside a workspace its place stays kept while it is
  hidden, so the bell and the undo pill never move as pages open and close. Under a sheet it stays
  as it was, open or closed, its 6 seconds waiting, and Back is the sheet's.
- **With the keyboard up** it stays closed unless tapped, and moves above the keyboard as the bell
  does. Where it starts, it never covers the editor's key row or the terminal's extra keys, because
  those are floors of the stack; moved by the user, it stands where they put it (D11).
- **Motion and access.**
  - It slides in about 200 ms and follows Android's animation scale; with animations off it simply
    appears.
  - TalkBack reads "Tools" with a custom action per tool, its badge said ("Source Control, 3
    changes"), and the quick switch as an action.
- **Kept as they are.** The Explorer's rail, the Editor's Terminal and Problems bar, the Terminal's
  Editor button and the Command Palette all stay. Their taps now switch tools instead of stacking.

**D7 — Badges** (feature b). Each badge is computed from the snapshot by a plain rule in `domain`
(`ToolBadges`) and tested. The Explorer's rail draws the same badges from the same rule, so a tool
looks the same wherever it is reached.

| Tool | Count | State |
|---|---|---|
| Editor | Unsaved files | — |
| Source Control | Changed files, counted as the rail counts them | — |
| Terminal | Errors in Problems | A dot while a command runs, in any terminal, from the shell's marks (needs publishing, §1.4) |
| Assistant | — | Accent while it works, as on the rail; amber while it waits for your answer or approval |
| Explorer, Search | — | — |

**D8 — Suggestions** (feature c). These follow fixed rules, never guesses (`ToolSuggestions`). One
suggestion is shown at a time: the newest, and of two at once the first in this table's order. It
is shown as the closed dock's icon in its tone and that tool's icon in the same tone, said by TalkBack. **It never
switches by itself.** What was already so when iCode started, or when another workspace opened, is
no event.

| When this happens while another tool shows | Suggested | Until |
|---|---|---|
| The assistant asks a question or an approval | Assistant | It is answered, or it is opened |
| A pull or merge left conflicts | Source Control | They are resolved, or it is opened |
| A run or build ended with more errors in Problems than before (a build's; a language server's, found as you type, do not count) | Terminal, on its Problems tab | They are gone, or it is opened |
| A terminal command that ran 30 s or more ended (the finish alert's rule, `FinishedCommand.long`) | Terminal, on that session | The session ends, or it is opened |
| The assistant's turn ended having changed files with its tools, in the open workspace's Git repository | Source Control | It is opened |

**D9 — Quick switch** (feature d).
- A double tap on the closed dock switches straight to the tool used before, as a double tap on
  antigem's floating switcher goes back (D13). The first tap opens it at once, never waiting to see
  whether a second comes; the second, on its icon where the first was, switches and closes it.
- In the opened row, that tool carries a small "last" mark: a quieter line under its icon than the
  accent line of the tool on screen.
- TalkBack offers "Switch to Editor", naming the tool used before.

**D10 — Restored where it was left** (feature a). This is D5. It also holds across a process
restart: the model's saver keeps all six histories, the tool order and the app stack.

**D11 — The pills where the user wants them** (owner's requests after the first phone check,
2026-11-10).
- **By default** the pills stand where they always have: the undo pill, the bell, the dock, at the
  end edge's foot, clear of the bars.
- **Dragged** (a finger moving on any part of a pill, past the touch slop, D13), a pill is picked
  up at once: the phone ticks, the pill lifts off its edge and floats as its own icon in a raised
  bubble under the finger, anywhere on the screen.
- **Let go**, it docks on the edge nearest the finger — left, right, top or bottom — its middle
  where the finger was along that edge, and glides there. It stays there across restarts,
  app-wide (`AppearanceSettings.pillPlaces`).
- **Anywhere** means the whole screen below the status bar and above the navigation bar: the corners
  and the bars included, which the user chose to cover. Its place is a share of its edge, so a
  keyboard coming up moves a side's pill down with the room. No pill stands over another
  (`PillPlaces.spots`): on one edge they make way for each other, and in a corner a side's pill
  makes way for one across the top or the bottom. Every move is animated.
- **Off the edge itself**: a pill stands 4 dp in from it, and its place is kept from Android's back
  gesture (`systemGestureExclusion`), which would otherwise take a touch that starts at the edge.
- **On any edge** a pill opens the same way: its row grows from its icon away from a side, and
  toward the middle on the top or the bottom (`PillPlaces.iconAtEnd`). Each pill stands on a closed
  pill's 48 dp square, its footing, so a pill opening moves nothing. The dock's order runs from its
  icon outward.
- **TalkBack** offers on each pill "Move … to the … edge" for the other three, and "Move … up" and
  "down" on a side, or left and right on the top or the bottom.
- A tap is the pill's own: a drag only begins once the finger moves.

**D12 — Each pill known at a glance, and quiet when not used** (owner's requests, 2026-11-10).
- **Its own icon**: the undo arrow (one icon for undo and redo, D14), the bell, the dock's grid, in
  what needs the user there (the unread tone, the suggestion's), so the three are told apart
  without opening any.
- **See-through**: no slab of its own. Each icon sits on a faint round of the raised surface (60 %),
  so it reads over code while the screen shows through, inside a 48 dp touch target; a count on it
  is drawn whole (`IcodePillButton`).
- **Resting**, as the assistant's bubble over other apps does (`IcodeResting`, one rule for both):
  a pill not awake — the bell after its ring, the undo pill after its change's time, the closed dock
  with no suggestion — and left alone 3 seconds docks into its edge, out of the way of the work: it
  slowly (0.7 s) sinks half behind the edge and fades to 55 %. A touch or anything new brings it
  out at once (0.18 s). A tap on a resting pill is still a tap.

**D13 — Tap to open, drag to move, as antigem does** (owner's report and request, 2026-11-10: the
pills could not be dragged; study antigem).
- **What was wrong.** A pill moved only after a 500 ms still hold, which nothing showed, and a
  drag did something else: on a side, sideways tucked or pulled it, and up or down was the dock's
  quick switch (nothing on the bell or the undo pill). Four gestures shared one 30 dp strip flush
  on the edge, inside Android's back gesture, and a tucked pill was a grey slab pushed into the
  edge.
- **What antigem does** (its floating switcher, `FloatingSwitcherWidget.kt`, and its voice orb's
  snap, `JarvisOverlay.kt`): a finger moving past the touch slop drags it at once, from anywhere on
  it, its buttons' taps cancelled; a tap opens it at once; a double tap goes back; the open panel
  grows from the closed pill's corner, so the arrow stays under the finger; it is placed in the
  layout's placement step and saved once, on the drop; the orb jumps to its new edge and a spring
  from where it was hides the jump.
- **As built**, the same: one detector on each pill, the down taken in the initial pass and given
  to its buttons, the slop then the pill's (`FloatingPills`). Tap opens or does the pill's action;
  drag moves; double tap on the dock is the quick switch. No pull, no swipe in, no swipe along, no
  hold.
- **Where antigem stops, and iCode goes on**: it docks on no edge (the switcher) or only the sides
  (the orb), lets its widgets overlap, has no resting state, and draws its closed switcher solid.
  iCode docks on all four edges, never overlaps, rests, and draws its pills see-through.

**D14 — One icon for undo and redo; at rest, in the edge** (owner's requests after the second phone
check, 2026-11-10: the screenshot showed ↷ beside a greyed ↶ and three round buttons standing out
from the edge).
- **One icon, its state what a tap does**: ↶ while a change can be undone; ↷ once only a redo is
  left, so undoing the last change turns the icon into its redo, crossfaded. While both can be
  done it shows ↶, and held, it redoes (TalkBack: the redo as an action). Nothing is ever greyed:
  the icon is there only while it does something.
- **Docked into the edge at rest** (D12): left alone, a pill sinks half behind its edge, slowly,
  as it fades, so nothing floats in the way of focused work; it comes out at once on a touch or
  anything new.

## 4. Where the code goes

As built:

| Layer | File | What |
|---|---|---|
| domain | new `WorkspaceTool.kt` | The six tools and their names; in `domain`, so the badge and suggestion rules need nothing of navigation |
| navigation | `Route.kt` | Each route's tool (`Route.tool`), the Launcher that leaves the workspace, the page that makes one; each tool's `home` (D1) |
| navigation | new `Navigation.kt`, `NavigationSaving.kt` | D3 and D4, pure, and its saved form |
| navigation | `NavigationActions.kt` | Unchanged for screens, but `returnTo` gone |
| ui | `NavigationState.kt`, `NavigationHost.kt` | Hold the model; the dock's `switchTo`; the stable keys of D5, each forgotten when its page closes |
| ui | `IcodeApp.kt` | The root `BackHandler` asks the model; the effects as D3; the dock in the floating stack |
| domain | new `ToolBadges.kt`, `ToolSuggestions.kt` | D7 and D8 from the snapshot, pure |
| domain, data | `TerminalContract.kt`, `TerminalSessionManager.kt`, `TerminalCommands.kt` | Whether a command runs, per session, from its shell's marks, and how many long ones have ended (§1.4) |
| domain, data | `AssistantContract.kt`, `AssistantTurns.kt` | A turn's end, and whether its tools changed a file |
| designsystem | `IcodeDockedPill.kt`, `IcodeFloatingStack.kt`, `IcodeRail.kt`, `IcodeStatus.kt` | The see-through pill buttons and the dock that opens (D12, D13), TalkBack's actions, the touch outside, a docked action's dot and line, shared with the rail |
| designsystem | new `IcodeFloatingPills.kt` | Each pill on its footing on any edge, dragged, floated as a bubble and docked (D11, D13) |
| designsystem | `IcodeMetrics.kt` | `IcodeResting`: how the bubble and the pills rest (D12) |
| domain, data, ui | new `FloatingPills.kt`, `AppearanceStore.kt`, new `ui/FloatingControls.kt` | Where each pill is kept, how pills share an edge, and the stack's three pills (D11) |
| ui | new `ToolFaces.kt`, `ui/dock/ToolDock.kt` | Each tool's icon; the dock (D6, D9) |
| ui/terminal | new `TerminalScroll.kt`, `TerminalHost.kt` | The scroll position kept per session (D5) |
| ui/screens/tools | `AssistantChatView.kt` | The chat position kept per conversation (D5) |

Every file stays at or under 300 lines: `IcodeApp.kt` is at 294.

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
  the empty editor's ← is Back (built so: D2), and Settings' "Open a Workspace" goes to the one
  Launcher.
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
  one hand. A switch takes 2 taps, or a double tap for the last tool.
- **K3** [P] Closed by default, and only its icon shows. Where it starts, it never covers a bottom bar, the key
  rows or the keyboard. It moves with the keyboard as the bell does.
- **K4** [P] It slides smoothly, with no dropped frames on the owner's phone, and follows
  Android's animation scale.
- **K5** [P] TalkBack reads each tool, its badge, the suggestion and the quick switch.
- **K6** [P] The bell and the undo pill work as before. They stand one step higher, are still
  hidden under sheets, and come back as they were.

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
- **E2** [T] The existing call sites compile unchanged, but the two D2 names: `returnTo`'s one call
  and the empty editor's ←.
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

## 8. Owner's decisions (2026-11-10)

| Question | Decision |
|---|---|
| A tool coming back | Drawn again from what it keeps, as Android's several back stacks do; any tool failing S3 on the phone stays composed instead |
| The dock's place | At the bottom of the floating stack, nearest the thumb; the undo pill and the bell one step higher |
| The icons' order from the edge | Editor, Terminal, Assistant, Source Control, Search, Explorer |
| A file opened for you | Shows the Editor's main screen; a Find page left over it closes |

The owner's further constraints: migrate the navigation cleanly, with no wrapper over the old stack;
no duplication; no inconsistency; maintainable and scalable; fix the faults found on the way.

## 9. Record

- **N1 — the model** (bcce942).
  - `Navigation` holds the app's pages, the six histories, the tool order, the Terminal tab, the
    workspace they belong to and a generation that starts anew with each workspace.
    `NavigationState` holds it for Compose; `NavigationSaving` keeps all of it across a process
    restart, and a saved form it cannot read starts the app as it would have.
  - The root `BackHandler` asks the model (D4). The effects: a workspace opened, a terminal opened in
    one, a repository made, a file opened, a notification's arrival (`show`: the tool on its main
    screen, D3). A file opened in another workspace starts that workspace's tools anew
    (`workspaceIs`).
  - Tests: `NavigationTest` (the journeys' JVM halves, D4's worked examples, B1–B6, N1–N3, F4),
    `NavigationFuzzTest` (N4: 10 000 random sequences of 60 actions keep N1, N2 and B2),
    `NavigationSavingTest` (S4) and `RouteTest` (each tool opens on one of its own main screens, and
    only the Launcher leaves the workspace).
- **N2 — nothing lost** (ba9a9fa).
  - Each page's saved state is keyed `<generation>/<tool>/<place>:<route>`, the app's
    `app/<place>:<route>`, and forgotten when its page closes (S1).
  - Terminal: `TerminalScroll` keeps each session's place as rows from its oldest line, so new
    output does not move it, and sets it once the view has its size (Termux sets the top row to the
    end after the emulator is given). Tested in `TerminalScrollTest`.
  - Assistant: the chat's list position is saved with the page, with whether it follows the end
    and the last message it saw sent, so coming back neither jumps to the end nor scrolls again.
- **N4 — badges and suggestions** (f1e9d9e), built before N3, which draws them.
  - `ToolBadges` (D7) and `ToolSuggestions` (D8), pure, tested in `ToolBadgesTest` (F2) and
    `ToolSuggestionsTest` (F3).
  - Published for them: whether a command runs in a session (`TerminalSessionState.commandRunning`,
    read from its `TerminalCommands` at each publish, so it is kept in one place); how many long
    commands each session has ended (`longRunsEnded`, by the finish alert's rule); and each turn's
    end with whether its tools changed a file (`AssistantState.ended`, `AssistantTurn.changedFiles`).
    Tested in `TerminalCommandsTest`, `CommandRunsTest`, `AssistantTurnEndTest` and
    `AssistantCoordinatorTest`.
- **N3 — the dock** (9d14f58).
  - `ToolDock` in the floating stack, below the bell (D6), with its state in `ToolDockState`: the
    row out or tucked, and the suggestion, followed even while the dock is hidden.
  - `IcodeDockedPill` gained the swipe along the grip and more TalkBack actions;
    `IcodeDockedAction` a running dot and a line under the icon; `IcodeFloatingStack` the touch
    outside. The rail and the dock share the dot, the line and the count (`IconMarks`, `UnderLine`),
    each tool's icon (`ToolFaces`) and name (`WorkspaceTool.label`).
- **N5 — docs as built:** this record, the decisions above where the build differs, layout.md §7.2
  and §8.2, and the CHANGELOG.
- **Gate** at N3 and N4: 1596 unit tests, all passing but `TreeSizeTest`'s unreadable folder, which
  cannot be unreadable to root in this sandbox; debug and release builds; lint, with only
  `SpeechProviderParts.kt:80`'s error from before; both consistency guards.
- **Where the build differs from §3–§4:**
  - The files are named as §4 now lists them: `Navigation.kt` for `NavigationModel.kt`,
    `WorkspaceTool.kt` (in `domain`) for `Tool.kt`, `ToolBadges.kt` and `ToolSuggestions.kt` for
    `DockRules.kt`.
  - `returnTo` is gone and the empty editor's ← is Back (D2).
  - Search's and the Editor's two faces, and Terminal's tab frames (D3).
  - Inside a workspace the dock's place is kept while it is hidden (D6).
  - The rail draws the dock's badges (D7). The Assistant on it is amber while it waits for you, as
    on the dock.
  - A suggestion marks its tool by its icon's colour (D8); the "last" mark is a quieter line (D9).
- **Faults fixed on the way:**
  - Today's three loops (§1.2): the Assistant's Workspace switches to the Explorer; the empty
    editor's ← is Back; Settings' "Open a Workspace" reaches the one Launcher.
  - A terminal scrolled back, and a chat scrolled up, came back at their ends (§1.3).
  - The finish alert's 30 seconds were compared in two places; `FinishedCommand.long` is the one rule.
  - The rail said "Source control"; every other place says "Source Control".
- **N6 — the owner's first phone check** (2026-11-10, two screenshots):
  - The Source Control count on the dock, and the lines under its icons, were cut: each action was
    clipped to a circle, which took the badge's corner and all but the middle of the line. The
    press now shows in that circle, and what the icon carries is drawn over it, whole; the line
    stands clear of the pill's edge.
  - The pills are bigger and easier to open: 48 dp tall, their actions 44 dp wide with 24 dp icons,
    and a tucked pill shows a 24 dp strip with a longer grip.
  - D11, first form: each pill can be held and moved to either side, at any height, and stays
    there (widened in N7).
  - The editor's suggestion list drew its chosen row in the accent at 18 %, a dull red on Ember that
    took the accent letters' contrast and ran into the rows beside it. It is now a quiet lift of the
    text colour (10 %), inset from the list's edges and from its neighbours, as a menu's chosen row.
  - Tests: `PillPlacesTest` and a case in `AndroidWorkspacePreferencesRepositoryTest`.
- **N7 — anywhere, and known at a glance** (2026-11-10):
  - D11 as above: the pills dock on all four edges, the whole screen theirs, and float as a bubble
    while moved. The placement rules are one pure function, `PillPlaces.spots`, with `placeAt` for
    where a pill let go docks.
  - D12: each tucked pill shows its own icon, and rests faded when left alone. The bubble's resting
    time and fade, which were its own constants, are now `IcodeResting`, which both use.
  - The tucked strip is 30 dp, room for the icon.
  - Tests: `PillPlacesTest` (the defaults, every edge, no overlap on an edge or in a corner, where a
    pill let go docks, right to left, the stored form).
- **N8 — tap to open, drag to move** (2026-11-10): D13. The pill parts are now `IcodePillRow` and
  `IcodePillButton` (the bell, the undo pill, a closed dock) and `IcodeDockedPill` (the dock, which
  opens); the edge-flush slab, its handle, its tuck, pull and swipes are gone. The undo pill keeps
  ↶ where it is docked, so ↷ coming and going never moves it from under the thumb. Tests:
  `PillPlacesTest` (where a row opens toward).
- **N9 — one undo icon, resting in the edge** (2026-11-10): D14. `IcodePillButton` is now the whole
  closed pill — its resting sink and fade, the back-gesture exclusion, the long press, TalkBack's
  moves — used by the bell, the undo pill and the closed dock alike; `IcodePillRow` is gone, every
  closed pill being one icon. The assistant's bubble fades over the same times (`IcodeResting`).
- **To check on the phone:** §5's [P] criteria — J1–J3, B5, S2, S3, K1–K6 and E1 — and D11–D14:
  drag each pill at once, without a hold, to the top, the bottom and the left; tap the dock to open
  it and a tool to switch; double tap it for the last tool; undo a change and see ↶ turn ↷, and hold
  ↶ to redo with two changes undone; restart iCode; open the keyboard; leave the pills 3 seconds and
  see them sink into the edge; tell the three apart.
