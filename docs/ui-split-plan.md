# UI Splitting & Consistency Plan (Phase 1.5)

Master refactoring plan for the completed Phase 1 UI port (40 Figma frames), executed before Phase 2 backend planning.

> **Note (2026-09-26).** The tables below record the split as it was done, with the names of
> the time. `demo/` (`DemoWorkbenchGateway`, `DemoContent`) no longer exists: screens read the
> real snapshot, and screenshots are drawn from debug-only capture fixtures
> (`app/src/debug/.../capture`; [code-separation-plan.md](./code-separation-plan.md) K1). Rules
> 2, 3, 8 and 10 below are updated to match.

- Source of truth: the code under `app/src/main/java/dev/icode/mobile`.
- Related: [plan.md](../plan.md) · [Todo.md](../Todo.md) · [layout.md](./layout.md) · [architecture.md](./architecture.md) · [backend splitting rules](./backend-split-rules.md) for future integration work.
- Enforcement: `tools/check-ui-consistency.sh` + `tools/ui-debt-allowlist.txt` (ratchet — counts may only shrink).

## 1. Pre-refactor architecture assessment

| Area | Before the split | Verdict |
|---|---|---|
| Screen files | `EntryScreens.kt` 442, `WorkspaceScreens.kt` 366, `ToolScreens.kt` 563, `SettingsScreens.kt` 499, `RecoveryScreens.kt` 166 | One file per feature group, up to 13 screens + 7 helpers per file |
| Design system | `IcodeComponents.kt` 393 (22 public symbols) + `IcodeTheme.kt` 82 | Right idea, wrong granularity; dead API and missing shared concepts |
| App host | `IcodeApp.kt` 164 owns actions, history, catalog and dispatch | Root mixes four responsibilities; screens depend on it |
| Domain seam | `WorkbenchContract.kt` + `DemoWorkbenchGateway.kt` | Correct, unchanged by this plan |
| Navigation | `Route.kt` (40 frames) + 40-frame unit test + capture script parsing it | Single source of truth; must not change |
| Insets shell | `IcodeScreen` applied once at the app root | Keep shell-owned insets; document as an invariant |
| State | 3 × `rememberSaveable` (host) vs 27 × `remember` (screens) | Rotation loses form state; violates `layout.md` §28 and architecture §18 |
| Enforcement | 1 unit test, no ktlint/detekt/CI guard | Nothing prevents duplication from returning |

## 2. Gaps, duplication and inconsistency ledger

| # | Finding | Evidence |
|---|---|---|
| D1 | `DetailValue` vs `DetailCompare` vs inline indent row — three label/value rows | `SettingsScreens.kt:261`, `RecoveryScreens.kt:100`, `SettingsScreens.kt:479` |
| D2 | Card-wrapped mono input implemented 6× | `ToolScreens.kt:300,203,247,419`, `EntryScreens.kt:344` |
| D3 | Selected picker row 4×; two keyed to demo literals (`index == 0`, `branch == "feature/auth"`) | `ToolScreens.kt:220,257,429`, `IcodeApp.kt:147` |
| D4 | Raw `Color(0x…)` outside tokens (diff + conflict surfaces) | `ToolScreens.kt:181-183,397,401` |
| D5 | Dead API: `IconText`, `VerticalGap`, `HorizontalGap`, `NavigationActions.reset` | usage counts = 0 |
| D6 | Presentation keyed to demo strings (`helpers.ts`, `README.md`, `package.json`) | `WorkspaceScreens.kt:152,177`, `RecoveryScreens.kt:147` |
| D7 | 27 `remember {}` in screens, 3 `rememberSaveable` in the app | `SettingsScreens.kt` has 14 |
| D8 | Static counts contradict snapshot data (Problems 3/5/2, editor badge 3) | `ToolScreens.kt:348-351`, `WorkspaceScreens.kt:245` |
| D9 | `KeyChip` used as toggle via external `.clickable`; `"Infos"` label bug | `ToolScreens.kt:356-358,389`, `SettingsScreens.kt:174` |
| D10 | Bottom-panel tab concept implemented twice | `ToolScreens.kt:339` vs `447` |
| D11 | Notice/banner concept 4× with ad-hoc color maps | `RecoveryScreens.kt:133,79`, `ToolScreens.kt:471`, `SettingsScreens.kt:271` |
| D12 | Empty-state concept 2× with different metrics | `EntryScreens.kt:251`, `WorkspaceScreens.kt:326` |
| D13 | Type-scale gaps force 8/9/10/22/23/28/32 sp overrides in screens | `EntryScreens.kt:96`, `SettingsScreens.kt:485` |
| D14 | No metric tokens; 39–74 dp literals per screen file, off-scale paddings | `layout.md` §4.5 scale never implemented |
| D15 | Inline shapes diverge (4/50/2/3/top-12) | screens + kit |
| D16 | Fully-qualified types instead of imports; positional `MonoLine` calls | `WorkspaceScreens.kt:345`, `ToolScreens.kt:196` |
| D17 | Demo literals duplicated across screens (`feature/auth` ×6, `token-utils.ts` ×5, …) | grep sweep |
| D18 | Data seam inconsistent: snapshot vs re-typed local copies | `SettingsScreens.kt:377` vs `DemoWorkbenchGateway.kt:52` |
| D19 | Screens silently depend on the single `IcodeScreen` call site | `IcodeApp.kt:90` |

