# Runtime foundation — findings from a real project (2026-10-01)

A real project ("fastapi-demo") was cloned, configured, tested and run through iCode's assistant
on the owner's phone, to find where the development environment is too weak for normal work. It
ran in the end; along the way it hit eight problems. This document records them, and for each
says what iCode's code shows about its cause, which layer owns it, and what is still unmeasured.

It **decides no implementation**. iCode must not grow support for FastAPI, Python, `uv` or any one
project: what a developer or an agent can configure stays theirs. Only limits that sit below
every project — in iCode's process model, its runtime or Android — are iCode's to fix, at the
level they occur. §5 lists the decisions those fixes need; §6 the measurements to take on a
phone before any of them.

## 1. At a glance

| # | Finding | Cause, as the code shows it | Layer | Confidence |
|---|---|---|---|---|
| 1 | Background processes die between commands | Each command is its own PRoot, run with `--kill-on-exit`; no tracee can outlive its PRoot | iCode's process model | Confirmed in code; reported from a phone (§7) |
| 2 | Commands are not interactive | `run_command` closes stdin (`</dev/null`), has no PTY, and keeps no shell between calls | iCode's process model | Confirmed in code |
| 3 | PATH, directory and environment do not carry over | Each command is a fresh, non-login `/bin/sh` (dash) at the workspace's top; the terminal is a login bash | iCode's execution context | Confirmed in code |
| 4 | Exit codes such as 127 and 143 explain little | The tool returns the raw status and stderr; it never names a signal, the directory or the PATH it ran with | iCode's tool contract | Confirmed in code |
| 5 | PRoot is not native Linux | Fake root (`-0`), no init, ptrace-based path translation, emulated hard links | PRoot, Android | Known; details to measure |
| 6 | Filesystem rules differ between operations | File tools are confined to the workspace; a command can write anywhere in the guest, other workspaces included | iCode's boundary model | Confirmed in code |
| 7 | `ss` and `netstat` missing | Not in `ubuntu-base`; and Android may deny what they read even once installed | Runtime image, Android | Absence reported from a phone; denial to measure |
| 8 | A running server is not reachable as a preview | Finding 1 ends it; nothing in iCode knows a port is listening; no preview exists | iCode (lifetime, discovery, UI) | Confirmed in code; loopback reported inside the runtime, another app's view to measure |
| 9 | The project needed its own setup | Newer Python, `uv`, dependencies, variables, one dependency left out | The project | Not a platform problem (§4) |

## 2. The findings

### 2.1 Process lifetime

**Observed.** A server started in the background ran during the command that started it and was
gone by the next.

**What the code shows.** Every assistant command runs through `GuestCommand.run`
(`data/runtime/GuestCommand.kt`). That starts a new PRoot for this command alone,
`prootCommand(..., killOnExit = true)`. Two facts then make survival impossible, whatever the
command does:

- `--kill-on-exit`: when the initial tracee (`/bin/sh -c`) exits, PRoot sends every remaining
  tracee SIGHUP, then SIGKILL two seconds later (`third_party/patches/proot/0002`).
- `PTRACE_O_EXITKILL` (the same patch): no tracee outlives its PRoot. Without it, a killed PRoot
  left its tracees running untraced, outside the guest's file system.

So `&`, `nohup`, `setsid` and `disown` cannot help. `nohup` survives the SIGHUP but not the
SIGKILL. A process started by `setsid` is still a tracee. A process that keeps stdout open also
keeps the call waiting until its timeout, and then the watchdog's SIGKILL ends PRoot and
everything in it.

iCode has **three separate process lifecycles**, each built for one feature:

| Kind | Where | Lifetime | Identity, state, stop |
|---|---|---|---|
| One-shot command | `GuestCommand` | The command; `--kill-on-exit` | None; cancellation kills |
| Terminal session | `TerminalSessionManager`, `TerminalLaunch` | The tab; `--kill-on-exit` | Tab ID; the foreground service keeps it |
| Language server | `LanguageServiceProcessManager` | Until stopped; no `--kill-on-exit` | ID, status, exit tail; TERM, then KILL after 2 s |

Architecture §10.5 specifies **one** process API, with stable process and session identifiers,
structured lifecycle events, PTY operations, and foreground-service ownership of long-running
work. What exists is three private ones, and only the language-server one keeps a process
running independently of a screen or a call. Neither the user nor the agent can start such a
process.

