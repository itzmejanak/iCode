# The agent drives iCode through the user's own backend — plan

Status: **plan** (2026-11-06), the owner's request. Nothing here is built yet.

> "Also need to integrate realtime logs when agent ran command and the ui is embedded with terminal
> … ensure consistency and 0 duplicacy regarding logic because the existing run process
> implementation is there … instead of making separate logic for same things."
>
> "Also make sure the ui look real terminal and terminal navigation and also ensure Agent can use
> same terminal and new according to the need … give better control to User and agent."
>
> "Also make sure agent have control to CRUD mcp server just like run command … Agent itself can
> control iCode so make sure to design the scalable and better architecture … ensure agent and user
> interact with same backend so no duplicacy."

## 1. The rule: one owner, two callers

Every capability of iCode has **one owner** in the data layer: a coordinator with typed
operations. The user's screens reach it through intents. The agent reaches it through tools. Both
are thin adapters over the same operations:
- The agent's change shows on the user's screen at once.
- The user can always see it, stop it and take it over.
- A tool never reaches past the owner: no store, SDK or process of its own.

This is already how the Run tab works ([run-tab-plan.md](./run-tab-plan.md) §2.5). This plan makes
it the rule, and closes the two places that break it.

| Area | Owner | The user | The agent today | Gap |
|---|---|---|---|---|
| Files | `WorkspaceWriter`, `WorkspaceFileSource` | the editor | `read_file`, `write_file`, `edit_file`… | — |
| Run tab | `RunCoordinator` | the Run tab | `run_android_app`, `*_run_command` | — |
| Processes | `RuntimeProcesses` | the Processes sheet, output tabs | `start_process`, `process_status`… | — |
| Phone | `PhoneControl` | Phone Control | `phone_*` | — |
| **Terminal** | `TerminalSessions` | the Terminal panel | — | `run_command` has a runner of its own (§2.2): its output is not live, its stderr comes at the end, and the user can neither see nor answer it |
| **MCP servers** | `McpServers` | the MCP pages (`McpIntent`) | — | no tools; `McpServers.apply` answers nothing a tool could read (it says notices) |

**For every capability to come** (settings, Runtime Tools, workspaces…), follow the same order:
1. Add the typed operation to its owner.
2. Have the intent handler call it, turning a failure into a notice.
3. Have the tool call it, turning a failure into the model's words.
4. Give the tool a kind for the gate (Read, Change, Command), and a place where it reaches beyond
   the workspace (`AssistantPlaceActor`).

## 2. Facts this plan rests on (checked 2026-11-06)

### 2.1 Termux v0.118.3 (iCode's `terminal-view` and `terminal-emulator`), read from source

- `TerminalSession` is `final`. Its reader thread puts the pty's bytes in a private queue. The main
  thread appends them to the `TerminalEmulator` (`MSG_NEW_INPUT`). Nothing outside it sees the
  bytes. `write()` may be called from any thread. The emulator is the main thread's alone.
- The emulator handles OSC 0, 1 and 2 (titles), 4, 10, 11, 52, 104 and 110–112. Any other number,
  133 included, goes to `unknownParameter`: it is logged, nothing is drawn, and the sequence ends.
  So a shell may print shell-integration marks without changing the screen. The app is never told
  of them.
- `TerminalView` draws a `TerminalSession`. Both `TerminalRenderer.render(emulator, canvas, …)` and
  `TerminalEmulator(TerminalOutput, …)` are public, so an emulator with no process behind it can be
  fed bytes and drawn.
- iCode already builds Termux's native half (`third_party/termux-terminal-jni/termux.c`) at this
  release. The Java comes from JitPack.

### 2.2 iCode as it is

- **`run_command`** (`AssistantShell.run`) runs `GuestCommand.stream`: bash in a PRoot of its own.
  Stdout comes line by line. Stderr goes to a file read once the command ends. It runs with
  `NO_COLOR`, `TERM=dumb` and stdin closed. Its directory is carried from one command to the next by
  an `EXIT` trap; its exports end with it. The step shows "Running…" until the command ends, then
  its text.
- **Managed processes:** one PRoot each, stderr joined to stdout, the output both in
  `ProcessOutput` (escapes removed, for the agent) and in `/tmp/icode/processes/<id>.log`, which a
  terminal tab follows with `tail -F` ([runtime-foundation-plan.md](./runtime-foundation-plan.md)
  §6).
- **`TerminalSessions.commandsFinished`** comes from the foreground service sampling each session's
  program. That is an estimate, not an event.
