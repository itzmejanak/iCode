<!-- Managed by iCode: written at every iCode start, so a change here does not last. -->
# This runtime

This is your Ubuntu, on your phone. The terminal, the Explorer and the assistant are three ways
into this one system: what one installs, sets or writes, the others see.

## What iCode holds to

- **One filesystem.** Every workspace is under `/workspace`, for every program. The rest is this
  runtime's own root filesystem, but for Android's own `/dev`, `/proc`, `/sys` and system folders.
- **One environment.** Every program starts with what a Linux login sets (`HOME`, `USER`, `SHELL`,
  `PATH`, `LANG`), then your login profile over it, then what its context needs over both.
- **Your shell.** A terminal starts root's login shell from `/etc/passwd`; `chsh -s` changes it.
- **Your files.** In a file of yours iCode only adds lines of its own, and you may delete them.
  What it writes says "Managed by iCode:" in it, and
  `grep -rl "Managed by iCode" /etc /usr/local /root 2>/dev/null` lists it all. Two things cannot
  say so: the `aid_*` groups in `/etc/group`, which name Android's groups for this app so
  `groups` and `ls -l` can, and `~/.icode/`, iCode's record of the Runtime Tools it set up.

## Contexts

`ICODE_CONTEXT` says who a program runs for, and `env` shows what it was given.

| Context | Who | Your profile |
|---|---|---|
| `terminal` | a terminal tab | read by the login shell itself |
| `command` | the assistant's commands and processes, the Run tab, Build & Run, Runtime Tools | read |
| `git` | Source Control, the assistant's Git views, gpg when it signs | read |
| `language-server` | the editor's language servers | read |
| `search` | Search, and the assistant's | read |
| `install` | the runtime's install, Repair and checks | not read, so a broken profile never stops them |

`command`, `git`, `language-server` and `search` have no terminal, so nothing in them waits on a
pager or a prompt. When a profile cannot be read (it fails, or never ends), what ran says so, and
it ran with iCode's defaults.

## Limits

Android's and PRoot's, not iCode's settings:

- **Root without root's powers.** You are uid 0 here, under PRoot, not on the phone: no mounting,
  no kernel modules, no raw devices. `sudo` is iCode's stand-in and runs the command as it is; a
  `sudo` you install with apt runs instead.
- **No init system.** No systemd, and nothing starts on its own: start a server yourself.
- **Hard links are emulated.** PRoot keeps one as a link to a hidden `.l2s.` file; renaming the
  folder that holds one breaks both names.
- **No `/proc/net`.** Android hides it from apps, so `netstat` and the like see no sockets.
- **Ports from 1024.** Android lets no app listen below 1024. A server here is at
  `http://127.0.0.1:<port>` on the phone.
- **Android ends processes past a limit.** From Android 12 it stops apps' child processes beyond
  about 32 in all; one it stops ends with SIGKILL.
- **Restartable sequences are off** (`GLIBC_TUNABLES=glibc.pthread.rseq=0`): glibc's misbehave
  under PRoot's tracing.
- **The time zone is the runtime's own**, UTC as installed, not the phone's: `apt install tzdata`,
  then `dpkg-reconfigure tzdata`, or set `TZ` in your profile.
