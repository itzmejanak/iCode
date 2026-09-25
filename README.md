# iCode

iCode is a native Android mobile IDE built from the complete Figma user flow. The app is now in production integration: workspace selection, recent workspaces, directory traversal, folder creation, text editing, and safe document saving use real Android storage. Unintegrated Git, runtime, terminal, recovery, and settings surfaces still use deterministic UI fixtures.

## Run the demo

Requirements:

- JDK 17
- Android SDK 36

Build:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

In the normal app flow, grant the requested developer storage access. iCode's own folder screen then browses phone storage without handing the flow to Android's Files app. You can select or create a workspace, browse its real directory tree, open a text file, edit it, and save it back to its original location. Android Back returns through the navigation history.

## Capture all 40 screens

Connect an authorized Android device, build the debug APK, then run:

```bash
tools/capture-figma-screens.sh
```

The command installs the current APK, launches every screen in deterministic static mode, and writes the screenshots to `artifacts/screens/`. It also creates two labeled review sheets in `artifacts/review/`. The screen list is read from `Route.kt`, so the capture workflow does not maintain a duplicate registry. The registry and capture extras are verification tooling; the app itself no longer exposes a screen catalog.

## Structure

```text
app/src/main/java/dev/icode/mobile/
├── designsystem/   # Semantic tokens (colors, type, metrics, tone, theme) + shared UI kit
├── domain/         # UI-facing immutable state and intent contract
├── data/workspace/ # Android storage, workspace history, tree projection, and text codec
├── demo/           # Deterministic fixtures retained for static visual verification
├── navigation/     # Figma screen registry and navigation actions
└── ui/             # Composition root and one file per screen
    └── screens/{entry,workspace,tools,settings,recovery}/
```

Each Figma frame lives in its own screen file with a small group host that dispatches it; the shared kit owns every reusable surface. Structural consistency is enforced by `tools/check-ui-consistency.sh` together with the shrinking ratchet in `tools/ui-debt-allowlist.txt` (currently zero debt).

`WorkbenchGateway` is the only state/intent/effect seam between Compose and implementations. Normal launches use `AndroidWorkbenchGateway`; static screenshot launches use `DemoWorkbenchGateway`, keeping visual verification deterministic without leaking fixtures into production storage behavior.

## Project documents

- [Development plan](./plan.md)
- [UI splitting & consistency plan](./docs/ui-split-plan.md)
- [Product requirements](./docs/prd.md)
- [Architecture](./docs/architecture.md)
- [Layout and interaction design](./docs/layout.md)
- [User stories](./docs/user-stories.md)
- [Implementation tracker](./Todo.md)
