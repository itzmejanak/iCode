# Runtime Contract — one Linux environment, user first, iCode visible

Status: **planned (2026-10-23)**. Nothing here is built yet. The cycles are §7, the cleanup is §8, the checks are §9.

The owner's problem statement (2026-10-23), in short:
- iCode is a native Android IDE around a genuine, user-controlled Linux environment.
- The terminal, the Explorer and the assistant are ways into that one environment.
- iCode must not override user configuration, split execution contexts silently, narrow the filesystem without saying so, or blur Linux behaviour with its own policy.

Its success criteria are §9.1's acceptance list.

The owner chose (2026-10-23):

| Question | Choice |
|---|---|
| Assistant reach | **One view for all.** The terminal, assistant commands, processes, the Run tab, Git and language servers see `/workspace` whole. The assistant's file tools still edit only the open workspace, and say so. Commands stay approval-gated. |
| Terminal shell | **The login shell from `/etc/passwd`**, always started as a login shell (`chsh` works as on Linux). Terminal › Shell is removed. |
| iCode's prompt | **A default for new runtimes only.** A fresh home's `~/.bashrc` gets one line sourcing `/etc/icode/prompt.sh`. iCode never writes the user's dotfiles again. |

## 1. What stands today (verified)

Each fact was checked in the code, and where it is behaviour, reproduced:
- **PRoot:** built here from the pinned submodule commit `7266fb3` with iCode's three patches, running a test root filesystem.
- **bash:** with Ubuntu's own `/root/.profile` and `/root/.bashrc`, the files the `ubuntu-base-24.04.5` image ships.
- **Starship:** its real `starship.bash` init, behind a stub binary.

