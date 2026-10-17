# Android Apps, Built on the Phone — Toolchain Plan

Status: **A2–A7 built (2026-10-17)**; §4.1–§4.5 describe what was built. A1, the phone measurement,
and A8, the New Android app template that waits on it, are open (§5, §6). It extends
[runtime-tools-plan.md](./runtime-tools-plan.md) (Set Up Tools and Runtime Tools) and uses
[runtime-foundation-plan.md](./runtime-foundation-plan.md) (managed processes, the login environment).

Owner ask (2026-10-17, verbatim): "clone this repo on temp dir: git@github.com:techjarves/Mobile-Harness.git
for the reference and your task is to see what is there limitations on Android app development how that
project solved it and how we can: probably: at onboarding step components we can give options to setup
android toolkit... make sure no gaps no issue if any archectural limitations made by us then need to figure
it out and need to resolve it from root instead of making our ide through wrong way... we are here for,
scalability manatianablility 0 duplicatcy no inconsistency best archecture if need we are here for migrate
no any fallback and complex code. Let's plan and be 100% accurate about the direction and be 100% accurate way"

## 1. The answer, short

| Question | Answer |
|---|---|
| What makes building Android apps on a phone hard? | Eleven limits, listed in §2. The decisive one: Google publishes the SDK's native host tools, `aapt2` above all, for x86-64 Linux only. Gradle's own `aapt2` download cannot run on an ARM64 phone. |
| How did Mobile Harness solve it? | It ships one 570 MB bundle containing JDK 17, SDK 36, ARM64 build tools, Gradle 8.14.3 and an offline Maven repository. It targets Android 9 (API 28) so it may run programs from its data folder. It installs APKs through Android's package installer. Its approach has gaps of its own (§2.2). |
| Does iCode's architecture block it? | **Partly, in four places** (§3). The runtime itself is already right: iCode targets API 36 and runs everything through PRoot's loader. It uses Ubuntu 24.04 with apt, mounts the Android system folders, keeps workspaces on ext4, and reads environment variables from the login profile. What blocks Android work is narrower: the tools catalog can only install apt packages, pinned downloads cover only the root filesystem, nothing can install an APK, and no build can be started by the user. |
| Where does it go? | **One tool, "Android SDK", in the tools catalog**: Set Up Tools in onboarding, Runtime Tools in Settings. It installs the JDK through apt. It installs Google's own `sdkmanager`, which fetches the platform and build tools after the user accepts Google's licence. Then it replaces the build tools' x86-64 executables with ARM64 builds of the same AOSP source. Each Gradle setting is written once, where Gradle documents it. Build → install → launch is one action, used by the UI and the assistant (§4). |

## 2. The limits — measured or read, and how the reference handled each

Each claim names its evidence. "Reference" is Mobile Harness v1.0.7 (MIT), cloned at `96b1da0` for reading only.

### 2.1 The limits