## 3. UI-kit audit

Charter: `docs/layout.md` §26 ("The shared native design system owns …"; "Screens compose these components and do not restyle raw controls independently"). Kit components accept primitives and slots only — never domain models.

- **Keep (split into role files, semantics unchanged):** `IcodeScreen`, `IcodeTopBar`, `PageHeading`, `SectionLabel`, `IcodeCard`, `PrimaryButton`, `SecondaryButton`, `DangerButton`, `LabeledField`, `SettingRow`, `IcodeToggle`, `StatusDot`, `StatusLabel`, `ProgressTrack`, `MonoLine`, `TwoColumnActions`.
- **Merge:** `DetailValue` + `DetailCompare` + inline → `DetailRow`; `Segment` + `SearchChip` + `KeyChip`-as-toggle → `SegmentedControl` + `ChoiceChip`; `DialogBackdrop` → `DialogScaffold`; inline sheet → `IcodeBottomSheet`.
- **Add:** `IcodeListRow`, `IcodeTextField`, `LabeledTextField`, `CodeSurface`, `NoticeCard`, `EmptyState`, `IconBadge`, `DiffRow`, `DiffBand`, `PanelTabStrip`, `BottomPanelBar`, `EditorTab`, `SectionHeader`, `Stepper`, `ActivityRail`, `DiagnosticRow`.
- **Delete:** `IconText`, `VerticalGap`, `HorizontalGap`, `NavigationActions.reset`.
- **Tokens added:** diff/conflict surfaces, `OnAccent`, `Scrim`; `Display`, `Logomark`, `LogomarkSmall`, `Heading`, `Micro`, `MonoMicro`; `IcodeSpacing`, `IcodeShapes`, `IcodeSizes`; `IcodeTone`.
- **Not changed (Figma fidelity):** button/Secondary casing stays per-frame; `Divider` keeps its name; component-internal metrics (e.g. `KeyChip` 8 sp compact) stay inside the kit where they define the component's identity.

## 4. Target structure

```text
app/src/main/java/dev/icode/mobile/
├── MainActivity.kt
├── designsystem/                     # tokens + kit, no feature dependencies
│   ├── IcodeColors.kt  IcodeType.kt  IcodeMetrics.kt  IcodeTone.kt  IcodeTheme.kt
│   └── IcodeScaffold.kt  IcodeCards.kt  IcodeButtons.kt  IcodeControls.kt
│       IcodeFields.kt  IcodeRows.kt  IcodeStatus.kt  IcodeFeedback.kt
│       IcodePanels.kt  IcodeDiff.kt  IcodeRail.kt  IcodeLayout.kt
├── navigation/{Route.kt, NavigationActions.kt}
├── domain/{WorkbenchContract.kt, …}
├── demo/{DemoWorkbenchGateway.kt, DemoContent.kt}
└── ui/
    ├── IcodeApp.kt  NavigationState.kt  WorkbenchLocals.kt
    └── screens/{entry,workspace,tools,settings,recovery}/
        └── <Group>Host.kt + one file per screen
```

