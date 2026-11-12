# iCode's home

Status: **plan** (2026-11-12), waiting on the owner's decisions (§8). Nothing is built yet.

> "Redesign my mobile app home UI to make it look like VS Code, keeping the same features." Option A,
> its bottom bar as VS Code's status bar (Option 1): "clicking Terminal and Assistant opens the
> existing pages, not new ones"; the terminal "open as root, cd $HOME"; the assistant "from root",
> with the control of the Linux environment real Ubuntu has. "No new layer of architecture: improve the
> existing ones; if limited, fix; if working, migrate; and clean up." "Make sure no wrapper of code
> brings a wrong decision in future."

**The answer:**
- The home becomes VS Code's Welcome page with its status bar.
- The status bar's Terminal and Assistant open the Terminal and Assistant pages that exist today, at
  the runtime's home, `/root`.
- In the code, the home becomes one more **place**: a folder in the runtime where things start, as a
  workspace is. Terminal tabs, chats, processes and notices already name their place by its location,
  so the home joins by its own location. No flag, no magic value and no new layer.
- §4 lists the shortcuts in today's code that would decide wrongly once the home is a place. Each is
  fixed or migrated, and what it leaves behind is removed.
- §5 checks what iCode adds inside Ubuntu. It is real Ubuntu: what iCode adds is what Android and
  PRoot require, or the user's to override. Two inaccuracies are found:
  - the prompt shows `$` for root, where Ubuntu shows `#` (D7);
  - iCode's "Copy all output" joins two lines when the first exactly fills the screen's width
    (W14), which the owner's copy of `ls - a` showed.

## 1. The home (Option A, status bar Option 1)