| # | Limit | Evidence | Reference's answer |
|---|---|---|---|
| L1 | **Google ships SDK host tools for x86-64 only.** Gradle's AGP downloads `com.android.tools.build:aapt2:<v>:linux`, an x86-64 ELF. `build-tools/*/aapt2`, `aidl` and `zipalign` are x86-64 too. | iCode's own build here fails at `processDebugResources` for exactly this reason (`audit-journal.md:17`). See also the Gradle forum thread in §8 | It bundles `build-tools/35.0.0` holding ARM64 executables. It sets `android.aapt2FromMavenOverride` three times over: in `gradle.properties`, in an init script, and as an environment variable (`RuntimeInstaller.kt:1099-1153, 1595`) |
| L2 | **Android 10+ forbids running files from the app's data folder** when `targetSdk ≥ 29` (W^X) | `integration-plan.md` §3 | It targets **API 28** for its sideloaded build (`build.gradle.kts:79`). That is a fallback which Play does not accept for new apps |
| L3 | **The JDK and Gradle must run under PRoot, with phone memory** | — | Temurin 17 sits in its bundle. It passes `--no-daemon --max-workers=2` and sets `-Xmx512m`, two workers, no parallel builds and the Kotlin compiler in-process (`AndroidBuildSupport.kt:91`, `RuntimeInstaller.kt:1139-1146`) |
| L4 | **Dependencies come from Google's Maven** at build time | — | It ships an offline Maven repository (≈ 500 MB). That pins its template to the versions inside it: AGP 8.11, Kotlin 1.9.22, Compose 1.3.0 (`AndroidProjectTemplateGenerator.kt:46-88`) |
| L5 | **There is no `adb` on the phone to install the APK** | — | A `PackageInstaller` session, with `REQUEST_INSTALL_PACKAGES`. The user confirms each install. Xiaomi devices get a separate `ACTION_INSTALL_PACKAGE` path (`AndroidAppInstaller.kt`) |
| L6 | **Launching the installed app**, with package visibility under API 30+ | AOSP `ResolveIntentHelper.getLaunchIntentSenderForPackage`: it checks only that the caller owns its own package, then resolves with `resolveForStart = true`, which skips app filtering | `getLaunchIntentSenderForPackage` on API 33+, `getLaunchIntentForPackage` below. Its API 28 target escapes the filtering |
| L7 | **Logcat shows each app its own logs only** (`READ_LOGS` is not grantable to apps) | Platform permission | A "logcat available" flag. The limit remains |
| L8 | **Android 12+ kills child processes beyond about 32 across all apps** (the phantom process killer) | Termux reports (§8); `runtime-foundation-findings.md:81`; `ProcessRules.MAX_RUNNING = 8` | Few workers, no daemon, the Kotlin compiler in-process |
| L9 | **The NDK and CMake hosts are x86-64 only** | ADT's README (§8) | None: Android apps with native C/C++ code are not built |
| L10 | **The Android SDK licence must be accepted** before the SDK is used | sdkmanager and AGP check `licenses/android-sdk-license` | Its bundle includes an already-accepted licence |
| L11 | **The debug signing key lives in the runtime.** A new key means the app on the phone cannot be updated (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) | `~/.android/debug.keystore` | Not handled |

### 2.2 What the reference got wrong, which iCode will not repeat

- **Targeting API 28** to run programs from the data folder (L2). iCode targets 36 and runs everything through PRoot's loader in the native-library folder. This is already the root solution.
- **One opaque 570 MB bundle.** Its contents are listed only in `dist/runtime-bundles/manifest.json`. Its ARM64 build tools have no recorded source in the repository: the `appdevforall.org` constants in `RuntimeInstaller.kt:1929-1935` are never used.
- **An offline Maven repository** that freezes project versions (L4).
- **The same setting written in three places** (L1). A setting in three places drifts.
- **Nothing for L11.** Rebuilding the runtime silently breaks updates of the installed app.

## 3. iCode today — what is right, and the four gaps

### Already right (no change)

| | Where |
|---|---|
| targetSdk 36, minSdk 30; PRoot and its loader run from `nativeLibraryDir` (W^X, L2) | `app/build.gradle.kts:23-24`, `RuntimeLayout.prootEnvironment` |
| ARM64 only, as the toolchain is | `ndk.abiFilters += "arm64-v8a"` |
| Ubuntu 24.04 (noble) with apt, so `openjdk-17-jdk-headless` comes from the archive | `assets/runtime/manifest.json` |
| `/system`, `/apex`, `/vendor` and `/product` mounted in the guest, for Bionic programs | `RuntimeLayout.HOST_BINDS` |
| Workspaces and the guest home on ext4, which suits Gradle's many small files | `workspace-storage-plan.md` §2 |
| **One environment:** terminals, managed processes and the assistant's commands all read the login profile, including `/etc/profile.d/*`. A tool exports its variables there once | `GuestEnvironment` |
| Repair keeps the guest home (a rename) and rebuilds the system; Reset discards both | `RuntimeHomeBackup` |
| Long work keeps a foreground service and a notification | `RuntimeForegroundService`, `long-operations-plan.md` |

### The four gaps (iCode's own architectural limits)

