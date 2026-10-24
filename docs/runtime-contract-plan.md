# Runtime Contract — one Linux environment, no iCode-made limits

Status: **in progress.** RC2–RC6 built (2026-10-24); RC7 planned. D1–D3 passed on the owner's phone (§9.3). The cycles are §7, the cleanup is §8, the checks are §9.

The owner's problem statement (2026-10-23), in short:
- iCode is a native Android IDE around a genuine, user-controlled Linux environment.
- The terminal, the Explorer and the assistant are ways into that one environment.
- iCode must not override user configuration, split execution contexts silently, narrow the filesystem, or blur Linux behaviour with its own policy.

Its success criteria are §9.1's acceptance list.

**The rule for every change here (owner, 2026-10-23): remove the code that limits; never add a second layer of limits.** Where a limit is removed, new code goes in only if the capability cannot work correctly without it (§4.2 is the one such piece). The assistant asks the user before every change and command. Those approvals are its gate, and nothing else is added in their place.

The owner chose (2026-10-23):

| Question | Choice |
|---|---|
| Assistant reach | **The terminal's reach, with approvals as its only gate.** Every context sees `/workspace` whole. The assistant's file tools take any path in the runtime. No iCode fence remains: not the workspace, not `.git`, not a time or process cap of iCode's making |
| Terminal shell | **The login shell from `/etc/passwd`**, always started as a login shell (`chsh` works as on Linux). Terminal › Shell is removed |
| iCode's prompt | **A default for new runtimes only.** A fresh home's `~/.bashrc` gets one line sourcing `/etc/icode/prompt.sh`. iCode never writes the user's dotfiles again |

## 1. What stands today (verified)

Each fact was checked in the code, and where it is behaviour, reproduced:
- **PRoot:** built here from the pinned submodule commit `7266fb3` with iCode's three patches, running a test root filesystem.
- **bash:** with Ubuntu's own `/root/.profile` and `/root/.bashrc`, the files the `ubuntu-base-24.04.5` image ships.
- **Starship:** its real `starship.bash` init, behind a stub binary.

### 1.1 The runtime