Per-file rules: one public `XxxScreen(actions)` per file; screen ≤ 250 lines target / 400 hard; kit file ≤ 300; host file ≤ 60 (pure `when` dispatch, keeps `else -> Unit` until the group finishes migrating).

## 5. Screen-by-screen decomposition (40 frames → 39 screen files)

| Frame | Screen | Target file | Local components | Cycle | Status |
|---|---|---|---|---|---|
| 3:11 | Splash | `entry/SplashScreen.kt` | logomark card | C5 | done |
| 3:27 | RuntimeInstalling | `entry/RuntimeInstallingScreen.kt` | — | C5 | done |
| 3:55 | RuntimeError | `entry/RuntimeErrorScreen.kt` | — | C5 | done |
| 3:90 | PermissionExplanation | `entry/PermissionScreen.kt` | — | C5 | done |
| 3:124 | LauncherHome | `entry/LauncherScreen.kt` | `LauncherActionTile`, `WorkspaceRow` | C5 | done |
| 3:202 | LauncherEmpty | `entry/LauncherScreen.kt` (`empty = true`) | (same file) | C5 | done |
| 3:239 | CloneRepository | `entry/CloneRepositoryScreen.kt` | — | C5 | done |
| 3:278 | CloneProgress | `entry/CloneProgressScreen.kt` | — | C5 | done |
| 2003:77 | CreateFolder | `entry/CreateFolderScreen.kt` | — | C5 | done |
| 2003:117 | OpenFolderBrowser | `entry/OpenFolderScreen.kt` | `demoFolders` | C5 | done |
| 3:456 | WorkspaceExplorer | `workspace/WorkspaceExplorerScreen.kt` | `WorkspaceHeader`, `ExplorerRow` | C6 | done |
| 3:519 | WorkspaceEditor | `workspace/WorkspaceEditorScreen.kt` | `demoCodeLines`, `highlightLine` | C6 | done |
| 3:586 | WorkspaceSearch | `workspace/WorkspaceSearchScreen.kt` | `demoSearchResults` | C6 | done |
| 2003:19 | EditorEmpty | `workspace/EditorEmptyScreen.kt` | `EmptyActionTile`, `RecentFile` | C6 | done |
| 3:641 | SourceControl | `tools/SourceControlScreen.kt` | `GitChangeRow` | C7 | done |
| 3:706 | DiffView | `tools/DiffViewScreen.kt` | `demoDiffLines` | C7 | done |
| 2003:246 | CommitPush | `tools/CommitPushScreen.kt` | — | C7 | done |
| 2003:290 | PullFetchProgress | `tools/PullProgressScreen.kt` | `demoPullOutput` | C7 | done |
| 2003:321 | GitAuth | `tools/GitAuthScreen.kt` | — | C7 | done |
| 3:796 | BranchSelector | `tools/BranchSelectorScreen.kt` | — | C7 | done |
| 3:1509 | ConflictResolution | `tools/ConflictResolutionScreen.kt` | `mergeOptions` | C7 | done |
| 3:760 | QuickOpen | `tools/QuickOpenScreen.kt` | `QuickOpenEntry`, `quickOpenFiles` | C8 | done |
| 3:835 | EditorFind | `tools/EditorFindScreen.kt` | `demoFindResults` | C8 | done |
| 3:1070 | TerminalExpanded | `tools/TerminalScreen.kt` | `demoTerminalLines`, `demoExtraKeys` | C8 | done |
| 3:1127 | ProblemsPanel | `tools/ProblemsScreen.kt` | `severityLabel`, `severityTone` | C8 | done |
| 2003:166 | CommandPalette | `tools/CommandPaletteScreen.kt` | `PaletteCommand`, `demoCommands` | C8 | done |
| 2003:208 | OutputPanel | `tools/OutputPanelScreen.kt` | `demoOutputLogs` | C8 | done |
| 3:1196 | SettingsRoot | `settings/SettingsRootScreen.kt` | `SettingsCatalog.kt` | C9 | done |
| 3:1277 | SettingsEditor | `settings/EditorSettingsScreen.kt` | `EditorSettingsState.kt` | C9 | done |
| 3:1344 | SettingsLanguages | `settings/LanguageServicesScreen.kt` | `LanguageServiceRow` | C9 | done |
| 3:1393 | SettingsLanguageDetail | `settings/LanguageDetailScreen.kt` | `capabilityChips` | C9 | done |
| 3:1455 | SettingsRuntime | `settings/RuntimeSettingsScreen.kt` | `runtimeDetails` | C9 | done |
| 2003:438 | Appearance | `settings/AppearanceScreen.kt` | `ThemeSwatch`, `themeOptions` | C9 | done |
| 2003:500 | TerminalSettings | `settings/TerminalSettingsScreen.kt` | `demoExtraKeys`, `cursorStyles` | C9 | done |
| 2003:575 | StorageSettings | `settings/StorageSettingsScreen.kt` | `usageLegend` | C9 | done |
| 2003:656 | About | `settings/AboutScreen.kt` | `buildSpecs`, `diagnosticActions`, `resources` | C9 | done |
| 2003:886 | WorkspaceSettings | `settings/WorkspaceSettingsScreen.kt` | `tabTypes` | C9 | done |
| 2003:731 | DeleteConfirmation | `recovery/DeleteConfirmationScreen.kt` | — | C4 | done |
| 2003:770 | ExternalFileChange | `recovery/ExternalChangeScreen.kt` | — | C4 | done |
| 2003:813 | DocumentRecovery | `recovery/DocumentRecoveryScreen.kt` | (docs from `DemoContent`) | C4 | done |

