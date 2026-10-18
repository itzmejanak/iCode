# Signed Commits — gpg's Passphrase in iCode's Dialog

Status: **built (2026-10-18)**; the phone check is §5.

Owner ask (2026-10-18): "The goal is to achieve gpg setup for signed commits and the password asking popup as gpg behavior after 30 min … don't overengineer or don't write code unnecessary".

## 1. What stood in the way (verified)

| Fact | Where it was checked | What follows |
|---|---|---|
| iCode runs `git` itself: `commit.gpgsign` and `user.signingkey` in `~/.gitconfig` already reach every commit iCode makes | `GitRepositoryClient.commit`, `RuntimeLayout.prootEnvironment` (`HOME=/root`) | Nothing to change in Git |
| A git command runs through pipes, without a terminal (`NativeProcess`). gpg-agent's pinentry-curses needs one: a passphrase-protected key fails with `Inappropriate ioctl for device` | The earlier analysis's test of the same pipe-only model, in a sandbox | The passphrase must be asked another way: a pinentry of iCode's own |
| Every command iCode runs is a PRoot session with `--kill-on-exit`. The gpg-agent that gpg starts during a commit is killed when the commit ends, and its cache with it | `GuestCommand.run`, `RuntimeLayout.prootCommand` | The agent must run in a session of its own. iCode starts it there |
| PRoot rewrites a Unix socket's path to its host path. So gpg in any session reaches an agent started in another, through `~/.gnupg/S.gpg-agent` (about 74 bytes on the phone; the limit is 108) | PRoot `src/syscall/socket.c`, `translate_socketcall_enter` | One agent serves the terminal, iCode's commits and the assistant |
| Without `--kill-on-exit`, PRoot ends only when its last tracee does | PRoot `event_loop`: `waitpid` until `ECHILD` | The agent's session lives exactly as long as the agent |
| GnuPG 2.4 reads `/etc/gnupg/<component>.conf` first, then the user's `~/.gnupg/<component>.conf`, which overrides it | `gpgrt_set_confdir (GPGRT_CONFDIR_SYS …)` in `agent/gpg-agent.c` and `g10/gpg.c`; tested: the user's `default-cache-ttl 900` won over the system's 1800 | iCode writes the system's file and never touches the user's |
| With no agent, gpg runs its `agent-program --homedir DIR --use-standard-socket --daemon`, waits for it to exit with 0, then connects (5 s more) | `start_new_service` in `common/asshelp.c` | iCode's agent program waits until iCode's agent answers |
| `gpgconf --launch` takes the spawn lock gpg already holds, and waits on it forever. `gpg-agent --daemon` takes none: it binds its socket, forks, and its parent exits 0 | `lock_spawning` (`dotlock_take (…, -1)`); `agent/gpg-agent.c` | iCode runs `gpg-agent --daemon`, never `gpgconf --launch` |
| A second `gpg-agent --daemon` exits 2 while one runs. `gpg-agent` with no command exits 0 when one answers | Tested | The status iCode reports is the second command's |
| `default-cache-ttl` is how long a passphrase is kept after its last use (gpg's default 600 s); `max-cache-ttl` caps it (default 7200 s) | GnuPG manual; tested with 3 s | "Asks again after 30 min", as gpg does it: `default-cache-ttl 1800` |
| gpg-agent asks again by itself when a pinentry does not confirm a repeated passphrase (`Please re-enter this passphrase`). It names the second button with `SETCANCEL` when a pinentry does not know `SETNOTOK` | `agent/genkey.c`, `agent/call-pinentry.c` | The dialog needs one field and two buttons |
| Ubuntu's `gpg`, `gpg-agent` and `gpgconf` ship nothing in `/etc/gnupg`. iCode installs without recommends, so `gpg-agent` is named; `pinentry-curses` comes as its dependency | `dpkg -L`, `apt-cache depends` (noble) | No conffile conflict; two packages |

## 2. Decisions

1. **GnuPG is a runtime tool** of its own, opt-in: `gpg` and `gpg-agent`, "Signed commits, the passphrase asked in iCode". It is not in Standard and not bundled with Git: signing is the user's choice, and Git's installed state does not depend on it.
2. **The runtime carries iCode's gpg settings**, written with it as `sudo` and `/etc/gitconfig` are (`GuestGpg`, at install, repair and reset):
   - `/usr/local/bin/icode-pinentry` and `/usr/local/bin/icode-gpg-agent`: one script, written by `GuestGpg.script` as `GuestSudo` writes `sudo`. iCode's own words in it — the folder, `.request` and `.answer`, the request kinds — come from the Kotlin side, so each is said once;
   - `/etc/gnupg/gpg.conf`: `agent-program /usr/local/bin/icode-gpg-agent`;
   - `/etc/gnupg/gpg-agent.conf`: `pinentry-program /usr/local/bin/icode-pinentry` and `default-cache-ttl 1800`.
3. **iCode starts the agent** (`GpgBridge`, owned by `RuntimeExecution`). gpg finds none, runs iCode's agent program, and that asks iCode. iCode runs `gpg-agent --daemon` in a PRoot session without `--kill-on-exit`, then `gpg-agent`'s own check, and writes its status back. The agent then outlives every command and keeps the passphrase as its cache says. A runtime rebuild ends it, and so does the end of iCode's process.
4. **The pinentry asks in iCode's dialog**: gpg-agent's own description ("Please enter the passphrase to unlock the OpenPGP secret key: …"), its error ("Bad Passphrase (try 2 of 3)"), a masked field, and its own button labels. Cancel answers as pinentry's Cancel.
5. **How the two sides talk**: the script writes `<pid>.request` into the guest's `/tmp/icode-gpg` and reads one line from the pipe `<pid>.answer` beside it. iCode watches the folder (`FileObserver`), answers into the pipe without blocking, and closes the dialog when the request goes: answered, or taken back by gpg-agent when gpg went away.
   - The folder deleted under the watch (the guest's `/tmp` emptied): iCode makes it again, watches it again, and reads what was handed over meanwhile.
   - The runtime rebuilt: what waits on an answer is told Cancel, and the agent ends.
6. **The passphrase is never kept by iCode.** It goes from the field through the pipe to gpg-agent: not to disk, not into iCode's state, not into a log (`GpgAnswer.Passphrase` prints `***`). The field is `remember`, not `rememberSaveable`.
7. **The answer never waits on the mutation lock**: the commit waiting on the passphrase holds it (`GpgIntent`). `GpgIntent` is declared beside its types in `GpgPrompts.kt`, a member of `ICodeIntent`'s sealed family: `ICodeIntent.kt` is at its 300-line budget.
8. **Trust is gpg-agent's own.** Any program of the user's in the runtime can ask gpg-agent for a passphrase with a text of its choosing, as on any computer with gpg-agent. The runtime is the user's own; nothing outside it can reach the folder, which is in iCode's private storage.

## 3. What iCode does, and what the user does

**iCode, by itself:**

| When | What |
|---|---|
| The runtime is built (install, Repair, Reset) | The pinentry, the agent program and `/etc/gnupg/gpg.conf` and `gpg-agent.conf` are written |
| gpg finds no agent | iCode starts one in a session of its own; it serves the terminal, Source Control and the assistant alike |
| gpg-agent needs the passphrase | The GnuPG dialog, over any screen |
| 30 minutes after the passphrase was last used (2 hours at most) | gpg-agent forgets it, and the next signature asks again |
| The runtime is rebuilt, or iCode's process ends | The agent ends, and the passphrase with it |

**The user, once, in the terminal** — keys and Git's settings are the user's, as on any computer:

1. **Runtime Tools › GnuPG**, then Apply.
2. A key, or a passphrase for the key already there:
   ```sh
   gpg --full-generate-key                      # a new key: the passphrase is asked in iCode's dialog
   gpg --passwd <ID>                            # or: a passphrase for a key that has none
   gpg --list-secret-keys --keyid-format=long   # the key's ID
   ```
3. Git signs with it:
   ```sh
   git config --global user.signingkey <ID>
   git config --global commit.gpgsign true
   ```
4. GitHub knows it: `gpg --armor --export <ID>`, pasted in GitHub › Settings › SSH and GPG keys. GitHub shows a commit as Verified when its committer email is one of the key's, so a workspace's own `user.email` must be that email too.

**A runtime built before this version** has none of iCode's gpg files until it is rebuilt: Runtime settings › **Repair Runtime** writes them. Repair keeps the home — the keys in `~/.gnupg`, `~/.gitconfig` — and installs packages anew, so GnuPG is ticked again in Set Up Tools or Runtime Tools.

**Changing what gpg does**, in gpg's own files:

| | |
|---|---|
| Ask again sooner or later | `default-cache-ttl <seconds>` in `~/.gnupg/gpg-agent.conf`, then `gpgconf --kill gpg-agent`: the next signature starts it with the new time |
| Forget the passphrase now | `gpgconf --reload gpg-agent` |

## 4. As built

| Where | What |
|---|---|
| `domain/RuntimeTools.kt` | `GnuPg` |
| `domain/GpgPrompts.kt` | `GpgPrompt`, `GpgAnswer`, `GpgRequest`, `GpgIntent`, `GpgRequests`: the request kinds, parsing, Assuan's escapes, the answer line |
| `data/runtime/GuestGpg.kt` | The script (`script`) and `/etc/gnupg`, written by `RuntimeInstaller.configureGuest` beside `GuestSudo` |
| `data/runtime/GpgBridge.kt` | Watches the requests, starts the agent, answers; owned by `RuntimeExecution` |
| `ui/GpgPromptDialog.kt`, `IcodeApp` | The dialog, over any screen; `IcodeConfirmDialog` gained `dismissLabel` |

Tests:
- `GuestGpgTest` runs the script with a real POSIX shell, as `GuestSudoTest` runs `sudo`. It speaks gpg-agent's side of the protocol and answers through the pipe as iCode does: a passphrase passed on, Cancel, the error said once, a confirmation's buttons, `SETNOTOK` unknown, the question taken back, and the agent program's exit status. Nothing is left behind.
- `GpgRequestsTest`: parsing and the answer line, with the lines gpg-agent 2.4.4 sent.
- By hand, with gpg 2.4.4 (Ubuntu 24.04's) and the script as `GuestGpg.script` writes it:
  - the agent started iCode's way, signing, a wrong passphrase and its retry, Cancel;
  - the cache: asked once, then not; asked again after a 3 s TTL, and after `gpgconf --reload gpg-agent`;
  - `git commit -S` with "Good signature", and a question taken back when gpg was killed;
  - key generation (the passphrase, its repeat, the empty-passphrase confirmation) and `gpg --passwd`.

## 5. On the phone, open

On a clean install, §3's steps; then commit from Source Control. The dialog appears once, a second commit within 30 minutes signs without it, and `git log --show-signature` says Good signature.

## 6. Not done, and why

| | Why |
|---|---|
| Key generation and Git's signing settings in iCode's own pages | The terminal does it in a few commands, once (§3) |
| "Verified" in Source Control | GitHub shows it; `git log --show-signature` in the terminal |
| A cache time to choose in Settings | gpg's own file chooses it (§3) |
| A notification when gpg asks while iCode is in the background | The question waits; the dialog is there on return, and the command waits with it |