| # | Fact | Evidence | Consequence |
|---|---|---|---|
| F1 | Every terminal start rewrites `~/.icode-prompt.sh` and appends `. /root/.icode-prompt.sh` to `~/.bashrc` **and** `~/.profile` when the file lacks the name; a line the user deletes comes back at the next start | `ShellPrompt.install()` (`ShellPrompt.kt:20-34`), called by `TerminalLaunch.prepare` (`TerminalLaunch.kt:37`) | iCode rewrites the user's dotfiles |
| F2 | Ubuntu's `/root/.profile` sources `~/.bashrc` first; iCode's line at the end of `~/.profile` then runs after everything in `~/.bashrc`, and sets `PROMPT_COMMAND=__icode_prompt` outright | Reproduced: with `eval "$(starship init bash)"` last in `~/.bashrc`, `bash -l -i` ends with `PROMPT_COMMAND=__icode_prompt`; plain `bash -i` keeps `starship_precmd` | **Starship is overridden in every terminal tab** (the tab runs `bash -l`). Any prompt tool is |
| F3 | Only the exact path `/bin/bash` is started as a login shell; any other shell path, `/usr/bin/bash` included, skips the profile. The shell comes from a Terminal setting, never from `/etc/passwd` | `TerminalLaunch.shellCommand` (`:81-83`); `TerminalSettings.shellPath` (`AppSettings.kt:167`) | `chsh` does nothing; `~/.profile` is lost for other shells |
| F4 | PRoot hands its whole launch environment to the guest | Reproduced: the guest's `env` shows `LD_LIBRARY_PATH` (the app's native-library path), `PROOT_TMP_DIR` (a host path), `PROOT_VERBOSE`, `GLIBC_TUNABLES` | Host-side plumbing appears in the user's shell. A user's `LD_LIBRARY_PATH` or `LD_PRELOAD` would break PRoot's own start, so `GuestEnvironment` drops them from the profile for every non-terminal context (`GuestEnvironment.kt:79-82`). Terminal and assistant then differ |
| F5 | No launch sets `USER`, `LOGNAME` or `SHELL`; bash sets `SHELL` for itself without exporting it | `RuntimeLayout.prootEnvironment` (`:134-160`); reproduced: `env -i … bash -l` exports none of the three | Programs started from the terminal see no user or shell, unlike after a real login |
| F6 | Environments by context. Terminal: the fixed launch set (`TERM=xterm-256color`), then the shell reads the profile itself. Assistant commands and `start_process`: the profile snapshot plus `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`, `NO_COLOR=1`, `TERM=dumb`. Run tab and Build & Run: the snapshot with `TERM=xterm-256color`. Language servers: the snapshot. **Source Control's Git, Search's ripgrep, apt, the gpg agent: the fixed set only** | `AssistantShell.COMMAND_ENVIRONMENT` (`:102-109`); `RunCoordinator.kt:47,142`; `AndroidApps.kt:57`; `LanguageServiceProcessManager.kt:44-49`; `GuestCommand.kt:147-160`; `RuntimePackageInstaller.kt:53`; `GpgBridge.kt:102` | **A Git hook that needs the user's PATH (husky running node from nvm) works in the terminal and fails from Source Control.** A proxy set in `~/.bashrc` reaches the terminal's `apt` and `git fetch`, not iCode's. Two no-terminal contexts set `TERM` differently |
| F7 | The assistant's commands and processes, the Run tab and Build & Run bind **only their own workspace** at `/workspace/<name>`; the terminal, Git, search and language servers bind the whole workspace folder at `/workspace` | `RuntimeLayout.prootCommand(…, workspace)` (`:210-216`); `AssistantShell` (`:29`); `RuntimeLayout.spawn` (`RuntimeProcesses.kt:234-245`) | Two filesystem views of one `/workspace` |
| F8 | Under a one-workspace bind, the root filesystem's own `/workspace` folder shows through. Any folder there reads as an **empty** workspace, and anything written to `/workspace/<other>` lands there, hidden from the terminal and the Explorer | Reproduced: `rootfs/workspace/iCode` (empty) reads as an empty `/workspace/iCode` from another workspace's command. `echo lost > /workspace/notes.txt` from one is "No such file" in the terminal and readable again from the next command | **This explains the empty `/workspace/iCode`.** It needs a leftover folder in the root filesystem's `/workspace`. Two writers are known: the one-workspace mount points earlier versions made (workspace-storage-plan §5.5, whose cleanup was removed with the migration, runtime-foundation-plan D10), and any assistant command that wrote under `/workspace/<other>`. Which made it on the owner's phone is told by D1 (§9.3). The model is told "Only this workspace is under /workspace" (`AssistantInstructions.kt:28`) while it can see such folders |
| F9 | PRoot is not a security boundary | PRoot runs as the app's uid; every guest process can read and write what the app can through any bound path | The one-workspace bind guards nothing real. It only hides state |
| F10 | With `-0`, PRoot reports any file the app's own uid owns as root's. Every workspace is in the app's private `files/workspaces`, so Git sees it as owned by its own user | PRoot `extension/fake_id0/stat.c:43-47`. Reproduced: a repository owned by PRoot's uid passes `git status` with no `safe.directory`; one owned by another uid fails "dubious ownership" | `-c safe.directory=*` on every command (`GitCommandLine.kt:34-43`) and the `git config --global --add safe.directory '*'` iCode writes into the user's `~/.gitconfig` (`CliGitService.kt:78-91`, marker `~/.icode-git-configured`) are leftovers of the phone-storage era. Their comment still names "a media uid" |
| F11 | `/usr/local/bin/sudo` is iCode's stand-in, ahead of `/usr/bin` in `PATH` | `GuestSudo.kt:19-26`; `RuntimeLayout.GUEST_PATH` | A real `sudo` the user installs with apt is shadowed by iCode's |
| F12 | iCode writes files into the runtime outside the workspaces, and the user cannot see the list anywhere:<br>- `/etc/resolv.conf`, `/etc/hosts`, `aid_*` lines in `/etc/group`;<br>- `/etc/dpkg/dpkg.cfg.d/icode-unsafe-io`, `/etc/gitconfig` (whole file);<br>- `/usr/local/bin/sudo`, `/usr/local/bin/icode-pinentry` and `icode-gpg-agent`;<br>- `/etc/gnupg/gpg.conf` and `gpg-agent.conf`;<br>- `~/.icode-prompt.sh`, two dotfile lines, `~/.icode-git-configured`, the global `safe.directory`;<br>- `~/.icode/runtime-tools/*` receipts;<br>- with the Android SDK, `/etc/profile.d/icode-android-sdk.sh`, a block in `~/.gradle/gradle.properties` and `~/.gradle/init.d/` | `RuntimeInstaller.configureGuest` (`:184-212`), `RuntimeLayout.ensureGuestGroups`, `GuestSudo`, `GuestGpg` (`:20-36`), `ShellPrompt`, `CliGitService`, `RuntimeToolSetup`, `AndroidSdkSetup` (`:91,127-129,155-160`) | The user cannot tell iCode's policy from Linux's (statement §3.5) |
| F13 | Terminal "finished" alerts read the foreground process group from `/proc`, not the prompt | `TerminalForeground.of` | The prompt can change freely; no feature depends on it |
| F14 | iCode's own runtime commands may start only in a workspace folder | `GuestCommand.run`: "`… is not in iCode's workspaces`" (`GuestCommand.kt:147`) | No iCode feature can run a command in `~`, `/etc` or `/tmp` |

### 1.2 Fences iCode put on the assistant

None of these come from Linux, Android or PRoot.

| # | Fence | Where |
|---|---|---|
| A1 | File tools resolve every path inside the open workspace; an absolute path, `~` or a link out of it is refused, "outside this workspace" | `AssistantWorkspace.resolve` (`:21-36`) |
| A2 | No write inside a `.git` folder | `WorkspaceChangeTools.kt:34`, `AssistantWorkspace.isGitInternal` |
| A3 | `list_files` hides `.git` | `WorkspaceReadTools.kt:44` |
| A4 | `search_text` searches only the open workspace, always without `.gitignore`'d files; a recursive `list_files` is the open workspace's index only | `WorkspaceReadTools.kt:50,128` |
| A5 | A chat's directory snaps back to the workspace's top when the last command ended outside it | `AssistantShell.directory` (`:82-93`) |
| A6 | `run_command` is stopped after 600 s at most, whatever the model asks | `RunCommandTool.MAX_TIMEOUT` (`RuntimeTools.kt:147`) |
| A7 | At most 8 managed processes at once | `ProcessRules.MAX_RUNNING`, `RuntimeProcesses.kt:69-71` |
| A8 | `process_status`, `send_process_input` and `stop_process` reach only the open workspace's processes ("There is no process … in this workspace") | `ProcessTools.kt:108-123,158,190` |
| A9 | A missing path and a folder both read "is not a file" | `WorkspaceReadTools.kt:36,82` |

What the assistant keeps, because none of it limits what it can do:
- **Approvals:** every change and command is asked first, unless the user chose otherwise. Since 2026-10-24, reaching outside the chat's workspace is asked too, in every mode but Full access ([assistant-permissions-plan.md](./assistant-permissions-plan.md)): one more question, not a fence.
- **The user's modes:** Ask and Plan, chosen by the user.
- **The unsaved-edits check:** it protects the user's unsaved text, and the model is told to ask.
- **Output windows:** `read_file` pages by `start_line`; a long output keeps its head and tail, with the whole in the step's Copy; a list or search says what it left out.

### 1.3 Links, as PRoot stores them

| # | Fact | Evidence | Consequence |
|---|---|---|---|
| F15 | An ordinary link made in the runtime is stored with the runtime path it names (`ln -s /workspace/app/README.md d` stores `/workspace/app/README.md`). A hard link PRoot emulates (`--link2symlink`) is stored as a link naming the **phone's** path of its `.l2s.` file | Reproduced with PRoot built from the pinned source | iCode's own file code follows links with Java's `canonicalFile`, which reads every link as a phone path. A runtime link breaks in the assistant's file tools (`AssistantWorkspace.kt:31`) and in the editor's and Explorer's inside-the-workspace checks (`DocumentSessionCoordinator.kt:49`, `WorkspaceItemsCoordinator.kt:254-255`, `WorkspaceLibraryCoordinator.kt:283`) |

## 2. The contract

What every iCode surface holds to from now on. `/etc/icode/README.md` says it inside the runtime (§6).

1. **One filesystem, no fences.**
   - Every process iCode starts in the runtime sees the same tree: the runtime's root, and every workspace at `/workspace/<name>`.
   - The assistant reaches whatever the terminal reaches, and its changes and commands are asked of the user first.
   - The Explorer's Private place is that same `/workspace`.
2. **One environment.**
   - Every process starts from the same login environment, the variables a fresh login shell exports.
   - Each process gets **base < the user's profile < its context's own variables**, and nothing else.
   - PRoot's own variables never reach the guest.
3. **Contexts are named.** `ICODE_CONTEXT` names the context in every guest process, and `env` shows exactly what it has. A difference between two contexts is a context's own variables, or a PTY versus none.
4. **The user's files are the user's.**
   - iCode writes into the home only when it creates the home: the first install, or Reset.
   - After that it writes there only what the user asks for in iCode: Runtime Tools, Git identity, credentials.
   - Every file iCode writes in the runtime starts with `Managed by iCode:` and why. A line in a file shared with the user is marked the same way. `grep -rl 'Managed by iCode' /etc /usr/local ~` finds them all.
5. **User choices win.**
   - The login shell comes from `/etc/passwd`.
   - A program the user installs is never shadowed by an iCode stand-in.
   - A setting in the user's profile reaches every context. A context's own variables win only where there is no terminal to be honest about (`TERM=dumb`) or no person to answer a prompt.
6. **The real limits are said once.** `/etc/icode/README.md` §Limits lists what Android and PRoot cannot do. The assistant's instructions point to it.

## 3. One launch, one environment

### 3.1 Host side and guest side

`RuntimeLayout` splits what is today one map:

- **`hostEnvironment()`**: only what PRoot itself needs, which never reaches the guest: `PROOT_LOADER`, `PROOT_TMP_DIR`, `PROOT_VERBOSE=-1`, `LD_LIBRARY_PATH` (bionic finds `libtalloc.so` through it).
- **Every guest program starts as `/usr/bin/env -i <variables> <argv…>`.**
  - The guest has exactly the documented variables (F4).
  - `env` replaces itself with the program, so its pid, the foreground-group reading (F13) and `--kill-on-exit` are unchanged.
  - Reproduced: in PRoot, `env -i HOME=… USER=root … /bin/bash -l` gives a shell whose `env` lists only those variables and what the profile adds.

### 3.2 The variables (`data/runtime/GuestContext.kt`: `GuestContext`, `GuestVariables`; pure, tested)

| Layer | Variables |
|---|---|
| **Base**, what a Linux login sets | `HOME=/root`, `USER=root`, `LOGNAME=root`, `SHELL=<root's shell from /etc/passwd>`, `PATH=<GUEST_PATH>`, `LANG=C.UTF-8`, `GLIBC_TUNABLES=glibc.pthread.rseq=0` (PRoot's limit), `ICODE_CONTEXT=<context>` |
| **Profile** | The login snapshot (§3.3); it overrides the base (a profile's `PATH`, `LANG`, `LD_LIBRARY_PATH`, a proxy) |
| **Context** | Below; it overrides both |

| Context (`GuestContext`) | Who | PTY | Its own variables |
|---|---|---|---|
| `terminal` | Terminal tabs | yes | `TERM=xterm-256color`, `COLORTERM=truecolor`. Starts the login shell with `-l`, which reads the profile itself (no snapshot) |
| `command` | Assistant `run_command` and `start_process`, Run tab, Build & Run, the Runtime Tools' apt | no | `TERM=dumb`, `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive` (nothing can answer a prompt); the assistant's also `NO_COLOR=1` (a model reads it) |
| `git` | Source Control, the assistant's `git_status` and `git_diff`, the gpg agent iCode starts for signing | no | As `command`: hooks, credential helpers and gpg run with the user's profile |
| `language-server` | Language services | no | As `command` |
| `search` | Search, `search_text`, a recursive `list_files` (ripgrep) | no | As `command`. ripgrep already runs with `--no-config` (`RipgrepCommand.kt:6`), so iCode's search options are the ones applied |
| `install` | Runtime install, Repair, health check, the snapshot itself | no | Base only: a broken profile must never stop the runtime from being built or repaired |

### 3.3 The login snapshot (`GuestEnvironment`, simplified)

- **Shell:** read with the user's login shell: `<shell> -l -i -c 'printf …; env -0'`, in context `install`. Today it is always `/bin/bash -lic`.
- **Dropped:** only `PWD`, `OLDPWD`, `SHLVL`, `_`, and the context-owned names. F4's exclusions (`LD_LIBRARY_PATH`, `LD_PRELOAD`, `GLIBC_TUNABLES`, `PROOT_*`) go: those names no longer collide with PRoot's.
- **Unchanged:** the fingerprint (now also `/etc/passwd`, zsh's and fish's files, and the shell itself), the 10 s limit, and the fall-back to base. The fall-back's note (`GuestEnvironment.withoutProfile`) is in the assistant's results, as before, and now in Git's failures too (`CliGitService.CommandResult.errorMessage`).

## 4. One filesystem, no fences

### 4.1 One bind

- `prootCommand` loses its `workspace` parameter: every launch binds `files/workspaces` at `/workspace` (F7).
  - `bindable()`, its `binds/` links and their hashing go.
  - Managed processes keep their workspace as an identity only: it names the workspace in the Processes sheet and holds a rename or delete while one runs.
- `GuestCommand` starts in any runtime folder (F14).
- **Leftovers in `rootfs/workspace`** are hidden from every view from now on. No code removes them, by the no-compatibility rule (D10). Device check D1 moves anything there into a workspace *before* this build is installed.

### 4.2 `GuestPaths`: the runtime's paths, followed as PRoot follows them

This is the one new piece. Without it, removing A1 would read runtime links wrongly (F15).

- **What it does:** maps a runtime path to the file on the phone, one component at a time.
  - **Bind points:** `/workspace` → `files/workspaces`; `/dev`, `/proc`, `/sys`, `/system`, `/apex`, `/vendor`, `/product` → themselves; anything else → the root filesystem.
  - **Links:** a link naming a runtime path is followed inside the runtime. One naming a phone path under the root filesystem or the workspaces (PRoot's hard-link stand-ins) is taken as it is.
  - **Loops:** 40 links deep is a loop, as on Linux (`ELOOP`).
- **What it replaces:** `WorkspacePaths` keeps its job (a workspace's location ↔ its runtime path). `GuestPaths` wraps it and replaces every `canonicalFile` that follows a link today:
  - the assistant's file tools;
  - `DocumentSessionCoordinator.kt:49`;
  - `WorkspaceItemsCoordinator.kt:254-255`;
  - `WorkspaceLibraryCoordinator.kt:283`;
  - `WorkspaceFileSource`'s listing, folder and file checks (found in RC4): a runtime link in a workspace was hidden from the Explorer, drawn as a file when it named a folder, and could not be opened.

### 4.3 The assistant, unfenced

Each of §1.2's fences is removed. Nothing takes their place but the approvals already there.

| Fence | Becomes |
|---|---|
| A1 | File tools take any runtime path: absolute as written, `~` as `/root`, relative from the workspace's top as today. They resolve through `GuestPaths` |
| A2, A3 | `.git` is written like any folder (asked first, as every change is) and listed like any folder |
| A4 | `search_text` and a recursive `list_files` take an optional `path` (any folder; the workspace's top by default) and `ignored` (also what `.gitignore` leaves out, ripgrep's `--no-ignore`) |
| A5 | The chat's directory stays wherever its last command ended; it goes back to the workspace's top only when that folder no longer exists, and says so |
| A6 | `timeout_seconds` is the model's to set, with no ceiling (default 120 s). The turn's Stop ends any command |
| A7 | No cap. Android's own limit on apps' processes (runtime-foundation-plan F2) is in README §Limits, and a process Android kills reads "ended by SIGKILL" as today (`RuntimeProcessContract.exitMeaning`) |
| A8 | The process tools reach every managed process; `process_status` lists each with its workspace |
| A9 | Every tool says what the system says: "`x` does not exist", "`x` is a folder", "`x` is not a folder", and the system's own words for anything else (Permission denied, Read-only file system) |

**What the model is told** (`AssistantInstructions`, the tool descriptions):
- The runtime is the user's Linux, and the assistant works in it on the user's behalf.
- Every workspace is under `/workspace`. "<name>" is this chat's, and relative paths start there.
- Changes and commands are asked of the user first.
- PRoot's and Android's limits are in `/etc/icode/README.md`.

The "Only this workspace is under /workspace" sentence goes.

## 5. Shell, prompt and dotfiles

- **The terminal starts root's login shell.**
  - It is read from the runtime's `/etc/passwd` on the host side, and checked as today's `shellAvailable` checks a path: inside the runtime, a regular file.
  - It starts with `-l` (bash, zsh, fish and dash all take it).
  - If it is missing, the tab starts `/bin/bash -l`, and its first line says why: "iCode: your login shell /usr/bin/zsh (from /etc/passwd) is not installed — `apt install zsh`, or `chsh -s /bin/bash`".
- **Terminal › Shell goes:** `TerminalSettings.shellPath`, its row, its DataStore key, `TerminalSettingRules.validShellPath`.
- **The prompt:**
  - `/etc/icode/prompt.sh` is iCode's, written at install and before every terminal starts, so an app update reaches it. It holds today's function, unchanged, with the accent's colour slot `ACCENT_COLOR_INDEX`.
  - When iCode creates a home, it appends one line to `~/.bashrc` only: `. /etc/icode/prompt.sh  # Managed by iCode: its prompt — delete this line to use your own`.
  - Nothing else ever touches a dotfile. Repair keeps the home, so it writes none.
  - Reproduced: on a fresh home a login shell shows iCode's prompt. Once `eval "$(starship init bash)"` follows the line, Starship's.
- **On the owner's phone:** device check D3 deletes the two old lines and `~/.icode-prompt.sh` once.

## 6. iCode, visible

There is no registry and no new screen: one marker, and one file in the runtime.

- **`Managed by iCode:`** is the first line, a comment, of every file in F12 that takes comments. It is the marker on a line in a file that is the user's (the `~/.bashrc` line, the `gradle.properties` block). The `aid_*` group names in `/etc/group` cannot carry a comment, so the README names them.
- **`/etc/icode/README.md`** is one asset, written with `prompt.sh` at every start. It has three parts:
  - the contract (§2);
  - how to see a context: `echo $ICODE_CONTEXT`, `env`;
  - §Limits: what Android and PRoot cannot do, the list the assistant's instructions carry today (PRoot's fake root, no systemd, emulated hard links, no `/proc/net`, ports from 1024, Android's limit on apps' processes, `rseq` off).

  It repeats no path and no variable list from the code: `grep` and `env` show those as they are.
- **`sudo`:** iCode's stand-in runs a real `sudo` when the user has installed one (`[ -x /usr/bin/sudo ] && exec /usr/bin/sudo "$@"`) (F11).
- **Git:** `-c safe.directory=*`, the global write, its marker and `ensureSafeDirectoryConfigured` go (F10). Device check D2 gates this. The entry already in the owner's `~/.gitconfig` is theirs to keep or delete.

## 7. Cycles

Each cycle runs the same gate before its push:
- the backend type-check and every `data`/`domain`/`navigation` test;
- the UI type-check of what it touches;
- both consistency guards;
- the PRoot lab script (§9.2);
- then commit, push the branch, push `main`.

| Cycle | What | Done when |
|---|---|---|
| **RC2** One environment | `hostEnvironment` and `env -i`; `GuestContext` and `GuestVariables`; the base with `USER`/`LOGNAME`/`SHELL`; the snapshot (§3.3); every caller on its context; `ICODE_CONTEXT` | Unit tests on each context's variables. Lab: no `PROOT_*` or host `LD_LIBRARY_PATH` in the guest; terminal and `command` differ only by their own variables |
| **RC3** One filesystem | One bind everywhere; `bindable` gone; `GuestCommand` in any folder; `GuestPaths` and its four replacements (§4.1–4.2) | `GuestPaths` tests on a test tree: bind points, runtime links, PRoot's stand-ins, loops. Lab: a `command` launch's write under `/workspace/other` is seen by a `terminal` launch |
| **RC4** The assistant, unfenced | §4.3, row by row; the instructions | Tool tests: read and write `/root/x` and `/workspace/other/y`; write in `.git`; `search_text` with `path` and `ignored`; a 900 s timeout taken as asked; a ninth process starts; the three error kinds |
| **RC5** Shell and prompt | The passwd login shell with `-l`; its missing-shell line; Terminal › Shell removed; `/etc/icode/prompt.sh`; the fresh-home line; `ShellPrompt` replaced | Tests: N starts leave `~/.bashrc` and `~/.profile` byte-identical; a fresh home gets one line. Lab: Starship wins once added; `chsh` to a shell installed in the lab is honoured |
| **RC6** iCode, visible | The marker on every written file; the README asset; `sudo` defers; `safe.directory` removed (after D2) | Each writer's test sees the marker. The `sudo` script test covers both branches |
| **RC7** Cleanup and docs | §8, then the full gate | The guards' allowlists only shrink; no doc says what §2 no longer holds |

### 7.1 RC4, as built (2026-10-24)

- **Paths:** `AssistantWorkspace(root, GuestPaths)` resolves every path the model writes (§4.3, A1) and says each result as it can be written back: from the workspace's top inside it, its runtime path elsewhere (`shown`). `WorkspaceFileSource` no longer refuses files outside the workspaces, so a write anywhere is the editor's atomic, fingerprint-checked save.
- **Errors (A9):** "`x` does not exist", "`x` is a folder", "`x` is not a folder", "`x`: Permission denied" — with the path as the model wrote it.
- **Search (A4):** `WorkspaceSearchEngine.index` and `search` take `ignored` (ripgrep's `--no-ignore`); `.git` stays out of a recursive listing and a search either way, and `list_files` without `recursive` shows it.
- **Processes (A7, A8):** `RuntimeProcesses.all()` lists every process; each is said with its workspace's name. `ProcessStatusTool`, `SendProcessInputTool` and `StopProcessTool` take no workspace.
- **Kept, as none limits what the assistant can do:** `start_process`'s `wait_seconds` (30 s at most) is only how long it waits before answering — the process runs on and `process_status` reads it; `read_file`'s 5 MB is the editor's file service — `run_command` reads a file of any size; the page opens only this workspace's files (`AssistantTool.file`), and attachments are the workspace's, as the Attach sheet picks them.
- **The README line** in the instructions ("PRoot's and Android's limits are in `/etc/icode/README.md`") lands with the file itself in RC6, so the model is never pointed at a file that is not there.
- **Tested:** the tool tests read and write `/root/x` and `/workspace/other/y`, write in `.git`, edit `/etc/hosts`, search `/etc` with `ignored`, list `/usr/lib`, start nine processes, and see the error kinds. A 900 s timeout is the model's value passed through (`coerceAtLeast(1)`, no ceiling); no test waits 900 s.

### 7.2 RC5, as built (2026-10-24)

- **The shell:** `TerminalLaunch.prepare` starts `RuntimeLayout.loginShell()` with `-l`, and `SHELL` is that shell. `recordedShell`, `loginShell` and `shellAvailable` read the runtime through `GuestPaths`, so a shell behind a link made in the runtime (`/usr/local/bin/fish` → `/opt/fish/bin/fish`) is found, as the runtime finds it.
- **A missing shell:** the tab runs `/bin/sh -c 'printf …; exec "$@"'` with the line, then `/bin/bash -l`: "iCode: your login shell /usr/bin/zsh (from /etc/passwd) is not installed — `apt install zsh`, or `chsh -s /bin/bash`".
- **The prompt:** `ShellPrompt` is an object: `write` puts the script at `/etc/icode/prompt.sh` (install, and before each terminal); `addTo` gives a fresh home's `~/.bashrc` its one line. `RuntimeInstaller.configureGuest(freshHome)` adds it only where no home was restored by Repair. The script's first line is its "Managed by iCode:" note.
- **Terminal Settings:** the first section is "Display" (font, size, scrollback); the catalogue's words no longer offer a shell.
- **Tested:** three starts leave `~/.bashrc` and `~/.profile` byte-identical and write only the script; a fresh home gets exactly one line; in real bash a fresh home shows iCode's prompt and a `PROMPT_COMMAND` set after the line (Starship's) wins; `chsh`'s record is followed through a runtime link; the missing-shell line is printed before the login shell starts. The lab has no RC5 check: neither fact is PRoot's.
- **On the owner's phone (D3):** the two old lines and `~/.icode-prompt.sh` stay until deleted by hand. This build neither reads nor removes them, and `/etc/icode/prompt.sh` arrives with the first terminal.

### 7.3 RC6, as built (2026-10-24)

- **The marker:** `RuntimeLayout.MANAGED` ("Managed by iCode:"), one constant for every writer. It is the first line of each base-system file (`GuestSystemFiles`: `resolv.conf`, `hosts`, dpkg's `icode-unsafe-io`, `/etc/gitconfig`, moved out of `RuntimeInstaller.configureGuest`), the line after `#!` in `sudo` and the gpg script, and the first line of `gpg.conf`, `gpg-agent.conf`, `prompt.sh`, the README, the Android SDK's `profile.d` file and init script, and both ends of its `gradle.properties` block. The receipts in `~/.icode/` and the `aid_*` groups cannot carry it; the README names them.
- **The README:** the `runtime/README.md` asset, copied to `/etc/icode/README.md` with `prompt.sh` by `RuntimeLayout.writeOwnFiles`, at install and at every iCode start (`RuntimeCoordinator.start`), so it is there before the assistant's first command. Before RC6 the prompt was written as a terminal started; now it is written with the README instead. The instructions end: "What PRoot and Android cannot do here is in /etc/icode/README.md". A test keeps its contexts table naming every `GuestContext`.
- **`sudo`:** `GuestSudo.script(installed)` opens with `[ -x '/usr/bin/sudo' ] && exec '/usr/bin/sudo' "$@"`. Whether a real `sudo` works under PRoot's fake root is sudo's to say; the stand-in no longer hides it.
- **Git:** `safe.directory` is gone from `GitCommandLine`, from `GitInspectParser`'s script (five more places the plan had not listed), and with `ensureSafeDirectoryConfigured`, its marker and its two calls.
- **Device:** D8 (§9.3) for an Android SDK set up before this build.

## 8. Cleanup

**Unused code found now**, each declared and referenced nowhere else in `app/src/main`, tests or resources:

| What | Where |
|---|---|
| `IcodeSizes.DotMd`, `TabStrip`, `HiddenInput`, `LineNumberGutter`, `MarkSm`, `MarkMd`, `MarkIconSm`, `MarkIconMd`, `MarkIconLg` | `designsystem/IcodeMetrics.kt` |
| `IcodeType.TerminalBase` | `designsystem/IcodeType.kt:54` |
| `ICodeStateHolder.onMain` | `data/workspace/ICodeStateHolder.kt:71` |
| `KoogFailures.isTransient` | `data/assistant/koog/KoogFailures.kt:114` |

**Removed by the contract:**

| What | Cycle |
|---|---|
| `prootEnvironment`'s guest half; `GuestEnvironment.LAUNCH_OWNED`'s PRoot names | RC2 |
| `AssistantShell.COMMAND_ENVIRONMENT`, which becomes the `command` context's variables (one list, not two) | RC2 |
| `prootCommand(workspace)`, `RuntimeLayout.bindable`, `BIND_LINKS`, the `workspace` bind in `GuestCommand` and `spawn`; `GuestCommand`'s workspaces-only folder check | RC3 |
| The four `canonicalFile` link-followers (§4.2) | RC3 |
| `WorkspaceFileSource`'s workspaces-only checks and its three `canonicalFile` link-followers; `RuntimeProcesses.of(workspace)`, replaced by `all()` | RC4 |
| `AssistantWorkspace.resolve`'s refusals, `isGitInternal` and its refusal, `list_files`' `.git` filter, `AssistantShell.directory`'s outside-the-workspace rule, `RunCommandTool.MAX_TIMEOUT`, `ProcessRules.MAX_RUNNING` and its refusal, the process tools' workspace filters | RC4 |
| `ShellPrompt.install`, `~/.icode-prompt.sh`, `ShellPromptTest`'s dotfile cases | RC5 |
| `TerminalSettings.shellPath`, `TerminalSettingRules.validShellPath`, the Shell row and its dialog, `terminalShellKey`, `WorkspacePreferences.setTerminalShellPath`; `TerminalLaunch.failure`, never set once a missing shell falls back to bash | RC5 |
| `GitCommandLine`'s and `GitInspectParser`'s `safe.directory`, `CliGitService.ensureSafeDirectoryConfigured`, `SAFE_DIRECTORY_MARKER` and its two calls; `RuntimeInstaller`'s base-system writes, now `GuestSystemFiles` | RC6 |

**Docs brought to the contract (RC7):**
- `runtime-foundation-plan.md`: D2 and D4, and §3.1–3.5, get a "superseded by runtime-contract-plan" note.
- `architecture.md`: the runtime paragraph (line 155).
- `assistant-koog-plan.md`: the tool table (line 230 on).
- `runtime-foundation-findings.md`: row 6.
- `terminal-separation-plan.md` §S6.2 and `accent-color-plan.md`'s test line: the shell-path check and the sourced-once prompt they describe are gone (RC5).

The guards' allowlists are re-baselined downward.

## 9. Checks

### 9.1 Acceptance: the statement's §7, made testable

| Criterion | Shown by |
|---|---|
| User-installed and configured tools keep working across starts | RC5: dotfiles byte-identical across starts; Starship's prompt wins in real bash (`ShellPromptTest`); D3, D5 |
| iCode defaults never reverse a user change | RC5; RC6 (`sudo`); the marker finds every iCode file |
| Terminal and assistant behave alike where their contexts are equal, and the difference is explained where not | RC2 tests and lab; D4 |
| One filesystem state across interfaces | RC3 lab; D6 |
| The assistant tells inaccessible from missing | Nothing is inaccessible by iCode's doing; RC4: "does not exist", and the system's own words otherwise |
| A user can tell where a behaviour comes from | `ICODE_CONTEXT`; `env`; the marker; README §Limits |
| A new compatible tool needs no iCode exception | Starship and `chsh` to zsh, with no code for either |
| No iCode-made limit on the assistant beyond approvals | RC4's tool tests, row by row of §1.2 |

### 9.2 Here

- Every cycle's unit tests.
- **The PRoot lab:** a script under `tools/` that builds PRoot from the pinned submodule with iCode's patches, then starts each context's real command line from `RuntimeLayout` against a test root filesystem and asserts the facts above. It is what this plan's reproductions were made with, kept so they stay true.

### 9.3 On the owner's phone

| # | Step | Expect |
|---|---|---|
| D1 | **Before installing RC3**: in an assistant chat, `run_command` `find /workspace -mindepth 1 -maxdepth 1 ! -name <this workspace>`. After it, only from a computer: `adb shell run-as dev.icode.mobile.debug ls -laR files/runtime/rootfs/workspace` (a debug build's package is `dev.icode.mobile.debug`) | Leftovers listed (F8). Move any file in one into a workspace; empty folders may stay, hidden |
| D2 | **Before RC6's removal**: in the terminal, in a workspace: `git -c safe.directory= status` | It succeeds (F10 on the device) |
| D3 | After RC5: `sed -i '/^# iCode prompt$/,+1d' ~/.bashrc ~/.profile; rm ~/.icode-prompt.sh`; open a tab; install Starship per its docs; open a tab | Ubuntu's prompt (the old home has no new line), then Starship's |
| D4 | `env \| sort` in a tab, and the same through `run_command` | The same, but for the `command` context's variables and `PWD`/`SHLVL`/`_` |
| D5 | A repository with a husky pre-commit hook and node from nvm: commit from Source Control | The hook runs (F6) |
| D6 | From an assistant chat: `write_file` `/workspace/<other>/probe.txt`, then `~/.config/probe.txt`; `read_file` `/etc/os-release` | Each asked and done; the first shows in the terminal and the Explorer |
| D7 | `chsh -s /usr/bin/zsh` (after `apt install zsh`), new tab; then `apt remove zsh`, new tab | zsh as a login shell; then bash with the explaining line |
| D8 | After RC6, only where the Android SDK was set up before it: Runtime Tools offers it again (its settings changed: the marker); install it, then delete the old block between `# >>> iCode: Android SDK` and `# <<< iCode: Android SDK <<<` from `~/.gradle/gradle.properties` | One block, `# >>> Managed by iCode: Android SDK …` |

**Results (2026-10-24, build `ad8a559`, checked over adb by the owner's local session):**
- **D1 passed.** `rootfs/workspace` holds only `.` and `..`; `files/runtime/binds` is gone. RC3 was installed before the assistant-side `find` could run, but the root filesystem dates from a fresh install the day before.
- **D2 passed.** `git -c safe.directory= status` exits 0 in the terminal in both workspaces, `portofolio` (cloned) and `Math` (no commits yet), both in `files/workspaces`. The `[safe] directory = *` in the owner's `~/.gitconfig` is theirs to keep or delete.
- **D3 passed**, Starship not installed (third-party software: the owner's call). RC5's install and a terminal's start left both dotfiles untouched. The `sed` took out exactly the two lines of each, and the next tab was `/bin/bash -l` with Ubuntu's `PS1` and no `PROMPT_COMMAND`. Each dotfile keeps one trailing blank line the old append left.
- **Found:** Ubuntu's root `~/.bashrc` colours its prompt only for `TERM=xterm-color`, so the prompt is plain under `xterm-256color` (Ubuntu's own behaviour). The runtime's time zone is UTC (`/etc/localtime` → `Etc/UTC`, no `TZ`) while the phone's is Asia/Kathmandu, so `date`, `ls -l` and Git's commit times read UTC. Open: §10.

## 10. Not in this plan

- **The runtime's home as an Explorer place:** new UI rather than a removed limit. The assistant and the terminal reach the home, and `GuestPaths` is ready for it.
- **A PTY for the assistant:** runtime-foundation-plan D3 stands; the `command` context's variables say there is no terminal.
- **Removing leftovers in `rootfs/workspace` or `files/runtime/binds` by code:** no-compatibility rule; D1 instead.
- **The runtime's time zone (open; found on the phone, 2026-10-24):** the phone's zone never reaches the runtime, which reads UTC. Two ways, neither built:
  - `TZ` from the phone's zone in the base variables (§3), at every launch. It follows the phone, and a profile's own `TZ`, or `unset TZ`, wins as over every base variable. But `TZ=Asia/Kathmandu` needs `/usr/share/zoneinfo`, and whether the Ubuntu base has it without `tzdata` is unchecked.
  - `/etc/localtime` from the phone's zone at install. That is Linux's own place, and `dpkg-reconfigure tzdata` changes it, but it is fixed at install.
  - Recommended: the first, once `ls /usr/share/zoneinfo/Asia/Kathmandu` on the phone shows the zone file is there. Until then the README says how to set it.