## 6. Ownership and dependency rules

1. **Token → component → screen → host** is the only allowed dependency direction.
2. `designsystem` depends on Compose and its own tokens only — no domain, capture, navigation or screen imports.
3. Screens may depend on: `designsystem`, `domain` contracts, `navigation.Route`/`NavigationActions` and `ui.WorkbenchLocals`. Never on a sibling screen package, never on `ui.IcodeApp`, never on fixtures.
4. Kit components accept primitives and slots; screens map domain models onto kit parameters (`Severity` → `IcodeTone`, `GitChange` → row slots).
5. Feature-local private composables stay in the screen file (e.g. `WorkspaceHeader`, `ExplorerRow`, `GitChangeRow`, `ThemeSwatch`). A component is promoted to the kit only when a second consumer exists — or when `layout.md` §26 assigns it to the design system (rail, editor tabs, diff rows, diagnostic rows, dialogs, fields, choice controls, empty/error states).
6. Screens never restyle raw controls and never write literal colors, `sp` sizes or layout `dp` values; all metrics come from `IcodeSpacing` / `IcodeShapes` / `IcodeSizes` / `IcodeType`.
7. `IcodeScreen` is owned by the shell; screens must not add system-bar padding or width clamps.
8. Screens show only snapshot data; sample content for screenshots lives in the debug-only `CaptureFixtures`, never in a screen.
9. Interactive screen state that survives rotation uses `rememberSaveable` (or a `Saver`-backed state holder such as `EditorSettingsState`).
10. The `Route` registry (39 routes for the 40 frames; frame 3:55 is a state of 3:27), its unit test and `tools/capture-figma-screens.sh` stay consistent with each other.
11. File budgets: screen ≤ 250 lines target (400 hard, guard threshold 300), kit file ≤ 300 lines, host file ≤ 60 lines.

## 7. Zero-duplication and zero-inconsistency enforcement