- **`/etc/icode/prompt.sh`** sets `PROMPT_COMMAND`. It is written at every start, and sourced from
  the `~/.bashrc` of homes iCode made, unless the user deleted that line.

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| A1 | **The agent's commands run in terminal sessions**, the user's own backend (`TerminalSessions`). A chat runs in its own tab in the Terminal panel: a real bash in a pty, where `cd` and `export` carry over as in any terminal. `AssistantShell.run` and its pipe go | §1: one runner, and the user sees it, answers it and stops it |
| A2 | **Exact output and end, through shell integration:** OSC 133, the FinalTerm marks iTerm2, WezTerm and others read (VS Code's 633 is its variant). A is the prompt, B its end, C the output's start, D;*code* the command's end. iCode reads them from the bytes through **one patch to Termux's `TerminalSession`: an output tap**. `terminal-emulator` is taken into `third_party` at v0.118.3, as `termux.c` already is | §2.1: no other way sees the bytes. The alternative, `script` logging in the guest, adds a pty per session and a log to bound, and depends on the guest |
| A3 | **Every bash tab iCode starts carries the integration.** The user's and the agent's tabs run `bash --rcfile /etc/icode/bashrc -i`. That file reads what a login bash reads (`/etc/profile`, then the first of `~/.bash_profile`, `~/.bash_login`, `~/.profile`), then adds the marks before any `PROMPT_COMMAND`, so `$?` is read first. Another login shell (zsh, fish) is left as it is: in its tab the agent may read and type, but not run and wait | The user's own `~/.bashrc`, Starship included, keeps working; the marks come last, so nothing overwrites them |
| A4 | **The agent's terminals:** a chat's first command opens its tab ("Assistant", "Assistant 2"…) in the chat's workspace. It stays until the user closes it or the chat is deleted. The agent opens another with `terminal: "new"`. It runs in a tab of the user's only by naming it, and that tab is a **place** the gate asks about ("Use terminal bash 2?" · Allow in it for this chat), as an app or an MCP server is | The user's own shell is theirs; the agent's tabs are the chat's |
| A5 | **The time limit** sends Ctrl-C to the command, as the user would, and the result says so. The tab and the shell stay | A terminal's own way to stop a command |
| A6 | **One command at a time per tab.** A tab whose command is still running, or whose user is typing at the prompt, is busy: `run_command` says so, and suggests `terminal: "new"` | No interleaving in one shell |
| A7 | **The step sheet is the terminal.** While a command runs, the sheet shows that session's own `TerminalHost`: the same view, font, palette, scrollback, selection, links and pinch, with the panel's extra-keys row. What the user types goes to the session. *Open in Terminal* shows the tab. Once the command has ended, its bytes are replayed into an emulator of their own and drawn by Termux's renderer, read-only and looking the same; Copy takes its text | The owner: "look real terminal and terminal navigation" |
| A8 | **The agent manages MCP servers** with `list_mcp_servers`, `save_mcp_server` (add or change: a configuration, a pasted JSON, on or off, each tool's choice) and `remove_mcp_server`. They run through `McpServers`' own operations, which the pages' intents use too. The agent may give a value, a token the user wrote in the chat for example, but never reads one back: a list says "saved" | A1's rule; D8 of [mcp-plan.md](./mcp-plan.md) |
| A9 | **A change to the MCP servers asks first in every mode**, Full access included | A server outlasts the chat and runs code later, outside it |
| A10 | **What a command leaves:** its raw bytes, at most 64 KB (the start and the end), kept with its result for the replay; the model reads the text, escapes removed and carriage returns applied (`ProcessOutput`'s rules), at most 32 000 characters as today | One output reader for processes and terminals |

## 4. Terminals

### 4.1 Data flow

```text
pty ─► TerminalSession (patched: tap) ─► TerminalEmulator ─► TerminalView      (the user)
                       └─► TerminalMarks (OSC 133 A/B/C/D, split across reads)
                             └─► TerminalCommands: per session, each command's
                                 start, end, exit code and bytes (ProcessOutput + raw, bounded)
                                   ├─► commandsFinished, exact where marks come (sampling elsewhere)
                                   └─► the agent: run_command waits on D; read_terminal reads
```

- `TerminalMarks` (data/runtime) is a pure parser. It is tested over sequences cut anywhere, and
  BEL or ST endings.
- `TerminalCommands` belongs to `TerminalSessionManager`, which owns the sessions. It is fed on the
  main thread, as the emulator is. Its records are read through `TerminalSessions`.
- A session's kind is one of **Shell** (the user's), **Assistant** (a chat's, with its chat), or
  **Output** (`tail -F` of a process). The tabs show the kind. The foreground service counts
  Shell and Assistant, as it counts shells today.

### 4.2 Running a command

1. The tab is ready: its last mark is B (a prompt waiting) and nothing has been typed after it.
2. The command is written as typed, then Enter. Bash's history gets it, as if the user typed it.
3. C opens the command's output. D;*code* ends it. The bytes between them are its output.
4. At the time limit comes Ctrl-C. A D within 5 s ends it as "Stopped at N s"; without one, the
   result says it is still running there, and the tab is busy until its D.
5. The environment of an Assistant tab adds `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`
   and `DEBIAN_FRONTEND=noninteractive`, so nothing pages and nothing waits on a password it cannot
   see. `TERM` is the terminal's own, so colours show. The model reads the text without them.

### 4.3 The agent's tools

| Tool | Kind | What it does |
|---|---|---|
| `run_command(command, timeout_seconds = 120, terminal?)` | Command | Runs in the chat's tab (opened if needed), a new one (`"new"`), or a named one (a place, §3 A4). Answers the exit code, the directory it ended in, and its output |
| `list_terminals()` | Read | The open tabs: id, title, kind, workspace, and whether each is ready, busy or ended |
| `read_terminal(id, lines = 100)` | Read | What the tab shows, its last lines, as text |
| `send_terminal_input(id, text?, keys?)` | Command | Types into a tab: text, then keys (`enter`, `ctrl-c`, `ctrl-d`, `tab`, `up`, `down`, `esc`). A user's tab is a place |

The process tools stay as they are. A server, a watcher or anything that must keep running belongs
in `start_process`: it has ports, a log and a row in the Processes sheet.

## 5. MCP servers for the agent

- `McpServers` gains typed operations, which both `McpIntent` and the tools call:
  - `save(server): Result<McpServer>`;
  - `import(json, single): Result<List<McpServer>>`;
  - `remove(id)`;
  - `setEnabled(id, on)`;
  - `setToolChoice(id, tool, choice)`.

  A failure is said as a notice for the user and as the model's words for the agent.
- **`list_mcp_servers()`** (Read): each server's name, kind and where it is, its state (§4.2 of the
  MCP plan's words), its tools on, and whether a value is saved, never the value.
- **`save_mcp_server`** (§3 A9: always asks) takes one of:
  - `config`: the JSON a README gives, read by `McpRules.import`;
  - fields: `name`, `command` and `args`, or `url`; `env` or `headers`; `enabled`; `tools` choices.

  An `id` changes that server; without one, it adds.
- **`remove_mcp_server(id)`** (always asks).
- A workspace's `.mcp.json` server keeps its rule: its configuration is the file's. The agent may
  change only what the user may (on or off, tool choices, values), and trusting one stays the
  user's alone.

## 6. The step sheet (§3 A7)

- **Running:** the Command section, then the terminal: the session's `TerminalHost` at a fixed share
  of the sheet, with Jump to latest, the extra-keys row, and *Open in Terminal*. The sheet's swipe
  closes from its title only, so a swipe on the terminal scrolls it.
- **Ended:** the replay view, which draws the same font, palette and colours, scrolls, and selects
  as the terminal does. Copy gives the text the model read.
- **The bubble and the notification** keep the step's line ("Run `npm test`"). The terminal is
  iCode's.

## 7. Cycles

Each cycle ends green on CI and the guards, with its docs, and is pushed.

| # | What | Tests |
|---|---|---|
| T0 | `terminal-emulator` v0.118.3 into `third_party`, with the tap. The JitPack artifact stays for `terminal-view`, without its transitive emulator | the tap sees every byte once, in order; the app builds and its terminal is unchanged on the phone |
| T1 | `TerminalMarks`, `TerminalCommands`, `/etc/icode/bashrc`, bash tabs started with it, exact `commandsFinished` | the parser, cut anywhere; a command's record from a recorded byte stream |
| T2 | The agent's terminals: `run_command` in a tab, `list_terminals`, `read_terminal`, `send_terminal_input`, the Terminal place; `AssistantShell.run` removed | the tools over a fake session; the gate for a user's tab; busy, timeout, ended |
| T3 | The step sheet: live terminal, replay, Open in Terminal | the replay from recorded bytes; Compose checks as the screens' |
| T4 | MCP for the agent: `McpServers`' typed operations, the three tools | the tools against a store; the intents unchanged |
| T5 | Docs as built, guards, CI | — |

**On the phone:**

| # | Do | See |
|---|---|---|
| TP1 | "List the files in colour" (`ls --color=always`) | the colours in the sheet's terminal as they print; the model reads plain text |
| TP2 | A command that asks (`read -p "Name? " n; echo $n`) | the sheet's terminal takes what is typed; the step ends with it |
| TP3 | `cd backend`, then `export X=1`, then `pwd; echo $X` | both carried over, as in a terminal |
| TP4 | `sleep 600` with `timeout_seconds: 5` | Ctrl-C at 5 s; "Stopped at 5 s"; the tab stays |
| TP5 | "Run it in my bash 2" | "Use terminal bash 2?"; Allow in it; it runs there |
| TP6 | A zsh tab, "run it there" | the plain words: that shell has no integration; the agent's own tab offered |
| TP7 | "Add the everything MCP server" | it asks, in Full access too; the server on the MCP page; listed with its tools; removed when asked |
| TP8 | The user's own command in a bash tab | `commandsFinished` at its D, no longer sampled |

## 8. Owner decisions open

| # | Decision | Recommended |
|---|---|---|
| D-1 | The agent's commands in terminal tabs (§3 A1), replacing the pipe runner | **Yes**: one runner, and the user sees and answers it |
| D-2 | How iCode reads the bytes: a patch to Termux's `TerminalSession` (§3 A2), or `script` in the guest | **The patch**: exact, any tab, nothing more in the guest |
| D-3 | The agent in the user's own tabs: only when named, each asked about once per chat (§3 A4) | **Yes** |
| D-4 | MCP server changes ask in every mode, Full access included (§3 A9) | **Yes** |
