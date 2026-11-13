# iCode's home

Status: **built** (2026-11-13): the home UI, and the home's Terminal and Assistant in `$HOME` (§2). The phone checks (§4) are still to be run.

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
| Recent | The five recent workspaces (`WorkspaceRow`, owner's choice A, 2026-11-13): a folder icon, the open workspace's open and in the accent; the name as a link; under it the branch, with Source Control's own branch icon (`CallSplit`), and the time; ⋮ and a long press. The path is not shown: it is always `/workspace/<name>`, the name again | The workspace; its sheet |
| Behind | The logo as a letterpress (`IcodeWatermark`) | — |
| Status bar, left | The runtime as VS Code's remote button (`IcodeIcons.Runtime`): the accent while healthy, amber otherwise | Healthy: Runtime Environment. Otherwise: the runtime's install, as the old label did |
| Status bar | Terminal (`IcodeDockedAction`, `ToolBadges`), as the dock draws and badges it | The Terminal page outside the workspace, its tabs starting in `$HOME` (§2) |
| Status bar | Assistant, the same way | The Assistant page outside the workspace, on the chats in no workspace, which work in `$HOME` (§2) |
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
- **The tab shown is where the page is** (owner, 2026-11-13: a workspace's tab was shown on the home's
  Terminal once one had been opened). Each time the page opens on Terminal it shows the running tab of
  its place: the open workspace's inside, one in no workspace outside. If there is none, it opens one
  there. Every tab stays listed, and a tap on another still shows it.
- **A tab in no workspace**, shown from its notification, opens on the Terminal page outside the
  workspace, wherever iCode is. Entering the workspace from that page closes it, so Back never shows
  Terminal twice.
- **How it is built:**
  - `Navigation` pushes Terminal's own tab onto the app's pages when they are on screen.
  - `NavigationActions.inWorkspace` tells the page where it is.
  - `OpenTerminalSession` and `EnsureTerminalSession` say it.
  - `TerminalCoordinator` starts the tab in the open workspace or in none.

**Assistant** (owner, 2026-11-12: "the Assistant from home should open from `cd $HOME`", and rule (a)):
- **From the home** it is the Assistant page outside the workspace, as Terminal is.
  - It shows the chats in **no workspace**. Their turns work from `$HOME`, where a login starts, so
    a relative path starts there, as a home tab's commands do.
  - Their commands, processes and their own terminal tab start there too, and belong to no workspace.
  - Its subtitle and the place line name it `~`, as the shell does.
- **Inside the workspace** it is its tool, on the workspace's chats, as before.
- **With no workspace open in the tool**, it keeps the chat shown, so the page and the bubble stay in
  the chat the user was in. Before the page has opened, the bubble writes to the open workspace's
  chats, else to the chats in no workspace. The old fall back to the most recently opened workspace
  is gone.
- **What a chat in no workspace may do** (rule a):
  - With no workspace there is no boundary: nothing is outside one, so reads run.
  - "Auto in workspace" has no workspace to run in, so changes and commands ask as under "Ask first".
  - Only "Full access" asks nothing.
  - There is no "Allow in …" for a change or a command: there is no workspace to allow it in.
  - Apps, MCP servers and terminal tabs are still asked about as places of their own.
  - The mode line under the chip says so.
- **What it is not offered:**
  - the Run tab's tools (a workspace's Run tab);
  - the language servers' code tools, which run for the workspace open in the editor;
  - `AGENTS.md`;
  - a workspace's `.mcp.json` servers.

  Its instructions say it works in no workspace, from `/root`.
- **How it is built:** nothing is renamed and nothing new stands in for `/root`.
  - "No workspace" is `null` wherever a chat's workspace was a location, as a terminal tab's
    already was: `AssistantChats`, `AssistantChatStore`, `AssistantTurns`, `AssistantGate`,
    `ManagedProcess`, `TerminalRuns`.
  - `AssistantWorkspace(null, …)` works from the runtime's own `$HOME`, which `TerminalLaunch`
    already uses.
  - The chats in no workspace are kept in their own folder of the chat store.
  - `AssistantState.opened` tells the page that has opened on them from one not opened yet.
  - `OpenAssistant(inWorkspace)` says where the page is.
  - `AssistantPermissions.effective(…, inWorkspace)` applies rule (a).
  - A notice of such a chat opens it outside the workspace (`NoticeTarget.AssistantOutside`).
  - Arrivals and the bubble do the same.

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