| # | Gap | Where | Root fix |
|---|---|---|---|
| G1 | **A tool is apt packages and nothing else.** "Installed" means dpkg, size means `apt --print-uris`, and the installer is apt. The Android SDK is not an apt package | `domain/RuntimeTools.kt` (`packages`), `RuntimePackageInstaller`, `DpkgStatus` | A tool may add a **setup** after its apt packages: pinned downloads plus steps it owns, with a receipt (§4.1) |
| G2 | **Only the root filesystem is a pinned download.** The downloader is named for it (`RootfsDownloader`), and the manifest has one `rootfs` entry | `RuntimeInstaller.kt:56`, `RootfsDownloader.kt` | The manifest gains `archives` and `packageBytes`. One `RuntimeManifest` reads them all. The downloader becomes `PinnedDownload`: it checks each pin's size and digest — SHA-256, or Google's own SHA-1 — as the bytes arrive (§4.2) |
| G3 | **No APK install, no launch, no "Install unknown apps" permission** | `domain/SystemAccess.kt` (`Notifications`, `Battery`, `Storage`) | `SystemPermission.InstallApps`; one installer and launcher (§4.5) |
| G4 | **No build the user can start.** Managed processes are started only by the assistant | `RuntimeProcesses.start`, `ICodeIntent.ProcessIntent` | Build is a managed process that iCode starts. Install and launch follow when it exits with 0. The ▶ button and the assistant share one action (§4.4) |

## 4. Decisions

### 4.1 One catalog entry: **Android SDK**

```kotlin
data class RuntimeTool(
    …,
    val packages: List<String>,          // apt, as before
    /** The data layer's setup, by id, run after [packages]; null for a tool apt installs whole. */
    val setup: String? = null,
    /** The licence installing it accepts, by id: shown, and accepted, before it is installed. */
    val licence: String? = null,
)

val AndroidSdk = RuntimeTool(
    "android-sdk", "Android SDK", "JDK 17, SDK Platform 36 and Build-Tools 36, with build tools that run on ARM64",
    "Build & Run for Android apps", listOf("openjdk-17-jdk-headless"), setup = "android-sdk", licence = "android-sdk-license",
)
```

**How a setup works (as built):**
- `RuntimeToolSetup` (data) has these members:
  - `id`;
  - `receipt`: what it installs, by version and checksum;
  - `measure()`: the size of each file it downloads, as pinned;
  - `install(onProgress, onLine)`: its steps, with its progress and output;
  - `configure()`: what it keeps outside the guest home (§4.2);
  - `remove()`.
- `ToolSetups` registers the setups by id and keeps their receipts in `~/.icode/runtime-tools/<id>`. A setup never writes a receipt itself. The one registration is `RuntimeToolsCoordinator`'s constructor.
- The domain stays pure: it knows a setup and a licence exist, not how they run.

**What changes where:**
- **Installed** means dpkg has every package *and* the setup's receipt equals the receipt this build pins (`RuntimeInventory`). A build that pins other versions reads as not installed, and installing brings it up to date.
- **Install** checks the licence first. A tool whose licence was not accepted is refused, so `ensure()`, which features call, can never install one. Then apt runs, then the setup unless its receipt matches. `configure()` runs either way, and the receipt is written last.
- **Removal** removes the setup and its receipt, then apt removes the packages.
- **Download** is apt's archives plus the setup's pinned files, unless its receipt matches. The page's one total still counts each file once.
- **Progress** stays one `RuntimeToolWork`. Once apt is done, the setup's `ToolSetupProgress` gives the step, fraction, file and whether it can be cancelled. Downloads and sdkmanager can be cancelled. Unpacking, the ARM64 overlay and writing settings run in `NonCancellable`, and the page says "This step can't be stopped". `ToolRuns` holds the run in hand and its Cancel. Every apt command stays in `RuntimePackageInstaller`, which the guard checks (`apt-owner`).

**Placement:** not in Standard, because it is opt-in and large.
- It is listed under "Used by iCode" in Set Up Tools (onboarding, Custom) and in Settings › Runtime Environment › Runtime Tools.
- Ticking it adds Android Studio's last step, **License Agreement**. That step shows the licence's id, which tools need it, and its text in a scrolling box, with Decline and Accept. Install or Apply stays disabled until each licence is accepted. `ApplyRuntimeTools(chosen, accepted)` carries the accepted ids.

### 4.2 What the Android SDK tool installs, exactly

The SDK lives at **`/root/Android/Sdk`**, which is Android Studio's location on Linux. It is in the guest home, so Repair keeps it (§3).

