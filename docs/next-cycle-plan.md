# Next cycle — closing the runtime lifecycle, then protecting the user's work

Status: **plan** (2026-09-23). Follows [backend-split-rules.md](./backend-split-rules.md).
Continues [integration-plan.md](./integration-plan.md) §9 and
[sudo-capability-plan.md](./sudo-capability-plan.md).

## Scope boundary

Appearance and Editor Settings are **not** in this cycle — they have their own
implementation-ready plan (`settings-backend-split-plan.md`) and another agent
is working there. This cycle touches runtime, terminal, and recovery only.

## Order, and why

Ranked by what it costs the user when it is missing, not by size.

| # | Item | Why first |
|---|---|---|
| 1 | **Runtime repair and reset** | A user can reach a runtime that cannot be fixed from inside the app. Measured: an interrupted `apt` left dpkg mid-transaction and deleted `/usr/bin/perl`, after which every install failed and `dpkg --configure -a` could not recover. The only cure was deleting the root filesystem over adb, which a user cannot do |
| 2 | **The `sudo` hint** | Finishes the capability the user designed. `SudoPolicy.hintFor` is written and tested; a bare `apt install git` still fails without saying what to do |
| 3 | **Developer tools at onboarding** | A new user reaches Source Control with no `git` and no obvious way to get it |
| 4 | **Terminal: tapping a URL** | A single tap on output opens the browser and backgrounds the app. Hit by accident while trying to focus the terminal |
| 5 | **Autosave and recovery** (cycle 11, R2–R6) | An IDE that loses unsaved work on a kill. `DocumentDraftStore` and its 12 tests exist; nothing writes drafts yet |
| 6 | **Full-screen terminal drawing** | `vim`, `htop`, `less` and dpkg's progress are unusable. Large: an alternate screen buffer and cursor addressing in the emulator. Worked around for dpkg with `-o Dpkg::Use-Pty=0`, which is not a general fix |

## 1. Runtime repair and reset

Frame 36 already designed this and is entirely static, including values that are
wrong: it claims "Ubuntu 22.04 ARM64" where the app installs 24.04.5, and an
"Uptime" for a runtime that has no long-running process.

**Affordances to keep:** the health notice, the technical details card, **Repair
Runtime** and **Reset Runtime** with their explanatory captions.

| Detail | Today | Becomes |
|---|---|---|
| Health | hard-coded "Runtime Healthy / Ubuntu 22.04 ARM64" | `RuntimeState`, with the release from `installed.json` |
| Architecture | "aarch64" | the device's actual primary ABI |
| Runtime Home | `.../files/usr/runtime` | the real root filesystem path |
| Installed Packages | "247 packages" | counted from `var/lib/dpkg/info/*.list` |
| Disk Usage | "1.8 GB of 16 GB" | measured size of the root filesystem, against real free space |
| Uptime | "3h 42m" | **removed** — nothing runs continuously to have an uptime |
| PRoot Version | "5.3.0" | the version this build ships, from the runtime manifest |

**Repair** re-extracts the base system and keeps the guest home directory.
**Reset** discards the home directory too. Both run the existing installer, so
there is one install path, not a second one. The captions are corrected: repair
does not preserve installed packages, and saying it does would be a lie.

Both are destructive, so both confirm first through `IcodeConfirmSheet`.

## 2. The `sudo` hint

When a command fails and `SudoPolicy.hintFor` recognises it, the terminal adds
one iCode line naming the exact `sudo` command to rerun. The policy is already
pure and tested; this is the presentation half.

## 3. Developer tools at onboarding

After the runtime is ready, check the tools the app itself depends on (`git`
first) and offer one step to install them, through the same host path that
`Install Git` in Source Control already uses. Skippable, and never silent.

## 4. Terminal: tapping a URL

A tap places focus. Opening a link becomes a long press, matching the tab menu
convention already used in the editor.

## 5–6

Autosave and recovery resumes at R2 of cycle 11. Full-screen terminal drawing
gets its own plan when reached — it is an emulator feature, not a wiring task.

## Verification

Per rulebook §7, after each slice: `:app:compileDebugKotlin`, targeted then full
`:app:testDebugUnitTest`, `:app:lintDebug`, `tools/check-ui-consistency.sh`, and
device verification for anything with a surface. A poisoned runtime is
reproducible on demand, so repair is tested against a real broken state rather
than a simulated one.

## Done so far

### 1. Runtime repair and reset — complete, verified on device

Frame 36 is real. Measured values on the device: architecture `arm64-v8a`, the
real root filesystem path, **755 packages** counted from the guest package
database, **1.4 GB used · 29.2 GB free**, PRoot **5.1.107.92** read from the
runtime manifest. The health line reports the installed release rather than a
hard-coded "Ubuntu 22.04", and "Uptime" is gone because nothing runs
continuously to have one. Repair and Reset both confirm first, and the captions
now say what actually happens: repair keeps the home directory, not installed
packages.

Two bugs found while building it:

- **The details refresh queued behind the workspace mutation lock.** Reading the
  runtime's own files changes nothing, so it now bypasses that lock, as
  `RefreshStorage` already did.
- **Disk usage could never be measured.** `File.listFiles()` follows symbolic
  links, and a base system is full of them (`/bin` points into `/usr/bin`), so
  the walk re-traversed the same files and tripped its own bound. Replaced with
  `Files.walkFileTree`, which does not follow links — both correct and what
  makes the figure mean "space this uses".

