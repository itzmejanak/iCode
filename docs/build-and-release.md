# Build and release

This document describes how to build, test, analyze, sign, and publish the current iCode Android application. Executable Gradle and workflow configuration is authoritative. Open automation and compliance work is tracked in the [current roadmap](./roadmap.md#code-quality-and-licence-compliance-automation).

## Status

| Area | Status | Current scope |
|---|---|---|
| Local Gradle build contract | Done | The wrapper, Android configuration, native inputs, source sets, variants, tests, lint, and consistency guards are defined. |
| Pull-request and `main` CI | Partially Done | CI runs debug/release JVM tests and assembles debug, but it omits lint, consistency guards, release-family assembly, and device tests. |
| CodeQL | Partially Done | Java/Kotlin debug compilation is analyzed; native C/C++ is outside the configured scope. |
| Signed release publishing | Partially Done | Tag and manual staging publication are automated, but the release workflow has no independent full verification gate. |
| Dependency and licence automation | Partially Done | Dependabot opens Gradle and Actions update PRs; dependency review, SBOM, native/runtime pin updates, and licence/notice verification are not automated. |

## Prerequisites

A clean maintainer checkout needs all of the following:

| Requirement | Exact current value or reason |
|---|---|
| JDK | JDK 17. Java source/target and Kotlin JVM target are 17. |
| Android SDK | Platform 36. `compileSdk` and `targetSdk` are 36; `minSdk` is 30. |
| Android Build Tools | Build Tools 36.0.0 are required by release signing and packaging commands. |
| Android NDK | `27.0.12077973`. |
| CMake | `3.22.1`. The project pins this version in both Gradle and CMake. |
| Host `patch` utility | `app/src/main/cpp/CMakeLists.txt` uses `find_program(... patch REQUIRED)` to apply repository patches to build-tree copies of PRoot and libandroid-shmem. |
| Recursive submodules | `third_party/proot` and `third_party/libandroid-shmem` are Git submodules. CMake also requires the tracked `third_party/talloc` source. Use recursive checkout so nested upstream requirements are not omitted. |
| Network | A fresh checkout needs Git network access for submodules, `services.gradle.org` for the wrapper, Google/Maven Central/Gradle Plugin Portal for plugins, Google/Maven Central for dependencies, and filtered JitPack access for Termux. The repository has no committed offline Maven repository. |
| Device tests and installation | An ARM64 Android 11/API 30 or newer device or emulator with adb connectivity. The APK is `arm64-v8a` only; x86 and x86_64 targets cannot run it. |

The native configuration fails any ABI other than `arm64-v8a`. The app also packages PRoot and its loader in a form that relies on ARM64 Android behavior.

## Checkout

Preferred fresh checkout:

```bash
git clone --recurse-submodules https://github.com/itzmejanak/iCode.git
cd iCode
```

For an existing clone:

```bash
git submodule update --init --recursive
```

CMake checks required upstream source files and reports that recursive update command when they are absent.

## Gradle and Android versions

Always use the checked-in wrapper:

```bash
./gradlew --version
```

The wrapper downloads Gradle `8.14.1`. The root build currently applies Android Gradle Plugin `8.13.2` and Kotlin Android/Compose plugins `2.4.21`. Use these executable pins rather than values from superseded reference material.

The application configuration currently declares:

- namespace and stable package: `dev.icode.mobile`;
- `minSdk = 30`;
- `compileSdk = 36`;
- `targetSdk = 36`;
- `ndkVersion = 27.0.12077973`;
- CMake `3.22.1`;
- one packaged ABI, `arm64-v8a`;
- `versionName = 0.1.0` in the current source.

Release tags are validated against `versionName` at workflow runtime, so use the pattern in [Release tags](#release-tags) rather than copying the current number into scripts.

## Local commands

### Build

Debug build:

```bash
./gradlew :app:assembleDebug
```

Release-family builds:

```bash
./gradlew :app:assembleStaging
./gradlew :app:assemblePreview -PreleaseChannel=preview -PreleaseNumber=N
./gradlew :app:assembleBeta -PreleaseChannel=beta -PreleaseNumber=N
./gradlew :app:assembleRelease
```

`N` must be an integer from 0 through 99. Passing `releaseChannel` and `releaseNumber` for preview and beta matters: those values participate in `versionCode`, while the build type supplies the package and `versionName` suffix.

To embed an iCode website base URL, add:

```text
-PicodeWebUrl=https://example.invalid
```

An empty value disables website-backed app features in that build.

### JVM tests

The same explicit pair used by CI is:

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
```

For the broader Gradle aggregate across generated unit-test tasks:

```bash
./gradlew test
```

The explicit CI pair does not execute staging, preview, or beta unit-test tasks.

### Lint

```bash
./gradlew :app:lintDebug
```

Lint is a project verification command, but the current CI and release workflows do not invoke it.

### Consistency guards

```bash
./tools/check-backend-consistency.sh
./tools/check-ui-consistency.sh
```

Run the guards without arguments. Their `--update` modes rewrite ratchet baselines and are maintenance operations, not verification commands.

### Connected-device tests

```bash
./gradlew :app:connectedDebugAndroidTest
```

Use a connected ARM64 API-30-or-newer target. The current instrumentation sources include editor search/navigation, native editor-colour, and Markdown math rendering tests under `app/src/androidTest`. Shared test helpers from `app/src/sharedTest/java` are also compiled into `androidTest`.

`gradle.properties` sets `android.injected.androidTest.leaveApksInstalledAfterRun=true`. This deliberately avoids uninstalling the debug package after tests, because uninstalling it would remove that package's runtime, settings, credentials, and workspaces.

### Recommended local gate

The following set covers the current CI commands plus the project checks CI omits:

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./tools/check-backend-consistency.sh
./tools/check-ui-consistency.sh
```

Add `:app:connectedDebugAndroidTest` when an appropriate device is available. Add the release-family assemble tasks when changing build types, shrinking, resource overlays, update channels, signing assumptions, or release automation.

## Source sets and generated inputs

| Source set or input | Role |
|---|---|
| `app/src/main` | Production Kotlin/Java, Android resources, assets, manifest, and native C/C++ entry point. |
| `app/src/test` | Local JVM tests and test resources. |
| `app/src/androidTest` | Connected-device instrumentation tests. |
| `app/src/sharedTest/java` | Helpers added explicitly to both `test` and `androidTest`. |
| `app/src/debug` | Debug-only capture implementation and development brand resources. |
| `app/src/release/java` | Production no-op capture implementation. Staging, preview, and beta explicitly import this Java source set. |
| `app/src/staging`, `app/src/preview`, `app/src/beta` | Channel-specific launcher and brand resource overlays. |
| `third_party/ratex/jniLibs` | Prebuilt RaTeX JNI libraries added to `main`. |
| `third_party/termux-terminal-emulator/src/main/java` | Termux emulator Java source added to `main`. |
| `app/src/main/cpp/CMakeLists.txt` | Native PRoot, process bridge, Termux JNI, talloc, and libandroid-shmem build. |

Every variant generates `CatalogFileTypes.kt` from `app/src/main/assets/textmate/catalog.json`. Packaging also depends on `check<Variant>PageAlignment`, which examines merged 64-bit native libraries and fails if a load segment is aligned below 16 KiB.

Native configuration copies upstream PRoot and libandroid-shmem into the build tree and applies sorted patches from `third_party/patches/<name>`. Submodule checkouts remain untouched.

## Build types, package IDs, and channels

There are exactly five build types:

| Build type | Environment | Package ID | `versionName` suffix | `UPDATE_CHANNEL` | Local signing |
|---|---|---|---|---|---|
| `debug` | Development | `dev.icode.mobile.debug` | `-debug` | empty | Android debug signing |
| `staging` | Staging | `dev.icode.mobile.staging` | `-staging` | empty | Unsigned |
| `preview` | Preview | `dev.icode.mobile.preview` | `-preview.N` | `preview` | Unsigned |
| `beta` | Beta | `dev.icode.mobile.beta` | `-beta.N` | `beta` | Unsigned |
| `release` | Stable | `dev.icode.mobile` | none | `stable` | Unsigned |

Staging, preview, beta, and release use release minification and resource shrinking. Debug alone contains capture tooling. The other four variants compile the no-op release capture implementation.

`releaseChannel` defaults to `stable`, and `releaseNumber` defaults to `0`. Version codes order preview before beta before stable for the same base version. Preview and beta local builds must pass the matching channel property to avoid a build-type suffix paired with a stable-stage version code.

### Unsigned local release warning

`app/build.gradle.kts` defines no release signing configuration. Local staging, preview, beta, and release outputs are therefore unsigned, conventionally at:

```text
app/build/outputs/apk/staging/app-staging-unsigned.apk
app/build/outputs/apk/preview/app-preview-unsigned.apk
app/build/outputs/apk/beta/app-beta-unsigned.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

Do not distribute these as official releases. The release workflow consumes the selected unsigned APK, zip-aligns it, signs it, verifies it, and publishes a checksum.

## Current CI scope

`.github/workflows/ci.yml` runs on every pull request and on pushes to `main`. It has read-only repository contents permission and cancels an older run for the same ref.

The job:

1. checks out recursive submodules;
2. selects Temurin JDK 17;
3. configures Gradle caching;
4. installs NDK `27.0.12077973` and Android Platform 36 with sdkmanager;
5. runs `./gradlew testDebugUnitTest testReleaseUnitTest --stacktrace`;
6. runs `./gradlew :app:assembleDebug --stacktrace`;
7. uploads matching debug APKs as the Actions artifact `app-debug` for seven days.

CI does **not** run lint, either consistency guard, connected-device tests, staging/preview/beta/release assembly, or an all-variant test matrix. It also does not explicitly install CMake 3.22.1; the build relies on the hosted runner or Android tooling to provide the project-pinned version.

## Current CodeQL scope

`.github/workflows/codeql.yml` runs on pull requests targeting `main`, pushes to `main`, and Mondays at 03:00 UTC. It checks out recursive submodules, selects Temurin 17, configures Gradle, and installs the same NDK and Platform 36 packages.

CodeQL initializes only `java-kotlin` in manual build mode and observes:

```bash
./gradlew :app:compileDebugKotlin --no-build-cache --rerun-tasks --stacktrace
```

The no-cache rerun makes compilation visible to the analyzer. This configuration does not analyze the C/C++ sources built through CMake, release-family variants, shell scripts, or workflow YAML. It also does not run tests, lint, or consistency guards.

## Release tags

The workflow trigger accepts every pushed tag matching `v*`, but its validation step permits only tags derived from the current Gradle `versionName`:

| Pattern | Build type | Channel | GitHub release kind |
|---|---|---|---|
| `v<versionName>` | `release` | `stable` | Latest stable release |
| `v<versionName>-preview.N` | `preview` | `preview` | Pre-release |
| `v<versionName>-beta.N` | `beta` | `beta` | Pre-release |

`N` must contain decimal digits and must satisfy Gradle's 0–99 requirement. An invalid `v*` tag starts the workflow and then fails tag validation.

Manual `workflow_dispatch` does not publish a release. It builds and signs `staging` with release number 0, embeds `ICODE_WEB_STAGING_URL`, and uploads the signed APK and checksum as a workflow artifact retained for 14 days.

## Signing inputs

The release workflow expects these repository **secrets**:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `ICODE_WEB_RELEASE_TOKEN`

It expects these repository **variables**:

- `ICODE_WEB_URL`
- `ICODE_WEB_STAGING_URL`

Only the names belong in repository documentation. Never record secret values, decoded keystores, passwords, aliases treated as confidential, or release tokens in source, logs, issue text, or artifacts.

The website release token is expected to match the website deployment's `RELEASE_PUBLISH_TOKEN`, but neither value belongs in this repository.

## Signing and artifact names

For the selected release-family build, the workflow:

1. assembles the unsigned APK with `releaseChannel`, `releaseNumber`, and the selected website URL;
2. reads the built `versionName`, `versionCode`, and minimum SDK using Build Tools 36.0.0 `aapt2`;
3. decodes the temporary keystore under the runner's temporary directory;
4. runs `zipalign -p -f 4`;
5. signs with `apksigner` using environment-backed passwords;
6. verifies the signed APK with `apksigner verify`;
7. writes a SHA-256 checksum file; and
8. on the successful path, removes the decoded temporary keystore.

The current shell step has no cleanup trap. A failure between decoding and the final removal can leave the temporary keystore in the ephemeral hosted runner until GitHub destroys that runner. Adding unconditional cleanup is tracked in the roadmap.

The signed filename is:

```text
iCode-<built-versionName>-arm64-v8a.apk
```

The checksum filename appends `.sha256`. Examples by form are:

```text
iCode-<versionName>-arm64-v8a.apk
iCode-<versionName>-preview.N-arm64-v8a.apk
iCode-<versionName>-beta.N-arm64-v8a.apk
iCode-<versionName>-staging-arm64-v8a.apk
```

A manual staging run names its Actions artifact exactly the same as the APK and includes both the APK and checksum. A tag release attaches both files to the GitHub Release. Ordinary CI separately uploads debug APKs under artifact name `app-debug`.

## Changelog extraction

For a tag build, release notes come from `CHANGELOG.md`. The workflow reads the current base Gradle `versionName`, starts after the heading:

```text
## [<versionName>]
```

and stops before the next `## [` heading. Preview and beta releases therefore share the base version's changelog section; there are no channel-specific changelog headings. If extraction produces an empty file, the fallback note is `iCode <built-versionName>`.

## Publication and website registration order

A tag release uses this order:

1. build the selected variant;
2. zip-align, sign, verify, and checksum the APK;
3. extract release notes;
4. create the GitHub Release and upload the APK and checksum;
5. POST release metadata to `${ICODE_WEB_URL}/api/v1/ci/releases`.

Website registration intentionally follows GitHub publication because its record contains the final GitHub asset URL. The request includes built version name and code, channel, ABI `arm64-v8a`, minimum SDK, asset URL, SHA-256, notes, and `publish: true`.

HTTP 200 and 201 are accepted. If either `ICODE_WEB_URL` or `ICODE_WEB_RELEASE_TOKEN` is absent, the workflow warns and skips website registration. Any other response fails the workflow, but the GitHub Release already exists because publication happened first. Manual staging runs never register a website release.

## Roadmap

The current workflows and their omissions are documented above. The canonical remaining requirements and completion evidence for independent release gates, CI variant coverage, explicit CMake provisioning, native security analysis, dependency inventory/SBOM work, and licence/notice verification live only in [Code quality and licence-compliance automation](./roadmap.md#code-quality-and-licence-compliance-automation).

The selected assemble task still runs its normal Gradle dependencies, including native compilation and the variant's 16 KiB page-alignment guard. That build-time behavior is not a replacement for the omitted independent quality gates.

## Configuration map

| Responsibility | Current path |
|---|---|
| Plugin versions | `build.gradle.kts` |
| Android versions, source sets, variants, generated source, and page-alignment guard | `app/build.gradle.kts` |
| Wrapper | `gradle/wrapper/gradle-wrapper.properties` |
| Repositories | `settings.gradle.kts` |
| Native prerequisites and patching | `app/src/main/cpp/CMakeLists.txt`, `.gitmodules`, `third_party/patches/` |
| Local JVM tests | `app/src/test/`, `app/src/sharedTest/` |
| Connected tests | `app/src/androidTest/`, `app/src/sharedTest/` |
| CI | `.github/workflows/ci.yml` |
| CodeQL | `.github/workflows/codeql.yml` |
| Release signing and publication | `.github/workflows/release.yml` |
| Dependency update PRs | `.github/dependabot.yml` |
| Licence inventory | `THIRD_PARTY_NOTICES.md` |
| Consistency guards | `tools/check-backend-consistency.sh`, `tools/check-ui-consistency.sh` |