**Also at this layer: Android.** From Android 12, the system kills an app's child processes —
PRoot and every tracee count — when all apps together exceed 32 of them, or when they use a lot
of CPU while the app is in the background ("phantom processes"). Even a process model that
outlives calls is subject to this. iCode's documents do not mention it yet (§6 P5).

### 2.2 Interactive processes

**Observed.** No stdin, no answering prompts, no SSH password, no persistent shell, no job
control. `disown` was missing.

**What the code shows.** `RunCommandTool` (`data/assistant/tools/RuntimeTools.kt`) wraps the
command as `/bin/sh -c "{ command\n} </dev/null"`. That is deliberate: a command that reads its
input ends instead of waiting forever. It has no PTY, so programs that test for a terminal behave
non-interactively. It is `/bin/sh`, which is dash, so bash's job control and `disown` do not
exist. And there is nothing to type into later, because the process is gone (§2.1).

The terminal has all of this, through Termux's PTY, but only for a person. The agent has no
session to read from or write to.

### 2.3 Execution environment

**Observed.** Installed executables were not always on PATH. Relative paths failed from another
directory. Setup had to be repeated in each command.

**What the code shows.** There are two contexts:

| | Terminal | Assistant command | Language server |
|---|---|---|---|
| Shell | `/bin/bash -l`: `/etc/profile`, `~/.profile`, `~/.bashrc` read | `/bin/sh -c`: nothing read | `/usr/bin/env`: nothing read |
| PATH | Fixed (`GUEST_PATH`), then whatever the profile adds | `GUEST_PATH` only | `GUEST_PATH` only |
| Directory | Kept by the shell (`cd` lasts) | The workspace's top, every call | The workspace's top |
| `export`, `source .venv/bin/activate` | Last for the session | Last for one call | — |

`GUEST_PATH` (`RuntimeLayout`) already puts `~/.local/bin` first, where pip, pipx and `uv`
install. A tool that adds itself through a shell profile is found in the terminal but not by the
assistant or a language server. Examples are `cargo`, `nvm`, `pyenv`, an SDK's `env` script, and
a virtual environment's activation. Nothing carries a directory or a variable from one call to
the next.

### 2.4 Diagnostics

**Observed.** Failures arrived as 127 or 143, which do not say whether a program was missing,
the path or directory was wrong, permission was denied, or a signal ended it.

**What the code shows.** `RunCommandTool` answers "Exit code N", the output and stderr, and
nothing more. Its stderr usually holds dash's `sh: 1: x: not found` for 127. It does not hold:

- that a status of 128+n means signal n (143 is SIGTERM, 137 SIGKILL, 129 SIGHUP);
- which signals iCode and PRoot themselves send. A timeout sends SIGKILL to PRoot (137). PRoot
  ends leftover processes with SIGHUP, then SIGKILL after two seconds. Closing a terminal tab
  sends SIGHUP. iCode sends SIGTERM only when it stops a language server, so a 143 came from
  inside the guest, from a `kill` or a tool;
- the directory and PATH it ran with, which explain most of finding 3's failures.

The tool's description tells the model "/bin/sh, no root, no systemd, nothing typed in, stopped
after timeout_seconds". It does not say that background processes end with the command, or that
each call starts afresh, so the model tried `&` and `disown`.

### 2.5 Linux compatibility under PRoot

**Observed.** A hard link made by `uv` failed with "Operation not permitted"; package management
was slow or unreliable; `uid=0` without root's powers; no systemd; some native builds may not
work unchanged.

**What is known.**

- `uid=0` is PRoot's `-0`, which reports root so package managers run. The kernel still sees an
  ordinary Android app.
- There is no init and no systemd: PRoot runs commands, not a booted system. A service manager,
  if one is wanted, has to be iCode's (§5 D1).
- Every system call that names a path stops in PRoot (ptrace), which is why package managers,
  which make many such calls, are slow. Seccomp lets the others run natively
  (`RuntimeLayout.prootEnvironment`).