- `tools/check-ui-consistency.sh` verifies: no `Color(0x…)` outside `designsystem/IcodeColors.kt`; no fully-qualified `androidx.*` references under `ui/`; no hard-coded `dp`/`sp` values under `ui/screens`; no screen file above 300 lines.
- `tools/ui-debt-allowlist.txt` is the ratchet: it records the maximum allowed count per `(rule, file)`. Cycles may only shrink it, never grow it.
- Kit additions require the two-consumer check (or a §26 charter reference), recorded in the commit message.
- Definition of done for a screen file: single public composable, private local helpers, tokenized styling, `rememberSaveable` for editable state, no sibling imports, ≤ 250 lines.

## 8. Execution cycles

| Cycle | Goal | Status |
|---|---|---|
| C0 | Baseline verification, guardrail script + ratchet allowlist, this plan | done |
| C1 | Token split: `IcodeColors`, `IcodeType`, `IcodeMetrics`, `IcodeTone`, `IcodeTheme` | done |
| C2 | Kit split into 12 role files, new shared components, dead-code removal | done |
| C3 | Composition root decomposition: `navigation/NavigationActions`, `ui/NavigationState`; temporary verification catalog isolated | done |
| C4 | Recovery split (3 frames) — first kit adoption | done |
| C5 | Entry split (10 frames, 9 files) | done |
| C6 | Workspace split (4 frames + explorer header/tree) | done |
| C7 | Tools split, source-control family (7 frames) | done |
| C8 | Tools split, editor/panel family (6 frames) | done |
| C9 | Settings split (10 frames + catalog + state holder) | done |
| C10 | Fixture centralization + state pass (applied inside C4–C9: `DemoContent`, `rememberSaveable`, state-driven selection) | done |
| C11 | Final verification: guard clean, tests, lint, APK, device capture, documentation | done |

Every cycle was required to keep the tree compiling and the 40-frame unit test green before moving on.

## 9. Validation matrix

| Gate | Command | Result |
|---|---|---|
| Compile | `./gradlew :app:compileDebugKotlin --offline` | passing after every cycle |
| Unit tests | `./gradlew :app:testDebugUnitTest --offline` | 40-frame catalog test green after every cycle |
| Lint | `./gradlew :app:lintDebug --offline` | see progress log |
| APK | `./gradlew :app:assembleDebug --offline` | debug APK rebuilt after the split |
| Anti-duplication | `tools/check-ui-consistency.sh` | **zero debt** (allowlist empty) |
| Visual regression (device) | `tools/capture-figma-screens.sh` + `artifacts/screens/` and `artifacts/review/` | 40/40 recaptured after the split; no unresolved overlap or clipping |
| Structure audit | file inventory + line budgets | 39 screen files, 5 hosts and 2 screen-support files; largest screen 152 lines, largest kit file 184 lines |

## 10. Decisions taken (with defaults applied)

1. **Page gutter** — kept at the implemented 16 dp (`IcodeSpacing.Gutter`) instead of the 20 dp named in `layout.md` §4.5, to preserve the validated Figma output; deviation recorded in `IcodeMetrics`.
2. **Button casing** — unchanged: `PrimaryButton` uppercases, secondary/danger labels keep Figma casing. Changing it would alter validated frames, so the guard treats it as intended behavior.
3. **`NavigationActions`** — moved to `navigation`, removing the screen → composition-root dependency.
4. **Demo strings** — centralized: `DemoContent` owns the duplicated fixtures (`activeBranch`, `branches`, `authMethods`, `appTsx`, `packageJson`, `selectedExplorerPath`, recovery documents); frame-unique content stays local to its screen.
5. **Dead API** — deleted (`IconText`, `VerticalGap`, `HorizontalGap`, `NavigationActions.reset`).
6. **Insets** — remain shell-owned in `IcodeScreen`; screens must not add system-bar padding (documented invariant).
7. **Type scale** — ad-hoc 8/9/10/22/23/28/32 sp overrides replaced with `Logomark`, `LogomarkSmall`, `Mark`, `Heading`, `Micro`, `MonoMicro`. Sub-pixel differences were accepted as intentional consistency fixes.
8. **Tokens stay in the `designsystem` package (flat)** rather than a `tokens/` subpackage: the split is by file, which keeps every existing import stable while still separating concerns.

## 11. Progress log

