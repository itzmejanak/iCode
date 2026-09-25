# Termux terminal JNI

`termux.c` is the native half of Termux's `terminal-emulator` library: it opens
the pseudo-terminal, spawns the shell and waits for it. It is copied unchanged
from

- repository: https://github.com/termux/termux-app
- tag: `v0.118.3` (commit `5b657c6`)
- path: `terminal-emulator/src/main/jni/termux.c`

— the same release as the `com.github.termux.termux-app:terminal-view` and
`terminal-emulator` artifacts in `app/build.gradle.kts`. Keep the two in step.

## Why it is built here

The library's prebuilt `libtermux.so` is linked for 4 KB memory pages. Android
devices with 16 KB pages cannot load it, and every native library iCode ships is
16 KB aligned (`app/src/main/cpp/CMakeLists.txt`). So `libtermux.so` is built
from this file with the rest of iCode's native code, and the prebuilt copy is
left out of the APK (`packaging.jniLibs` in `app/build.gradle.kts`).

## Licence

`termux-app`'s `LICENSE.md` releases the repository under GPLv3, except the
`terminal-emulator` and `terminal-view` libraries, which it names as Apache
License 2.0 (derived from Jackpal's Terminal Emulator for Android). This file is
part of `terminal-emulator`. See `docs/terminal-maturity-plan.md` §3.