- Android refuses `link()`. iCode already emulates hard links (`--link2symlink`, patched by
  `0001` so that a failed link leaves the file intact), in the root filesystem and the workspaces
  alike. **Why `uv`'s link still failed is not known.** A manual `ln` worked, so the cause is the
  particular call `uv` makes. It could be a link to an already-emulated link, a link across the
  `/workspace` bind, a directory descriptor form, or a reflink tried first. §6 P4 measures it.
  `UV_LINK_MODE=copy` is the project-level way around it, not the platform's answer.

### 2.6 Filesystem boundaries

**Observed.** iCode's write tool refused a path outside the workspace. A shell command wrote to
`/tmp`. A later manual link test behaved differently from `uv`.

**What the code shows.**

- The assistant's file tools resolve every path inside the workspace (`AssistantWorkspace`),
  which PR-ASSIST-04 requires.
- Commands are not confined. They run in the whole guest: `/tmp`, `/root`, apt's trees, and every
  other workspace, because all of them are bound at `/workspace` in every launch
  (`RuntimeLayout.prootCommand`).
- Architecture §7.2 says PRoot is not a security boundary, and approval of each command
  (PR-ASSIST-05) is what stands in for one.

Both rules are intended. What is missing is that neither is stated where it is met. The model is
not told that its file tools stop at the workspace while its commands do not, and the user is not
told what an approved command can reach.

### 2.7 Runtime tools

**Observed.** `ss` and `netstat` were unavailable.

**What is known.** The runtime is `ubuntu-base` (`runtime/bootstrap/README.md`), deliberately
minimal. `iproute2` and `net-tools` are not in it, and not among the runtime components
(`RuntimeComponents`). Installing them may not be enough. Since Android 10, an app targeting
API 29 or later cannot read `/proc/net`, and iCode targets 36. `netstat` reads `/proc/net`, and
`ss` asks netlink's socket diagnostics, which Android may deny as well. If both are denied,
inspecting sockets from inside the runtime cannot work on Android at all, and any "what is
listening" answer has to come from what iCode itself started. §6 P2 measures it.

### 2.8 Reaching a server from Android

**Observed.** The application ran inside the runtime but was not reachable as a preview from the
phone; nothing exposed the port.

**What the code shows.**

- PRoot does not create a network namespace. A server in the runtime listens in iCode's own
  network stack, and Android apps share loopback, so `http://127.0.0.1:8000` should already open
  in the phone's browser while the server runs. §6 P3 confirms it.
- In this test the server had already ended (§2.1).
- Beyond that, iCode knows nothing of listening ports. A URL in terminal output can be tapped and
  opens the external browser (`TerminalMoreMenu` → `OpenExternalUrl`). There is no list of
  running services, no port discovery, and no in-app preview.
- iCode's network security config allows plain `http://` to `localhost` and `127.0.0.1`, so an
  in-app preview would be permitted.
- Any app on the phone can reach a server bound to `127.0.0.1`. That is worth stating wherever a
  preview is offered.

## 3. What they reduce to

| Gap | Findings | In one sentence |
|---|---|---|
| G1 — no shared process model | 1, 2, 4, 8 | Architecture §10.5's one process API was never built: three private lifecycles exist, and only language servers may outlive a call or a screen |
| G2 — two execution contexts | 3, 4 | The terminal is a login bash with a lasting directory and environment; commands and language servers are fresh, profile-less shells |
| G3 — rules and facts not stated where met | 2, 4, 5, 6 | Confinement, lifetime, signals and PRoot's limits are true but unsaid to the model and the user |
| G4 — Android's limits unmeasured | 1, 5, 7, 8 | Phantom-process limits, `/proc/net` and netlink, `uv`'s link and cross-app loopback have not been measured on a device |
| G5 — services invisible | 7, 8 | Nothing in iCode knows that a process listens on a port, so nothing can show or open it |

G1 and G2 are the foundation: most of the rest is either caused by them or can only be solved on
top of them.

## 4. What is not a platform problem

The project needed a newer Python than Ubuntu 24.04's, `uv`, its dependencies, environment
variables, configuration, and one development dependency left out. That is the project's own
setup, and the user or the agent did it. It becomes iCode's concern only where the runtime
stopped normal setup from working. Here that was `uv`'s hard link (§2.5), and the environment not
carrying between commands (§2.3). No language-, framework- or package-manager-specific behavior
follows from this test.

## 5. Decisions for the owner

