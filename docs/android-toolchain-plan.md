# Android Apps, Built on the Phone — Toolchain Plan

Status: **plan, decisions taken (2026-10-17), for the owner's review**. Nothing is built yet. It extends
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
| Where does it go? | **One tool, "Android SDK", in the tools catalog**: Set Up Tools in onboarding, Runtime Tools in Settings. It installs the JDK through apt. It installs Google's own `sdkmanager`, which fetches the platform and build tools after the user accepts Google's licence. Then it replaces the build tools' x86-64 executables with ARM64 builds of the same AOSP source. Gradle is configured once, in one file. Build → install → launch is one action, used by the UI and the assistant (§4). |

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
| G2 | **Only the root filesystem is a pinned download.** The downloader is named for it (`RootfsDownloader`), and the manifest has one `rootfs` entry | `RuntimeInstaller.kt:56`, `RootfsDownloader.kt` | The manifest gains `archives`. One `RuntimeManifest` reads both. The downloader becomes `PinnedDownload` and verifies the SHA-256 itself (§4.2) |
| G3 | **No APK install, no launch, no "Install unknown apps" permission** | `domain/SystemAccess.kt` (`Notifications`, `Battery`, `Storage`) | `SystemPermission.InstallApps`; one installer and launcher (§4.5) |
| G4 | **No build the user can start.** Managed processes are started only by the assistant | `RuntimeProcesses.start`, `ICodeIntent.ProcessIntent` | Build is a managed process that iCode starts. Install and launch follow when it exits with 0. The ▶ button and the assistant share one action (§4.4) |

## 4. Decisions

### 4.1 One catalog entry: **Android SDK**

```kotlin
data class RuntimeTool(
    …,
    val packages: List<String>,          // apt, as today
    /** The data layer's setup run after the apt packages, by id; null for an apt-only tool. */
    val setup: String? = null,
)

val AndroidSdk = RuntimeTool(
    "android-sdk", "Android SDK", "Build, install and run Android apps: JDK 17, SDK Platform 36, Build-Tools 36 (ARM64)",
    usedBy = "Build & Run for Android projects", packages = listOf("openjdk-17-jdk-headless"), setup = "android-sdk",
)
```

**How the setup works:**
- It is one data-layer interface, `RuntimeToolSetup`, with these members:
  - `id`;
  - `measure()`, which gives exact bytes;
  - `install(progress)`;
  - `isInstalled()`, read from its receipt;
  - `remove()`.
- Setups are registered by id. The domain stays pure: it knows a setup exists, not how it runs.

**What changes where:**
- **Installed** means dpkg has every package *and* the setup's receipt matches. Install runs apt first, then the setup. Removal reverses that order.
- **Download** is apt's archives plus the setup's measured bytes, so the page's one total still counts each thing once.
- **Progress** stays one `RuntimeToolWork`. The setup reports its own steps in the same phase and step fields: "Downloading Android platform 36 · 41 of 63 MB", then "Setting up build tools". Downloads report bytes; apt does not.

**Placement:** not in Standard, because it is opt-in and large. It is listed in Set Up Tools (onboarding, Custom) and in Settings › Runtime Environment › Runtime Tools, the same list (`runtime-tools-plan.md` §8).

### 4.2 What the Android SDK tool installs, exactly

The SDK lives at **`/root/Android/Sdk`**, which is Android Studio's location on Linux. It is in the guest home, so Repair keeps it (§3).

| Order | What | From | Checked by |
|---|---|---|---|
| 1 | `openjdk-17-jdk-headless`. JDK 17 is AGP 9's minimum and default; Gradle 7.3 and later run on it | apt (noble) | dpkg |
| 2 | Google **cmdline-tools** (`sdkmanager`), into `…/Sdk/cmdline-tools/latest` | Google's download page, pinned in `manifest.json` `archives` with URL, SHA-256 and size | SHA-256 |
| 3 | `platforms;android-36` and `build-tools;36.0.0`. 36.0.0 is AGP 9's minimum, and AGP 8.x accepts it | `sdkmanager --install`: Google's own installer writes `package.xml` and checks Google's checksums | sdkmanager |
| 4 | **ARM64 host tools** over `build-tools/36.0.0`: `aapt`, `aapt2`, `aidl`, `zipalign`, `dexdump`, `split-select` | lzhiyong/android-sdk-tools release **35.0.2**, `android-sdk-tools-static-aarch64.zip`, SHA-256 `db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2`, 14,401,170 bytes. Its `aapt2` is "ELF 64-bit, ARM aarch64, statically linked" (read here). It is built by the project's public CI from AOSP source, under Apache-2.0 | SHA-256 |
| 5 | The environment: `/etc/profile.d/icode-android.sh` sets `ANDROID_HOME` and adds `cmdline-tools/latest/bin` and `build-tools/36.0.0` to `PATH` | Written by the setup | — |
| 6 | Gradle, in one file: `~/.gradle/init.d/icode-android.gradle` (§4.3) | Written by the setup | — |
| 7 | The receipt `~/.icode/runtime-tools/android-sdk`: the versions and checksums installed | Written by the setup | — |
| 8 | Verification: `java -version` reports 17, `sdkmanager --version` runs, `aapt2 version` runs, and `platforms/android-36/android.jar` exists | Guest commands | exit codes |

