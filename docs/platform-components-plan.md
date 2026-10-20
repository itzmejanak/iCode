# Platform Components — UI/UX Consistency & Deduplication Plan

Status: **plan (2026-10-14)** — evidence-checked against the workspace, not guessed.
Owner ask: "plan for all the ui material components like drop-down you see there is color palette and theme inconstant so that we need to make plan for the whole platform components ships and by introducing better ui/Ux and best and clean, and without duplication we need to make things clean and perfect that exactly need to be without being out of boundary"
Screenshot evidence: `1000367358.jpg` (11:59 AM, 410k tokens) — `Orchestrator ∨` / `claude-fable-5.1 ∨` chips with `IcodeChoiceMenu`, vs settings' card dropdowns.

## 1. Evidence (what was read, not assumed)

| Source | What it proves |
|---|---|
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeDropdown.kt` (165 lines) — `IcodeDropdown` (label+`IcodeCard`+Menu), `IcodeChoiceMenu` (`DropdownMenu` + `DropdownMenuItem`), `IcodeChoiceChip` (compact row+arrow) | One product has **two owners for one purpose**: card dropdown vs chip dropdown share one menu. Selected state: chip = `Caption/TextSecondary`, dropdown selected = `Body/Accent + Check` (drift). |
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeColors.kt` (152 lines) — `IcodePalette {Dark,Light,Amoled}` + `withAccent()` + `LocalIcodePalette` + `IcodeColors` object | Three palettes exist. `withAccent()` swaps `accent/onAccent`. `IcodeColors` is the only token read site — but menus still fall back to Material defaults (see next). |
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeTheme.kt` (56 lines) — `IcodeTheme(palette, uiTextScale, compactLayout)` → `CompositionLocalProvider` + `darkColorScheme`/`lightColorScheme` | Bridge maps only `primary/onPrimary, secondary, tertiary, background/onBackground, surface/onSurface, surfaceVariant, outline, error`. `DropdownMenuItem` text color defaults to Material, not `IcodeType`+`IcodeColors`. Light palette reads washed. |
| `IcodeControls.kt`, `IcodeFields.kt`, `IcodeButtons.kt`, `IcodeMenus.kt`, `IcodeCards.kt`, `IcodeFeedback.kt`, `IcodeRows.kt`, `IcodeType.kt`, `IcodeMetrics.kt`, `IcodeLayout.kt`, `IcodePanels.kt`, `IcodeScaffold.kt` (+ 24 files total under `designsystem/`) | Kit is flat, feature-free (no `domain`/`navigation` imports per `docs/ui-split-plan.md` §6). Each touches `IcodeColors/IcodeType/IcodeSpacing/IcodeShapes` — no raw `Color(0x)` outside `IcodeColors.kt`. |
| `tools/check-ui-consistency.sh` (146 lines) — rules `raw-color, fq-androidx, dp-literal, sp-literal, ui-lines, capture-fork, orphan-route` + `tools/ui-debt-allowlist.txt` (6 lines, ratchet) | Guard already enforces zero duplication: `ui/screens` must not contain `Color(0x`, `androidx.` FQ, `dp`/`sp` literals, `>300` lines. Current status verified 2026-10-14: **both guards PASS** (6 allowlisted `capture-fork`+`orphan-route` only). Any new kit must keep this green. |
| Grep `IcodeDropdown` (7 hits): `GitAuthSections.kt:76,85`, `TerminalSettingsScreen.kt:85,99`, `EditorSettingsScreen.kt:65,81`, `AssistantProviderScreen.kt:101` | Dropdown consumers are exactly those 4 settings screens — migration is bounded. Chip consumers are composer only. |
| `docs/ui-split-plan.md` (239 lines, executed C0-C11, allowlist 18→0) + `docs/architecture.md` §8-10 | Split already done: `designsystem` depends only on Compose; `ui/screens` → `designsystem`+`domain`+`navigation`. No screen → `data`. This plan **extends** that split — never crosses boundaries. |

## 2. What we have — component inventory (today)

Tokens: `IcodeColors` (palette + `terminalAnsi`), `IcodeType` (`ChakraPetch`/`PlexMono` + 11 styles), `IcodeMetrics` (`IcodeSpacing.Space1..6,Gutter,ItemGap,SectionGap,SectionBreak,IconButtonGutter,RowGutter,CardPadding,IndentStep`; `IcodeShapes`; `IcodeSizes`), `IcodeTone` (6 tints), `IcodeTheme` (bridge).

Kit (each file ≤ ~300 lines, all under `designsystem/`):
- Containers: `IcodeCard`, `IcodeSection`, `CodeSurface`, `IcodeScaffold` (`IcodePageColumn`), `IcodePanels` (`IcodeFootBar`), `IcodeSheetOverlay`, `IcodeLayout`, `IcodeBrandMark`; `EdgeLine` and `Divider`
- Inputs: `IcodeTextField/LabeledTextField/LabeledField`, `IcodeSelect` (card or chip), `IcodeToggle/IcodeCheckbox/SegmentedControl/IcodeSlider/Stepper`
- Actions: `Primary/Secondary/DangerButton` (Primary with a leading icon), `IcodeTextButton`, `IcodeNavigationLink`, `KeyChip` (text or icon), `JumpToLatestButton`
- Lists: `IcodeListRow (card+compact)`, `SettingRow`, `DetailRow`, `IconBadge`, `IcodeChoiceRow`
- Feedback: `NoticeCard (tone→border/title)`, `EmptyState`, `IcodeDialogCard/IcodeConfirmDialog/IcodeSaveDiscardDialog/IcodeBottomSheet`
- Navigation: `IcodeRail`, `IcodeTopBar`, `IcodeTabStrip`, `IcodeMenus (IcodeOverflowMenu/IcodeActionMenu, one IcodeDropdownMenu surface with IcodeSelect's)`, `IcodeStatus`, `IcodeProgressDialog`, `IcodeDiff`, `IcodeZoom`

## 3. Findings — ID, category, severity, confidence, evidence

| ID | Category | Sev | Conf | File:line | Evidence→Root cause →Impact |
|---|---|---|---|---|---|
| PC-01 | Duplication | Medium | High | `IcodeDropdown.kt:40-84` vs `:142-164` | Two entry points for "choose one" — card vs chip — share `IcodeChoiceMenu` but differ in label style, hit area, semantics. Fixing one leaves the other drifting. |
| PC-02 | Theme | Medium | High | `IcodeTheme.kt:28-42` + `IcodeDropdown.kt:100-134` / `IcodeMenus.kt:67-99` | `DropdownMenu` `containerColor=SurfaceElevated` is set in menus but menu items use Material `Text` default; light palette's `SurfaceElevated (#E7EBE5)` vs `Dark (#1C231B)` makes selected `Accent` contrast differ. No palette snapshot. |
| PC-03 | Tokens | Low | High | `IcodeMenus.kt:71-73`, `IcodeDropdown.kt:129` | `IcodeMenus` borders use `Outline`, shape `Small` — correctly tokenized. Verifies kit is clean; risk is regression if a screen re-introduces `dp`/`sp`/`Color(0x)`. |
| PC-04 | Consistency | Low | Medium | `IcodeButtons.kt:98` `buttonHeight(compact)` vs `IcodeDropdown.kt:54` border logic | Small inconsistencies (selected mark: check only on dropdown but not on overflow grouping) are intentional today but undocumented. |
| PC-05 | Boundary | — | High | `docs/ui-split-plan.md §6` rules 1-6 | Any new "MaterialDropdown" that imports `domain` or screen code would violate `designsystem` isolation. Plan forbids it. |

No fabricated metrics: counts from `tools/check-ui-consistency.sh` run; palette values copied from `IcodeColors.kt`; chip vs dropdown diff from direct read.

## 4. What we exactly need (and what is out of boundary)

In:
- One `Select` primitive with `variant: Card | Chip` — same label style (`IcodeType.Body` selected→`Accent`+Check, unselected→`TextPrimary`), same tokens, same `Shape.Small`, same `Outline` border, same `DropdownMenu` `tonalElevation=0`.
- Theme proof: `IcodeTheme` snapshots Dark/Light/Amoled at `withAccent(+/-)`, verified at 100% and 150% `uiTextScale` (see `Todo.md` P12).
- Zero `Color(0x`/`dp`/`sp` outside tokens — guard must stay PASS.

Out (not this plan):
- Adding a DI framework, new Gradle module, or generic `Util` — violates `docs/backend-split-rules.md §4`.
- Changing product routes, adding extension marketplace, browser preview — out of UI kit.
- Touching `AndroidICodeGateway`/`data/` — this is a UI-kit plan only.

## 5. Target architecture (clean, non-duplicative)

```
tokens (IcodeColors / IcodeType / IcodeSpacing / IcodeShapes / IcodeSizes / IcodeTone)
  ↓
kit (one file per role, ≤300 lines, no domain/nav/capture deps)
  IcodeSelect.kt ← IcodeDropdown + IcodeChoiceChip unify (single Select + shared Menu)
  IcodeTheme.kt  ← palette→Material mapping + snapshots
  ...other kit unchanged, still primitives+slots only...
  ↓
screens (one file per screen, ≤250 target, `rememberSaveable`) → kit + domain contracts only
```

Dependency direction strictly `tokens → kit → screens`; `composition root → impl`. `designsystem` imports only Compose + tokens.

## 6. Cycles — small, independently verifiable

| Cycle | Goal | Files to create/modify/delete | Validation |
|---|---|---|---|
| **C1 — Select unify** | Replace 2 entry points with one `IcodeSelect` (`variant Card/Chip`). Migrate 4 `IcodeDropdown` + composer `IcodeChoiceChip` calls. | Create `IcodeSelect.kt`; edit `GitAuthSections`, `TerminalSettingsScreen`, `EditorSettingsScreen`, `AssistantProviderScreen`, `AssistantComposer` (chips); delete forked branches in `IcodeDropdown.kt` (keep as re-export shim until C1 passes) | `tools/check-ui-consistency.sh` PASS, `tools/check-backend-consistency.sh` PASS, capture 37 routes — no visual delta except unified selected state (Accent+Check always) |
| **C2 — Theme parity** | Make every menu/surface read `IcodeColors/IcodeType` only; add Light vs Dark vs Amoled snapshots (100%/150%). | Edit `IcodeTheme.kt`, `IcodeMenus.kt`, `IcodeSelect.kt` | Snapshots reviewed, Light not washed, `raw-color` still 0 |
| **C3 — Palette consolidation** | Remove any stray `Color(0x` that would appear if C1 regresses; pin `OnAccent` via `AccentColorRules`. | Edit only `IcodeColors.kt` if needed | Guard `raw-color` 0, no screen touches palette |
| **C4 — Type & metrics lock** | Enforce `IcodeType` everywhere, `IcodeSpacing/Sizes` only; no `sp`/`dp` in `ui/screens`. | No new files; edits scoped to offending screen if `dp-literal`/`sp-literal` fires | Guard `dp-literal`/`sp-literal`/`ui-lines` PASS |
| **C5 — Final capture & debt sweep** | 37-route capture at both scales, record watermark, reset allowlist only downwards. | Docs only | `ui-debt-allowlist.txt` ≤6 entries, never grown |

Each cycle is compilable and reverts independently; no cycle depends on a later one's code.

## 7. Validation (must pass every cycle)

- `tools/check-ui-consistency.sh` and `tools/check-backend-consistency.sh` — PASS (ratchet never grows).
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` on x86-64 — kit files ≤300, screens ≤300 budget.
- `tools/capture-figma-screens.sh` — 37 screens at 100% and 150% (Todo P12) — no clipping from Select height (`BarChoice=32dp` vs `TouchTarget=48dp`).
- Arm64 AAPT2 limitation (`findings.md:F2`) means full gate needs x86-64; this container only checks guards + JVM compile.

## 8. Risks & acceptance

- Risk: Unifying selected style changes visible check placement → mitigate with snapshot diff in C1.
- Risk: `DropdownMenu` elevation/border drift on Light → covered by C2 snapshots.
- Acceptance: Your dropdown shot (chip mode) shows same selected `Accent`+check as settings card mode; Light theme's menu reads identical to Dark except palette swap.

## 9. Deliverable

This doc is the plan. Execution starts only on your `go` for C1. No assumptions beyond the files read above; unresolved Qs are remeasured on device (palette review candidate Light/Amoled — `IcodeColors.kt:64,78`).