None of these is taken. Each names the question and the honest options. The recommendation is
only an order to take them in: measure first (§6), then D1 and D2, then the rest on top.

| # | Question | Options |
|---|---|---|
| D1 | What owns a process that must outlive the call or screen that started it? | (a) A runtime process service — §10.5 built: long-lived processes with IDs, output, state and stop, shared by the terminal, the agent and features such as language servers, kept by the foreground service. (b) Terminal tabs only: the agent opens and reads tabs as the user does. (c) Keep today's three lifecycles and add a fourth for the agent |
| D2 | What do commands run in? | (a) The terminal's context: a login shell's profile for every command. (b) A session per chat or per workspace that keeps directory and environment between commands. (c) Stateless, but with a per-workspace environment file both read |
| D3 | Can the agent interact with a process? | (a) No: say so plainly in the tool contract. (b) Write to a process's stdin and read its output by ID. (c) A PTY session the agent reads and types into, as the user does |
| D4 | What is the agent's reach? | (a) As now, stated: file tools confined to the workspace, approved commands reach the guest. (b) Commands confined as well (a working directory and binds per workspace) — at the cost of `/root`, caches and apt |
| D5 | Which diagnostic tools belong to the runtime? | Image, a component, or the user's own apt. Only after P2 says whether they can work at all |
| D6 | What does "preview" mean? | Discovery (from the processes iCode started, or a probe of a port), then the external browser, an in-app view, or both |

### 5.1 Proposals from iCode's assistant (2026-10-01) — not decisions

The owner forwarded proposals for D1–D4. They were written by iCode's own assistant (`minimax/minimax-m3:free`,
through an OpenAI-compatible provider) on the owner's phone, not by the owner. The reply stopped in the middle of
D3 (§7.1). They were input only. **The decisions are taken in
[runtime-foundation-plan.md](./runtime-foundation-plan.md)**, on researched facts.

- **D1 — one process service, shared by everyone.** Build architecture §10.5's single process
  API: stable IDs, lifecycle events, a stop API, and one place that records processes and applies
  one set of lifetime rules. Its consumers would be:
  - terminal tabs, with a PTY, hung up when the tab closes;
  - the assistant's tools, which start a process, read its output, write to it and stop it, all
    by ID, without attaching to a shell;
  - the language-server manager, folded in.
- **D2 — the terminal's login shell, plus a per-workspace environment file.**
  - The terminal is the source of truth for shell state: its directory, its exported variables,
    its activated virtual environment. Assistant commands and language servers start from a
    snapshot of it.
  - What must be reproducible goes in a per-workspace environment file, written by the user and
    never by the agent, layered on top of that snapshot.
  - Questions any plan must answer first:
    - Which terminal, when a workspace has several tabs or none?
    - How is the state captured? Variables exported after a shell starts cannot be read from
      outside it (`/proc/<pid>/environ` holds the environment it started with), so the shell has
      to record them itself — iCode's prompt script could, for bash.
    - Does the agent's own `cd` last between its commands? The relative-path failures came from
      its own `cd` being lost.
    - Which variables reach the agent? A token exported in the terminal would reach the model's
      provider through command output.
- **D3 — reading output and writing to stdin, by process ID; no attaching to a shell.** It began
  "three tiers, one per tool, with start-and-get-an-ID as the default", and was cut off there.
- **D4** — the reply ended before it.

## 6. Measurements to take on a phone

These need a device: this environment cannot run iCode. Each says what the result decides.

| # | Run | Tells |
|---|---|---|
| P1 | In the assistant, `sleep 300 & echo $!`; in the next command, `ps -p <pid>`. In a terminal tab, the same, with the tab kept open | That lifetime ends with the call (expected, §2.1) and survives in a tab |
| P2 | `cat /proc/net/tcp`; then `sudo apt install iproute2 net-tools` and `ss -ltn`, `netstat -ltn` | Whether socket inspection can work in the runtime at all (D5, D6) |
| P3 | In a terminal tab, `python3 -m http.server 8000 --bind 127.0.0.1`; open `http://127.0.0.1:8000` in the phone's browser | That loopback is shared across apps, so a preview needs no forwarding (D6) |
| P4 | `ln a b` in `/tmp`, in `/root`, in `/workspace/<w>`, and from `/root/.cache` to `/workspace/<w>`; `uv -v pip install <pkg>` with `UV_LINK_MODE=hardlink`, reading which file and call fail | Which link forms the emulation refuses (§2.5) |
| P5 | The Android version; with `adb`, `logcat` for "PhantomProcessRecord" while many processes run and while iCode is in the background | Whether Android's child-process limits end runtime processes in practice (D1) |
| P6 | `bash -lc 'echo $PATH'` against `sh -c 'echo $PATH'` in the assistant, after installing a tool that edits `~/.bashrc` | The size of the context gap (D2) |