| Order | What | From | Checked by |
|---|---|---|---|
| 1 | `openjdk-17-jdk-headless`. JDK 17 is AGP 9's minimum and default; Gradle 7.3 and later run on it | apt (noble) | dpkg |
| 2 | Google **Command-line Tools** 22.0, unpacked into `…/Sdk/cmdline-tools/latest` through a staging folder, executable bits kept (commons-compress reads the zip's Unix modes) | `manifest.json` `archives.android-cmdline-tools`: Google's URL, size 181,833,628 bytes and **Google's own SHA-1** `040d3996…` | SHA-1 and size, as the bytes arrive |
| 3 | The licence the user accepted: `licenses/android-sdk-license`, a newline and then the SHA-1 of the licence text, exactly as sdkmanager writes it | The text shipped as `assets/runtime/licences/android-sdk-license.txt` | A test pins its SHA-1 to `24333f8a63b6825ea9c5514f83c2829b004d1fee`, the hash sdkmanager accepts |
| 4 | `platforms;android-36` and `build-tools;36.0.0`, by `sdkmanager --sdk_root=/root/Android/Sdk --install …` with stdin closed: a licence it does not find accepted is declined, never waited on | Google's own installer: it writes `package.xml` and checks Google's checksums. Sizes are pinned in `manifest.json` `packageBytes` (65,878,410 and 63,737,259 bytes) | sdkmanager's exit code; `android.jar` and the build tools folder must exist afterwards |
| 5 | **ARM64 host tools** over `build-tools/36.0.0`: `aapt`, `aapt2`, `aidl`, `dexdump`, `split-select`, `zipalign`, and nothing else from the archive | lzhiyong/android-sdk-tools **35.0.2**, `android-sdk-tools-static-aarch64.zip`, SHA-256 `db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2`, 14,401,170 bytes. Its `aapt2` is "ELF 64-bit, ARM aarch64, statically linked". It is built by the project's public CI from AOSP source, under Apache-2.0 | SHA-256 and size; all six must be placed |
| 6 | Gradle's settings (§4.3) | Written by the setup | — |
| 7 | Verification: `java -version` says 17, and `aapt2 version` and `sdkmanager --version` each exit with 0 | Guest commands | exit codes |
| 8 | The receipt `~/.icode/runtime-tools/android-sdk`: the licence hash, both archive checksums, both packages, and a SHA-256 of the settings written | Written by `ToolSetups` | — |
| — | The environment: `/etc/profile.d/icode-android-sdk.sh` sets `ANDROID_HOME` and appends `cmdline-tools/latest/bin` and `build-tools/36.0.0` to `PATH`. Written by `configure()` on every install | The system's | — |

Both zips stay in the cache until the whole setup succeeds. A retry after a later step fails verifies them and downloads nothing again.

**Why these ARM64 tools, and not another source:**
- **Mobile Harness's bundle** has no recorded source (§2.2).
- **ADT** (katiusu/adt, Apache-2.0) uses the same build system, adapted from lzhiyong. Its builds link against glibc, and its "36.0.0" and "37.0.0" are built from the same AOSP tag. Its own README says so: "AOSP has no newer platform-tools tag".
- **Building them in iCode's own CI** needs a public place to publish the artifacts. iCode's repository could not be seen by search from here, which suggests it is private, so phones could not download from it. The owner can make that move later with the same `build.py`. It would be a replacement, not a second path.
- **Statically linked** means no loader and no libraries. Under PRoot that is the most robust form.

**Why sdkmanager, and not iCode unpacking zips:** sdkmanager is Google's installer. It writes `package.xml` and resolves dependencies as Android Studio does. The user also gets the standard `sdkmanager` to add other platforms later.

**The licence (L10), as built:**
- iCode pins Google's packages as it pins every other download. Nothing reads Google's repository at runtime.
  - The Command-line Tools are pinned with Google's own SHA-1, and the two packages with their sizes. Both come from Google's repository data as nixpkgs mirrors it (`androidenv/repo.json`, updated 2026-10-17).
  - `dl.google.com` cannot be reached from this container, so nothing here could check it more directly.
- The licence text ships as an asset, byte for byte. `.editorconfig` keeps an editor from adding a final newline.
- The License Agreement shows that text. Accept is what makes iCode write sdkmanager's record, whose hash is computed from the same bytes, so the two cannot differ.
- **If Google changes the licence,** sdkmanager finds it not accepted and declines it. The setup then fails with sdkmanager's own words. A new build of iCode ships the new text.

**What Repair and Reset do:**
- **Repair** rebuilds the system, which removes the JDK and `/etc/profile.d`. It keeps the home: the SDK, receipt, Gradle settings and debug key.
  - The tool then reads as not installed, because its package is missing.
  - Installing it again fetches only the JDK, since the receipt matches. `configure()` writes the environment again.
- **Reset** removes everything, and the tool is offered again.
- **The debug key (L11):** it stays `~/.android/debug.keystore`, so Repair keeps it.

### 4.3 Gradle on the phone — each setting once, where Gradle documents it

| Where | What | Why |
|---|---|---|
| `~/.gradle/gradle.properties`, a block between `# >>> iCode: Android SDK …` and `# <<< iCode: Android SDK <<<` | `android.aapt2FromMavenOverride=/root/Android/Sdk/build-tools/36.0.0/aapt2` (L1) | Gradle's own `aapt2` download is x86-64 and cannot run |
| the same block | `org.gradle.jvmargs=-Xmx<heap>m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8`. The heap is a quarter of the phone's RAM, between 768 MB and 2 GB (L3) | Phones differ: iCode reads the RAM when the setup runs |
| the same block | `org.gradle.workers.max=2`, `org.gradle.parallel=false`, `kotlin.compiler.execution.strategy=in-process` (L3, L8) | Fewer processes, and fewer killed |
| the same block | `org.gradle.daemon=false` | A guest process ends with its PRoot session (`--kill-on-exit`), so a daemon could not outlive the build anyway |
| `~/.gradle/init.d/icode-android.gradle` | When an Android module's plugin is applied, it sets the default `buildToolsVersion` to `36.0.0`. A build that names its own keeps it | A project on AGP 8.x would otherwise fetch Google's x86-64 build tools; AGP 9's default is already 36.0.0 |

These are Gradle properties, and Gradle documents the user's `gradle.properties` as their place. It takes precedence over a project's own file: a project asking for more memory than the phone has gets the phone's figure. The rest of that file is the user's and is never touched. Removal takes out iCode's block, and deletes the file only when nothing else was in it. This settles the open point the plan left to A1: the properties are not set from an init script.

### 4.4 Build → install → launch: one action (G4), as built

- **`AndroidApps`** (data) is the one action. The Explorer's ▶ and the assistant's `run_android_app` both call `buildAndRun(workspace)`.
  1. **Find** the app (`AndroidProjects`).
     - The top must hold `gradlew` and `settings.gradle(.kts)`.
     - An app module is the top, or a folder in it, that holds `src/main/AndroidManifest.xml`. Its build file must apply the application plugin, by id (`com.android.application`) or by Android Studio's version-catalog alias (`libs.plugins.android.application`).
     - With several app modules, `app` wins, as Android Studio's run configuration does; otherwise the first by name.
     - It is read again whenever the open workspace's files change, and ▶ shows only when there is one.
  2. **Build** as a managed process named "Build app": `bash ./gradlew --console=plain :app:assembleDebug`.
     - It uses the project's own wrapper, so the project's own Gradle. bash runs the wrapper whether or not it is executable.
     - It runs with the login environment, where `ANDROID_HOME` now is.
     - Its output is that process's tab in the Terminal panel, and Stop is the process's own Stop. A second Build & Run of the same app is refused while one runs.
  3. **When it ends:**
     - What it printed goes through `GradleIssues` into the Problems panel. That panel had no producer before.
     - Exit 0: AGP's `build/outputs/apk/debug/output-metadata.json` gives the `applicationId` and the one `SINGLE` APK. It must be newer than the build's start. The APK is then installed and opened (§4.5).
- **The ▶ button** asks for what is missing first:
  - Without the Android SDK, a dialog offers Open Runtime Tools.
  - Without "Install unknown apps", the Explorer shows the shared `PermissionExplanation`. Once Android's page grants it, the build starts.
  - While a run goes, ▶ is accented and opens its output.
- **A notice** under the Explorer's label says Building…, Installing…, or Build & Run Failed with the reason. It offers Show Output, and Dismiss for a failure.
- **Issues:** Kotlin's `e:`/`w: file:///…:line:col message` and javac's `/….java:line: error|warning: message` lines become `Diagnostic`s with source "Gradle", by file within the workspace. They are kept per workspace: opening another workspace clears them.

### 4.5 Installing and launching (L5, L6, G3), as built

- **The permission:**
  - The manifest has `REQUEST_INSTALL_PACKAGES`.
  - `SystemPermission.InstallApps` is read with `canRequestPackageInstalls()` and requested through `ACTION_MANAGE_UNKNOWN_APP_SOURCES`. It is never asked in onboarding.
  - Distributing through Play would need the `REQUEST_INSTALL_PACKAGES` declaration. This is noted for the owner.
- **Install (`ApkInstaller`):**
  - A `PackageInstaller` session in full-install mode.
  - Its result goes to a receiver registered with `RECEIVER_NOT_EXPORTED` for that session alone. The PendingIntent is mutable, because Android adds the outcome to it.
  - `STATUS_PENDING_USER_ACTION` opens Android's confirmation. Android does not let an app in the background open it, so it is kept: the notice's **Install** opens it again from iCode's screen, and **Cancel** abandons the session.
  - Failures are said in Android's words. Signature mismatch (L11) says to uninstall the old copy.
- **Launch:**
  - `startActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER).setPackage(package))`.
  - Android resolves activity starts "for start", which skips package visibility, so it works from API 30 without `<queries>` or `QUERY_ALL_PACKAGES`.
  - "No launchable activity" is said when there is none.
- **No device-specific branch** (the reference's Xiaomi path) unless a device measured shows it is needed.

### 4.6 A new Android app

The Private top's **+** offers **New Android app**, beside New workspace. The template is Android Studio's "Empty Activity" (Compose):
- the newest AGP and Kotlin that pass check A1 on the phone (§6);
- the Gradle wrapper of a pinned Gradle release, with `distributionSha256Sum` from services.gradle.org;
- the wrapper jar shipped as an asset from that release.

There is no offline Maven repository: the first build downloads Gradle and the dependencies, as on a desktop. Runtime Tools says so, with the size.

### 4.7 Not done, and why

| | Why |
|---|---|
| NDK / CMake (L9) | Google's NDK host is x86-64 only. Native Android code needs a separate plan, for example Ubuntu's clang with an Android target, as ADT does |
| `adb` and the built app's logcat (L7) | They need Wireless debugging paired on the same phone, after every reboot. That is a feature of its own, not part of the toolkit |
| An offline Maven repository | It freezes versions (L4). Gradle's cache keeps what one build downloaded |
| x86 emulators, release signing by iCode | Debug builds only. A project's own release signing still works through Gradle |
| Targeting API 28 | Not needed: iCode already runs everything through PRoot's loader (L2) |

## 5. Steps (each: gate, commit; the phone where it says so)

| Step | What | Gate | State |
|---|---|---|---|
| A1 | **Measure on the phone.** Install the Android SDK from Runtime Tools, then Build & Run an Empty Activity project with AGP 9.x, then 8.13.x. Record time, peak memory, and the number of processes against the 32 limit | Recorded in §7 | Open: needs the phone |
| A2 | G2: `RuntimeManifest` (rootfs, archives, package sizes) and `PinnedDownload` (download, size, SHA-256 or SHA-1, progress), replacing `RootfsDownloader`; `ArchiveExtractor` (tar.gz and zip) replacing `RootfsExtractor` | Unit tests; the rootfs install unchanged | Built |
| A3 | G1: `RuntimeTool.setup` and `licence`, `RuntimeInventory`, `RuntimeToolSetup`, `ToolSetups` (receipts), `ToolRuns`, the installer running apt then the setup, sizes, removal | Unit tests with a scripted setup | Built |
| A4 | The Android SDK setup (§4.2), the licence asset, and Gradle's settings (§4.3) | Unit tests; the overlay run on the pinned zip here; phone install | Built; phone install open |
| A5 | UI: the Android SDK row, setup progress, and the License Agreement step | Type-check | Built |
| A6 | G3: `SystemPermission.InstallApps`, `ApkInstaller` (session, receiver, confirmation, launch) | Type-check, API-30 check; phone install | Built; phone install open |
| A7 | G4: `AndroidApps`, `AndroidProjects`, `GradleIssues`, the ▶ button, its notice, and `run_android_app` | Unit tests (module discovery, APK metadata, Gradle issues); phone build & run | Built; phone build open |
| A8 | New Android app (§4.6) | Phone: new app → ▶ → it opens | Waits on A1's AGP version |

## 6. Device checks (none can run here)

This container is x86-64. `dl.google.com` is refused by its proxy (403), and Google's Maven too. So the build, sdkmanager and `aapt2` can only run on the phone.

| Check | Decides |
|---|---|
| `aapt2 version` from the pinned zip, under PRoot | That the static ARM64 binary runs (L1) |
| AGP 9.x with an `aapt2` built from AOSP 35.0.2 | The template's AGP. ADT validated its 35.0.2 build tools on a real device, but AGP 9 has not been measured with them |
| The init script's default `buildToolsVersion` on an AGP 8.x project | §4.3: that such a project uses the ARM64 build tools |
| Peak RAM and process count of `assembleDebug` | §4.3 values; the phantom-process risk (L8) |
| Install → confirm → launch on API 30–36 | §4.5's launch, without `<queries>` |
| Repair, then Build & Run with the same app installed | That the debug key survives (L11) |

## 7. Record

**2026-10-17, built and checked here (x86-64, no phone):**
- **Data and domain tests pass**, apart from one known exception. They cover:
  - the manifest's pins;
  - `PinnedDownload` against a local server: a match kept, another digest or size deleted, HTTP errors;
  - zip extraction with modes and refusal of paths that escape;
  - setups through the installer: install, receipt, a Repair-like matching receipt, other pins, the licence gate, Cancel, failure, removal, sizes;
  - the coordinator with the Android SDK;
  - Gradle settings;
  - module discovery;
  - APK metadata;
  - Gradle issues.

  `TreeSizeTest`'s unreadable-folder case fails here only because the container runs as root.
- **The overlay was run on the pinned ARM64 zip** (SHA-256 checked). Exactly the six programs land in place of the old files, all executable, and `aapt2` is "ELF 64-bit LSB executable, ARM aarch64, statically linked".
- **The licence asset's SHA-1 is `24333f8a63b6825ea9c5514f83c2829b004d1fee`**, the hash sdkmanager accepts for `android-sdk-license`.
- **Other checks:**
  - the UI type-checks against Compose's desktop jars;
  - the new code's platform calls all exist at API 30;
  - the backend and UI consistency guards pass.

**Open, on the phone (§6):** A1's measurements, the SDK install end to end, and install → confirm → launch.

## 8. Sources

- [Mobile Harness](https://github.com/techjarves/Mobile-Harness): MIT. Read: `RuntimeInstaller.kt`, `AndroidBuildSupport.kt`, `AndroidAppInstaller.kt`, `AndroidProjectTemplateGenerator.kt`, `build.gradle.kts`, `dist/runtime-bundles/manifest.json`.
- [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools): Apache-2.0. AOSP build tools built with CMake, static, in public GitHub Actions; `PLATFORM_TOOLS_VERSION "35.0.2"`. Read at `fe637bf` (2026-10-16), and the 35.0.2 asset downloaded and inspected here.
- [katiusu/adt](https://github.com/katiusu/adt): Apache-2.0. Read at `9d1aa9d`. "build-tools 36.0.0/37.0.0 are built from the AOSP platform-tools-35.0.2 tag"; NDK host is x86-64 only.
- AOSP `frameworks/base` (GitHub mirror): `ResolveIntentHelper.getLaunchIntentSenderForPackage` and `AppsFilterBase.shouldFilterApplication`. No package-manager call grants an installer implicit visibility.
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes): JDK 17 and SDK Build Tools 36.0.0 are the minimum and default.
- [Gradle forum: the Maven aapt2 is x86-64](https://discuss.gradle.org/t/error-tyying-to-build-a-project/52127); [`android.aapt2FromMavenOverride` on Linux ARM64](https://openfde.atlassian.net/wiki/spaces/OpenFDEEN/pages/839778305/Install+Android+Studio+on+Linux+ARM64+Machines).
- Phantom processes: [Andronix on Android 12+](https://docs.andronix.app/android-12/andronix-on-android-12-and-beyond), [Phantom-Process-Killer notes](https://github.com/atamshkai/Phantom-Process-Killer).
