# Contributing to iCode

Thanks for helping. This guide covers setup, the change flow, and the rules the codebase already follows.

## Setup

- JDK 17
- Android SDK 36 and NDK 27.0.12077973
- An ARM64 Android 11+ device or emulator (iCode ships `arm64-v8a` only)

```bash
git clone --recurse-submodules https://github.com/itzmejanak/iCode.git
cd iCode
./gradlew :app:assembleDebug
./gradlew test
```

Debug builds install as `dev.icode.mobile.debug`, so they can sit next to a release build.

## Change flow

1. Open or find an issue for anything larger than a small fix.
2. Branch from `main` (`fix/…`, `feature/…`, `docs/…`).
3. Keep each PR to one change. Add or update unit tests under `app/src/test/`, mirroring the main source tree.
4. Run `./gradlew test` and `./gradlew :app:assembleDebug` locally. CI runs both on every PR.
5. Fill in the PR template. Include screenshots for UI changes.
6. Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for user-visible changes.

## Code rules

These come from the existing project docs. Read them before changing an area:

- [docs/architecture.md](docs/architecture.md): system boundaries and the decisions that are not negotiable.
- [docs/backend-split-rules.md](docs/backend-split-rules.md): how `domain/`, `data/` and `ui/` are separated.
- [docs/layout.md](docs/layout.md): the screen layout specification.
- UI uses the design system's semantic tokens (`designsystem/`), never hard-coded colours.
- Workspace content never leaves the device unless the user explicitly sends it (for example, to their chosen AI provider).

The scripts `tools/check-backend-consistency.sh` and `tools/check-ui-consistency.sh` check some of these rules.

## Commit messages

Use short, imperative subjects, for example `git: show signed status in history`. Conventional
Commit prefixes (`feat:`, `fix:`, `docs:`) are welcome.

## Licence

By contributing, you agree that your contribution is licensed under the [GPL-3.0](LICENSE).
