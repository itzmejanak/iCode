# Runtime foundation — plan, decided (2026-10-01)

The owner asked for decisions taken, not offered: researched, accurate, the foundation made right
before production. This plan takes them, each on facts checked in iCode's code, Koog 1.3.0's
sources or primary documentation. It answers the findings of
[runtime-foundation-findings.md](./runtime-foundation-findings.md) and the assistant's open points
(reasoning, cut-short replies, the output limit).

Two rules hold throughout:

- **No compatibility layers.** iCode is not in production. Nothing reads an older version's data;
  every migration goes (§2 D10).
- **No project-specific behavior.** Nothing here knows Python, `uv`, FastAPI or any one tool. It
  fixes what sits below every project.

## 1. Facts

| # | Fact | Evidence | Consequence |
|---|---|---|---|
| F1 | No process outlives the PRoot that started it; every assistant command is its own PRoot, run with `--kill-on-exit` | `GuestCommand.run`; `third_party/patches/proot/0002` (`PTRACE_O_EXITKILL`; SIGHUP, then SIGKILL after 2 s) | A process that must outlive a call needs a PRoot of its own, owned by something that outlives the call |
| F2 | From Android 12, the system kills apps' child processes past 32 across all apps, and those using much CPU while their app is in the background | [Google issue 205156966](https://issuetracker.google.com/issues/205156966); [Termux discussion 3387](https://github.com/termux/termux-app/discussions/3387) | Long-running processes are counted and bounded; the user is told the limit exists |
| F3 | Apps cannot read `/proc/net` on Android 10 and later | [b4x forum thread](https://www.b4x.com/android/forum/threads/on-devices-that-run-android-10-or-higher-apps-cannot-access-proc-net.119129/) and Android's own SELinux policy | `ss`, `netstat` and `lsof -i` cannot list sockets in the runtime even once installed; ports are known only from what iCode started |
| F4 | Apps share the phone's loopback, so a server on `127.0.0.1` opens in the phone's browser; an app that is not root cannot listen below port 1024 | [Termux local server guides](https://www.learntermux.tech/2020/10/Apache2-Termux.html) | A preview needs no forwarding: the browser opens it. Servers use ports from 1024 |
| F5 | A non-interactive login bash reads `/etc/profile` and `~/.profile`; Ubuntu's `~/.bashrc` returns at once unless the shell is interactive | Ubuntu's `/etc/skel/.bashrc` (`case $- in *i*) ;; *) return;; esac`) | What installers add to `~/.bashrc` (nvm, a conda init) is seen only by an interactive shell: the snapshot is taken with `bash -lic` |
| F6 | A variable a shell exports after it started is invisible from outside it | `/proc/<pid>/environ` holds the environment at `execve` | A live terminal's state cannot be copied; the profile can be read |
| F7 | Koog 1.3.0 streams soundly only through some clients | §1.1 | Streaming where it is sound and needed: OpenRouter |
| F8 | OpenRouter sends reasoning as `message.reasoning` (whole) and `delta.reasoning` (streamed); it treats `max_completion_tokens` as `max_tokens`; it puts usage in the last streamed chunk | [OpenRouter reasoning tokens](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens), [parameters](https://openrouter.ai/docs/api_reference/parameters), [usage accounting](https://openrouter.ai/docs/cookbook/administration/usage-accounting) | Koog's whole-reply path (`OpenAIMessage`, `reasoning_content` only) loses it; its streamed path keeps it |
| F9 | Koog's OpenAI client sends an output limit as `max_completion_tokens`, and flattens `additionalProperties` into the request | `OpenAILLMClient.kt:182`, `OpenAIChatCompletionRequestSerializer` | A compatible server that reads only `max_tokens` is sent that too |

### 1.1 Koog 1.3.0's streams, client by client

| Client | Text, tools | Reasoning | Usage, finish | Verdict |
|---|---|---|---|---|
| OpenRouter | yes | `delta.reasoning` | yes (last chunk) | **Sound** |
| OpenAI, Chat Completions | yes | `reasoning_content` | `include_usage` asked | Sound |
| OpenAI, Responses | yes | summaries only if asked | `response.incomplete` not handled: a reply cut short ends in `IncompleteStreamException` | Loses cut-short replies |
| Anthropic | yes | thinking deltas, **signature dropped** (`signature_delta` unhandled) | yes | **Broken** with thinking and tools: the next request throws "Encrypted signature is required" |
| Gemini | yes | yes | yes | Degraded: a function call's thought signature is dropped and Koog sends its stand-in |
| Ollama | yes | yes | **no End frame**: no usage, no finish reason, the last text may never be completed | **Broken** |

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Managed processes**: `RuntimeProcesses` starts long-running processes, each in a PRoot of its own (F1), kept by the foreground service until stopped, until they end, or until iCode's process ends. Each has an ID, its command, state, exit status explained, output kept (the last 256 KB, escape codes removed), its input open for writes, and the ports its output names. At most 8 run at once (F2). The terminal keeps its PTY sessions and the language servers their protocol manager: the three share the spawn core (`RuntimeProcess`, `RuntimeLayout.prootCommand`) and differ in how their input and output are used | One owner for what must outlive a call. Folding a PTY or a JSON-RPC stream into a text-output process would change nothing for the user and risk both |
| D2 | **One environment for every process started on the user's behalf**: the agent's commands, managed processes and language servers start with the user's login environment, read once with `bash -lic` and read again when a profile file changes (F5). iCode's own commands (Git, ripgrep, apt) keep their fixed environment. The agent's working directory lasts between its commands, per chat, from the workspace's top; its `export`s do not, and its tools say so. No iCode environment file: a project's settings are its own (`.env`, its scripts) | The terminal's live state cannot be copied (F6); the profile can. A lasting directory removes the failures the test hit; lasting variables would be invisible state |
| D3 | **The agent's tools**: `run_command` (bash, foreground, bounded); `start_process`, `process_status`, `send_process_input`, `stop_process`. No PTY for the agent | Covers servers, watchers, workers and prompts that read their input. A PTY would add a terminal emulator the model cannot see |
| D4 | **Reach**: file tools stay inside the workspace. The agent's commands and processes see the whole runtime but only their own workspace under `/workspace`. The terminal sees all workspaces. The tools and the instructions say so | An approved command — or every command, under "Allow all changes" — can no longer touch another workspace |
| D5 | **No socket tools added** to the runtime; "what is listening" is iCode's: the ports a managed process's output names (`http://127.0.0.1:8000`, `localhost:5173`, `0.0.0.0:8000`), each checked with a connection from iCode | F3 |
| D6 | **Preview** is the phone's browser: a port opens `http://127.0.0.1:<port>` | F4; nothing to forward |
| D7 | **Diagnostics**: a command's result says its exit status in words — 126 "not executable", 127 "not found", 128+n "ended by signal NAME" — where it ran, and, for 127, the PATH searched. A timeout says to start a server or watcher with `start_process` | The test's 127 and 143 said nothing |
| D8 | **Streaming** for OpenRouter only (§1.1): its reasoning and answer appear as they are written, and its reasoning becomes visible at all (F8). The reasoning is shown, never sent back, as today. Every other client stays on whole replies. An OpenAI-compatible provider whose address is `openrouter.ai` is told to use the OpenRouter type | Streaming everywhere would break Anthropic and Ollama in Koog 1.3.0 |
| D9 | **Output limit**: an OpenAI-compatible server is sent `max_tokens` as well as `max_completion_tokens` | F9; OpenAI's own API keeps the one it accepts |
| D10 | **No compatibility code**: removed — the provider store's round-2 migration, the chat store's single-chat migration and round-2 attachments, the workspace storage migration's one-time part and its notice (`WorkspacesMovedIn`, `EarlierWorkspace`, the earlier recents), the Git credentials file of earlier versions. The staging sweep at every start stays: it is recovery from a crash, not from a version | Owner, 2026-10-01 |
| D11 | **`uv`'s hard link** is not worked around: its failing call is unknown without a trace on a device, and a global `UV_LINK_MODE` would be one tool's setting in the platform | The findings' rule |

## 3. Design

### 3.1 The login environment — `data/runtime/GuestEnvironment`

- `suspend fun variables(): Map<String, String>`: runs
  `bash -lic 'printf "\0icode-environment\0"; env -0'` in the guest home, with stdin closed and
  `HISTFILE=/dev/null`, and keeps what follows the marker (a banner a profile prints comes before
  it).
- It drops what each launch sets itself: `PWD`, `OLDPWD`, `SHLVL`, `_`, `HOME`, `TERM`,
  `COLORTERM`, `HISTFILE`, `LD_LIBRARY_PATH`, `LD_PRELOAD`, `GLIBC_TUNABLES`, `PROOT_*`.
- It is cached against a fingerprint — the size and time of `/etc/environment`, `/etc/profile`,
  `/etc/bash.bashrc`, each file in `/etc/profile.d`, and `~/.profile`, `~/.bash_profile`,
  `~/.bash_login`, `~/.bashrc`, `~/.bash_aliases` — and read again when it changes.
- If the profile cannot be read in 10 s, or does not reach the marker (an `exec zsh`, say), the
  launch gets iCode's fixed environment, and `failure` says why: the next command's result
  carries it once.

### 3.2 Launch scope — `RuntimeLayout.prootCommand(..., workspace)`

`workspace = null` binds every workspace at `/workspace`, as now. A workspace's location binds
that one alone, at its own guest path. The agent's commands and processes pass theirs.

### 3.3 The agent's shell — `data/assistant/tools/AssistantShell`

- **Where:** per chat, the guest directory the last command ended in, kept in memory. It starts
  at the workspace's top, goes back there if it lies outside the workspace or no longer exists,
  and the result says so.
- **How:** `/bin/bash -c` with the login environment, plus `PAGER=cat`, `GIT_PAGER=cat`,
  `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`, `NO_COLOR=1`, `TERM=dumb`. The script
  runs `cd` to the directory, sets an `EXIT` trap that writes `pwd -P` to a file of its own in
  the guest's `/tmp`, then runs the command with stdin closed.
- **Result:** the first line is "Exit code N — meaning", then "in /workspace/app/backend", then the
  output (its start and end within 32 000 characters), then stderr.

### 3.4 Managed processes — `data/runtime/RuntimeProcesses`

- `start(workspace, directory, command, name)`: refused past 8 running. Runs `/bin/bash -c` with
  `exec 2>&1; cd …; command` and the login environment, in a PRoot with `--kill-on-exit` bound to
  its workspace; stdin stays open.
- Output: a reader decodes UTF-8 across reads, removes ANSI escapes, and keeps the last 256 KB with
  a running offset, so a reader asking "since N" learns what it missed. The URLs it prints give
  the ports.
- `stop(id)`: SIGTERM to PRoot, which passes it on (patch 0002), then SIGKILL after 3 s.
  `input(id, text)` writes to its stdin. An ended process stays listed until forgotten.
- State reaches the snapshot as `WorkbenchSnapshot.processes`, at most four times a second while
  output flows.
- `BackgroundWorkRules` adds one item while any run ("2 processes running" · their names), so
  the foreground service keeps them; its Cancel stops them all, and its tap opens the Assistant.
- `WorkspaceUsage` holds a workspace's rename and delete while a process runs in it ("A process
  is running in app. Stop it first"), as it does for a terminal session.

### 3.5 The agent's process tools

| Tool | Kind | What it does |
|---|---|---|
| `start_process(command, name?, wait_seconds = 5)` | Command | Starts it in the chat's directory; waits up to `wait_seconds` (at most 30) for its first output, so a server's "running on http://127.0.0.1:8000" comes back with the ID |
| `process_status(id?)` | Read | Without an ID, the workspace's processes; with one, its state, ports (each said to answer or not), and output since the agent last looked (the end, within 16 000 characters) |
| `send_process_input(id, text, newline = true)` | Command | Writes to a running process's input |
| `stop_process(id)` | Command | Stops it |

In Ask mode only `process_status` is offered.

### 3.6 Language servers

`LanguageServiceProcessManager.start` takes the login environment; nothing else changes.

### 3.7 The Processes sheet

On the Assistant page, a row above the composer while the workspace has processes:
"▶ 2 running". It opens a sheet listing each process with:
- its name and command;
- its state ("Running · 4m", "Exited 0", "Ended by SIGKILL");
- its ports as chips that open the browser;
- its last output, in mono;
- Stop while it runs, Remove once it has ended.

The sheet says that Android may end background processes when many run.

### 3.8 Streaming for OpenRouter

- **The turn:** `KoogAssistantEngine` builds the turn's strategy per run. For OpenRouter, its two
  requests stream: frames are collected, deltas reported, and the reply put together from the
  completed frames.
  - Tool arguments: blank arguments become `{}`. Arguments that are not JSON reach Koog's tool
    runner, which answers the model about them.
  - The prompt: the reply is appended without its reasoning.
- **Events:** the reply, the tokens and the output limit are reported the same way for streamed
  and whole replies. A stream that ends early says "The connection ended before the reply was
  complete — try again".
- **Live state:** `AssistantEvents.writing(reasoning, text)` reaches
  `AssistantState.live`, at most ten times a second, and is cleared when the reply is recorded.
- **The page:** while live, the reasoning row is open and grows, and the answer grows below it
  as Markdown. The recorded reply then replaces both, its reasoning folded.
- **The provider page:** an OpenAI-compatible provider whose address is on `openrouter.ai` says
  "Choose the OpenRouter type for OpenRouter: only it shows the models' reasoning".

### 3.9 The output limit

`KoogParams` for an OpenAI-compatible server adds `max_tokens` (`additionalProperties`) whenever
it sends a limit.

### 3.10 What the model is told

`AssistantInstructions` says what is true of the runtime:
- Ubuntu ARM64 under PRoot: `uid 0` without root's powers, no systemd;
- its workspace alone under `/workspace`;
- ports from 1024;
- servers and watchers go through `start_process`.

`run_command`'s description adds:
- bash, with the user's login environment;
- the directory lasts, and `export` does not;
- stdin is closed;
- it reaches the whole runtime, while the file tools stop at the workspace.

## 4. Cycles

| # | Step | Verified by |
|---|---|---|
| F0 | This plan; the findings point to it | Review |
| F1 | Compatibility removed (D10) | Type-check; tests updated, none reading old data |
| F2 | `GuestEnvironment`, launch scope, `AssistantShell`, diagnostics, instructions (D2, D4, D7) | Unit tests for the parser, filter, fingerprint, exit statuses and directory rule |
| F3 | `RuntimeProcesses`, the tools, background work, workspace usage, language servers (D1, D3, D5) | Unit tests over a fake spawner: output ring, offsets, ports, limits, stop |
| F4 | The Processes sheet (D6) | Compose type-check, UI guard |
| F5 | Streaming for OpenRouter, live page, OpenRouter hint, output limit (D8, D9) | Engine tests with a streaming fake client; params test |
| F6 | Architecture, PRD, user stories, plans as built; guards; API-30 check; commit | Review |
