# C and C++ in Android Apps, Built on the Phone — NDK Plan

Status: **plan, decisions taken (2026-10-18), for the owner's review**. Nothing is built yet. It extends
[android-toolchain-plan.md](./android-toolchain-plan.md), whose §4.7 left native code out (L9), and uses its
tool setups, pinned downloads, Gradle settings and Build & Run.

Owner ask (2026-10-18, verbatim): "Plan ndk support and make sure to make the design and implementation like
that by assuming the ICode app will be reinstall again with clean environment so this flow mainatsion the user
interaction at fir tike with ICode without assumption of user already using ICode which will make code much
complex and hard so clean implementation is needed"

**Design premise.** iCode is installed fresh, and every flow starts at a first run. Nothing from an earlier iCode is assumed, read, repaired or migrated: no leftover SDK paths, no old receipts, no `local.properties` written by something else. What a project brings with it (its own `ndkVersion`, its CMake version) is handled. What a previous iCode might have left is not.

## 1. The answer, short

| Question | Answer |
|---|---|
| What stops C and C++ builds on the phone? | Google ships the NDK's compiler and CMake for x86-64 computers only. On the owner's phone, iCode's own build failed at `configureCMakeDebug` (2026-10-18). A second trap: when a project needs an NDK or CMake that is missing, AGP downloads Google's, an x86-64 package of 660–780 MB that cannot run here |
| Does an ARM64 NDK exist? | Not from Google: every NDK from r27 to r30 has one Linux zip, for x86-64. **android-ndk-custom** (MIT) publishes Google's NDK with its toolchain rebuilt for ARM64 Linux. The rebuild comes from the exact LLVM revision and patches Google's NDK names, made in public GitHub Actions. Kitware publishes CMake for Linux aarch64, and the Ninja project publishes Ninja for it (§2) |
| Does it work? | **Measured here** (§2.4), under ARM64 emulation on iCode's own Ubuntu 24.04 arm64 base: <ul><li>iCode's own native build, all 85 steps (PRoot, talloc, shmem, terminal JNI, spawn), with NDK r27, CMake 3.22.1 and Ninja 1.12.1. Every library is arm64 Android code, 16 KB-aligned;</li><li>Android Studio's "Native C++" template code with NDK r28c.</li></ul> |
| Where does it go? | Beside the Android SDK in Runtime Tools, as Android Studio's SDK Tools tab has them: **NDK (Side by side) 28.2.13676358**, **NDK (Side by side) 27.0.12077973** and **CMake 3.22.1**. Ticking an NDK ticks CMake and the Android SDK with it. Build & Run builds an app with C or C++ once they are installed |

## 2. What was checked

### 2.1 The options for an ARM64 toolchain

| Option | What it is | Verdict |
|---|---|---|
| Google | NDK r27–r30 for Linux: one zip each, x86-64 (nixpkgs' copy of Google's repository, §6) | Cannot run here |
| ADT (katiusu/adt), read before | Its "NDK shim" points `android.toolchain.cmake` at the host's gcc, and its "CMake shim" drops every Android flag (`setup.sh` 1490–1600). That builds Linux programs, not Android code | Not a toolchain: rejected |
| lzhiyong/termux-ndk | LLVM rebuilt for Termux, swapped into the NDK | Built for Termux's Bionic userland, not iCode's Ubuntu guest: rejected |
| Apex-Studio-Dev/apex-toolchains | A custom NDK for a Linux ARM64 **Bionic** host | Same: rejected |
| SnowNF/ndk-aarch64-linux | Notes on building LLVM on aarch64 Linux, no releases | Nothing to install |
| **HomuHomu833/android-ndk-custom** (MIT) | Google's NDK zip with every ELF tool replaced by its `llvm-custom` build for the host. Static musl, glibc and Bionic builds of every release from r25 to r30, point releases included | **Chosen** (§2.2) |

### 2.2 android-ndk-custom, read and run

**How it is made**, as read in `scripts/make-ndk.sh` and its sibling repository `llvm-custom` (`scripts/fetch-source.sh`), at `9cce984` and `874f0a8` (2026-10-17):
1. It downloads Google's NDK zip from `dl.google.com`.
2. It reads the LLVM revision from that NDK's own `clang --version` and `clang_source_info.md`. It fetches that revision from `android.googlesource.com/toolchain/llvm-project` and applies the `llvm_android` patches the NDK lists.
   - **Caveat:** a patch that fails to apply is skipped silently (`git apply … || true`).
