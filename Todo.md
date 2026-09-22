# iCode Delivery Todo

Source of truth: [plan.md](./plan.md)  
Design source: [iCode — Full User Flow](https://www.figma.com/design/0CD7ua8fMaEfvznhEA9cGM/iCode-%25E2%2580%2594-Full-User-Flow--Editable-?node-id=0-1)

This file tracks implementation evidence. Product requirements remain in [docs/prd.md](./docs/prd.md), architecture in [docs/architecture.md](./docs/architecture.md), layout rules in [docs/layout.md](./docs/layout.md), and behavior in [docs/user-stories.md](./docs/user-stories.md).

## Phase 1 — UI implementation and repository readiness

### Repository foundation

- [x] Restore a minimal Android repository with Gradle wrapper support.
- [x] Use one application module and feature packages; avoid premature Gradle-module complexity.
- [x] Configure Kotlin, Jetpack Compose, Material 3, Java 17, API 30 minimum, and API 36 target.
- [x] Add repository hygiene files (`.gitignore`, `.editorconfig`) and bundled font licenses.
- [x] Keep all product documentation under lowercase `docs/` with consistent links.

### Figma audit and design system

- [x] Read all 40 top-level Figma frames through design context.
- [x] Establish the Figma palette, typography, spacing, shapes, buttons, cards, fields, status, list, and settings primitives.
- [x] Bundle Chakra Petch and IBM Plex Mono locally for deterministic offline rendering.
- [x] Centralize semantic design tokens and reusable UI-kit components.
- [x] Avoid screen-local copies of colors, fonts, buttons, fields, and settings rows.

### Complete demo flow

- [x] Implement entry, runtime installation, runtime error, and permission states.
- [x] Implement populated and empty launchers, open/create folder, clone form, and clone progress.
- [x] Implement explorer, editor, editor empty state, search, Quick Open, and editor find.
- [x] Implement source control, diff, branch selector, commit/push, pull progress, authentication, and conflict resolution.
- [x] Implement terminal, problems, output, and command palette.
- [x] Implement settings root, editor, language service list/detail, runtime, appearance, terminal, storage, about, and workspace settings.
- [x] Implement delete confirmation, external-change conflict, and document recovery.
- [x] Make every Figma screen directly reachable through the in-app screen catalog by tapping a page title.
- [x] Preserve normal user-flow navigation and Android back behavior.

### Later-integration seam

- [x] Define immutable UI-facing workspace, file, Git, diagnostic, and language-service models.
- [x] Define `WorkbenchGateway` as the single UI data/intent boundary.
- [x] Keep demo state in `DemoWorkbenchGateway`; Compose screens do not access files, Git, processes, or storage directly.
- [x] Keep backend implementation out of Phase 1.

### Verification

- [x] Debug APK assembles successfully.
- [x] Unit tests pass, including the 40-frame catalog completeness check.
- [x] Android lint passes without errors.
- [x] Install and launch the debug APK on a physical Android device.
- [x] Capture and inspect all 40 entry, workspace, Git, settings, and recovery screens.
- [x] Record visual differences and resolve implementation-side overlap and clipping defects.

Verification evidence:

- Individual device captures: [`artifacts/screens/`](./artifacts/screens/)
- Labeled review sheets: [`artifacts/review/`](./artifacts/review/)
- Automated capture command: `tools/capture-figma-screens.sh`
- Result: no unresolved content overlap or control clipping was found in the final 40-screen review. Android system bars remain device-native, and long settings content remains intentionally scrollable.

## Phase 2 — Backend planning

Do not begin until Phase 1 is visually validated.

- [ ] Convert finalized UI states and intents into backend use-case contracts.
- [ ] Plan direct workspace storage, runtime/process, Git, search, editor, recovery, settings, and persistence adapters.
- [ ] Define validation, error mapping, lifecycle, cancellation, and test boundaries.
- [ ] Review the plan before implementation.

## Phase 3 — Integration

Do not begin until the Phase 2 plan is approved.

- [ ] Replace `DemoWorkbenchGateway` with production adapters behind the same UI boundary.
- [ ] Connect every loading, success, empty, error, conflict, and recovery state.
- [ ] Run end-to-end user-flow, lifecycle, accessibility, and regression verification.