| # | Fact | Evidence | Consequence |
|---|---|---|---|
| F1 | Every terminal start rewrites `~/.icode-prompt.sh` and appends `. /root/.icode-prompt.sh` to `~/.bashrc` **and** `~/.profile` when the file lacks the name; a line the user deletes comes back at the next start | `ShellPrompt.install()` (`ShellPrompt.kt:20-34`), called by `TerminalLaunch.prepare` (`TerminalLaunch.kt:37`) | iCode rewrites the user's dotfiles |
| F2 | Ubuntu's `/root/.profile` sources `~/.bashrc` first; iCode's line at the end of `~/.profile` then runs after everything in `~/.bashrc`, and sets `PROMPT_COMMAND=__icode_prompt` outright | Reproduced: with `eval "$(starship init bash)"` last in `~/.bashrc`, `bash -l -i` ends with `PROMPT_COMMAND=__icode_prompt`; plain `bash -i` keeps `starship_precmd` | **Starship is overridden in every terminal tab** (the tab runs `bash -l`). Any prompt tool is |
| F3 | Only the exact path `/bin/bash` is started as a login shell; any other shell path, `/usr/bin/bash` included, skips the profile. The shell comes from a Terminal setting, never from `/etc/passwd` | `TerminalLaunch.shellCommand` (`:81-83`); `TerminalSettings.shellPath` (`AppSettings.kt:167`) | `chsh` does nothing; `~/.profile` is lost for other shells |
| F4 | PRoot hands its whole launch environment to the guest | Reproduced: the guest's `env` shows `LD_LIBRARY_PATH` (the app's native-library path), `PROOT_TMP_DIR` (a host path), `PROOT_VERBOSE`, `GLIBC_TUNABLES` | Host-side plumbing appears in the user's shell. A user's `LD_LIBRARY_PATH` or `LD_PRELOAD` would break PRoot's own start, so `GuestEnvironment` drops them from the profile for every non-terminal context (`GuestEnvironment.kt:79-82`). Terminal and assistant then differ |
| F5 | No launch sets `USER`, `LOGNAME` or `SHELL`; bash sets `SHELL` for itself without exporting it | `RuntimeLayout.prootEnvironment` (`:134-160`); reproduced: `env -i … bash -l` exports none of the three | Programs started from the terminal see no user or shell, unlike after a real login |
| F6 | Environments by context. Terminal: the fixed launch set (`TERM=xterm-256color`), then the shell reads the profile itself. Assistant commands and `start_process`: the profile snapshot plus `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`, `NO_COLOR=1`, `TERM=dumb`. Run tab and Build & Run: the snapshot with `TERM=xterm-256color`. Language servers: the snapshot. **Source Control's Git, Search's ripgrep, apt: the fixed set only** | `AssistantShell.COMMAND_ENVIRONMENT` (`:102-109`); `RunCoordinator.kt:47,142`; `AndroidApps.kt:57`; `LanguageServiceProcessManager.kt:44-49`; `GuestCommand.kt:147-160` (`prootEnvironment(environment)`) | **A Git hook that needs the user's PATH (husky running node from nvm) works in the terminal and fails from Source Control.** A proxy set in `~/.bashrc` reaches the terminal's `apt` and `git fetch`, not iCode's. Two no-terminal contexts set `TERM` differently |
| F7 | The assistant's commands and processes, the Run tab and Build & Run bind **only their own workspace** at `/workspace/<name>`; the terminal, Git, search and language servers bind the whole workspace folder at `/workspace` | `RuntimeLayout.prootCommand(…, workspace)` (`:210-216`); `AssistantShell` (`:29`); `RuntimeLayout.spawn` (`RuntimeProcesses.kt:234-245`) | Two filesystem views of one `/workspace` |
| F8 | Under a one-workspace bind, the root filesystem's own `/workspace` folder shows through. Any folder there reads as an **empty** workspace, and anything written to `/workspace/<other>` lands there, hidden from the terminal and the Explorer | Reproduced: `rootfs/workspace/iCode` (empty) reads as an empty `/workspace/iCode` from another workspace's command. `echo lost > /workspace/notes.txt` from one is "No such file" in the terminal and readable again from the next command | **This explains the empty `/workspace/iCode`.** It needs a leftover folder in the root filesystem's `/workspace`. Two writers are known: the one-workspace mount points earlier versions made (workspace-storage-plan §5.5, whose cleanup was removed with the migration, runtime-foundation-plan D10), and any assistant command that wrote under `/workspace/<other>`. Which made it on the owner's phone is told by D1 (§9.3). The model is told "Only this workspace is under /workspace" (`AssistantInstructions.kt:28`) while it can see such folders |
| F9 | PRoot is not a security boundary | PRoot runs as the app's uid; every guest process can read and write what the app can through any bound path | The one-workspace bind guards nothing real. It only hides state |
| F10 | The assistant's file tools resolve every path inside the open workspace. A missing path and a folder both read "is not a file"; outside the workspace reads "outside this workspace" | `AssistantWorkspace.resolve` (`:21-36`); `WorkspaceReadTools.kt:36,82` | "Missing" and "not reachable" are not told apart well enough |
| F11 | With `-0`, PRoot reports any file the app's own uid owns as root's. Every workspace is in the app's private `files/workspaces`, so Git sees it as owned by its own user | PRoot `extension/fake_id0/stat.c:43-47`. Reproduced: a repository owned by PRoot's uid passes `git status` with no `safe.directory`; one owned by another uid fails "dubious ownership" | `-c safe.directory=*` on every command (`GitCommandLine.kt:34-43`) and the `git config --global --add safe.directory '*'` iCode writes into the user's `~/.gitconfig` (`CliGitService.kt:78-91`, marker `~/.icode-git-configured`) are leftovers of the phone-storage era. Their comment still names "a media uid" |
| F12 | `/usr/local/bin/sudo` is iCode's stand-in, ahead of `/usr/bin` in `PATH` | `GuestSudo.kt:19-26`; `RuntimeLayout.GUEST_PATH` | A real `sudo` the user installs with apt is shadowed by iCode's |
| F13 | Files iCode writes into the runtime outside the workspaces, none of them listed anywhere the user can see: `/etc/resolv.conf`, `/etc/hosts`, `aid_*` lines in `/etc/group`, `/etc/dpkg/dpkg.cfg.d/icode-unsafe-io`, `/etc/gitconfig` (whole file), `/usr/local/bin/sudo`, `/usr/local/bin/icode-pinentry` and `icode-gpg-agent`, `/etc/gnupg/gpg.conf` and `gpg-agent.conf`, `~/.icode-prompt.sh`, two dotfile lines, `~/.icode-git-configured`, the global `safe.directory`, `~/.icode/runtime-tools/*` receipts, and, with the Android SDK, `/etc/profile.d/icode-android-sdk.sh`, a block in `~/.gradle/gradle.properties` and `~/.gradle/init.d/` | `RuntimeInstaller.configureGuest` (`:184-212`), `RuntimeLayout.ensureGuestGroups`, `GuestSudo`, `GuestGpg` (`:20-36`), `ShellPrompt`, `CliGitService`, `RuntimeToolSetup`, `AndroidSdkSetup` (`:91,127-129,155-160`) | The user cannot tell iCode's policy from Linux's (statement §3.5) |
| F14 | Terminal "finished" alerts read the foreground process group from `/proc`, not the prompt | `TerminalForeground.of` | The prompt can change freely; no feature depends on it |
| F15 | The Explorer and editor reach the workspaces and phone storage; the runtime's home and `/etc` only through the terminal | `StoragePlace` (`FilesBrowser.kt:25`); `GuestCommand.kt:147` | Outside this plan (§10) |

