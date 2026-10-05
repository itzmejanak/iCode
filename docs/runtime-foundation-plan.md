# Runtime foundation — plan, as built (2026-10-02)

Status: **F0–F6 done (2026-10-02)**, each checked as far as this container allows (§5). Left for
the owner: the Gradle build and the device pass (§5.3).

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
| F10 | OpenRouter ends every reply with one of five finish reasons — `tool_calls`, `stop`, `length`, `content_filter`, `error` — and reports a provider that fails after the reply began as a chunk with an `error` and `finish_reason: "error"` | [Standardized finish reasons](https://openrouter.ai/announcements/standardized-finish-reasons), [streaming](https://openrouter.ai/docs/api_reference/streaming), [LangChain.js issue 11699](https://github.com/langchain-ai/langchainjs/issues/11699) | A stream with no finish reason closed early; one ending in `error` failed. Neither is a finished reply |
| F11 | Koog's OpenRouter client emits its `End` frame whenever the stream's body ends, closed early or not, and keeps a chunk's `finish_reason` but not its `error`; Koog's SSE client wraps every failure without a status in `KoogHttpClientException`, its other clients wrap theirs in `LLMClientException` | `OpenRouterLLMClient.processStreamingResponse`, `KtorKoogHttpClient.sse`, `AbstractOpenAILLMClient.getResponse` | iCode reads the finish reason itself (F10), and explains a failure without a status by its cause |
| F12 | Providers say when their content rules stopped a reply: `content_filter` (OpenAI, OpenRouter, compatible servers), `refusal` (Anthropic), `SAFETY`, `RECITATION`, `BLOCKLIST`, `PROHIBITED_CONTENT`, `SPII`, `IMAGE_SAFETY` (Gemini); Koog passes each through as the reply's finish reason | Each client's response mapping, as for the output limit's reasons | Such a reply passed for a finished one, as one cut short did |
| F13 | Koog's OpenAI client, reading a whole reply, parses each call's `arguments` with `Json.parseToJsonElement` outside any error handling, and throws on arguments that are not a JSON object — losing the whole reply. A long call cut at the output limit is one: on a phone, a `run_command` script cut inside its string ended the turn with "Unexpected EOF at path: $", again after "continue". Koog's Anthropic, Google and Ollama clients parse a call's arguments the same way when they send it back | `AbstractOpenAILLMClient.toolCallPartsOrNull`; `AnthropicLLMClient`, `GoogleLLMClient`, `OllamaConverters`; reproduced against Koog's real client (§5.1) | Arguments that are not an object are carried in one before Koog reads them, and never sent back as they were (§3.12) |

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
| D12 | **A reply that stopped short says why** — at the output limit, or by a content filter (F12) — and is kept; a reply with only reasoning that stopped so gives that reason, not "empty reply". An OpenRouter stream that closed early or failed (F10) is not kept, and says so | The cut-short finding's rule, for every way a reply ends before it is finished |

## 3. Design

### 3.1 The login environment — `data/runtime/GuestEnvironment`

- `suspend fun current(): Login` — its variables, and why the profile could not be read: runs
  `bash -lic 'printf "\0icode-environment\0"; env -0'` in the guest home, with stdin closed and
  `HISTFILE=/dev/null`, and keeps what follows the marker (a banner a profile prints comes before
  it).
- It drops what each launch sets itself: `PWD`, `OLDPWD`, `SHLVL`, `_`, `HOME`, `TERM`,
  `COLORTERM`, `HISTFILE`, `LD_LIBRARY_PATH`, `LD_PRELOAD`, `GLIBC_TUNABLES`, `PROOT_*`.
- It is cached against a fingerprint — the size and time of `/etc/environment`, `/etc/profile`,
  `/etc/bash.bashrc`, each file in `/etc/profile.d`, and `~/.profile`, `~/.bash_profile`,
  `~/.bash_login`, `~/.bashrc`, `~/.bash_aliases` — and read again when it changes.
- If the profile cannot be read in 10 s, or does not reach the marker (an `exec zsh`, say), the
  launch gets iCode's fixed environment, and `failure` says why: each command's result carries
  it until the profile is read.

### 3.2 Launch scope — `RuntimeLayout.prootCommand(..., workspace)`

`workspace = null` binds every workspace at `/workspace`, as now. A workspace's location binds
that one alone, at its own guest path. The agent's commands and processes pass theirs. PRoot splits
`-b` at its first colon and resolves links in the host side, so a location whose name holds a colon
is bound through a link to it in the runtime's `binds/`, named by the location's hash.

### 3.3 The agent's shell — `data/assistant/tools/AssistantShell`

- **Where:** per chat, the guest directory the last command ended in, kept in memory. It starts
  at the workspace's top, goes back there if it lies outside the workspace or no longer exists,
  and the result says so.
- **How:** `/bin/bash -c` with the login environment, plus `PAGER=cat`, `GIT_PAGER=cat`,
  `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`, `NO_COLOR=1`, `TERM=dumb`. The script
  runs `cd` to the directory, sets an `EXIT` trap that writes `pwd -P` to a file of its own in
  the guest's `/tmp`, then runs the command with stdin closed.
- **Result:** the first line is "Exit code N (meaning)" — or, at the time limit, "Stopped after N s,
  still running: it ended with everything it started. Run a server, a watcher or anything that
  must keep running with start_process." — then "in /workspace/app/backend", any note (the
  directory it went back from, a profile that could not be read), the PATH searched for 127, the
  output (its start and end within 32 000 characters), then stderr.

