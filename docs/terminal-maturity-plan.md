# Mature terminal — root causes, target design, cycles

Status: **done** (T1–T5 recorded in §6; closed by S6, [terminal-separation-plan.md](./terminal-separation-plan.md) §7, 2026-09-29). Earlier status: **plan** (2026-09-24). Follows [backend-split-rules.md](./backend-split-rules.md).
Supersedes item 6 of [next-cycle-plan.md](./next-cycle-plan.md) and the bridge
parts of [sudo-capability-plan.md](./sudo-capability-plan.md). Engine choice was
already made in [integration-plan.md](./integration-plan.md) ("Termux
`terminal-emulator` + `terminal-view` — confirm licence and API in the cycle");
this document does that confirmation.

## 1. Evidence (device: Infinix X6881, 2026-09-24)

| # | Reproduction | Observed | Verdict |
|---|---|---|---|
| E1 | `claude` (Claude Code 2.1.281) | Theme picker drawn with spaces missing (`Choosethetextstyle…`), each redraw appended below the last, stray `B`, yellow "Full-screen terminal drawing is not rendered yet" banner that stays after claude exits | Emulator gap |
| E2 | On-screen Enter in claude's picker | Selection never confirmed | Enter reaches the pty as `\n`; raw-mode programs expect `\r` |
| E3 | Ctrl‑C ×2 sent back-to-back | Claude saw one ("Press Ctrl-C again"); paced 0.4 s apart, it exited | Sticky CTRL lost when tap and key arrive together — input path is a hidden text field |
| E4 | Multi-line `curl … \` typed with on-screen Enter | Worked: `>` continuations, JSON `id: 101` | Cooked-mode input is fine |
| E5 | `read -p 'ok? [y/n] ' a` | `got:y` | Normal-session stdin is fine |
| E6 | `sudo bash -c 'read -p "ok?" a; …'` | No prompt shown; typed `y` goes nowhere; blocks forever | Sudo bridge has no stdin and no tty |
| E7 | Ctrl‑C on E6 | Guest client dies; host PRoot 24853 + children stay alive | Sudo cancellation missing → orphans |
| E8 | `sudo echo hi` after E7 | Hangs | Bridge handles requests one at a time → head-of-line blocking |
| E9 | Kill the orphan | The cancelled `sudo echo hi` then **ran** (`.out`=`hi`, exit 0); files left in `runtime/bridge` | A cancelled sudo executes later — unsafe for `sudo apt remove` |
| E10 | `exit` | Tab stays with ✗; header says "Process terminated by signal 2" | Tab never closes; bash exited normally with status 130, not by signal |
| E11 | `sleep 25 &` then `exit` | `logout` printed, tab stays "running" (green) 25 s with no prompt | PRoot waits for every tracee |
| E12 | `find /storage/emulated/0 -name '.l2s.*'` | `icodeTest/.git/objects/95/.l2s.tmp_obj_SKeJ3t0001.0002` | Debris in the user's repository from the link-emulation bug below |

## 2. Root causes (from source, not inference)

| Cause | Where | Explains |
|---|---|---|
| **R1 Hand-written transcript, not an emulator.** `AnsiTextBuffer` drops cursor movement, alternate screen, scroll regions, charset designation (`ESC ( B` → literal `B`), colours; its own KDoc says so | `data/runtime/AnsiTextBuffer.kt` | E1 |
| **R2 Input through a hidden `BasicTextField`.** Enter arrives as `"\n"` from the IME and is forwarded raw; CTRL is Compose state read by the next IME edit; arrows always send `ESC [ A` (ignores DECCKM application-cursor mode) | `TerminalControls.kt` `TerminalKeyboardSink`, `dispatchKey` | E2, E3; vim/less arrow keys |
| **R3 Link emulation is not transactional.** `move_and_symlink_path` renames the file to `.l2s.<name>0001.0002` **before** creating the symlink; on Android storage `symlink()` is `EPERM` and nothing rolls back — the original is gone, git's rename fallback hits `ENOENT` (the recorded `unable to rename temporary '*.pack'`), debris stays | `third_party/proot/src/extension/link2symlink/link2symlink.c` 584–596 | E12; why terminal sessions run **without** `--link2symlink`; why dpkg fails in the terminal; therefore why the sudo bridge exists at all (E6–E9) |
| **R4 PRoot's exit status is not the shell's.** `last_exit_status` is overwritten by *any* tracee that exits normally; a signalled tracee never sets it; `event_loop` returns only when *all* tracees are gone | `third_party/proot/src/tracee/event.c` 252, 481, 436 | E10, E11 |
| **R5 Exit code and signal folded into one int.** `nativeWaitFor` returns "exit code, or 128+signal"; `exitDescription` then calls 130 "signal 2" | `NativeProcess.kt`, `TerminalContract.kt` 42 | E10 |
| **R6 No session-ending policy.** `Exited` keeps the tab; nothing closes it | `TerminalSessionManager.kt` | E10 |
| **R7 Spawn hygiene.** `icode_spawn.c` does not reset the signal mask inherited from ART, does not close the app's inherited descriptors, does not set `IUTF8` on the pty (backspace over multi-byte characters in cooked mode) | `app/src/main/cpp/icode_spawn.c` 120–160 | Latent; Termux's spawn does all three (`termux.c` 57, 75, 95) |

R3 is the ground-level one: fix it and the terminal can emulate hard links
everywhere, `apt`/`dpkg` work in the user's own pty, and the second process that
`sudo` hops to — with every one of E6–E9 — has no reason to exist.

## 3. Library verification (the confirmation integration-plan.md asked for)

- **Artifact:** `com.github.termux.termux-app:terminal-emulator:v0.118.3` and
  `:terminal-view:v0.118.3` on JitPack — POM returns 200. The wiki's
  `com.termux.termux-app:…` coordinates return 404 (stale).
- **API at v0.118.3** (read from the tag's source): `TerminalEmulator(TerminalOutput, cols, rows, cellW, cellH, transcriptRows, TerminalSessionClient)`,
  `TerminalRenderer(textSize, Typeface).render(…)`, `KeyHandler.getCode(keyCode, mode, cursorApp, keypadApp)` are public.
  `TerminalView.attachSession(TerminalSession)` takes the **final** class
  `TerminalSession`, which spawns through Termux's own JNI (`createSubprocess`:
  `setsid`, `IUTF8`, unblocked signals, inherited fds closed, `execvp`) and
  reports `waitFor` as exit code or **negative** signal.
  The process starts in `initializeEmulator`, i.e. on the view's first layout
  unless called explicitly. On exit it prints `[Process completed … - press Enter]`
  and calls `TerminalSessionClient.onSessionFinished`.
  Input: `onCreateInputConnection` uses `VISIBLE_PASSWORD | NO_SUGGESTIONS`,
  maps IME Enter to `\r`, and asks the client `readControlKey()` /
  `readAltKey()` — the hook Termux's own extra-keys row uses.
- **Licence:** `termux-app/LICENSE.md` (same text at v0.118.3): repository is
  GPLv3-only, **except** the Jackpal-derived code in `terminal-view` and
  `terminal-emulator`, named as Apache-2.0. No per-module licence file; one
  file carries an Apache header. iCode has no LICENSE file yet, so whether that
  residual ambiguity matters depends on how iCode is licensed — **owner's
  decision**, recorded here. PRoot (GPLv2) is already shipped as a separate
  executable.

**Decision: use `terminal-view` + Termux `TerminalSession` whole.** Using only
the emulator would keep our spawn but means hand-writing the IME connection,
selection handles, fling/scroll, mouse reporting and cursor — exactly the
hand-rolled layer that produced R2. The PRoot argv/env stay owned by
`RuntimeLayout` (one command builder); Termux's JNI only spawns them.

## 4. Target design

**Ownership map (backend-split-rules §4)**

| Fact | Owner | Observed by |
|---|---|---|
| PRoot argv / env | `RuntimeLayout.prootCommand/prootEnvironment` (unchanged role) | session manager, git, LSP, installer |
| Interactive session process + screen | Termux `TerminalSession` held by `TerminalSessionManager` | `ui/terminal` view host only |
| Session metadata (id, title, status, exit) | `TerminalSessionManager` → `TerminalSnapshot` (no `lines`) | tabs, top bar, gateway |
| Non-interactive processes (git, LSP, installer) | `RuntimeProcess` / `icode_spawn.c` | their services |

- **Rendering/input:** `ui/terminal/TerminalHost` = `AndroidView(TerminalView)`
  inside the existing Compose chrome (top bar, tab strip, extra keys). Colours
  mapped from `IcodeColors` into `TerminalColorScheme`; typeface from
  `TerminalSettings.fontFamily`; size from `fontSizeSp` × pinch scale.
- **Keys:** ESC/TAB/arrows go through `KeyHandler.getCode` with the emulator's
  cursor-app/keypad modes; CTRL/ALT stay sticky chips answered through
  `readControlKey/readAltKey` (consumed per key, so E3 cannot recur).
- **Preserved affordances:** tap focuses + shows keyboard; long-press on a URL
  opens it (verified behaviour); long-press elsewhere starts selection with
  copy/paste; pinch zoom; tabs select/reorder/close/new; Output-panel button;
  runtime gate; per-session titles.
- **Session end policy:** the tab lives exactly as long as its shell.
  PRoot gets `--kill-on-exit` (already in PRoot: only the initial tracee carries
  it) so background jobs end with the shell (E11). Shell exits by itself, any
  code → the tab closes; non-zero code → one transient notice "bash exited with
  code N". Shell could not start → tab stays with the reason and **Restart**.
  Closing the last tab by `exit` returns to the previous screen.
  Background work that should outlive a shell belongs in its own tab.
- **PRoot fidelity patches** (kept as `third_party/patches/proot/*.patch`,
  applied to a build-dir copy by CMake; the submodule stays on upstream tag
  v5.1.107.92 because commits there could not be fetched by anyone else):
  1. `link2symlink`: create the symlinks first and rename last, undoing each
     completed step on failure, so a refused link returns the kernel's `EPERM`
     with the file untouched; git then renames as it does without the flag.
  2. `event.c`: the exit status is the **initial** tracee's; if it was killed by
     a signal, PRoot re-raises that signal on itself after teardown, so
     `waitpid` on the host sees the truth.
  3. `--kill-on-exit`: SIGHUP first, SIGKILL after a 2 s grace (a hangup, as a
     closing terminal delivers), instead of immediate SIGKILL.
- **Exit reporting:** `nativeWaitFor` returns a structured result
  (`Exited(code)` / `Signalled(signal)`); `exitDescription` stops inferring.
- **`sudo`:** stays the word users type, now a guest script that runs the
  command in the same pty (`sudo -E/-H/-n/-S/--/-u root/-i/-s` accepted,
  `-u <other>` refused with a clear message). Prompts, `y`, Ctrl‑C, colours,
  progress bars all work because it is the same process tree. The dpkg speed-up
  moves to the standard place: `/etc/dpkg/dpkg.cfg.d/icode-unsafe-io` with
  `force-unsafe-io` (what container images ship), so it applies with or without
  `sudo`. `Dpkg::Use-Pty=0` is no longer needed once the emulator draws scroll
  regions. Deleted: `SudoBridge`, bridge bind and directory, `GuestSudoShell`
  hint wrappers (a hint saying "needs privileges" would now be false),
  `SudoPolicy`.
- **Spawn hygiene** in `icode_spawn.c` for the processes it still starts:
  unblock all signals and reset dispositions, close fds ≥ 3, `IUTF8` on ptys.
- **Env:** `TERM=xterm-256color` becomes true; add `COLORTERM=truecolor`
  (the emulator renders 24-bit colour).
- **Restore:** sessions do not survive process death (unchanged, architecture.md
  §540); after restart the terminal opens a fresh shell and says nothing false.

## 5. Cycles (each compiles, is tested, is verified on device)

| Cycle | Change | Verification |
|---|---|---|
| **T1 PRoot patches** | Patch files + CMake apply step; `prootCommand` always passes `--link2symlink`, sessions add `--kill-on-exit` | Device, workspace-bound session **with** link2symlink: `git init/add/commit`, `git clone` of icodeTest, `mkdir`/`mv`, `npm init` — no `.l2s.*` created; `sudo`-less `apt install tree` in the terminal succeeds; `exit` status 0/1/130 reported exactly; `kill -9 $$` reports SIGKILL; `sleep 25 & exit` ends in ≤ 2 s |
| **T2 Spawn hygiene** | `icode_spawn.c`: sigmask, fd close, IUTF8; structured wait result | `ls -l /proc/self/fd` in a git-run process shows 0–2 only; `trap -p`/`grep SigBlk /proc/self/status` = 0; unit tests for exit mapping |
| **T3 Engine swap** | Dependencies; `ui/terminal/TerminalHost`; manager holds Termux sessions; extra keys, URL long-press, pinch, theme; delete `AnsiTextBuffer`, `KeystrokeQueue`, `TerminalKeyboardSink`, `TerminalBody`, their tests, the degraded banner; debug `TerminalProbeActivity` moved to the new API | claude picker renders and Enter confirms; `vim`, `less`, `htop`, `nano`, `git log` pager, `apt` progress bar; arrows in vim (DECCKM); Ctrl‑C/Z/D; paste multi-line; autocorrect cannot touch input; rotation keeps the session |
| **T4 Lifecycle** | Session-end policy above; last-tab back navigation; Restart for failed start; notice for non-zero exit | `exit`, `exit 3`, `logout`, Ctrl‑D, `kill -9 $$`, bad shell path in settings, closing a tab with a running `sleep` |
| **T5 sudo** | Passthrough `sudo`, dpkg.cfg.d file, removal of bridge/hints/policy; installer migration deletes `runtime/bridge` and old wrappers on next session start | `sudo apt install htop` (answers `Y` at the prompt), `sudo bash -c 'read …'` gets `y`, Ctrl‑C mid-install leaves no process, `sudo -u nobody id` refused; timing vs the 84 s baseline for git |
| **T6 Acceptance + docs** | Update `Todo.md`, `sudo-capability-plan.md` (superseded), `next-cycle-plan.md`, `runtime/bootstrap/README.md` §6 (the "rename fails" row was this bug) | Full matrix above on a fresh install; `:app:testDebugUnitTest`, `:app:lintDebug`, `tools/check-ui-consistency.sh` |

**Not done without asking:** removing
`icodeTest/.git/objects/95/.l2s.tmp_obj_SKeJ3t0001.0002` from the user's
repository (it is a stray temporary object; git ignores it).

## 6. Record (2026-09-24, fresh install on device)

T1–T5 are implemented. Device results, all through the app's own terminal:

| Check | Result |
|---|---|
| `git add/commit` in the workspace with link emulation on | Commit written, no `.l2s.*` files (0001) |
| `apt-get install tree` / `curl` with no `sudo` | Works in the user's pty (bridge removed) |
| `sudo bash -c 'read -p "ok? " a'` + `y` | Prompt shown, `got:y` |
| `sudo apt install htop` | Real `[Y/n]` prompt, `y` installs |
| `sudo -u nobody id` | Refused: "the runtime has one user, root", exit 1 |
| `claude` (2.1.281) login picker | Renders; ▼ moves; Enter confirms |
| `ls -la /usr/bin \| less`, arrows, `q` | Alternate screen, scrolls, restores the prompt |
| `sleep 30 & exit` | Tab closes within 3 s, no process left |
| `kill -9 $$` | Tab stays: "Ended by SIGKILL"; Enter closes it (0002) |
| `exit 3` in the last tab | Tab closes, "bash exited with code 3", panel returns |
| Closing a tab with jobs running | Nothing survives untraced (0002 `PTRACE_O_EXITKILL`) |
| `/proc/self/status` in the guest | SigBlk 0, SigIgn 0 (0003); 4 open fds, was 31 |
| Scroll back while output arrives | View stays; "Jump to latest" returns |
| Long-press URL / word | Opens browser / selects with Copy |
| Background → resume, browser → back | Screen and session intact |
| Unit tests | 206 pass; lint 0 errors; UI consistency passes |

Deviations from §4, each for a measured reason: the guest's signal state is
reset in PRoot (0003) rather than in `icode_spawn.c`, so terminals get it too;
`libtermux.so` is built from the vendored `termux.c` because the prebuilt one
is 4 KB aligned; `PROOT_VERBOSE=-1` keeps PRoot's notes out of the terminal.

### Open

1. ~~Every system call costs ~50 µs~~ — **resolved 2026-09-24** by removing
   `PROOT_NO_SECCOMP=1` (no reason for it was recorded anywhere). The guest now
   carries PRoot's seccomp filter (`Seccomp_filters: 2`), so calls needing no
   path translation run natively. Same benchmark, same device, before → after:
   200 000 one-byte `read`+`write` 10 423 → 95 ms; `find /usr` 9 121 →
   4 956 ms; copy 255 files 4 874 → 3 970 ms; `git status` (231 files) 1 972 →
   1 668 ms. opencode's first launch (it installs 3 650 dependency files before
   drawing anything): unfinished after ~4 min → 95 s to a working interface;
   ~9 → ~53 files/s. Re-verified with the filter on: apt/dpkg, `sudo` + `y`,
   rootfs hard links, workspace `ln` refused cleanly, workspace mkdir/mv, git
   commit with no debris, signal state, pipes, exit codes, Ctrl-C, `less`,
   `exit` with a background job, `kill -9`, closing busy tabs. The network was
   never the limit: plain `curl` fetched the 60 MB opencode archive at 15 MB/s.
2. **`/proc` entries Android denies to apps** (measured): `stat`, `loadavg`,
   `uptime`, `version`, `vmstat`, `filesystems`, `sys/kernel/cap_last_cap`
   (`meminfo`, `cpuinfo` readable). `htop` stops at "Cannot open /proc/stat".
   proot-distro binds static stand-ins; that shows invented numbers. Owner's
   choice: stand-ins, or leave the honest error.
3. **Link-emulation bookkeeping sits next to the files it serves.** Visible in
   `/usr/bin` (`.l2s.perl.dpkg-new…`), and `rm -rf` of a directory holding an
   emulated hard link fails "Directory not empty", leaving `.l2s.a0001.0001`.
   A/B on device: identical with the seccomp filter on and off, so it predates
   that change. Affects trees inside the runtime (`/tmp`, home, dpkg's), not the
   workspace, where links are refused. Upstream's remedy is `PROOT_L2S_DIR` (one
   directory for all of it, as proot-distro does); with our 0001 a refused
   workspace link would then report `EXDEV` rather than the kernel's `EPERM`,
   so 0001 would need to fall back to the real `link()` for its answer.
   Proposed, not done.
4. Observed once, not reproduced in 3 tries each: a long-press that did not
   start selection; a terminal screen found blank after returning from the
   browser.
5. Not exercised on device: pinch zoom, rotation, Light palette, cursor-style
   setting. Licence decision in §3 still the owner's.