## 2. The contract

What every iCode surface holds to from now on. `/etc/icode/README.md` carries it into the runtime (§6).

1. **One filesystem.** Every process iCode starts in the runtime sees the same tree: the runtime's root, and every workspace at `/workspace/<name>`.
   - The Explorer's Private place is that same `/workspace`.
   - The only narrower reach is a tool's *policy*: the assistant's file tools edit the open workspace. A policy is stated in the tool's own error, never enforced by hiding files.
2. **One environment.** Every process starts from the same login environment, the variables a fresh login shell exports. Each process gets **base < the user's profile < its context's own variables**, and nothing else. PRoot's own variables never reach the guest.
3. **Contexts are named and visible.**
   - `ICODE_CONTEXT` names the context in every guest process.
   - Each context's own variables are listed in the README.
   - A difference between two contexts is one of those lists, or a PTY versus none.
4. **The user's files are the user's.**
   - iCode writes into the home only when it creates the home: the first install, or Reset.
   - After that it writes there only what the user asks for in iCode (Runtime Tools, Git identity, credentials), each listed (§6).
   - System files iCode needs live under `/etc/icode/`, or in Linux's standard drop-in folders under a name starting `icode-`. Each starts with a header naming iCode and why.
5. **User choices win.**
   - The login shell comes from `/etc/passwd`.
   - A program the user installs is never shadowed by an iCode stand-in.
   - A setting in the user's profile reaches every context. A context's own variables win only where there is no terminal to be honest about (`TERM=dumb`) or no person to answer a prompt.
6. **Limits are said where they bite.** PRoot's and Android's limits are listed once (README §Limits), and the assistant's instructions point to the list.

## 3. One launch, one environment

### 3.1 Host side and guest side

`RuntimeLayout` splits what is today one map:

- **`hostEnvironment()`**: only what PRoot itself needs, which never reaches the guest: `PROOT_LOADER`, `PROOT_TMP_DIR`, `PROOT_VERBOSE=-1`, `LD_LIBRARY_PATH` (bionic finds `libtalloc.so` through it).
- **`guestCommand(context, variables, argv)`**: every guest program starts as `/usr/bin/env -i <variables> <argv…>`.
  - The guest then has exactly the documented variables (F4).
  - `env` replaces itself with the program, so its pid, the foreground-group reading (F14) and `--kill-on-exit` are unchanged.
  - Reproduced: in PRoot, `env -i HOME=… USER=root … /bin/bash -l` gives a shell whose `env` lists only those variables and what the profile adds.

### 3.2 The variables (`domain/GuestEnvironmentRules`, pure, tested)