| Step | Result |
|---|---|
| C0–C1 | Tokens split into `IcodeColors`, `IcodeType`, `IcodeMetrics`, `IcodeTone`, `IcodeTheme`; `:app:compileDebugKotlin` + `:app:testDebugUnitTest` green |
| C2 | `IcodeComponents.kt` (393 lines) replaced by 12 kit files; new shared components added; dead API deleted; compile + tests green |
| C3 | `IcodeApp.kt` reduced from 164 to 67 lines; `NavigationActions` moved to `navigation`; temporary verification catalog extracted; tests green |
| C4–C9 | All 40 frames migrated: 39 screen files + 5 group hosts; the five monoliths (`EntryScreens`, `WorkspaceScreens`, `ToolScreens`, `SettingsScreens`, `RecoveryScreens`) were deleted; compile + tests green after each group |
| Debt ratchet | 18 → 12 → 8 → 4 → **0 entries**; `tools/check-ui-consistency.sh` passes with an empty allowlist |
| State hygiene | 0 plain `remember {}` left in `ui/screens`; every editable screen state uses `rememberSaveable` or a `Saver`-backed holder (`EditorSettingsState`) |
| Structure audit | 46 files under `ui/screens` (+5 hosts); largest screen 152 lines; largest kit file 184 lines; total Kotlin ≈ 5.7k lines across role-scoped files |
| Final cleanup | Temporary in-app screen catalog removed after verification; direct ADB capture remains the single 40-frame review path |






## 12. Final target architecture

```text
Compose UI  →  screens (one file per frame, private helpers, saveable state)
            →  designsystem kit (primitives + slots, tokenized, no feature deps)
            →  tokens (colors / type / metrics / tone / theme)

screens     →  domain contracts (WorkbenchSnapshot, WorkbenchIntent, gateway interfaces)
            →  navigation (Route registry, NavigationActions)
            →  demo fixtures (DemoContent — replaced by gateway data in Phase 3)
```

Invariants achieved: no monolith screen file, no duplicated component, no literal color outside tokens, no hard-coded dp/sp in screens, no screen → composition-root import, no plain `remember` for editable state, and a passing ratchet guard with zero recorded debt. Phase 2 can convert each screen's state holder and `DemoContent` reference into gateway-backed state without restructuring the UI.

### 12.1 Cycle file inventory

| Cycle | Created | Deleted |
|---|---|---|
| C0 | `docs/ui-split-plan.md`, `tools/check-ui-consistency.sh`, `tools/ui-debt-allowlist.txt` | — |
| C1 | `designsystem/IcodeColors.kt`, `IcodeType.kt`, `IcodeMetrics.kt`, `IcodeTone.kt` | colors/type/fonts removed from `IcodeTheme.kt` |
| C2 | `designsystem/{IcodeScaffold,IcodeCards,IcodeButtons,IcodeControls,IcodeFields,IcodeRows,IcodeStatus,IcodeFeedback,IcodePanels,IcodeDiff,IcodeRail,IcodeLayout}.kt` | `designsystem/IcodeComponents.kt` |
| C3 | `navigation/NavigationActions.kt`, `ui/NavigationState.kt`, temporary `ui/catalog/ScreenCatalog.kt` | `NavigationActions` + catalog ownership removed from `IcodeApp.kt` |
| C4 | `ui/screens/recovery/*` (4 files), `demo/DemoContent.kt` | `ui/screens/RecoveryScreens.kt` |
| C5 | `ui/screens/entry/*` (10 files) | `ui/screens/EntryScreens.kt` |
| C6 | `ui/screens/workspace/*` (5 files) | `ui/screens/WorkspaceScreens.kt` |
| C7–C8 | `ui/screens/tools/*` (14 files) | `ui/screens/ToolScreens.kt` |
| C9 | `ui/screens/settings/*` (13 files) | `ui/screens/SettingsScreens.kt` |
| C10 | fixtures centralized in `DemoContent.kt`; state converted in place | — |
| C11 | documentation updates (`README.md`, `Todo.md`, this plan) | temporary `ui/catalog/ScreenCatalog.kt` after visual verification |
