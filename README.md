# iCode

iCode is a native Android mobile IDE built from the complete Figma user flow. It works on real
phone storage and a real Linux runtime: workspaces, the file tree, the editor with autosave and
recovery drafts, Git (clone, status, diff, commit, push, pull, branches, credentials), the
terminal with background sessions and alerts, language services, and settings.

## Build and run

Requirements:

- JDK 17
- Android SDK 36

Build:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

On first launch iCode installs its runtime, then asks for the permissions it needs, one at a
time, with Android's own dialogs. iCode's folder screen browses phone storage itself; open or
create a workspace, or clone a repository, and work on its real files.

## Capture all 39 screens

Connect an authorized Android device, build the debug APK, then run:

```bash
tools/capture-figma-screens.sh
```

The command installs the current debug APK and opens every route with the extras
`capture_route=<Route>` and `capture=true`, which draw the screen from fixed capture fixtures
instead of the device, so every run gives the same pixels. It writes the screenshots to
`artifacts/screens/` and two labeled review sheets to `artifacts/review/`. The route list is read
from `Route.kt`. The fixtures live in `app/src/debug` only: a release build ignores the extras and
contains no fixture.

## Structure

```text
app/src/main/java/dev/icode/mobile/
├── designsystem/   # Semantic tokens (colors, type, metrics, tone, theme) + shared UI kit
├── domain/         # UI-facing immutable state and the intent/effect contract
├── data/           # Android implementations: workspace, git, runtime, settings, language, notices, system
├── navigation/     # Routes and navigation actions
└── ui/             # Composition root, editor, terminal, and one file per screen
    └── screens/{entry,workspace,tools,settings,recovery}/
app/src/debug/java/dev/icode/mobile/capture/   # Capture fixtures (debug builds only)
```

Each Figma frame lives in its own screen file with a small group host that dispatches it; the
shared kit owns every reusable surface. Structural consistency is enforced by
`tools/check-ui-consistency.sh` with the shrinking ratchet in `tools/ui-debt-allowlist.txt`.

`WorkbenchGateway` is the only state/intent/effect seam between Compose and implementations.
The app uses `AndroidWorkbenchGateway`; a capture launch uses `CaptureGateway`, which draws the
fixtures and ignores every intent, so no fixture reaches real storage.

## Project documents

- [Development plan](./plan.md)
- [UI splitting & consistency plan](./docs/ui-split-plan.md)
- [Product requirements](./docs/prd.md)
- [Architecture](./docs/architecture.md)
- [Layout and interaction design](./docs/layout.md)
- [User stories](./docs/user-stories.md)
- [Implementation tracker](./Todo.md)