### 3.4 Managed processes — `data/runtime/RuntimeProcesses`

- `start(workspace, directory, command, name)`: refused past 8 running. Runs `/bin/bash -c` with
  `exec 2>&1` and the command, in the directory and with the login environment, in a PRoot with
  `--kill-on-exit` bound to its workspace; stdin stays open. A process is its command: what the
  command leaves running in the background ends with it.
- Output: a reader decodes UTF-8 across reads, removes ANSI escapes, and keeps the last 256 KB with
  a running offset, so a reader asking "since N" learns what it missed. The URLs it prints give
  the ports.
- `stop(id)`: SIGTERM to its process group, which PRoot also passes on to every process it runs,
  killing those still running two seconds later (patch 0002); then SIGKILL after 3 s if PRoot is
  still running, which ends it and, through `PTRACE_O_EXITKILL`, everything it runs.
  `write(id, text)` writes to its stdin. An ended process stays listed until forgotten.
- State reaches the snapshot as `ICodeSnapshot.processes` when it changes — started, a port
  found, ended — not its output (§6 P2).
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

On the Assistant page, a row above the composer while any process is listed — "2 running ·
1 ended" — in every state of the page, so the notification's tap always leads to a way to stop
them. It opens a sheet listing every process, this workspace's first, another's named by its
workspace, each with:
- its name and command;
- its state ("Running · 4m", "Exited 0", "Ended by SIGKILL");
- its ports as chips that open the browser;
- its last output, in mono;
- Stop while it runs, Remove once it has ended.

The sheet says that Android may end background processes when many run. *Reworked in §6: the
sheet lists processes; their output is in the terminal.*

### 3.8 Streaming for OpenRouter

