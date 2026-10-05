# Figma Wording Cleanup — Plan

Status: **plan** (2026-10-06). Owner's request: remove unnecessary "Figma" references from docs and code comments to keep project scope tight. Keep "Figma" only where it names the actual design tool, a node ID, or the capture script. No explanations in output — just cleaner wording.

## 1. What stays as "Figma"

These are legitimate references to the external design tool and must not change:

| Context | Example | Why it stays |
|---|---|---|
| Node IDs in `Route.kt` | `figmaNode: String?`, `"3:11"` | Direct reference to a Figma frame |
| Capture script name | `tools/capture-figma-screens.sh` | The script captures from the Figma-defined route list |
| Design-tool attribution | "the Figma design", "ported from Figma" | Names the source-of-truth tool |
| Frame counts tied to the design | "40 Figma frames" | Quantifies the design deliverable |
| Test assertions about frame coverage | `TOTAL_FIGMA_FRAMES`, `every Figma frame` | Validates design-to-code parity |
| Visual-regression baseline | "validated Figma output" | Explains why a metric differs from spec |

## 2. What changes

Generic references that use "Figma" as a synonym for "design", "screen", or "UI spec" when the context is already clear.

| Old wording | New wording | Where |
|---|---|---|
| "built from the complete Figma user flow" | "built from its complete design" | `README.md` line 3 |
| "Each Figma frame lives in its own screen file" | "Each screen lives in its own file" | `README.md` line 58 |
| "The Figma design is completed and will be treated as the single source of truth" | "The design is the single source of truth" | `plan.md` line 7 |
| "based on the Figma design" | "based on the design" | `plan.md` lines 14, 18 |
| "from the Figma design" | "from the design" | `plan.md` line 18 |
| "UI porting from Figma" | "UI porting from the design" | `plan.md` line 81 |
| "per Figma" / "matching the Figma frames" | "per the design" / "matching the design" | `IcodeType.kt`, `IcodeFields.kt`, `IcodeMetrics.kt`, `SplashScreen.kt` |
| "Figma fidelity" | "design fidelity" | `ui-split-plan.md` |
| "Figma inconsistency" | "design inconsistency" | `integration-plan.md` |
| "Figma correction" | "design correction" | `integration-plan.md` |
| "no Figma frame" (in plan docs describing owner-added pages) | "no design frame" | `runtime-components-plan.md`, `workspaces-page-plan.md` |
| "the first without a Figma frame" | "the first without a design frame" | `attention-labels-cleanup-plan.md` |
| "Figma strings" | "design tokens" | `backend-split-rules.md` |
| "Capture all 39 screens" section heading | "Capture all screens" | `README.md` |

## 3. Files affected

### Code (comments only, no logic changes)

| File | Changes |
|---|---|
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeType.kt` | "Figma" → "the design" in KDoc |
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeFields.kt` | "Figma frames" → "design" in KDoc |
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeMetrics.kt` | "Figma says otherwise" → "the design says otherwise" |
| `app/src/main/java/dev/icode/mobile/designsystem/IcodeButtons.kt` | "Figma casing" → "design casing" |
| `app/src/main/java/dev/icode/mobile/ui/screens/entry/SplashScreen.kt` | "per Figma" → "per the design" |
| `app/src/main/java/dev/icode/mobile/navigation/Route.kt` | KDoc: "The Figma frame" → "The design frame" (the field name `figmaNode` stays) |
| `app/src/test/java/dev/icode/mobile/navigation/RouteTest.kt` | Comments: "Figma frames" → "design frames" where generic; test names and `TOTAL_FIGMA_FRAMES` constant stay |

### Documentation

| File | Changes |
|---|---|
| `README.md` | Lines 3, 29, 58 per table above |
| `plan.md` | Lines 7, 14, 18, 81 per table above |
| `docs/ui-split-plan.md` | "Figma fidelity" → "design fidelity"; "Figma casing" → "design casing"; "validated Figma output" → "validated design output" |
| `docs/integration-plan.md` | "Figma inconsistency" → "design inconsistency"; "Figma correction" → "design correction"; "Figma frames" → "design frames" where generic |
| `docs/backend-split-rules.md` | "Figma strings" → "design tokens" |
| `docs/runtime-components-plan.md` | "No Figma frame" → "No design frame" (×2) |
| `docs/workspaces-page-plan.md` | "no Figma frame" → "no design frame"; "list of Figma frames" → "list of design frames" |
| `docs/workspace-storage-plan.md` | "Figma frames" → "design frames" where generic; "Frames are the Figma frames" → "Frames are the design frames" |
| `docs/attention-labels-cleanup-plan.md` | "without a Figma frame" → "without a design frame" (×2); "Figma routes" → "design routes" |
| `docs/backend-split-audit.md` | "Figma frames" → "design frames" where generic (keep "40 Figma frames" when counting the design deliverable) |
| `docs/editor-colors-plan.md` | No changes needed (no generic Figma references) |
| `docs/ui-polish-plan.md` | "capture-figma-screens.sh" stays (script name) |

### NOT changed

| File | Reason |
|---|---|
| `tools/capture-figma-screens.sh` | Script name stays; internal comments referencing "Figma frames" stay when they mean the design deliverable count |
| `tools/build-screenshot-contact-sheets.py` | "40 Figma frames" is a design-deliverable count |
| `Todo.md` | "Figma: iCode — Full User Flow" is a link label to the design file; "capture-figma-screens.sh" is a script reference |
| `.gitignore` | References the capture script by name |
| `docs/workbench-to-icode-rename-plan.md` | "Figma frame references" explains what was excluded from the rename |
| `app/src/main/java/dev/icode/mobile/navigation/Route.kt` field name `figmaNode` | This is a code identifier tied to the Figma API concept; renaming it would break the capture pipeline |
| `RouteTest.kt` constant `TOTAL_FIGMA_FRAMES` | Tied to the design deliverable; renaming would obscure what it counts |

## 4. Execution order

```
Step 1: Edit code comments (7 files)
Step 2: Edit README.md and plan.md
Step 3: Edit docs/*.md (9 files)
Step 4: Verify — grep -rni 'figma' . | grep -v '.git/' | review each remaining hit is legitimate
Step 5: Commit
```

## 5. Verification

After execution, every remaining "Figma" occurrence must fall into one of the §1 categories. Run:

```bash
grep -rni 'figma' --include='*.kt' --include='*.md' --include='*.sh' --include='*.py' --include='*.txt' . \
  | grep -v '.git/' \
  | grep -vi 'figmaNode' \
  | grep -vi 'TOTAL_FIGMA_FRAMES' \
  | grep -vi 'capture-figma-screens' \
  | grep -vi 'Figma frame' \
  | grep -vi 'Figma design' \
  | grep -vi 'from Figma' \
  | grep -vi 'Figma:' 
```

Any remaining hit must be manually reviewed and either justified under §1 or fixed.

</content>