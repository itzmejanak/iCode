# iCode

iCode is a native Android mobile IDE concept implemented from the complete Figma user flow. The current repository is intentionally in **Phase 1: UI demo**—all visible data and operations are deterministic demo state, not a backend simulation.

## Run the demo

Requirements:

- JDK 17
- Android SDK 36

Build:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

In the app, follow the normal clickable flow or tap any page title to open the 40-screen Figma catalog. Android Back returns through the demo history.

## Capture all 40 screens

Connect an authorized Android device, build the debug APK, then run:

```bash
tools/capture-figma-screens.sh
```

The command installs the current APK, launches every screen in deterministic static mode, and writes the screenshots to `artifacts/screens/`. It also creates two labeled review sheets in `artifacts/review/`. The screen list is read from `DemoScreen.kt`, so the capture workflow does not maintain a duplicate registry.

## Structure

```text
app/src/main/java/dev/icode/mobile/
├── designsystem/   # Tokens and reusable Compose UI kit
├── domain/         # UI-facing immutable state and intent contract
├── demo/           # Replaceable Phase 1 data source
├── navigation/     # Figma screen registry
└── ui/             # App host and screen families
```

`WorkbenchGateway` is the only state/intent seam between the UI and future implementations. Real storage, Git, runtime, editor, and persistence adapters will be planned in Phase 2 and integrated in Phase 3 without moving those concerns into Compose screens.

## Project documents

- [Development plan](./plan.md)
- [Product requirements](./docs/prd.md)
- [Architecture](./docs/architecture.md)
- [Layout and interaction design](./docs/layout.md)
- [User stories](./docs/user-stories.md)
- [Implementation tracker](./Todo.md)