| Part | What it is | Opens (all existing) |
|---|---|---|
| Top bar | "iCode", Settings | Settings |
| Start | Import…, Clone Repository…, Workspaces, as links | The file explorer at phone storage, Clone, the file explorer at iCode's storage (as today's tiles) |
| Recent | The five recent workspaces: name, path, branch, time, ⋮ and long press | The workspace; its sheet (as today) |
| Behind | The logo as a letterpress | — |
| Status bar, left | VS Code's remote button, **><**: the Ubuntu runtime. Accent while it is ready, amber otherwise | Ready: Runtime Environment. Otherwise: the runtime's install (as today's label) |
| Status bar | Terminal icon | The Terminal page, at home (§3.2) |
| Status bar | Assistant icon | The Assistant page, at home (§3.2) |
| Status bar, right | Bell, with its unread dot | Notifications |

- **Badges:** the icons carry `ToolBadges`, as the dock does.
  - Terminal: its dot while a command runs in any tab, since the page lists every tab.
  - Assistant: its tone while the home's chat works or waits for the user.
- **States as today:** loading rows, "No workspaces yet", the runtime not installed. The Terminal and
  Assistant pages already show their own gate for a missing runtime and an unset provider.
- **Goes:** the three tiles (`LauncherActionTile`), the "RUNTIME HEALTHY" label and the top bar's bell.

## 2. Facts this plan rests on (checked in the code, 2026-11-12)

- **Terminal**
  - The Terminal page lists every tab, whatever its place (`TerminalSnapshot`).
  - Only "+" and the first tab depend on a place: `TerminalCoordinator.open/ensure/type` start in
    `activeRoot()`, the open workspace, even when the user is on the Launcher.
  - A tab with no workspace is `null`, and `TerminalLaunch` then falls back to `/root`. It also maps a
    place with `layout.workspaces::guestOf`, so a place outside `/workspace` silently becomes `/root`.
  - Showing such a tab from its notice emits `WorkspaceTerminalOpened` with the open workspace, so a
    home tab opens in that workspace's tools. With none open it emits `""`, which opens the workspace
    shell with no workspace: a dock of six tools, four with nothing to show.
- **Navigation**
  - Terminal and Assistant are workspace tools (`Route.tool`). Asked for from the Launcher, they
    switch into the workspace shell.
  - `show`/`opened` enter the workspace explicitly. Notices and arrivals use them, and also
    `navigate(Route.Assistant)`.
- **Assistant**
  - Chats are kept by their place's location (`AssistantChatStore`, by its SHA-256), so any location
    works.
  - Paths resolve anywhere in the runtime, and anything outside the chat's place is asked about by the
    mode (`AssistantWorkspace`, `AssistantPermissions`).
  - What limits it to workspaces:
    - `AssistantCoordinator.place()` falls back to the most recently opened workspace, and else says
      "Open a workspace first".
    - `AssistantTurns.setup` requires a workspace (`describe`).
    - The Run tab's tools are added to every chat, although its doc says "for a workspace only".
    - The instructions say "## Workspace".
    - `AssistantPlace.name` takes a location's last segment, which would name `/root` "root".
- **History (L1):** chats from `/root`, the "phone chats" with a home-screen Assistant button, existed
  and were removed on 2026-11-03 (`eb5c65f`; docs/phone-agent-plan.md L1). Their place was the magic
  string `"phone"`, never a location, with a `phone` flag in `AssistantWorkspace`: special cases in
  every layer. This plan brings chats outside a workspace back (D1), by the home's real location,
  with no such cases.
- **Runtime busy:** Repair and Reset are refused while a tab, a language server or a process runs
  (`runtimeBusy`). An assistant turn between commands is not counted, and Reset empties `/root`.

## 3. Design: the home is a place

### 3.1 One place model (domain and backend)

- **A place** is a location in iCode's storage that the runtime sees: a workspace
  (`/workspace/<name>`), or the home, `layout.home` (`/root`).
  - `ICodeSnapshot.home` carries the home's location, set once by the backend.
  - The snapshot's existing `activeWorkspaceLocation` is unchanged.
- **Intents that start something "here" say where:** `OpenTerminalSession(place)`,
  `EnsureTerminalSession(place)` and `OpenAssistant(place)`. The backend never guesses from
  `activeRoot()`.
- **Terminal:**
  - A tab's place is never null (migrated from `workspaceHostPath: String?`). A home tab has
    `layout.home`.
  - `TerminalLaunch` maps every place with `GuestPaths.guestOf`, the one map of runtime paths, and
    refuses a place outside the runtime. The two `?: GUEST_HOME` fallbacks go.
  - `show(id)` of a home tab tells the UI the tab's place, not `""`.
- **Assistant:**
  - The page's place is the one asked for. With nothing asked (the bubble, with no workspace open),
    it is the chat shown, else the home (D6). "Open a workspace first" goes.
  - A turn's place: a workspace (it must still exist, as today) or the home.
  - The Run tab's tools are offered for a workspace only, as their doc says.
  - The instructions name the place: a workspace as today; the home as root's home `/root` in the
    user's own Ubuntu, where a relative path starts.
  - `AssistantPlace.name(place, home)`: "Home" for the home (D3), else the workspace's name. Its 17
    callers pass `snapshot.home`.
- **Notices:** a home tab's or the home chat's notice gets its place from `NoticePlaces.of`, which
  knows the home. The `null` branch that gave "Linux runtime" goes.
- **Runtime busy:** a running assistant turn counts, so Repair and Reset never start under one.
- **Language installs** type in a home tab: an install is the runtime's, not a workspace's.
  `typeInTerminal` stops reading `activeRoot()`.

Nothing new is added beyond a field, intent parameters and a parameter of `AssistantPlace.name`:
the same owners (`TerminalCoordinator`, `AssistantCoordinator`, `NoticePlaces`) decide, from one
model.

### 3.2 The pages at home (navigation and UI)

- **One rule, in `Navigation`:** asked for from the app's pages, Terminal and Assistant open there,
  as pages over the Launcher. Back returns to it, and no dock shows.
  - Inside the workspace they stay its tools.
  - `show`/`opened` still enter the workspace.
- **A screen's place:** `NavigationActions` says whether the screen is one of the app's pages. A
  screen's place is then the home on the app's pages, and the open workspace in its tools.
  - The Terminal and Assistant pages ask for their place whenever it changes, keyed by the place, so
    the Assistant tool never shows the home's chat or the reverse.
- **Terminal page at home:** the Terminal tab only (D5). Run, Problems and Output are the
  workspace's. "+" opens a tab in `/root`.
- **Notices and arrivals:**
  - A home tab, the home chat or the home's turn opens its page at home.
  - A workspace's opens its tool through `show`, as today.
  - `NoticeActions` and `Arrival` decide this by the place. `WorkspaceTerminalOpened("")` and the
    shell with no workspace go.

## 4. Shortcuts that would decide wrongly (fixed by §3)

| # | Where | What it decides wrongly | Fix |
|---|---|---|---|
| W1 | `TerminalLaunch` | A place outside `/workspace` silently becomes `/root` | `GuestPaths.guestOf`; no fallback |
| W2 | `TerminalCoordinator.open/ensure/type` | "+" at home starts in the open workspace | The place from the intent |
| W3 | `TerminalCoordinator.show` → `WorkspaceTerminalOpened("")` | A workspace shell with no workspace | The home's page |
| W4 | `AssistantCoordinator.place()` | A chat lands in the most recent workspace the user did not choose; "Open a workspace first" | The place asked; else shown; else home |
| W5 | `AssistantTurns.setup` | Any place but a workspace "no longer exists" | Workspace or home |
| W6 | `AssistantToolbox.forWorkspace` | Run-tab tools offered where there is no Run tab (doc and code disagree) | Workspace places only |
| W7 | `AssistantInstructions` | The home told it is a workspace | The place's own wording |
| W8 | `AssistantPermission` label | "Auto in workspace" at home | The place's name (D4) |
| W9 | `AssistantPlace.name` | `/root` named "root" | "Home" (D3) |
| W10 | `ToolBadges` (Terminal) | Workspace errors on the home's icon | The home's scope (§1) |
| W11 | `NoticePlaces` null branch | Two paths for one place | `NoticePlaces.of` knows the home |
| W12 | `runtimeBusy` | Reset can empty `/root` under a turn | Counts a running turn |
| W13 | `LanguageServices.typeInTerminal` | An install typed in the open workspace | A home tab |
| W14 | `TerminalMoreMenu` "Copy all output" | Two lines copied as one when the first exactly fills the width (§5) | Only rows that really wrapped are joined |

## 5. Ubuntu as it is (what iCode adds inside the runtime)

iCode's own files say `Managed by iCode:` on their first line, or after a script's `#!`
(`grep -rl` finds them). The `aid_*` lines are added to Ubuntu's own `/etc/group`.

| What | Why | Verdict |
|---|---|---|
| PRoot `-0`, `--link2symlink`, the bound trees (`/proc`, `/dev`, `/sys`…), `/sdcard`, `/workspace` | Android has no root, refuses `link()`, and keeps storage elsewhere | Required: keep |
| `GLIBC_TUNABLES=glibc.pthread.rseq=0` | glibc's rseq misbehaves under PRoot's tracing | Required: keep |
| `/etc/resolv.conf`, `/etc/hosts`, `aid_*` groups | Android gives the runtime none | Required: keep |
| `/etc/gitconfig` `core.createObject=rename` | PRoot's emulated hard links break when renamed | Required: keep |
| `env -i` with a login's base (`HOME=/root`, `USER=root`, `SHELL`, `PATH`, `LANG=C.UTF-8`) | What a Linux login sets, and nothing of PRoot's | Matches a login: keep |
| `/etc/icode/bashrc` (the tab's rcfile) | Reads what a login bash reads, then adds the terminal's marks (OSC 133, OSC 7) | Feature, not a limit: keep |
| `/usr/local/bin/sudo` | Root has nothing to elevate; scripts written with `sudo` work. A real `sudo` from apt wins | Compatibility: keep |
| gpg's pinentry and agent program, `/etc/gnupg` | iCode's passphrase sheet; `~/.gnupg` overrides | Feature: keep |
| `dpkg` `force-unsafe-io` | Skips dpkg's syncs, as container images do (measured: git 152 s to 84 s) | Keep |
| Android SDK lines (Runtime Tools) | Removed with the SDK | Keep |
| Assistant commands only: `PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`, `TERM=dumb` | No one is there to answer a pager or a prompt. Never in the user's tabs | Required: keep |
| The prompt (`/etc/icode/prompt.sh`, one line in a new `~/.bashrc`) | A short path for a phone's line | **Inaccurate: `$` for root, where bash and Ubuntu show `#`** (D7) |

- **Found in iCode, not in Ubuntu (W14):** the owner's copy of `ls - a` ran its two errors and the
  next prompt together. Ubuntu printed them right; iCode's copy joined them.
  - More › "Copy all output" reads `transcriptTextWithFullLinesJoined` (`TerminalMoreMenu`). That
    joins every row that exactly fills the width to the next, as if it had wrapped.
  - Both errors are 48 characters, so on a 48-column screen each one fills its row and is joined.
    The lines of other lengths in the same copy kept their breaks.
  - The emulator itself flags a row as wrapped only when a character really wraps
    (`TerminalEmulator.emitCodePoint`). "Copy all output" should keep exactly those joins:
    `transcriptText`. The links found in the same menu keep the full-line join, so a URL that fills
    a row is still found whole.

## 6. Cycles

| # | What | Checked by |
|---|---|---|
| H0 | The place model in the backend (§3.1, W1, W2, W4–W7, W9, W11–W13), and W14: `ICodeSnapshot.home`; the intents' place; tabs' places never null; `GuestPaths` in `TerminalLaunch`; the home's turns, tools and instructions; notices; busy | Unit tests: a home tab starts in `/root`; "+" at home with a workspace open; a home chat sent, kept, listed and its turn set up; no Run tools at home; instructions; the home named "Home"; Reset refused under a turn; the guards |
| H1 | Navigation and the pages at home (§3.2, W3, W8, W10): the rule; the screens' place; the Terminal page's tab at home; notices and arrivals by place | `NavigationTest`: from the Launcher, Terminal and Assistant are pages, Back returns, no dock; from tools, unchanged. The page keyed by its place. UI type-check |
| H2 | The home screen (§1) | UI type-check; the capture frames `LauncherHome` and `LauncherEmpty`; the UI guard |
| H3 | Names (D8): `workspace` → `place` where it names a chat's, tab's or process's place, by rename only | Every test as before; the guards |
| H4 | Docs as built: this plan; `layout.md` §9; dock-navigation-plan; phone-agent-plan L1; the changelog. CI | — |

**Phone checks:**
- **From the Launcher:** Terminal opens on a tab in `~`, and Assistant on the home's chat. Back
  returns to the Launcher each time.
- **With a workspace open:** its tools keep its own chats and tabs.
- **Notifications:** a home tab's or home chat's notification opens at home.
- **The runtime button:** amber while the runtime is not installed, and it opens the install.

## 7. Not now

- A wide-screen layout of the home (the dock's rail on wide screens is also later).
- The other VS Code status bar items (branch, problems) belong to the workspace, which has the dock.

## 8. Owner decisions

| # | Decision | Recommended |
|---|---|---|
| D1 | Chats outside a workspace again: the home's, which reverses L1 (2026-11-03) as asked, by the home's real location, not the old `"phone"` value | Yes |
| D2 | The home chat's boundary: `/root`, as a workspace is its own boundary (outside asks, by the mode; Full access asks nothing), or all of `/` | `/root` |
| D3 | The home's name in the Assistant page, notices and the mode's label: "Home" or "~" | "Home" |
| D4 | The mode's label follows the place: "Auto in workspace" in a workspace, "Auto in home" at home | Yes |
| D5 | The Terminal page at home: the Terminal tab only, or all four | Terminal only |
| D6 | The bubble with no workspace open uses the home's chat (today: the most recent workspace's) | Home |
| D7 | The prompt: the short path with `#` for root (bash's own `\$`), or Ubuntu's own `root@localhost:~#`, or as today | Short path with `#` |
| D8 | Rename `workspace` to `place` where it names a place (H3), or keep the names | Rename |
