# Third-party notices

iCode is licensed under GPL-3.0, but it includes, links to, or downloads components under other terms. This file is a notice and provenance index. It is not a replacement for the referenced license texts, a complete inventory of every transitive dependency, or a legal compatibility conclusion.

Versions and identifiers below reflect the current source and local artifact metadata. Distributors must review the corresponding source, license, notice, source-offer, relinking, and attribution requirements for the exact release they ship. Remaining release-audit work is tracked in [`docs/roadmap.md`](docs/roadmap.md).

## Native code and vendored sources

| Component | Repository location or source | License record |
|---|---|---|
| PRoot 5.1.107.92, Termux fork | `third_party/proot` Git submodule; <https://github.com/termux/proot> | `third_party/proot/COPYING` (GPL-2.0-only) |
| libandroid-shmem | `third_party/libandroid-shmem` Git submodule; <https://github.com/termux/libandroid-shmem> | `third_party/libandroid-shmem/LICENSE` (BSD-3-Clause) |
| talloc 2.4.3 | `third_party/talloc` | `third_party/talloc/LICENSE` and the source headers (LGPL-3.0-or-later) |
| Termux terminal JNI | `third_party/termux-terminal-jni` | Apache-2.0; provenance and license explanation in its `README.md` |
| Termux terminal emulator 0.118.3, including iCode changes | `third_party/termux-terminal-emulator` | `third_party/termux-terminal-emulator/LICENSE-termux-app.md` and `README.md` (Apache-2.0 exception for terminal libraries) |
| RaTeX 0.1.15 native library | `third_party/ratex` | `app/src/main/resources/licenses/RaTeX.txt` |

The native build copies PRoot and libandroid-shmem into the build directory and applies `third_party/patches/<component>/*.patch`. The checked-out submodules remain unchanged.

## Packaged libraries

This table names the principal direct dependencies. `app/build.gradle.kts` is the version authority.

| Component | Version in source | License or notice source |
|---|---:|---|
| AndroidX and Jetpack Compose | Gradle-managed | Apache-2.0 |
| Kotlin and kotlinx libraries | Gradle-managed | Apache-2.0 |
| Termux terminal-view | 0.118.3 | Apache-2.0 |
| Sora Editor and TextMate module | 0.24.6 | LGPL-2.1-or-later in the bundled Sora grammar/catalog records; verify artifact terms for release |
| Sora `oniguruma-native` native Oniguruma binding | 0.24.6 | The locally resolved POM declares LGPL-2.1; the cached AAR contains no embedded license text, so native-engine notices require release-audit verification |
| Koog | 1.3.0 | Apache-2.0 |
| Ktor and OkHttp | Gradle-managed | Apache-2.0 |
| MCP Kotlin SDK client | 0.16.0 | Apache-2.0, with earlier contributions identified under MIT by the upstream project |
| JetBrains Markdown | 0.7.17 | Apache-2.0 |
| Apache Commons Compress | 1.27.1 | Apache-2.0 |
| XZ for Java | 1.10 | 0BSD |
| colorpicker-compose | 1.1.4 | Apache-2.0 |
| Shizuku API and provider | 13.1.5 | MIT, as declared by the locally resolved module POMs |
| AndroidSVG | 1.4 | Apache-2.0 |
| `desugar_jdk_libs` | 2.1.5 | GPL-2.0-only with the Classpath Exception, as declared by the locally resolved POM |

The Android package configuration excludes dependency `META-INF/LICENSE*` and `META-INF/NOTICE*` resources to avoid merge conflicts. A release audit must therefore confirm that required notices and license texts are supplied through appropriate bundled or accompanying materials.

## Fonts, grammars, icons, and palettes

| Asset | Provenance and license record |
|---|---|
| IBM Plex Mono | `app/src/main/resources/licenses/OFL-IBM-Plex-Mono.txt` (OFL-1.1) |
| Chakra Petch | `app/src/main/resources/licenses/OFL-Chakra-Petch.txt` (OFL-1.1) |
| KaTeX fonts | `app/src/main/resources/licenses/OFL-KaTeX.txt` (OFL-1.1) |
| 357 TextMate language and format definitions | Machine-readable source, commit, path, and license fields in `app/src/main/assets/textmate/catalog.json`; human-readable catalog in [`docs/editor-grammar-provenance.md`](docs/editor-grammar-provenance.md); imported by `tools/import-grammars.py` |
| Material Icon Theme file and folder icons | `app/src/main/assets/file-icons/`; imported by `tools/import-file-icons.py`; `app/src/main/resources/licenses/MIT-Material-Icon-Theme.txt` |
| Lobe Icons 1.103.0 assistant provider marks (OpenAI, Anthropic, Gemini, OpenRouter, Ollama) | `app/src/main/res/drawable/mark_*.xml`, each converted from the package's `icons/<name>.svg` path data and naming its source file; `app/src/main/resources/licenses/MIT-Lobe-Icons.txt`. The marks themselves are their owners' trademarks, shown only to name the provider. |
| Theme palettes | `app/src/main/java/dev/icode/mobile/designsystem/palettes/`; provenance and notices in `app/src/main/resources/licenses/MIT-Theme-Palettes.txt` |

The grammar catalog includes multiple licenses, including MIT, Apache-2.0, BSD variants, ISC, MPL-2.0, LGPL, GPL-3.0, Unlicense, and the TextMate Bundle License. Consult each catalog entry rather than applying one license to the whole grammar set.

## Downloaded at runtime

The Ubuntu base image and optional runtime tools are not bundled in the APK. When requested, they are downloaded from the exact sources recorded in the bundled runtime manifest, whose role and integrity boundary are documented in [Runtime and terminal](docs/runtime-and-terminal.md#exact-role-of-the-runtime-manifest). Current categories include:

- Ubuntu base and packages obtained through Ubuntu repositories;
- Android command-line tools, Platform, Build Tools, and related Google packages;
- ARM64 Android host tools;
- CMake and Ninja;
- optional Android NDK toolchains;
- Node.js; and
- uv/uvx.

These components retain their own upstream terms. The Android SDK license shown before SDK installation is stored at `app/src/main/assets/runtime/licences/android-sdk-license.txt`. The runtime manifest is provenance and integrity metadata, not a substitute for each downloaded component's license.

## Bundled license resources

Additional license texts and attribution records currently shipped in the source tree are under `app/src/main/resources/licenses/`. Submodules and vendored directories may also contain their own authoritative license files. Review both locations, the Gradle dependency graph, generated assets, native outputs, and the runtime manifest for every release.