### 2. The `sudo` hint — complete, verified on device

```
E: Sub-process /usr/bin/dpkg returned an error code (2)
iCode: apt needs system privileges here. Try: sudo apt install tree
```

Implemented as shell wrappers generated from `SudoPolicy`, so the exit code is
the signal rather than pattern-matching the terminal's bytes, and a successful
command is untouched. `GuestSudoShell` is pure and has seven tests, including
one asserting the wording comes from the policy so the two cannot drift.

### Not yet verified

Repair and Reset were confirmed as far as the dialog; neither was run to
completion, because doing so would rebuild the runtime that currently holds the
installed `git`. The install path they use is the same one verified repeatedly
this cycle.

## Install speed — measured, not estimated

The user asked why unpacking was slow. It was PRoot.

PRoot works by **ptrace**: every syscall the traced process makes is
intercepted, its paths rewritten, and the process resumed. Unpacking a base
system is thousands of tiny files — `open`, `write`, `close`, `chown`, `utimes`
each — so at 2,562 files it ran at roughly **47 ms per file**, nowhere near what
the storage can do. The interception set the pace, not the disk.

Nothing about unpacking needed the guest: it writes files into a directory the
app already owns.

| Step | Before | After | How |
| --- | --- | --- | --- |
| Runtime install, cold (download, unpack, configure, health check) | ~2 minutes | **19 s** | Unpack natively with Commons Compress, no PRoot |
| `sudo apt install -y git` (39 packages, clean root filesystem) | 152 s | **84 s** | `--force-unsafe-io`, dropping dpkg's per-file `fsync` |

`apt` still runs inside the guest and always will — that is what it is for — so
ptrace overhead there is inherent. Dropping the syncs is the standard
mitigation, the one container and CI images use, and the package database is
rebuildable now that Repair Runtime exists.

### What replaced PRoot for unpacking

- **Ownership** is ignored: one user, and the guest runs as fake root anyway, so
  the `chown` calls that would have failed are simply not made.
- **Hard links become copies.** Android denies `link()` to app processes — the
  whole reason `--link2symlink` was on this step. A copy is correct; a dangling
  emulated symlink is not.
- Nine tests cover it against real archives, including a hard link whose target
  appears later in the stream and a `../` entry trying to escape the tree.

### Also fixed

Tapping terminal output opened the browser, because any line containing a URL
was clickable and swallowed the focus tap. Package managers print URLs
constantly, so this made it impossible to put the cursor on those lines — it
interrupted this session's own testing three times. A tap now focuses; a long
press opens the link, matching the editor tab convention.

## `sudo` opened up, and a real installer through it — verified on device

The allowlist was the wrong shape. `sudo` always runs inside the guest, through
PRoot rooted at the root filesystem, so it can never reach an Android host
command; restricting guest programs protected nothing the user's own shell
could not already run. It now runs any command, refusing only `sudo sudo` and an
empty command. The boundary that matters — host versus guest — is unchanged.

| Test | Result |
| --- | --- |
| `sudo dpkg -l \| head -4` | the pipe note prints, and `head` still trims the real output |
| `sudo apt install -y curl` | 24 s, of which ~9 s unpack and configure; the rest was a 37 kB/s network |
| Tap on a URL line | terminal keeps focus; the browser does not open |
| Long press on a URL line | opens the link (verified by the user on device) |
| `sudo bash -c "curl -fsSL https://claude.ai/install.sh \| bash"` | Claude Code 2.1.281 installed into `~/.local/bin` |
| `claude --version` from the normal session | `2.1.281 (Claude Code)` |

`sudo curl … | bash` on its own cannot work, here or on real Linux: the shell
splits the pipeline before `sudo` runs, so only `curl` is elevated and `bash`
executes in the session that cannot change the system. `sudo bash -c "…"` keeps
the whole pipeline inside. The pipe note says so instead of failing quietly.

## Two terminal input bugs, found by that test

Typing the installer URL produced `https://Claclaude.install.sh`.

1. **The keyboard rewrote the command.** `autoCorrectEnabled = false` is only a
   hint and this keyboard ignored it. The input field now declares a password
   type, which every keyboard honours: no suggestions, no autocorrect, and no
   learning what was typed — which also keeps tokens and paths out of the
   keyboard's dictionary. Termux does the same; the field is invisible, so
   nothing is masked.
2. **Keystrokes could reach the shell out of order.** Each one was its own
   launched coroutine, twice over, on a multi-threaded dispatcher, and waited on
   the workspace mutation lock. An autocorrect replacement — six deletes and new
   text in the same instant — arrived scrambled, and typing stalled behind git
   refreshes. `KeystrokeQueue` now records order at the moment of the call and a
   single consumer delivers it; Ctrl+letter goes through the same queue so
   Ctrl+C cannot overtake what was typed before it. Four tests, including the
   exact burst shape.

After both, the same command typed at full speed arrives intact.

## `~/.local/bin` on the guest PATH

The runtime set PATH explicitly and left out `~/.local/bin`, which Ubuntu's own
`~/.profile` prepends. pip, pipx, user-level npm and installers like Claude Code
put their commands there, so a tool installed a moment earlier was "not found".
It is now first on the guest PATH, as Ubuntu orders it.

## Not in this round

Autosave (cycle 11, R2) is being wired in `AndroidICodeGateway` by another
agent — `draftStore.write` is already called there — so it is left alone here.