- **The turn:** `KoogTurn.strategy(stream)` builds the turn's strategy per run. For OpenRouter,
  its two requests stream (`KoogTurn.streamed`): the reply is put together from the frames
  (`KoogStream`) — the reasoning and the text from their deltas, each call as Koog's stream
  completed it — and reported.
  - Tool arguments: blank arguments become `{}`. Arguments that are not JSON reach Koog's tool
    runner, which answers the model about them (Koog's own `toMessageResponse()` would throw).
  - The prompt: the reply is appended without its reasoning.
  - How it ended: no finish reason means the stream closed early, `error` that OpenRouter's
    provider failed (F10, F11); neither reply is kept.
- **Events:** the reply, the tokens, the output limit and a content filter's stop are reported
  the same way for streamed and whole replies (`KoogTurn.Reporter`). A stream that broke or closed
  early says "The connection ended before the reply was complete — try again"; one whose
  provider failed, "The model's provider failed while writing the reply — try again".
- **Live state:** `AssistantEvents.writing(reasoning, text)` reaches `AssistantTurn.live` — the
  turn's, so it goes with the turn — at most ten times a second, read from the stream only when
  shown. The recorded reply replaces it in the same change of the page's state.
- **The page:** while live, the reasoning row is open and grows, and the answer grows below it
  as Markdown; the page keeps the end in view unless the user scrolled up to read. The recorded
  reply then replaces both, its reasoning folded.
- **The provider page:** an OpenAI-compatible provider whose address is on `openrouter.ai` says
  "Choose the OpenRouter type for OpenRouter: only it shows replies as they are written, and the
  models' reasoning".

### 3.9 The output limit

`KoogParams` for an OpenAI-compatible server adds `max_tokens` (`additionalProperties`) whenever
it sends a limit. On the wire, against a local server: `"max_completion_tokens":4000` and
`"max_tokens":4000` with a limit, neither without.

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

### 3.11 Why a turn stopped (D12)

`KoogFailures` and the engine say:

| What | What the user reads |
|---|---|
| A reply stopped by a content filter (F12) | Kept, and under it: "The provider's content filter stopped the reply" |
| A summary stopped so | "The provider's content filter stopped the summary, so the chat was not compacted" |
| A reply with only reasoning, cut short or filtered | The limit's or the filter's words, not "The model sent an empty reply" |
| A stream that broke, or closed early | "The connection ended before the reply was complete — try again" |
| OpenRouter's `finish_reason: "error"` | "The model's provider failed while writing the reply — try again" |
| A timeout (OkHttp's, Ktor's request or connect timeout) | "api.example.com did not answer in time" |
| A connection that broke under a whole reply | "The connection ended before the reply was complete — try again" — only for a failure Koog's client wrapped, never a file read |

### 3.12 Arguments that are not an object (F13)

- **Read whole:** iCode's OpenAI client (`KoogClients`' `ReadingOpenAIClient`, for OpenAI and
  OpenAI-compatible servers) passes each whole reply through `KoogArguments.readableResponse`
  before Koog reads it: a call whose arguments are not a JSON object gets an object that carries
  them, `{"icode_unreadable_arguments": "…"}`. Nothing else in the reply changes; a reply with
  nothing to change is read as it came. A streamed call's arguments go the same way (`KoogStream`).
- **Refused:** `KoogTool` refuses such a call before it runs or asks the user: "The call did not
  run: its arguments were not a complete JSON object (N characters, ending “…”). A long call is
  cut at the output limit: write long content in parts — a file with write_file, then a short
  command that runs it." The turn goes on, the reply's text and other calls kept.
- **Kept and sent:** the page and the chat keep what the model wrote; the step is named by its
  tool. Every request — within the turn and in later ones — sends such a call as the carrying
  object, so no provider is sent broken JSON, and Koog's Anthropic, Google and Ollama clients,
  which parse a call's arguments to send it, never throw on one.
- **A reply that cannot be read at all** says "The provider's reply could not be read — try
  again", never a parser's words.

## 4. Cycles

| # | Step | Verified by | Done |
|---|---|---|---|
| F0 | This plan; the findings point to it | Review | `dd09138` |
| F1 | Compatibility removed (D10) | Type-check; tests updated, none reading old data | `dd09138` |
| F2 | `GuestEnvironment`, launch scope, `AssistantShell`, diagnostics, instructions (D2, D4, D7) | Unit tests for the parser, filter, fingerprint, exit statuses and directory rule | `25bdf78` |
| F3 | `RuntimeProcesses`, the tools, background work, workspace usage, language servers (D1, D3, D5) | Unit tests over a fake spawner: output ring, offsets, ports, limits, stop | `25bdf78` |
| F4 | The Processes sheet (D6) | Compose type-check, UI guard | `75df794` |
| F5 | Streaming for OpenRouter, live page, OpenRouter hint, output limit, why a turn stopped (D8, D9, D12) | Engine tests with a streaming fake client; params test; Koog's real clients against a local server | `800f661` |
| F6 | Architecture, PRD, user stories, plans as built; guards; API-30 check; commit | Review | `bfec8c8` |
| F7 | Arguments that are not an object (F13, §3.12), from the phone's "Unexpected EOF at path: $" | The failure reproduced against Koog's real client; unit tests; the real client over HTTP with the phone's cut reply | this commit |