| Layer | Variables |
|---|---|
| **Base**, what a Linux login sets | `HOME=/root`, `USER=root`, `LOGNAME=root`, `SHELL=<root's shell from /etc/passwd>`, `PATH=<GUEST_PATH>`, `LANG=C.UTF-8`, `GLIBC_TUNABLES=glibc.pthread.rseq=0` (PRoot's limit: README §Limits), `ICODE_CONTEXT=<context>` |
| **Profile** | The login snapshot (§3.3); it overrides the base (a profile's `PATH`, `LANG`, `LD_LIBRARY_PATH`, a proxy) |
| **Context** | Below; it overrides both |

| Context (`GuestContext`) | Who | PTY | Its own variables |
|---|---|---|---|
| `terminal` | Terminal tabs | yes | `TERM=xterm-256color`, `COLORTERM=truecolor`. Starts the login shell with `-l`, which reads the profile itself (no snapshot) |
| `command` | Assistant `run_command`, `start_process`, Run tab, Build & Run | no | `TERM=dumb`, `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, `DEBIAN_FRONTEND=noninteractive`; the assistant's also `NO_COLOR=1` (a model reads it) |
| `git` | Source Control, the assistant's `git_status` and `git_diff`, and the gpg agent iCode starts for signing (`GpgBridge.startAgent`) | no | As `command`: hooks, credential helpers and gpg run with the user's profile |
| `language-server` | Language services | no | As `command` |
| `search` | Search and the assistant's `search_text` (ripgrep) | no | As `command`. ripgrep already runs with `--no-config` (`RipgrepCommand.kt:6`): iCode's Match Case, Whole Word and Regex options decide, not `~/.ripgreprc`. The README says so |
| `install` | Runtime install, Repair, health check, the snapshot itself | no | Base only: a broken profile must never stop the runtime from being built or repaired |

The Runtime Tools' apt (`RuntimePackageInstaller`, today the fixed set plus `DEBIAN_FRONTEND`) is `command`: a proxy in the profile reaches it as it reaches `apt` in the terminal.

### 3.3 The login snapshot (`GuestEnvironment`, simplified)

- **Shell:** read with the user's login shell: `<shell> -l -i -c 'printf …; env -0'`, in context `install`. Today it is always `/bin/bash -lic`.
- **Dropped:** only `PWD`, `OLDPWD`, `SHLVL`, `_`, and the context-owned names (`TERM`, `COLORTERM`, `ICODE_CONTEXT`). F4's exclusions (`LD_LIBRARY_PATH`, `LD_PRELOAD`, `GLIBC_TUNABLES`, `PROOT_*`) go: those names no longer collide with PRoot's.
- **Unchanged:** the fingerprint, the 10 s limit, and the fall-back to base with a note.
- **Note in the open:** today only the assistant's command results carry the note. It moves to every context that uses the snapshot: Git's and the Run tab's failure text, and the Processes sheet.

## 4. One filesystem view

- `prootCommand` loses its `workspace` parameter: every launch binds `files/workspaces` at `/workspace` (F7).
  - `bindable()`, its `binds/` links and their hashing go. A workspace's location reaches no `-b` argument any more.
  - Managed processes keep their workspace, but as an identity only: it drives the Processes sheet's list and the hold on rename and delete. It is no longer a bind.
- **The assistant's words:**
  - The instructions and the tool descriptions (`AssistantInstructions`, `RunCommandTool`, `StartProcessTool`, `AssistantShell` KDoc) say: every workspace is under `/workspace`; the file tools change only "<name>"; `run_command` reaches the rest, as the user approves.
  - The instructions point to `/etc/icode/README.md`.
- **The file tools tell three cases apart (F10):**
  - a missing path: "`src/x.kt` does not exist";
  - a folder where a file was wanted: "is a folder";
  - a path in another workspace: "`/workspace/other/a.kt` is in the workspace “other”; file tools work in “app” only — use run_command to read it".
- **Leftovers in `rootfs/workspace`** are hidden from every view from now on (§2.1). No code removes them, by the no-compatibility rule (D10). Device check D1 moves anything there into a workspace *before* this build is installed.

## 5. Shell, prompt and dotfiles

- **The terminal starts root's login shell.**
  - It is read from the runtime's `/etc/passwd` on the host side, and checked as today's `shellAvailable` checks a path: inside the runtime, a regular file.
  - It starts with `-l` (bash, zsh, fish and dash all take it).
  - If it is missing, the tab starts `/bin/bash -l`, and its first line says why: "iCode: your login shell /usr/bin/zsh (from /etc/passwd) is not installed — `apt install zsh`, or `chsh -s /bin/bash`".
- **Terminal › Shell goes:** `TerminalSettings.shellPath`, its row, its DataStore key, `TerminalSettingRules.validShellPath`.
- **The prompt:**
  - `/etc/icode/prompt.sh` is iCode's, written at every runtime start so an app update reaches it. It holds today's function, unchanged, with the accent's colour slot `ACCENT_COLOR_INDEX`.
  - When iCode creates a home, it appends to `~/.bashrc` only: `# iCode's prompt: delete this line to use your own` then `. /etc/icode/prompt.sh`.
  - Nothing else ever touches a dotfile. Repair keeps the home, so it writes none.
  - Reproduced: on a fresh home a login shell shows iCode's prompt. Once `eval "$(starship init bash)"` follows the line, Starship's.
- **On the owner's phone:** device check D3 deletes the two old lines and `~/.icode-prompt.sh` once.

## 6. iCode, visible

- **One registry, `RuntimeFootprint` (data, pure):** every file iCode writes into the runtime, with:
  - its path;
  - whose it is (iCode's, or the user's once created);
  - when it is written (install, every start, a Runtime Tool's setup);
  - why, in one line.

  The writers (`RuntimeInstaller.configureGuest`, `GuestSudo`, `GuestGpg`, the prompt, `AndroidSdkSetup`, `RuntimeToolSetup`) take their paths and header lines from it. A test fails when a writer's path is missing from it.
- **`/etc/icode/README.md`**, written at every start from the registry and `GuestEnvironmentRules`:
  - the contract (§2);
  - each context's variables (§3.2);
  - iCode's files (the registry);
  - §Limits, which absorbs what the assistant's instructions say today: PRoot's fake root, no systemd, emulated hard links (`--link2symlink`), no `/proc/net`, ports from 1024, at most 32 child processes across apps, `rseq` off.
  - `cat /etc/icode/README.md` answers "is this Linux, Android, PRoot, my context, or iCode?"
- **Settings › Runtime › iCode in the runtime:** opens the same file in the Markdown viewer. There is no second copy of its text.
- **`sudo`:** iCode's stand-in runs a real `sudo` when the user has installed one (`[ -x /usr/bin/sudo ] && exec /usr/bin/sudo "$@"`) (F12).
- **Git:** `-c safe.directory=*`, the global write, its marker and `ensureSafeDirectoryConfigured` go (F11). Device check D2 gates this. The entry already in the owner's `~/.gitconfig` is theirs to keep or delete.

## 7. Cycles

Each cycle runs the same gate before its push:
- the backend type-check and every `data`/`domain`/`navigation` test;
- the UI type-check of what it touches;
- both consistency guards;
- the PRoot lab script (§9.2);
- then commit, push the branch, push `main`.

| Cycle | What | Done when |
|---|---|---|
| **RC2** Launch contract | `hostEnvironment`/`guestCommand` with `env -i`; `GuestContext` and `GuestEnvironmentRules` (domain, tested); base with `USER`/`LOGNAME`/`SHELL`; the snapshot simplified (§3.3); Git, search, language servers, Run tab and Build & Run on their contexts; `ICODE_CONTEXT` | Unit tests on every context's variables. The lab script shows no `PROOT_*` or host `LD_LIBRARY_PATH` in the guest, and terminal and `command` differ only by §3.2's lists |
| **RC3** One filesystem | The one-workspace bind and `bindable` removed; managed processes keep their workspace as identity; the assistant's instructions and tool texts; the file tools' three errors (§4) | Lab: a file written by a `command` launch under `/workspace/other` is seen by a `terminal` launch. Tool tests for missing, folder, other workspace |
| **RC4** Shell and prompt | The passwd login shell with `-l`; its missing-shell line; Terminal › Shell removed; `/etc/icode/prompt.sh`; the fresh-home line; `ShellPrompt` replaced | Tests: N starts leave `~/.bashrc` and `~/.profile` byte-identical; a fresh home gets one line. Lab: Starship wins once added, and `chsh` to a shell installed in the lab is honoured |
| **RC5** iCode, visible | `RuntimeFootprint`; `/etc/icode/README.md`; the Settings entry; `sudo` defers to a real one; `safe.directory` removed (after D2) | Footprint test (every writer registered). README generated from the code's own constants. The `sudo` script test covers both branches |
| **RC6** Cleanup and docs | §8, then the full gate | The guards' allowlists only shrink; no doc says what §2 no longer holds |

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
| `prootEnvironment`'s guest half, `GuestEnvironment.LAUNCH_OWNED`'s PRoot names | RC2 |
| `AssistantShell.COMMAND_ENVIRONMENT`, which becomes the `command` context's variables (one list, not two) | RC2 |
| `prootCommand(workspace)`, `RuntimeLayout.bindable`, `BIND_LINKS`, `GuestCommand`'s and `spawn`'s `workspace` bind | RC3 |
| `ShellPrompt.install`, `~/.icode-prompt.sh`, `ShellPromptTest`'s dotfile cases | RC4 |
| `TerminalSettings.shellPath`, `TerminalSettingRules.validShellPath`, the Shell row, `terminalShellKey` | RC4 |
| `GitCommandLine`'s `safe.directory`, `CliGitService.ensureSafeDirectoryConfigured`, `SAFE_DIRECTORY_MARKER` and its two calls | RC5 |

**Docs brought to the contract (RC6):**
- `runtime-foundation-plan.md`: D2 and D4, and §3.1–3.3, get a "superseded by runtime-contract-plan" note.
- `architecture.md`: the runtime paragraph (line 155).
- `assistant-koog-plan.md`: the `run_command` row (line 230).
- `runtime-foundation-findings.md`: row 6.

The guards' allowlists are re-baselined downward.

## 9. Checks

### 9.1 Acceptance: the statement's §7, made testable

| Criterion | Shown by |
|---|---|
| User-installed and configured tools keep working across starts | RC4: dotfiles byte-identical across starts; lab: Starship kept; D3, D5 |
| iCode defaults never reverse a user change | RC4, RC5 (`sudo`); `RuntimeFootprint` lists every write |
| Terminal and assistant behave alike where their contexts are equal, and the difference is explained where not | RC2 unit tests and lab: equal base and profile, differences only §3.2's lists; D4 |
| One filesystem state across interfaces | RC3 lab; D6 |
| The assistant tells inaccessible from missing | RC3 tool tests |
| A user can tell where a behaviour comes from | `ICODE_CONTEXT`; `/etc/icode/README.md`; the snapshot note in every context |
| A new compatible tool needs no iCode exception | Starship and `chsh` to zsh, with no code for either |

### 9.2 Here

- Every cycle's unit tests.
- **The PRoot lab:** a script under `tools/` that builds PRoot from the pinned submodule with iCode's patches, then starts each context's real command line from `RuntimeLayout` against a test root filesystem and asserts the facts above. It is what this plan's reproductions were made with, kept so they stay true.

### 9.3 On the owner's phone

| # | Step | Expect |
|---|---|---|
| D1 | **Before installing RC3**: in an assistant chat, `run_command` `find /workspace -mindepth 1 -maxdepth 1 ! -name <this workspace>` | Leftovers listed (F8). Move any file in one into a workspace; empty folders may stay, hidden |
| D2 | **Before RC5's removal**: in the terminal, in a workspace: `git -c safe.directory= status` | It succeeds (F11 on the device) |
| D3 | After RC4: `sed -i '/^# iCode prompt$/,+1d' ~/.bashrc ~/.profile; rm ~/.icode-prompt.sh`; open a tab; install Starship per its docs; open a tab | Ubuntu's prompt (the old home has no new line), then Starship's |
| D4 | `env \| sort` in a tab, and the same through `run_command` | The same, but for §3.2's `command` list and `PWD`/`SHLVL`/`_` |
| D5 | A repository with a husky pre-commit hook and node from nvm: commit from Source Control | The hook runs (F6) |
| D6 | From an assistant chat, `echo ok > /workspace/<other>/probe.txt` | It shows in the terminal and the Explorer |
| D7 | `chsh -s /usr/bin/zsh` (after `apt install zsh`), new tab; then `apt remove zsh`, new tab | zsh as a login shell; then bash with the explaining line |

## 10. Not in this plan

- **The runtime's home in the Explorer or editor (F15):** a new place, and a product decision of its own.
- **A PTY for the assistant:** runtime-foundation-plan D3 stands; `command`'s variables say there is no terminal.
- **Removing leftovers in `rootfs/workspace` or `files/runtime/binds` by code:** no-compatibility rule; D1 instead.