**Why these ARM64 tools, and not another source:**
- **Mobile Harness's bundle** has no recorded source (§2.2).
- **ADT** (katiusu/adt, Apache-2.0) uses the same build system, adapted from lzhiyong. Its builds link against glibc, and its "36.0.0" and "37.0.0" are built from the same AOSP tag. Its own README says so: "AOSP has no newer platform-tools tag".
- **Building them in iCode's own CI** needs a public place to publish the artifacts. iCode's repository could not be seen by search from here, which suggests it is private, so phones could not download from it. The owner can make that move later with the same `build.py`. It would be a replacement, not a second path.
- **Statically linked** means no loader and no libraries. Under PRoot that is the most robust form.

**Why sdkmanager, and not iCode unpacking zips:** sdkmanager is Google's installer. It writes `package.xml` and resolves dependencies as Android Studio does. The user also gets the standard `sdkmanager` to add other platforms later.

**The licence (L10):**
- Runtime Tools reads Google's repository (`repository2-3.xml`, the file sdkmanager reads) for two things only:
  - the download sizes of the two packages, measured and never written in the code;
  - the text of the licence they require (`android-sdk-license`).
- With Android SDK ticked, Install shows that text with **Accept** and **Decline**, as Android Studio's setup does.
- Accept writes `licenses/android-sdk-license`, the licence's SHA-1, which is what sdkmanager writes. Only then does sdkmanager run. The parser is `SdkRepository`: pure, and tested on a recorded copy.

**What Repair and Reset do:**
- **Repair** rebuilds the system, which removes the JDK, but keeps the home: the SDK, receipt, init script and debug key. The tool then reads as not installed, because one package is missing. Install fetches only the JDK, since the receipt is still valid.
- **Reset** removes everything, and the tool is offered again.
- **The debug key (L11):** it stays `~/.android/debug.keystore`, so Repair keeps it. Reset's confirmation says the apps it installed must be uninstalled before they can be installed again.

### 4.3 Gradle on the phone — one file

All of iCode's Gradle settings live in one init script, `~/.gradle/init.d/icode-android.gradle`, applied to every build. Nothing goes in `gradle.properties`, and there is no environment variable.

**What it sets:**

| Setting | Value | Why |
|---|---|---|
| aapt2 | AGP's override, pointing at `…/build-tools/36.0.0/aapt2` (L1) | Gradle's own `aapt2` download is x86-64 and cannot run |
| Build tools for every Android module | `buildToolsVersion = "36.0.0"` | Every project uses the one ARM64 build tools. Without it, a project naming another version would make AGP download x86-64 tools that cannot run. The build log says so in one line |
| Memory | Heap = one quarter of the phone's RAM, between 768 MB and 2 GB; metaspace 512 MB (L3) | Phones differ. iCode writes the figure when the setup runs |
| Workers | 2, no parallel builds, the Kotlin compiler in-process (L3, L8) | Fewer processes, and fewer killed |
| Daemon | Off | A guest process ends with its PRoot session (`--kill-on-exit`), so a daemon could not outlive the build anyway |

**One part depends on measurement:** whether an init script can set properties that AGP reads as Gradle properties, namely the `aapt2` override and `org.gradle.jvmargs`. Step A1 measures this on the phone. If it cannot, those two lines go in `~/.gradle/gradle.properties` as a block marked as iCode's, written and removed by the setup. It is still one owner and one place for each setting, never both.

### 4.4 Build → install → launch: one action (G4)

- **`AndroidApps`** (data, one coordinator) has three steps:
  1. **Find** the module: `settings.gradle(.kts)` and a module holding `src/main/AndroidManifest.xml`, under the workspace.
  2. **Build** as a *managed process* named "Build <app>", running `./gradlew assembleDebug` (the project's own wrapper, so it uses the project's Gradle). Its output goes to the Terminal panel tab that managed processes already have. Stop is the process's own Stop.
  3. **When it ends with 0**, read `outputs/apk/debug/output-metadata.json` for the APK, then **install and launch** it (§4.5).
- **The ▶ button** appears in the Explorer header when the workspace holds an Android project, with "Build & Run". It is disabled until the Android SDK tool is installed, and then says what is missing. The assistant gets one tool, `run_android_app`, that calls the same action. There is no second build path.
- **Issues** come from Gradle's own `e: file:///…:line:col` and `error:` lines. They become the Problems panel's existing `Diagnostic` (`domain/DiagnosticsContract.kt`, with source "Gradle"), parsed by one pure `GradleIssues`. iCode has no other parser of build output, so nothing is duplicated.

### 4.5 Installing and launching (L5, L6, G3)