## 5. Record (2026-10-02)

### 5.1 Checked here

- **Tests.** 638 `data/` and `domain/` tests run (after F7); one fails, `TreeSizeTest`'s unreadable
  directory, as before every cycle: the container runs as root, which reads any directory. The
  new ones cover the profile's parser, cache and failures; the output ring (escapes cut across
  writes, carriage returns, offsets, the line cap); the processes over a fake spawner (ports, the
  limit, SIGTERM then SIGKILL, input, forgetting, a workspace's rename and delete); the shell (the
  directory kept per chat, its return to the top, words for exit statuses, the time limit, the
  PATH searched); the process tools; the stream's assembly; the streamed turn, its live writing,
  its echo without reasoning, a stream that broke, closed early or failed; the content filter's
  words for each provider; the live entries; the provider hint; the page's live state replaced
  by the recorded reply. The streaming and coordinator tests passed five runs in a row.
- **On the wire.** Koog's real OpenAI client against a local server: the compatible request
  carries both limits (§3.9). Koog's real OpenRouter client against a local server speaking
  OpenRouter's stream: the reasoning and the answer reach the page as written, a call split over
  three chunks runs, the usage comes from the last chunk, the next request carries the reply
  without its reasoning and the call's result; OpenRouter's in-band error chunk and a stream
  closed without a finish reason each end the turn with their words, nothing recorded.
- **The phone's failure.** Koog's own OpenAI client, given a whole reply whose `run_command`
  arguments were cut inside their string, threw `JsonDecodingException: Unexpected EOF at path: $`
  — the phone's words. Through iCode's client the same reply is read: the call is refused with
  why, the reply kept, the next request sent the call as an object, and the turn finished.
- **Builds and guards.** The backend and the Assistant's screens type-check (the screens against
  Compose's desktop jars); the backend and UI guards pass; every class, backend and UI, is checked
  against Android 11's API (minSdk 30) — none reaches past it.

### 5.2 Not checked here

- A PRoot process on a phone: the container has no Android. What rests on PRoot's and Android's
  behavior — the bind of one workspace, the process group's signals, kill-on-exit, a port opened
  in the browser — rests on their sources and documentation (§1), not on a run.
- OpenRouter itself: its documentation is closed to this container (its egress policy), so F10's
  sources were read through search, and the stream's shape was taken from them and Koog's types.

### 5.3 The device pass

1. A FastAPI or Vite project: `start_process` starts its server, the result names the port and
   says it answers, the Processes sheet's port opens the page in the browser, Stop ends it.
2. A command that `cd`s into a folder, then one that runs there; `nvm`'s Node found by a command
   with nothing set by hand.
3. OpenRouter with a reasoning model: the reasoning and the answer appear as written; the
   recorded reply folds its reasoning.
4. A compatible server with an Output Limit: the limit holds.

## 6. The Processes sheet lists processes; their output is in the terminal (owner, 2026-10-02)

The owner, on the phone: the sheet should show the processes, not their logs; to see what one
prints, go to the terminal — a clean separation of the two. Found with it: uvicorn's sheet named
three ports, `8000` and two of its *clients'* (`INFO: 127.0.0.1:50744 - "GET …"`): §3.4's rule took
every `host:port` in the output.