## 7. Results (2026-10-01)

Run and reported by iCode's assistant (`minimax/minimax-m3:free`, through an OpenAI-compatible
provider) on the owner's phone, and forwarded by the owner. They are the model's own account of commands it ran; none was
checked independently. Each agrees with what the code predicts.

| # | Result | What it settles | What it leaves open |
|---|---|---|---|
| P1 | uvicorn, started in one command, answered 200 in that command. In the next command, `curl` got no connection (`000`) and `ps` showed no uvicorn. `nohup`, `setsid` and `&` made no difference | §2.1 exactly as the code says: a process ends with the command that started it, and nothing inside the guest can escape that | The terminal-tab half: a process there is expected to last as long as the tab |
| P4 | `os.link` works in the guest — cache to virtual environment, and in `/tmp` — on one filesystem (same `st_dev` and `f_fsid`). `uv` failed with `Operation not permitted (os error 1)` on a hard link | Plain hard links work as the guest sees them. A `link()` succeeding in the guest shows that iCode's emulation (`--link2symlink`) handles it, not that Android accepts real hard links — it does not, which is why the emulation exists. The failure belongs to the particular call or sequence `uv` makes | Which call that is: `uv -v`, or `RUST_LOG=uv=trace`, names the failing file and operation. Until it is known, it counts as a gap in the emulation rather than in `uv` alone, since other tools that link out of a cache (pnpm's store, for one) may make the same call. Meanwhile `UV_LINK_MODE=copy` is the project's workaround, and needs nothing from iCode |
| P2, in part | `ss`, `netstat` and `lsof` are not in the image | §2.7's absence | Whether they could work once installed. `cat /proc/net/tcp` decides that, and it also shapes D6: if it is readable, iCode can tell which of its processes holds which port; if not, iCode knows only what it started and has to find ports another way |
| P3, in part | Inside the runtime, `curl http://127.0.0.1:8000/v1/health` answered 200 while the server ran | The server serves on loopback. A preview inside iCode — its own process, its own network stack — reaches it with no forwarding. Once processes can outlive a command (D1), that preview needs no tunnel | Another app — the phone's browser — reaching it: expected, since Android apps share loopback, but not yet measured |
| P5 | Not measurable without a device running iCode with many processes, and `adb` | — | Still open; it bears on D1 |
| P6 | Not run | — | — |

The decisions are in [runtime-foundation-plan.md](./runtime-foundation-plan.md).

### 7.1 A finding of its own: a reply cut short looked finished

The assistant's reply carrying the proposals stopped mid-sentence ("…get back a `process_id`.
Tools"), and iCode showed it as a finished answer, with Copy under it and no notice. iCode never
read why a reply ended. Koog passes each provider's own reason through (`finishReason`):
`length` (OpenAI, OpenRouter, OpenAI-compatible servers), `max_tokens` (Anthropic), `MAX_TOKENS`
(Gemini); Koog's Ollama client gives none.

The model, `minimax/minimax-m3:free`, reached through an OpenAI-compatible provider, is not in
Koog's catalogue and had no Output Limit set. So iCode sent no limit, and the server's own
default applied. That the reply ended at that default is the likely reading, not a measured one.

An Output Limit set for such a model goes out as `max_completion_tokens`: Koog's OpenAI client,
which serves OpenAI-compatible servers, sends OpenAI's current name for it. A server that reads
only the older `max_tokens` ignores it, so on such a server the setting changes nothing.

Fixed (2026-10-01):
- A turn whose last reply ended at the output limit keeps the reply and says under it: "The reply
  was cut short at the model's output limit — ask it to continue". For a model iCode does not
  know, it adds "or set a higher Output Limit for <model> in its provider's settings".
- A summary cut short no longer compacts the chat. A part summary would have replaced it.
