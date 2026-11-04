# Third-party notices

iCode includes or builds on the following components. Each keeps its own licence. This list covers
what is in this repository and `app/build.gradle.kts`. Check each project for its full terms.

## Native code built into the app

| Component | Source | Licence |
|---|---|---|
| PRoot (Termux fork) | `third_party/proot` submodule, https://github.com/termux/proot | GPL-2.0 |
| libandroid-shmem | `third_party/libandroid-shmem` submodule, https://github.com/termux/libandroid-shmem | BSD-3-Clause |
| talloc 2.4.3 | `third_party/talloc` (from samba.org) | LGPL-3.0-or-later |
| Termux terminal JNI | `third_party/termux-terminal-jni` | Apache-2.0 |
| RaTeX 0.1.15 (`libratex_ffi.so`) | `third_party/ratex` | See `app/src/main/resources/licenses/RaTeX.txt` |

## Libraries

| Component | Licence |
|---|---|
| AndroidX and Jetpack Compose | Apache-2.0 |
| Kotlin and kotlinx libraries | Apache-2.0 |
| Termux terminal-view | Apache-2.0 |
| Sora Editor and its TextMate module | LGPL-2.1 |
| Koog | Apache-2.0 |
| Ktor and OkHttp | Apache-2.0 |
| JetBrains Markdown | Apache-2.0 |
| Apache Commons Compress | Apache-2.0 |
| XZ for Java | 0BSD |
| colorpicker-compose | Apache-2.0 |
| Shizuku API and provider 13.1.5 | MIT |

## Fonts and assets

| Component | Licence file |
|---|---|
| IBM Plex Mono | `app/src/main/resources/licenses/OFL-IBM-Plex-Mono.txt` (OFL-1.1) |
| Chakra Petch | `app/src/main/resources/licenses/OFL-Chakra-Petch.txt` (OFL-1.1) |
| KaTeX fonts | `app/src/main/resources/licenses/OFL-KaTeX.txt` (OFL-1.1) |
| TextMate grammars | `app/src/main/assets/textmate/` (provenance in `docs/editor-grammar-provenance.md`) |

## Downloaded at runtime (not bundled)

The Ubuntu base image and optional tools (for example the Android command-line tools, CMake and
Ninja) are downloaded on the device from the sources in `app/src/main/assets/runtime/manifest.json`,
under their own licences. The Android SDK licence is shown before it is installed
(`app/src/main/assets/runtime/licences/android-sdk-license.txt`).
