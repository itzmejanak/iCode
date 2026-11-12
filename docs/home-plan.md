# iCode's home

Status: **built** (2026-11-12): the home UI, and the home's Terminal in `$HOME` (§2). The phone checks (§4) are still to be run.

> "Redesign my mobile app home UI to make it look like VS Code, keeping the same features." Option A
> with Option 1's status bar; "clicking Terminal and Assistant opens the existing pages, not new
> ones." Then: "Ubuntu owns the filesystem and runtime; iCode owns the UI, workspaces and interaction
> with the runtime. The goal is to redesign the home UI, not redesign how Ubuntu works inside iCode."
> "Focus on UI only."

**The answer:** the Launcher is VS Code's Welcome page with its status bar. Every way in is one that
existed; Terminal and Assistant open their own pages the way the Explorer's rail opens them. Nothing
in the runtime, the workspaces or the backend changes.

## 1. The home

| Part | What it is | Opens (as before) |
|---|---|---|
| Top bar | "iCode", Settings | Settings |
| Start | Import…, Clone Repository…, Workspaces: links in the accent (`IcodeListRow`) | The file explorer at phone storage; Clone; the file explorer at iCode's storage |
| Recent | The five recent workspaces (`WorkspaceRow`): the name as a link, the path, the branch, the time, ⋮ and a long press | The workspace; its sheet |
| Behind | The logo as a letterpress (`IcodeWatermark`) | — |
| Status bar, left | The runtime as VS Code's remote button (`IcodeIcons.Runtime`): the accent while healthy, amber otherwise | Healthy: Runtime Environment. Otherwise: the runtime's install, as the old label did |
| Status bar | Terminal (`IcodeDockedAction`, `ToolBadges`), as the dock draws and badges it | The Terminal page outside the workspace, its tabs starting in `$HOME` (§2) |
| Status bar | Assistant, the same way; muted while there is no workspace to work in | `navigate(Route.Assistant)`, as the rail |
| Status bar, right | The bell, with the accent dot while anything is unread or Document Recovery holds work | Notifications |

- **Gone:** the three tiles (`LauncherActionTile`), the "RUNTIME HEALTHY" label, the top bar's bell,
  and `WorkspaceRow`'s `size` and `onOpen`, which nothing passed.
- **States, as before:** loading rows, "No workspaces yet", and the runtime not installed (the amber
  button).
- **The status bar is a floor** (`Modifier.floor`): the floating stack's pills stand above it, as above
  every bar.
- **Shared, not copied:**
  - the dock's icon button and badges;
  - the tools' icons (`WorkspaceTool.icon`);
  - what TalkBack says of a tool and its badge (`ToolBadge.spoken`, now used by the dock and the bar).

## 2. How Terminal and Assistant behave

**Terminal** (owner, 2026-11-12: "opening Terminal from home should open from `cd $HOME`"):
- **From the home** it is the Terminal page, outside the workspace. A tab started there, "+" or the
  first, is in no workspace, so it starts where a login starts: `$HOME`, whatever workspace is open.
  - This is the runtime as it was: `TerminalLaunch` already starts a tab in no workspace there.
  - The page's tabs are every tab, as inside the workspace.
  - Run, Problems and Output belong to the workspace, so only Terminal shows.
  - No dock shows, and Back returns to the home.
- **Inside the workspace** Terminal is its tool, as before; its tabs start in the workspace.
- **A tab in no workspace**, shown from its notification, opens on the Terminal page outside the
  workspace, wherever iCode is. Entering the workspace from that page closes it, so Back never shows
  Terminal twice.
- **How it is built:**
  - `Navigation` pushes Terminal's own tab onto the app's pages when they are on screen.
  - `NavigationActions.inWorkspace` tells the page where it is.
  - `OpenTerminalSession` and `EnsureTerminalSession` say it.
  - `TerminalCoordinator` starts the tab in the open workspace or in none.

**Assistant** is still the workspace's tool, reached as from the Explorer's rail:
- **With a workspace open:** it opens in it, and Back goes through its tools, then to the home
  (docs/dock-navigation-plan.md D4).
- **With none open:** it opens the chat shown last, else the most recently opened workspace's chats.
- **With no workspace at all:** its button is muted.
- **Not yet:** opening it from `$HOME` needs a chat in no workspace, which chats cannot be today
  (docs/phone-agent-plan.md L1).

## 3. Considered and dropped

An earlier version of this plan (`db6dfdd`) made the runtime's home a "place" beside the workspaces.
It would have opened Terminal and Assistant at `/root` with chats of their own, and renamed
`workspace` to `place`. The owner declined it: `/root` is Ubuntu's own directory, not an iCode
concept, and the runtime already works as real Ubuntu.

Found while checking and left as it is, outside the home:
- **"Copy all output"** (`TerminalMoreMenu`) reads `transcriptTextWithFullLinesJoined`. That joins a
  row that exactly fills the width to the next, so two lines can be copied as one: the owner's
  48-character `ls` errors were. The emulator flags only rows that really wrapped, and
  `transcriptText` joins those alone.
- **The prompt** iCode writes (`/etc/icode/prompt.sh`) shows `$` for root, where Ubuntu shows `#`.

## 4. Phone checks

- The home in Dark, Light and AMOLED, with and without workspaces; the loading rows at start.
- **Runtime button:** healthy, it opens Runtime Environment; with the runtime missing, it is amber and
  opens the install.
- **Terminal and Assistant:** from the home with a workspace open, without one, and with none at all
  (Assistant muted).
- **Bell:** the dot with an unread notice, and TalkBack's words on every button.