| Step | What |
|---|---|
| P1 | A port is a server's only where its output names it as one: a URL (`http://0.0.0.0:8000`, `ws://…`), or a bare address on a line that says it listens (`Listening on 0.0.0.0:8000`). An access log's client address is neither |
| P2 | Each process's output, as it came — colours and all — is written to a log in the runtime, `/tmp/icode/processes/<id>.log`, which every terminal sees (§3.2 binds no `/tmp`); past 2 MB it moves to `<id>.log.1` and a new one starts. Deleted when the process leaves the list, and all of them when iCode starts (IDs start again at `p1`). The in-memory output stays: it is what the assistant reads. The snapshot no longer carries output, so it is published when a process's state or ports change, not four times a second while a server logs |
| P3 | **Output in the terminal**: a tab of its own per process, named after it, whose program is `tail -F` of its log — the last screenfuls (the terminal's scrollback) and everything after, as printed, with the terminal's scrollback, selection and copy. It opens where the process runs, in its workspace (a switch asks as any does). A second open selects the same tab while it follows. Ctrl-C or × closes the view, never the process; its ending is not announced, and it is not a "lost session" should Android end iCode. What a session is looking at (`TerminalAttention`) leaves `TerminalSessionManager` to make room |
| P4 | **The sheet**: each process one compact row — its state's dot, its name, "Running · 5m"; its command on one line; its ports, each opening the browser; Stop (or Remove, once ended) at its end. A tap on the row is its output in the terminal. This workspace's first, then another's, named by it. No output, no hint paragraph: the limit Android sets is said in one line under the list |
| P5 | Gate, device: uvicorn's sheet names only `8000`; its row opens a tab that follows its log; Ctrl-C closes it and the server runs on |

### 6.1 Record (2026-10-02)

- P1: `ProcessRules.ports` reads whole lines — a URL anywhere, a bare address only where the line
  says it listens; `RuntimeProcesses` scans the lines that ended, so an address cut between two
  reads is found once its line ends. Tests: the phone's uvicorn request log names no port; a URL cut
  across two reads is found.
- P2: `ProcessLogs` (`/tmp/icode/processes/<id>.log`, 2 MB, then `.1`); created as the process
  starts, removed when it leaves the list or its workspace is deleted, all cleared when iCode starts
  — creating nothing, as the runtime may not be installed. `ManagedProcess.tail` is gone and output
  alone publishes nothing (a test holds it).
- P3: `TerminalLaunch.following` — `/usr/bin/tail -n <scrollback> -F <log>` (present in the phone's
  runtime) — and `TerminalSessions.follow`, which selects the tab already following a process or
  opens one titled by its name. Such a tab closes quietly on its program's end and is not recorded
  as a lost session. `TerminalCoordinator.showOutput` opens it, then shows it as a notification's
  tap does (its workspace, the panel, that tab); `ShowProcessOutput` is the intent.
  `TerminalAttention` holds what the user is looking at, out of `TerminalSessionManager` (298 lines).
- P4: `AssistantProcessesSheet` — one compact row per process (dot, name, state; command; ports;
  Stop or Remove); a tap shows its output and closes the sheet; one line on Android's limit.
- Gate: 736 tests; lint 0 errors / 18 warnings; both guards pass. Device: P5, below.
- P4, redrawn (owner: "the list design is not good", then "no transparent design"): the rows are
  one raised group, divided within, as a settings group — the dot on the name's line, the state at the row's end, the command and the ports from
  where the name starts, Stop or Remove at the end; a port link starts where the text does.
- Found on the phone (owner, the notification drawer): each output tab was a "terminal session"
  row — "fastapi-uvicorn · Tests · running libproot.so", Exit, Acquire wakelock — beside "2
  processes running", whose bar swept and whose button said Cancel; and iCode was two cards. Now a
  session's state says what it follows (`TerminalSessionState.follows`), and the foreground service
  counts only shells: the process is its work's row already. The processes' row has no bar
  (`BackgroundItem.progresses`) and says Stop All (`cancelLabel`). The work's progress rows join the
  service's group under its summary — one iCode card — and the summary names the work by its own
  words ("1 session · 2 processes running"), not "tasks". Failures and questions still pop up alone.