- **The permission:**
  - The manifest gets `REQUEST_INSTALL_PACKAGES`.
  - `SystemPermission.InstallApps` is read with `canRequestPackageInstalls()` and requested through `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
  - It is asked where it is needed, the first time something is installed, with the shared `PermissionExplanation` wording.
  - Distributing through Play would need the `REQUEST_INSTALL_PACKAGES` declaration. This is noted for the owner.
- **Install:**
  - A `PackageInstaller` session in full-install mode.
  - The result goes to a receiver that is not exported. `STATUS_PENDING_USER_ACTION` starts Android's confirmation; success carries `EXTRA_PACKAGE_NAME`.
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

| Step | What | Gate |
|---|---|---|
| A1 | **Measure on the phone first.** This step decides §4.3's one open point and the template's AGP version. Run by hand in iCode's terminal: <ul><li>JDK 17 from apt under iCode's PRoot;</li><li>cmdline-tools and `sdkmanager --install`;</li><li>the ARM64 `aapt2 version`;</li><li>an Empty Activity project with AGP 9.x, then 8.13.x, through `assembleDebug`, with the settings of §4.3;</li><li>time, peak memory, and the number of processes against the 32 limit.</li></ul> | Recorded in §7 |
| A2 | G2: `RuntimeManifest` (rootfs plus archives) and `PinnedDownload` (download, SHA-256, size, progress), replacing `RootfsDownloader` | Unit tests; the rootfs install unchanged |
| A3 | G1: `RuntimeTool.setup`, `RuntimeToolSetup`, receipts, the installer running apt then the setup, sizes, and removal | Unit tests with a scripted setup |
| A4 | The Android SDK setup (§4.2) and `SdkRepository` (sizes and licence, tested on a recorded `repository2-3.xml`), plus the init script (§4.3) | Unit tests; phone install |
| A5 | UI: the Android SDK row and the licence Accept/Decline in Select Tools and Verify Settings | Type-check, captures |
| A6 | G3: `SystemPermission.InstallApps`, the installer, the receiver and the launcher | Unit tests where pure; phone install |
| A7 | G4: `AndroidApps`, the ▶ button and `run_android_app` | Unit tests (module discovery, APK metadata, Gradle issues); phone build & run |
| A8 | New Android app (§4.6); docs as built; guards; push | Phone: new app → ▶ → it opens |

## 6. Device checks (none can run here)

This container is x86-64. `dl.google.com` is refused by its proxy (403), and Google's Maven too. So the build, sdkmanager and `aapt2` can only run on the phone.

| Check | Decides |
|---|---|
| `aapt2 version` from the pinned zip, under PRoot | That the static ARM64 binary runs (L1) |
| AGP 9.x with an `aapt2` built from AOSP 35.0.2 | The template's AGP. ADT validated its 35.0.2 build tools on a real device, but AGP 9 has not been measured with them |
| An init script setting the `aapt2` override and `jvmargs` | §4.3: init script alone, or plus a marked `gradle.properties` block |
| Peak RAM and process count of `assembleDebug` | §4.3 values; the phantom-process risk (L8) |
| Install → confirm → launch on API 30–36 | §4.5's launch, without `<queries>` |
| Repair, then Build & Run with the same app installed | That the debug key survives (L11) |

## 7. Record

Filled in at A1 and later.

## 8. Sources

- [Mobile Harness](https://github.com/techjarves/Mobile-Harness): MIT. Read: `RuntimeInstaller.kt`, `AndroidBuildSupport.kt`, `AndroidAppInstaller.kt`, `AndroidProjectTemplateGenerator.kt`, `build.gradle.kts`, `dist/runtime-bundles/manifest.json`.
- [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools): Apache-2.0. AOSP build tools built with CMake, static, in public GitHub Actions; `PLATFORM_TOOLS_VERSION "35.0.2"`. Read at `fe637bf` (2026-10-16), and the 35.0.2 asset downloaded and inspected here.
- [katiusu/adt](https://github.com/katiusu/adt): Apache-2.0. Read at `9d1aa9d`. "build-tools 36.0.0/37.0.0 are built from the AOSP platform-tools-35.0.2 tag"; NDK host is x86-64 only.
- AOSP `frameworks/base` (GitHub mirror): `ResolveIntentHelper.getLaunchIntentSenderForPackage` and `AppsFilterBase.shouldFilterApplication`. No package-manager call grants an installer implicit visibility.
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes): JDK 17 and SDK Build Tools 36.0.0 are the minimum and default.
- [Gradle forum: the Maven aapt2 is x86-64](https://discuss.gradle.org/t/error-tyying-to-build-a-project/52127); [`android.aapt2FromMavenOverride` on Linux ARM64](https://openfde.atlassian.net/wiki/spaces/OpenFDEEN/pages/839778305/Install+Android+Studio+on+Linux+ARM64+Machines).
- Phantom processes: [Andronix on Android 12+](https://docs.andronix.app/android-12/andronix-on-android-12-and-beyond), [Phantom-Process-Killer notes](https://github.com/atamshkai/Phantom-Process-Killer).
