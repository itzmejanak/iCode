# Contributing to iCode

Contributions are welcome. Keep changes focused, verify claims against the current source, and include tests and documentation when behavior changes.

## Development setup

Required host tools:

- JDK 17
- Android SDK Platform 36 and Build Tools `36.0.0`
- Android NDK `27.0.12077973`
- CMake `3.22.1`
- the `patch` command
- Git with recursive submodule support
- an ARM64 Android 11+ device or emulator for device testing

Set `ANDROID_HOME` or add the SDK path to `local.properties`, then clone and build:

```bash
git clone --recurse-submodules https://github.com/itzmejanak/iCode.git
cd iCode
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use `dev.icode.mobile.debug`, so they can be installed beside a release build.

The native build requires both Git submodules. CMake applies `third_party/patches/*/*.patch` to build-directory copies; it does not modify the submodule working trees.

## Branch and pull-request workflow

1. Search existing issues and pull requests. Open an issue before a large or behavior-changing proposal.
2. Branch from `main`. Use a descriptive name such as `fix/editor-scroll`, `feature/runtime-tool`, or `docs/privacy`.
3. Keep the pull request limited to one concern. Avoid unrelated formatting or refactoring.
4. Add or update tests for changed behavior. Include before/after screenshots for visible UI changes.
5. Update relevant public documentation and add a concise entry under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
6. Run the applicable quality gates and complete the pull-request template. Explain any test that could not be run.

## Tests and quality gates

### What CI runs

The `CI` workflow checks out recursive submodules, uses JDK 17, installs NDK `27.0.12077973` and Android Platform 36, then runs exactly:

```bash
./gradlew testDebugUnitTest testReleaseUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

That workflow does **not** run Android instrumentation tests, lint, consistency guards, release assembly, or screenshot capture.

### Full local quality gate

Run the checks relevant to your change. Before a broad code pull request, run:

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./tools/check-backend-consistency.sh
./tools/check-ui-consistency.sh
```

Also run `./gradlew :app:assembleRelease` for changes to native code, packaging, shrinking, manifests, build types, dependencies, or release/debug source-set boundaries. Run `./gradlew :app:connectedDebugAndroidTest` when a suitable ARM64 API-30-or-newer device or emulator is available and the affected behavior needs device coverage. Run `./tools/capture-figma-screens.sh` for deliberate visual verification; it requires a configured Android device.

Do not use either guard's `--update` option to make a regression pass. It is only for recording a deliberately reduced baseline.

### Test locations

- `app/src/test/java/`: local JVM unit tests
- `app/src/test/resources/`: JVM fixtures, including syntax samples
- `app/src/sharedTest/java/`: editor-color tests compiled into both local and Android test source sets
- `app/src/androidTest/java/`: Android instrumentation and device tests; these are not run by the `CI` workflow

Place tests with the closest existing package and reuse production parsers and contracts rather than duplicating behavior in test-only helpers.

## Architecture and code rules

Read the [documentation index](docs/README.md) and the documents relevant to the area before editing it. The durable starting points are:

- [`docs/architecture.md`](docs/architecture.md) for system boundaries and data ownership;
- [`docs/engineering-rules.md`](docs/engineering-rules.md) for package ownership, dependency direction, guarded writes, lifecycle, persistence, and testing rules; and
- [`docs/design-and-navigation.md`](docs/design-and-navigation.md) for the phone-first interface, navigation, design system, and wider-screen roadmap boundary.

Follow the current physical structure: the repository has one `:app` Gradle module, with domain contracts, Android/data implementations, Compose UI, navigation, and the design system separated by package.

The consistency guards enforce part of this contract. The UI guard checks design-token use, screen dependencies, route reachability, dialogs/toasts, capture forks, and file-size debt. The backend guard checks ownership of package installation, Git execution, stores, hashing, byte formatting, notices, and the TextMate engine.

Never commit credentials, provider responses, private workspace content, personal device data, or unsanitized logs and screenshots.

## Documentation and status

- [`docs/README.md`](docs/README.md) is the documentation map and identifies each document's role.
- [`docs/roadmap.md`](docs/roadmap.md) is the only public roadmap. Do not duplicate its backlog in another document.
- Source and tests are authoritative for current behavior. Design and implementation records can provide evidence and history, but they do not prove that a feature shipped.
- `CHANGELOG.md` records user-visible release history, not internal cycles, task counts, or implementation diaries.
- Use only the canonical roadmap labels: **Done** means the required scope and evidence are complete; **Partially Done** means useful implementation exists but required scope is missing; **Not Started** means there is no production implementation; **Needs Verification** means implementation exists but required device, provider, network, performance, release, or integration evidence is missing. A status does not by itself mean the work is in a published release.
- Update links, status, privacy, permissions, and limitations in the same pull request as the behavior they describe.

## Privacy, security, and licensing checklist

Before requesting review, check whether the change:

- stores, transmits, logs, exports, or deletes new data; if so, update [PRIVACY.md](PRIVACY.md);
- adds a permission, exported Android component, deep link, native/runtime boundary, download, provider, MCP path, or credential flow; if so, include a security review and update [SECURITY.md](SECURITY.md) when the public scope changes;
- adds or updates a dependency, native component, grammar, font, icon, palette, or runtime download; if so, verify provenance and license terms and update [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), the grammar catalog/provenance, and bundled license resources as applicable;
- changes release behavior; if so, confirm that the current `versionName`'s `## [<versionName>]` changelog section remains extractable by the release workflow; and
- preserves user control at trust boundaries, keeps secrets in Keystore-backed storage where supported, validates paths and URLs, and avoids secrets or workspace content in logs.

Do not make legal compatibility claims that the repository's source and license files do not establish. Release-license audits remain tracked in the [roadmap](docs/roadmap.md).

## Commit messages

Use a short, imperative subject that describes the change, for example:

```text
git: show signed status in history
privacy: document report attachments
```

A plain subject is fine. Conventional Commit prefixes are optional, not required.

## Conduct and license

Follow the [Code of Conduct](CODE_OF_CONDUCT.md). By contributing, you agree that your contribution is licensed under the repository's [GPL-3.0 license](LICENSE).
