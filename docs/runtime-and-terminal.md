# Runtime and terminal

This document defines the Linux runtime, Runtime Tools, terminal, managed-process, and Build & Run behavior shipped in iCode. Current implementation and tests are authoritative; open implementation and acceptance status is tracked in the [runtime and Android toolchains roadmap](./roadmap.md#runtime-and-android-toolchains).

## Status

| Area | Status | Current scope |
|---|---|---|
| Ubuntu guest installation and launch implementation | Done | The app implements installation of one ARM64 Ubuntu guest in app-private storage and launches it through PRoot. Real-device lifecycle acceptance remains separate. |
| Runtime Tools orchestration and package serialization | Done | Tool selection, dependency closure, receipts, setup flows, and one serialized dpkg writer are implemented. Real package and tool installation remains separate. |
| Terminal session and background-service implementation | Done | Multiple real PTY sessions are owned process-wide and represented by a foreground service while work exists. Device and process-pressure acceptance remains separate. |
| Existing Android project Build & Run implementation | Done | The app implements debug builds for existing Gradle Android application modules, Android-confirmed installation, and launch. Toolchain and device acceptance remains separate. |
| New Android app template | Not Started | The template remains roadmap work; there is no production implementation. |
| Runtime, terminal, Android toolchain, and device acceptance | Needs Verification | Canonical installation, SDK, NDK, CMake, Build & Run, process-pressure, terminal-lifetime, install, and launch checks live in the [runtime roadmap](./roadmap.md#runtime-and-android-toolchains). |

## Runtime identity

The bundled runtime manifest currently selects one guest:

- architecture: `arm64-v8a`;
- root filesystem: `ubuntu-24.04.5-base-arm64`;
- base: Ubuntu 24.04.5 LTS (noble) base;
- PRoot: `5.1.107.92` from Termux's AArch64 build;
- minimum allocatable storage before installation: `1,610,612,736` bytes;
- expected rootfs archive entries for progress: `3,413`.

The guest is Ubuntu user space running through PRoot. PRoot starts it with fake uid 0 so Ubuntu package tools can operate, but iCode remains an ordinary Android app UID. Guest `root` is not Android root and gains no kernel privilege.

PRoot and its loader ship as extracted native libraries under Android's executable `nativeLibraryDir`. The writable root filesystem lives under app-private `files/runtime/rootfs`; Android does not permit iCode to execute arbitrary binaries copied into its normal writable data directory.

## Exact role of the runtime manifest

`app/src/main/assets/runtime/manifest.json` is the bundled source of install and Runtime Tool pins. Schema 1 carries:

- the required guest architecture;
- rootfs identity, source URL, SHA-256, minimum free bytes, and archive-entry count;
- the PRoot version shown by runtime inspection;
- pinned archives downloaded by tool-specific setup code, each with a digest and optional byte count;
- expected download sizes for packages fetched by another installer, such as sdkmanager.

The manifest is not an inventory of every installed file. Runtime installation reads the rootfs descriptor, while Android SDK, CMake, NDK, Node.js, and uv setup code reads its own named archive pins. The installer rejects a device whose supported ABI list does not contain the manifest architecture.

A valid website-published manifest can override the bundled manifest when it uses the same schema and architecture. The last valid website copy remains cached when the website is unreachable, returns malformed content, or supplies an unusable schema or architecture. An explicit “not published” response clears the website cache and returns selection to the bundled manifest. Refreshing the manifest does not reinstall anything; the selected manifest applies to the next runtime installation or Runtime Tool download.

Checksums belong in the manifest rather than prose. A download that does not match its current pin is deleted and never installed. This is especially important for upstream release assets that may be replaced at the same URL.

## Installation, Repair, and Reset

All three flows use one state-driven installer and report readiness only after an actual command runs inside the guest.

### Common install sequence

1. Read and validate the selected manifest and device ABI.
2. Verify the shipped PRoot binaries are present.
3. Reserve the manifest's minimum allocatable storage. Android may reclaim other apps' cache through `StorageManager` when the platform permits it.
4. Reuse a verified cached rootfs archive or delete, redownload, and digest-check it as bytes arrive.
5. Remove the prior rootfs and extract the archive directly from Android into app-private storage. Extraction does not run under PRoot.
6. Require at least `bin/sh` and `usr/bin/apt` in the extracted tree.
7. Write iCode-managed system files, guest group names, the `sudo` stand-in, GPG bridge files, runtime README, and the default prompt line only for a fresh home.
8. Run a guest health check that reads `/etc/os-release`.
9. Write `installed.json` only after the health check succeeds, create the Runtime Tools offer marker, remove the cached rootfs archive, and publish Ready.

A cancelled, partial, or failed install has no ready marker and is not reported as usable.

### Lifecycle differences

| Action | Home behavior | Package behavior | Workspace behavior |
|---|---|---|---|
| Initial install | Creates the guest's first `/root`. It refuses to replace a home preserved by an interrupted Repair. | Starts from the base image. | Leaves workspaces unchanged. |
| Repair | Moves `/root` to `files/runtime/home-backup`, rebuilds the system, and restores the same home. The backup and repair marker survive failure, process death, and retry. | apt/dpkg packages are not preserved because they live in the rebuilt system. Runtime Tool receipts remain in `/root`; a matching setup can reinstall missing packages and rerun `configure()` without redownloading setup archives. | Leaves workspaces unchanged. |
| Reset | Builds a fresh home and discards any older repair backup only after the new runtime verifies. | Removes installed packages, tool setup data, receipts, SDKs, Gradle home configuration, SSH/GPG material, and the debug keystore that lived under `/root`. | Leaves workspaces unchanged because they live outside the rootfs. |

Repair and Reset require explicit confirmation and are refused while runtime users are active. That protection prevents rebuilding the filesystem beneath terminal sessions, managed processes, language servers, or other guest work.

## Storage and one guest filesystem

There is one guest root, one `/root`, and one workspaces tree for every runtime context.

- iCode stores workspaces under its app-private `files/workspaces` directory.
- Every PRoot launch binds that whole directory at `/workspace`.
- Every workspace therefore appears as `/workspace/<workspace-name>` to terminals, Source Control, search, language servers, assistant commands, Runtime Tools, and managed processes.
- A context may start in one workspace, but the mount does not hide the others.

Shared phone storage is optional and is not needed to install Ubuntu. iCode declares All Files Access for features that operate on phone storage and requests it only when such a feature needs it. When `/storage/emulated/0` exists, PRoot binds it at that Android path and iCode creates `/sdcard` as a symlink if the user has not already placed something there. Android's permission determines whether the app can use the bound path. Once permission is granted, an already-running terminal can access it because Android enforces access dynamically rather than at terminal creation.

Workspaces remain app-private even when shared-storage access is granted. Shared storage also does not provide the same link, executable-bit, case, or package-manager behavior as the app-private workspace filesystem.

## One login environment, named contexts

“One environment” means one guest filesystem and one layered login environment, not that a PTY and a noninteractive process receive identical terminal-control variables.

PRoot itself receives only host plumbing such as `LD_LIBRARY_PATH`, `PROOT_LOADER`, `PROOT_TMP_DIR`, and `PROOT_VERBOSE`. Each guest program starts through `/usr/bin/env -i`, so those host paths do not leak into the guest.

The guest base environment is:

- `HOME=/root`;
- `USER=root` and `LOGNAME=root`;
- `SHELL` from root's `/etc/passwd`, falling back to `/bin/bash` if unavailable;
- iCode's guest `PATH`, with `/root/.local/bin` first;
- `LANG=C.UTF-8`;
- `GLIBC_TUNABLES=glibc.pthread.rseq=0`;
- `ICODE_CONTEXT=<context>`.

The runtime then layers the user's login profile over the base for contexts that act on the user's behalf without their own login shell. It reads the profile with root's configured login shell, caches the exported variables, and rereads them when known profile files change. Profile capture has a 10-second bound. A failing, hanging, or early-`exec` profile falls back to the base environment and reports that fallback.

| `ICODE_CONTEXT` | Execution | Profile behavior |
|---|---|---|
| `terminal` | User terminal PTY | The login shell reads its own profile. |
| `assistant-terminal` | Assistant's terminal PTY | Bash reads the profile; pagers and unattended prompts are disabled. |
| `command` | Assistant commands, Run, Build & Run, Runtime Tools | Cached login profile, then noninteractive overrides. |
| `git` | Source Control, assistant Git views, GPG agent | Cached login profile, then noninteractive overrides. |
| `language-server` | Editor language servers | Cached login profile, then noninteractive overrides. |
| `search` | Workspace search | Cached login profile, then noninteractive overrides. |
| `install` | Runtime install, Repair, health checks, profile capture | Does not read the user profile, so a broken profile cannot block repair. |

Terminal contexts receive `TERM=xterm-256color` and true-color support. Non-PTY contexts receive `TERM=dumb`, `PAGER=cat`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`, and `DEBIAN_FRONTEND=noninteractive` so nothing waits on an invisible interface.

`chsh` changes root's recorded shell. New terminal tabs start that shell as a login shell. If it is recorded but no longer installed, the tab explains the problem and falls back to `/bin/bash -l`.

## GuestCommand

`GuestCommand` is the boundary for one bounded, noninteractive operation:

- it runs `/bin/sh -c` without a PTY;
- it starts in `/root` by default or at any host location that maps into the guest;
- its entire argument vector is POSIX-quoted;
- it creates a unique app-private guest `/tmp` file for stderr and can poll it for progress;
- it always launches PRoot with `--kill-on-exit`;
- normal captured stdout is limited to 8 MiB;
- retained stderr is the last 1 MiB;
- one streamed record is limited to 16 MiB.

These are in-memory output bounds, not limits on files the command writes. `GuestCommand` itself imposes no generic wall-clock timeout. Callers may apply their own timeout or cancel the coroutine.

Cancellation, an output consumer stopping early, or a read failure sends SIGKILL to PRoot, closes the pipe by killing its writer, waits for and reaps the process, destroys the native handle, and deletes the stderr file. PRoot's `--kill-on-exit` behavior and iCode's PRoot patch prevent traced children from surviving the command.

## Managed processes and process count

Long-running work uses `RuntimeProcesses` instead of `GuestCommand`. Each managed process receives:

- its own PRoot process;
- open stdin;
- merged incremental stdout/stderr;
- a stable in-process ID and runtime log;
- running/ended/stopped state;
- detected localhost ports where output identifies them;
- Stop support.

Stopping first sends SIGTERM to the process group. After three seconds, iCode sends SIGKILL if PRoot is still running. A managed process lasts until it exits, the user stops it, or Android ends iCode's process.

iCode has no artificial managed-process count cap. Android remains the external constraint: Android 12 and later may kill app child processes when the platform's approximate cross-app child-process limit is reached. The guest README describes that limit as about 32. Such a kill appears as SIGKILL; iCode does not reserve or virtualize process slots.

## Runtime Tools and package serialization

Runtime Tools is the supported UI for optional guest tooling. The current catalog includes:

- Standard: Git, OpenSSH client, and ripgrep;
- optional: GnuPG, Android SDK, CMake 3.22.1, NDK 28.2.13676358, NDK 27.0.12077973, Node.js, uv, curl, Python 3, and native build tools.

A feature names a catalog tool rather than duplicating package names or paths. A tool counts as installed when dpkg contains all of its packages and, where it has a custom setup, the setup receipt matches this build's current pins. Receipts live under `/root/.icode/runtime-tools`, so Repair preserves them; a changed pin makes the prior setup no longer current.

One `RuntimePackageInstaller` owns all apt/dpkg mutation:

- one mutex serializes every package-list update, install, and removal;
- requests for the same in-flight tool share one operation;
- requests for different tools queue;
- the operation belongs to process scope, so leaving a screen or cancelling a feature caller does not interrupt dpkg;
- Apply removes deselected tools first, then installs selected tools;
- tool dependencies close transitively, so an NDK also selects Android SDK and CMake.

An apt install downloads first with `apt-get install -d`; that phase is cancellable because dpkg has not changed. The subsequent apt install and package-removal mutation are deliberately non-cancellable. Custom setup steps declare whether each phase can be stopped. Setup removal runs before apt decides which packages can safely be removed.

Download-size measurement does not install anything. It combines apt `--print-uris` results with manifest-pinned setup sizes. Measurements may be batched, but an actual install takes precedence and all dpkg mutation remains serialized.

## Android SDK, CMake, and NDK setup

### Android SDK

The **Android SDK** Runtime Tool installs `openjdk-17-jdk-headless` through apt, then places setup data under `/root/Android/Sdk`, which Repair preserves.

Its custom setup:

1. downloads manifest-pinned Google Command-line Tools 22.0 and the manifest-pinned ARM64 host-tool overlay;
2. records the Android SDK licence after the user accepts it;
3. runs sdkmanager for `platforms;android-36` and `build-tools;36.0.0`;
4. replaces exactly `aapt`, `aapt2`, `aidl`, `dexdump`, `split-select`, and `zipalign` with static ARM64 programs;
5. writes the user-level Gradle settings and `/etc/profile.d/icode-android-sdk.sh`;
6. verifies Java 17, `aapt2`, and sdkmanager by executing them in the guest.

The profile exports `ANDROID_HOME=/root/Android/Sdk` and adds the command-line and build-tools directories to `PATH`. Generated Gradle configuration supplies the ARM64 `aapt2` override, constrains Gradle memory and workers, disables parallel and daemon behavior inside the phone runtime, and installs guards for supported native-tool versions.

### CMake

The CMake Runtime Tool provides CMake `3.22.1` and Ninja `1.12.1`. sdkmanager first creates Google's expected `cmake;3.22.1` package tree and metadata. iCode then removes the x86-64 programs and modules and overlays manifest-pinned ARM64 Kitware CMake and ARM64 Ninja. The retained program set is `cmake`, `ctest`, `cpack`, and `ninja`, plus CMake's shared modules. Setup executes `cmake --version` and `ninja --version` before writing its receipt.

### NDK

The current side-by-side NDK choices are:

| Revision | Catalog compatibility |
|---|---|
| `28.2.13676358` | Android Gradle Plugin 9.0–9.4 |
| `27.0.12077973` | Android Gradle Plugin 8.7–8.13 |

Each row requires Android SDK and CMake and installs `xz-utils`. The manifest pins an `aarch64-linux-musl` NDK archive. Guest GNU tar/xz extracts it under `/root/Android/Sdk/ndk/<revision>` rather than decoding its 256 MiB XZ window in the Android app heap. Extraction uses a staging directory and replaces the destination only after a complete unpack. Verification checks `source.properties`, compiles a small C object for Android with the included Clang, and confirms sdkmanager lists the revision.

Only the listed versions are managed and guarded. Projects that require another NDK or CMake version are outside the supported Build & Run toolchain.

## Terminal sessions and tabs

`TerminalSessionManager` is a singleton per Android process. It owns all Termux `TerminalSession` instances, PTYs, emulator state, tab order, selection, and metadata. A screen attaches to that manager; leaving or recreating the screen does not own or close the shell.

Singleton does not mean one tab. Users can create, select, reorder, and close multiple terminal tabs:

- **+** always opens a new shell;
- shell tabs receive automatic names such as `bash` and `bash 2`; there is no manual rename action;
- opening the Terminal panel or a workspace-specific terminal can reuse a running tab for that workspace;
- each tab starts immediately, before a view attaches;
- a workspace tab starts in that workspace's `/workspace/<name>` directory; a tab outside a workspace starts in `/root`;
- closing a tab hangs up its session;
- a normal shell exit removes the tab; a nonzero normal exit is reported once;
- a signal-killed shell remains as **Exited** so the user can inspect what happened;
- a managed-process output tab is a `tail -F` view of that process's log and closes without claiming ownership of the process.

The terminal uses a real PTY with ANSI/VT emulation and the configured login shell. It is the correct place for interactive commands that need a terminal, pager, password prompt, full-screen UI, or job control.

## Foreground service and process-death truth

Starting a terminal ensures `RuntimeForegroundService` is running. The service observes process-wide terminal sessions and background work such as runtime installation, Runtime Tools, Git transfers, and long operations. It posts a foreground notification while either set is nonempty and stops itself when both become empty.

Each running terminal can have a notification row with Exit and an optional partial wake-lock toggle. A foreground service improves Android process priority and provides visible control. It does not guarantee CPU progress with the screen off or defeat every manufacturer freezer. A wake lock is user-controlled because it costs battery, and even it cannot guarantee progress on every device.

The service returns `START_NOT_STICKY`. Android must not recreate a service that no longer has its in-memory sessions or processes.

Terminal tabs survive Back, rotation, activity recreation, and removal from Recents only while the same Android process survives. If Android, force stop, update, low memory, a crash, or another cause ends that process, the PRoot processes end too and cannot be resumed. iCode records only the names and workspaces of live user terminal tabs. On the next process start it consumes that record once and reports which sessions were lost and the best available process-exit cause. It never presents recreated shells as continued sessions.

Managed processes have the same Android-process boundary. Logs and in-memory state do not turn an ended guest process into a resumable one.

## Build & Run

Build & Run appears in the Terminal panel's **Run** tab. It handles existing Android application projects and user-saved commands through the same managed-process owner.

Android project discovery requires:

- a workspace-root `gradlew` file;
- `settings.gradle` or `settings.gradle.kts` at the root;
- an application module at the root or one ordinary child directory;
- that module's `src/main/AndroidManifest.xml`;
- the `com.android.application` plugin or its version-catalog alias.

If any module declares a CMake or ndk-build script path, the project is treated as native. A run refuses to start until Android SDK is installed; a native project also requires CMake and one supported NDK.

The run uses the project's own wrapper:

```text
bash ./gradlew --console=plain <module-or-root-assembleDebug-task>
```

It runs as a managed process in the workspace. Output opens or follows in a terminal tab, **Stop** targets that same process, and parsed Kotlin, Java, and Clang diagnostics appear in Problems.

After exit code zero, iCode reads AGP's debug `output-metadata.json`, accepts one `SINGLE` APK output, and invokes Android's `PackageInstaller`. Android asks the user to allow installs from iCode when required and confirms each install. iCode then opens the built application. A user-stopped run remains stopped; a failed build reports parsed errors or Gradle's failure block and does not install an APK.

Build & Run is debug-only. It does not provide an emulator, adb pairing, logcat, native debugging, an offline Maven repository, or iCode-managed release signing. A project can still run its own release task and signing configuration from the terminal.

## Deliberate constraints

- The shipped runtime and app native libraries are ARM64-only.
- PRoot fake root does not provide Android root, mounting, kernel modules, raw-device access, or systemd.
- Android apps cannot bind privileged ports below 1024, and Android hides `/proc/net` from ordinary apps.
- PRoot emulates hard links as symlinks to hidden `.l2s.` files. Renaming their containing folder can break those links.
- Restartable sequences are disabled because guest glibc behavior is incompatible with PRoot tracing.
- The guest time zone is its own Ubuntu configuration, not an automatic mirror of the phone.
- One-shot commands are non-PTY and noninteractive. Interactive work belongs in terminal tabs.
- Runtime package mutation is serialized, and dpkg mutation phases are not cancellable.
- iCode adds no process-count cap; Android remains free to kill child processes.
- Workspaces are always app-private and all visible under `/workspace`; shared storage is optional and permission-mediated.
- Managed Android toolchain versions are fixed to SDK Platform 36, Build Tools 36.0.0, CMake 3.22.1, and the two listed NDK revisions.
- Background service and wake-lock behavior does not cross Android process death and cannot override every OEM freezer.

## Implementation map

| Responsibility | Current path |
|---|---|
| Runtime pins and override selection | `app/src/main/assets/runtime/manifest.json`; `data/runtime/RuntimeManifest.kt`, `ManifestSource.kt` |
| Guest filesystem and PRoot command | `app/src/main/java/dev/icode/mobile/data/runtime/RuntimeLayout.kt`, `GuestPaths.kt` |
| Install, Repair, Reset, and home preservation | `app/src/main/java/dev/icode/mobile/data/runtime/RuntimeInstaller.kt`, `RuntimeCoordinator.kt`, `RuntimeHomeBackup.kt` |
| Context variables and login profile | `app/src/main/java/dev/icode/mobile/data/runtime/GuestContext.kt`, `GuestEnvironment.kt` |
| Bounded one-shot commands | `app/src/main/java/dev/icode/mobile/data/runtime/GuestCommand.kt` |
| Long-running managed processes | `app/src/main/java/dev/icode/mobile/data/runtime/RuntimeProcesses.kt`, `RuntimeExecution.kt` |
| Runtime Tools catalog and dependency rules | `app/src/main/java/dev/icode/mobile/domain/RuntimeTools.kt` |
| Serialized package owner and receipts | `app/src/main/java/dev/icode/mobile/data/runtime/RuntimePackageInstaller.kt`, `RuntimeToolSetup.kt` |
| Android SDK, CMake, and NDK setup | `app/src/main/java/dev/icode/mobile/data/android/AndroidSdkSetup.kt`, `CmakeSetup.kt`, `AndroidNdkSetup.kt`, `GradleSettings.kt` |
| Terminal ownership and launch | `app/src/main/java/dev/icode/mobile/data/runtime/TerminalSessionManager.kt`, `TerminalCoordinator.kt`, `TerminalLaunch.kt` |
| Background service | `app/src/main/java/dev/icode/mobile/data/runtime/RuntimeForegroundService.kt`, `TerminalSessionLedger.kt` |
| Build & Run | `app/src/main/java/dev/icode/mobile/data/android/AndroidProjects.kt`, `AndroidApps.kt`, `ApkInstaller.kt` |
| Runtime and Android regression coverage | `app/src/test/java/dev/icode/mobile/data/runtime/`, `app/src/test/java/dev/icode/mobile/data/android/`, `app/src/test/java/dev/icode/mobile/domain/` |

## Roadmap

This guide records current behavior and constraints. The canonical remaining scope and acceptance evidence for runtime installation, Repair, Reset, terminal lifetime, Android SDK/NDK/CMake, existing-app Build and Run, immutable tool hosting, and the new Android app template live only in [Runtime and Android toolchains](./roadmap.md#runtime-and-android-toolchains).
