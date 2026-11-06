# Termux terminal emulator

The Java half of Termux's `terminal-emulator` library — the VT/xterm emulator, its buffer and the
session that runs a program in a pseudo-terminal — copied from

- repository: https://github.com/termux/termux-app
- tag: `v0.118.3` (commit `5b657c6adf4304e5198951ce815fe0205dcac29c`)
- path: `terminal-emulator/src/main/java/com/termux/terminal/`

— the release of `termux.c` in [`../termux-terminal-jni`](../termux-terminal-jni) and of the
`terminal-view` artifact in `app/build.gradle.kts`, which is taken without its own copy of this
library. Keep the three in step.

## Why it is here

iCode reads what a terminal prints, as the emulator gets it, to know where each command's output
starts and ends (docs/agent-control-plan.md §3 A2). The library gives no way to: `TerminalSession`
is final, and its bytes go from a private queue to the emulator alone.

## What iCode changed

Two additions to `TerminalSession.java`, each line marked `iCode:`:

- an `OutputTap` interface, `setOutputTap`, and the tap's call in `MainThreadHandler`, on the main
  thread, after the emulator has the bytes;
- `TerminalSession.replay(printed, transcriptRows, client)`: a session that runs nothing and shows
  a command's recorded output, so the view that draws a live terminal draws a finished command the
  same way (docs/agent-control-plan.md §3 A7). Its emulator is given the bytes when a view first
  sizes it; it has no pty to resize, and it has ended from the start, so nothing typed is sent.

Nothing else differs from the tag.

## Licence

`termux-app`'s `LICENSE.md` ([`LICENSE-termux-app.md`](LICENSE-termux-app.md)) releases the
repository under GPLv3, except the `terminal-emulator` and `terminal-view` libraries, which are
under the Apache License 2.0 (derived from Jackpal's Terminal Emulator for Android). This is
`terminal-emulator`; the change above is iCode's, under the same licence.