3. It replaces each ELF tool in `toolchains/llvm/prebuilt/linux-x86_64/bin` with the ARM64 build, and rebuilds `make`, `yasm` and `python`.
4. It renames the host folder to `linux-arm64`, leaves `linux-x86_64` as a symlink to it, and patches the NDK's CMake toolchain files and `ndk-build` to detect the host.
5. It writes `package.xml`.

It is built in GitHub Actions, and each release asset carries GitHub's SHA-256.

**The two archives iCode pins** (`aarch64-linux-musl`, static), downloaded and checked here:

| | r27 | r28c |
|---|---|---|
| File | `android-ndk-r27-aarch64-linux-musl.tar.xz` | `android-ndk-r28c-aarch64-linux-musl.tar.xz` |
| Size | 132,302,760 bytes | 133,286,584 bytes |
| SHA-256 (equal to GitHub's) | `1708a617fa872125ea02b1b01c4340d80fb29a98d1faf86232dd97f77def81f8` | `cefa41c41dff466aa0de034319dbaf9c726ddaf22e1f4310cb1c6168a6d50bf1` |
| Unpacked | 1.3 GB | 1.4 GB |
| `source.properties` | `Pkg.Revision = 27.0.12077973` | `Pkg.Revision = 28.2.13676358` |
| `package.xml` | `<localPackage path="ndk;27.0.12077973">`, `android-sdk-license`: sdkmanager's own form | the same, for `ndk;28.2.13676358` |
| clang | "ELF 64-bit, ARM aarch64, statically linked", "based on r522817", as Google's r27 `AndroidVersion.txt` says | "ELF 64-bit, ARM aarch64, statically linked" |

**Why musl, and not the glibc build:**
- Static, like the ARM64 build tools: no loader and no libraries under PRoot.
- On the same emulated build of iCode's native code, the musl build took 60.6 s and the glibc build 69.3 s. musl was not slower.

### 2.3 Versions: which NDK, which CMake

- **NDK.** AGP picks an NDK when a project sets none. From Google's release notes, each read:
  - AGP 8.7, 8.10 and 8.13 use 27.0.12077973 (r27);
  - AGP 9.0, 9.2 and 9.4, the current release (September 2026), use 28.2.13676358 (r28c).

  These two versions are what every project on AGP 8.7–9.4 uses unless it pins another. iCode's own project pins r27 on AGP 8.13.
- **CMake 3.22.1.**
  - Android Studio's "Native C++" template and iCode's own build set it.
  - AGP's own default when a project sets none is not documented for AGP 8 and 9; the older page says 3.10.2. So iCode makes 3.22.1 the default (§3.4).
  - Kitware: `cmake-3.22.1-linux-aarch64.tar.gz`, 46,757,098 bytes, SHA-256 `601443375aa1a48a1a076bda7e3cca73af88400463e166fffc3e1da3ce03540b`, equal to Kitware's own `cmake-3.22.1-SHA-256.txt`.
  - Ninja 1.12.1: `ninja-linux-aarch64.zip`, 121,787 bytes, SHA-256 `5c25c6570b0155e95fce5918cb95f1ad9870df5768653afe128db822301a05a1`.
  - Google's `cmake;3.22.1` package: `cmake-3.22.1-linux.zip`, 22,308,647 bytes, SHA-1 `fd0a48b4a758310df8c7aa51f59840ed48fe7ed8`, licence `android-sdk-license`.
- **Licence.** Google's NDK and CMake packages use `android-sdk-license`, the one the Android SDK's License Agreement already shows.

### 2.4 Measured here (x86-64, ARM64 emulated with qemu-user, on `ubuntu-base-24.04.5-base-arm64`, SHA-256 `a91d5a93…`, iCode's own pin)

- **iCode's native build.** The configure arguments are the ones AGP passes: `-DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -GNinja`. The pieces are NDK r27 (musl), Kitware CMake 3.22.1 and Ninja 1.12.1.
  - **Result:** "Configuring done", then 85 of 85 build steps, in 60.6 s.
  - **Output:** `libproot.so` has interpreter `/system/bin/linker64`. `libtermux.so`, `libtalloc.so`, `libandroid-shmem.so`, `libicodespawn.so` and `libprootloader.so` are arm64 too. Every `LOAD` segment is aligned to `0x4000`.
- **Android Studio's "Native C++" template code** (`native-lib.cpp`, `android` and `log`, `c++_shared`) with NDK r28c.
  - **Output:** an arm64 shared object, `0x4000`-aligned, needing `libc++_shared.so`, `liblog.so` and `libandroid.so`.
- **Not measured here:**
  - AGP itself, which needs Google's Maven, refused here;
  - PRoot;
  - speed on a phone;
  - `ndk-build`.

  These are the phone checks (§5).

## 3. Decisions

### 3.1 Three rows, as Android Studio's SDK Tools tab

| Row | id | apt | Setup | Licence | Requires |
|---|---|---|---|---|---|
| NDK (Side by side) 28.2.13676358: "C and C++ for Android apps: Android Gradle plugin 9's NDK, built for ARM64" | `android-ndk-28` | — | `android-ndk-28` | `android-sdk-license` | `cmake`, `android-sdk` |
| NDK (Side by side) 27.0.12077973: "…: Android Gradle plugin 8.7–8.13's NDK, built for ARM64" | `android-ndk-27` | — | `android-ndk-27` | `android-sdk-license` | `cmake`, `android-sdk` |
| CMake 3.22.1: "CMake and Ninja for C and C++ builds, built for ARM64" | `cmake` | — | `cmake` | `android-sdk-license` | `android-sdk` |

- They sit under "Used by iCode" ("Build & Run for apps with C or C++"), are not in Standard, and come after the Android SDK in the catalog's order.
- **`RuntimeTool.requires`** (ids) is the one new idea in the catalog, and the one pure rule `RuntimeToolRules.toggle(choice, id)` keeps a choice whole:
  - ticking a tool ticks what it requires;
  - unticking one unticks what requires it;
  - `plan` reads the choice with its requirements.
- **Order:**
  - The catalog lists what is required before what requires it, and a test holds it to that. One install therefore runs Android SDK, then CMake, then each NDK.
  - Removing the Android SDK removes its whole folder, `ndk/` and `cmake/` with it, and each receipt goes with its row.
- **The License Agreement** shows `android-sdk-license` once: "For Android SDK, CMake 3.22.1 and NDK (Side by side) 28.2.13676358."

### 3.2 What each row installs (setups, `data/android`)

**CMake 3.22.1 (`CmakeSetup`)**, the pattern of the ARM64 build tools: Google's package, with programs built for ARM64 from the same upstream version.
1. `sdkmanager --install "cmake;3.22.1"`: Google's package, its `package.xml` and its checksum.
2. Every x86-64 program in `cmake/3.22.1/bin` is replaced by its ARM64 build. `cmake`, `ctest` and `cpack` come from Kitware's 3.22.1, and `ninja` from Ninja 1.12.1. A program with no ARM64 counterpart is removed, so nothing is left that cannot run.
3. **Verify:** `cmake --version` says 3.22.1, and `ninja --version` exits with 0.

**NDK (Side by side) r28c and r27 (`AndroidNdkSetup`, one class, one instance per pin):**
1. The pinned archive downloads, and its SHA-256 is checked as the bytes arrive.
2. It unpacks into a staging folder, then is renamed to `/root/Android/Sdk/ndk/<revision>`, Android Studio's side-by-side location. Its `package.xml` makes sdkmanager list it as installed.
3. **Verify:**
   - `source.properties` names the pinned revision;
   - the NDK's `clang --target=aarch64-linux-android24 -c` compiles a one-line C file, which proves the compiler and the sysroot;
   - `sdkmanager --list_installed` lists `ndk;<revision>`.

**What neither installs:**
- **Nothing through apt.** An Android native build needs no host compiler.
- **Host tools a project's own CMake scripts call.** iCode's build runs `patch`, for example. Those come from the Build tools row: `build-essential` depends on `dpkg-dev`, which depends on `patch`. That is the project's requirement, said by CMake's own error.

**Extraction:** `ArchiveExtractor` gains `.tar.xz` (commons-compress with `org.tukaani:xz`, its documented optional dependency) and a placement filter for tar.gz, as zip has. It unpacks from the app, never through PRoot, which crawls on many small files (`ArchiveExtractor`'s own reason).

### 3.3 Pins

`assets/runtime/manifest.json` `archives` gains:
- `android-ndk-28` and `android-ndk-27`: android-ndk-custom, SHA-256 and size (§2.2);
- `cmake-arm64`: Kitware;
- `ninja-arm64`: Ninja.

`packageBytes` gains `cmake;3.22.1`. Each receipt names its pins, so a build that pins others reads as not installed. On a clean install there is nothing older to reconcile.

### 3.4 Gradle never fetches an x86-64 NDK or CMake

The Android SDK tool's one init script (`~/.gradle/init.d/icode-android.gradle`) gains two things for any module that builds C or C++ (`externalNativeBuild` with a path).
- **A default.** It sets CMake 3.22.1 when the module sets no version, the way it already sets the default build tools. A module that names its own keeps it.
- **A check, before AGP resolves anything:**
  - a module whose `ndkPath` is set is left alone;
  - otherwise, an `ndkVersion` with no `ndk/<version>` installed fails the build, and so does a CMake version with no `cmake/<version>` (where `3.22.1+` means at least that).
  - The failure says which versions iCode installs: "iCode: NDK 26.1.10909125 is not installed. Runtime Tools installs NDK 28.2.13676358 and 27.0.12077973: set android.ndkVersion to one of them, or install the one this module names on a computer."
  - Without this, AGP would spend 700 MB downloading an NDK that cannot run.

One point is left to the phone: whether `ndkVersion` reads as AGP's default when a project sets none. If it does, the check also covers an AGP older than 8.7. If it reads empty, AGP picks its own default, which iCode installs for AGP 8.7–9.4.

### 3.5 Build & Run with C and C++

- **Before building:**
  - A module that builds C or C++ needs CMake and at least one NDK row installed. Otherwise ▶ shows **NDK and CMake Needed**, with Open Runtime Tools, as it already does for the SDK.
  - Which NDK version is Gradle's to say (§3.4); iCode does not parse build files for it.
- **When it fails,** the notice shows Gradle's own "What went wrong" line, its first two lines without the `> `. So the init script's message, or CMake's, reaches the user.
- **Problems** gains clang's own diagnostics, `<path>:<line>:<col>: error|warning: <message>`, with or without the `C/C++: ` prefix AGP's native tasks print. The prefix is to be confirmed on the phone; the clang form itself is fixed.

### 3.6 Size, licence and the owner's notes

- **Download and disk:**

  | | Download | Disk |
  |---|---|---|
  | NDK r28c | 133 MB | 1.4 GB |
  | NDK r27 | 132 MB | 1.3 GB |
  | CMake | 22 MB from Google, 47 MB from Kitware, 0.1 MB for Ninja | about 100 MB |

  Each row's download shows in Runtime Tools, and the page's foot shows the space available.
- **Licence:**
  - android-ndk-custom redistributes Google's NDK with its open-source toolchain rebuilt.
  - Google's SDK licence §3.4 restricts redistributing the SDK; §3.5 leaves its open-source components (LLVM, libc++, bionic's headers) to their own licences.
  - iCode redistributes nothing: the phone downloads the archive from its publisher, as it does the ARM64 build tools. **For the owner to know**, as with the Play note in the toolchain plan.
- **Trust:** the same model as the ARM64 build tools: a public CI build from Google's sources, pinned by SHA-256.
  - A mutated release asset fails its checksum and is never used. The publisher's workflow allows replacing assets (`replacesArtifacts: true`).
  - The owner can move the build into iCode's own CI later, with the same scripts, as a replacement.

### 3.7 The set-aside edits (`git stash`, 2026-10-18)

- **Kept: "say Gradle's What went wrong"** (§3.5).
- **Reshaped: "refuse projects with C or C++"** becomes "needs NDK and CMake" (§3.5).
- **Dropped: "write `sdk.dir` into `local.properties`".** On a clean install a project has none, and `ANDROID_HOME` names iCode's SDK.

The stash is applied, reshaped, in N4 and then dropped.

### 3.8 Not done, and why

| | Why |
|---|---|
| NDK versions other than 28.2.13676358 and 27.0.12077973 | They are what AGP 8.7–9.4 use. A project pinning another gets §3.4's message. Adding one later is one manifest pin and one catalog row |
| CMake versions other than 3.22.1 | Same: one pin per version, of Kitware's build of that version |
| `ndk-build` (Android.mk) projects | android-ndk-custom patches `ndk-build`'s host detection and ships ARM64 `make` and `python`, so they should build. That is not measured, so it is not claimed |
| Native debugging (lldb, gdbserver) | It needs `adb` and Wireless debugging (toolchain plan §4.7) |

## 4. Steps (each: gate, commit)

| Step | What | Gate |
|---|---|---|
| N1 | Domain: `RuntimeTool.requires`, `RuntimeToolRules.toggle`, the three rows, the catalog's order | Unit tests: toggle, plan with requirements, order |
| N2 | Data: `.tar.xz` and filtered tar.gz in `ArchiveExtractor`; the pins; `CmakeSetup`; `AndroidNdkSetup`; their registration | Unit tests on small fixture archives; the overlay rule on Kitware's real tarball, run here |
| N3 | Gradle: the init script's default CMake and its checks (§3.4) | `GradleSettings` tests; the script parsed here by Gradle's Groovy |
| N4 | Build & Run: NDK and CMake Needed, Gradle's "What went wrong", clang diagnostics; the stash reshaped | Unit tests (`AndroidProjects`, `GradleIssues`) |
| N5 | UI: the rows, their requirement line ("Needs Android SDK and CMake 3.22.1"), the toggle; the dialog | Type-check; UI tests |
| N6 | Docs as built, guards, API-30 check, pull, push | All green |

## 5. Phone checks (none can run here)

| Check | Decides |
|---|---|
| Install CMake and both NDKs from Runtime Tools on a fresh iCode | §3.2 end to end under PRoot, with sizes and times |
| Build & Run a new "Native C++" project (AGP 9.4, r28c) | The template path: the app opens and calls its C++ |
| Build & Run iCode itself (AGP 8.13, r27; Build tools installed for `patch`) | A real project with native code |
| A project pinning NDK 26 | §3.4's message, and that AGP downloads nothing |
| `ndkVersion` printed for a project that sets none | Whether §3.4's check also covers AGP older than 8.7 |
| Peak memory and process count of a native build | The phantom-process risk (toolchain plan L8) |

## 6. Sources

- [android-ndk-custom](https://github.com/HomuHomu833/android-ndk-custom) (MIT): `scripts/make-ndk.sh`, workflows, releases `r27` and `r28`. [llvm-custom](https://github.com/HomuHomu833/llvm-custom): `scripts/fetch-source.sh`. Both read at their 2026-10-17 heads.
- [ADT](https://github.com/katiusu/adt) `setup.sh` (`create_ndk_shim`, `create_cmake_shim`) and `docs/TECHNICAL_REFERENCE.md` "NDK / PRoot reality".
- [termux-ndk](https://github.com/lzhiyong/termux-ndk), [apex-toolchains](https://github.com/Apex-Studio-Dev/apex-toolchains/releases/tag/ndk-r27), [ndk-aarch64-linux](https://github.com/SnowNF/ndk-aarch64-linux): their READMEs.
- AGP release notes: [9.4.0](https://developer.android.com/build/releases/gradle-plugin), [9.2.0](https://developer.android.com/build/releases/agp-9-2-0-release-notes), [9.0.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [8.13.0](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [8.10.0](https://developer.android.com/build/releases/agp-8-10-0-release-notes), [8.7.0](https://developer.android.com/build/releases/agp-8-7-0-release-notes): their compatibility tables.
- [Install and configure the NDK and CMake](https://developer.android.com/studio/projects/install-ndk) and [Configure the NDK for AGP](https://developer.android.com/studio/projects/configure-agp-ndk): side-by-side `sdk/ndk/<version>`, `ndkVersion` and `ndkPath`, CMake from `cmake.dir` or `PATH`, automatic installs once licences are accepted.
- [Kitware CMake v3.22.1](https://github.com/Kitware/CMake/releases/tag/v3.22.1) and its `cmake-3.22.1-SHA-256.txt`; [Ninja v1.12.1](https://github.com/ninja-build/ninja/releases/tag/v1.12.1).
- Google's `cmake` and `ndk` package records, from nixpkgs' `androidenv/repo.json` (as in the toolchain plan).
