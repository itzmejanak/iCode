# Rename "Workbench" → "ICode" — Execution Plan

Status: **plan** (2026-10-06). Owner's request: remove all "workbench" wording by renaming files and updating code across the repo for wording consistency. 100% accurate, no guesses, no assumptions, 0 gaps.

## 1. Scope (measured)

| Metric | Count |
|---|---|
| Files containing "workbench" (case-insensitive) | 132 |
| Files with "Workbench" in their filename | 11 |
| Total occurrences across all tracked files | ~1,050 |

## 2. What is NOT renamed

- **Workspace / Workspaces** — a distinct concept (a project folder); stays as-is.
- **Figma frame references** in docs (e.g., "frame 40") — unrelated.
- **Third-party library names** — none found containing "workbench".

## 3. File renames (11 files)

Renamed via `git mv` to preserve history. Every import referencing them must update.

| Current path | New path |
|---|---|
| `app/src/main/java/dev/icode/mobile/domain/WorkbenchGateway.kt` | `…/domain/ICodeGateway.kt` |
| `app/src/main/java/dev/icode/mobile/domain/WorkbenchSnapshot.kt` | `…/domain/ICodeSnapshot.kt` |
| `app/src/main/java/dev/icode/mobile/domain/WorkbenchIntent.kt` | `…/domain/ICodeIntent.kt` |
| `app/src/main/java/dev/icode/mobile/domain/WorkbenchEffect.kt` | `…/domain/ICodeEffect.kt` |
| `app/src/main/java/dev/icode/mobile/data/workspace/AndroidWorkbenchGateway.kt` | `…/data/workspace/AndroidICodeGateway.kt` |
| `app/src/main/java/dev/icode/mobile/data/workspace/WorkbenchProcess.kt` | `…/data/workspace/ICodeProcess.kt` |
| `app/src/main/java/dev/icode/mobile/data/workspace/WorkbenchStateHolder.kt` | `…/data/workspace/ICodeStateHolder.kt` |
| `app/src/main/java/dev/icode/mobile/ui/AndroidWorkbenchViewModel.kt` | `…/ui/AndroidICodeViewModel.kt` |
| `app/src/main/java/dev/icode/mobile/ui/WorkbenchLocals.kt` | `…/ui/ICodeLocals.kt` |
| `app/src/main/java/dev/icode/mobile/ui/WorkbenchQuestions.kt` | `…/ui/ICodeQuestions.kt` |
| `app/src/test/java/dev/icode/mobile/data/workspace/WorkbenchStateHolderTest.kt` | `…/data/workspace/ICodeStateHolderTest.kt` |

## 4. Symbol renaming rules (Kotlin/Java code)

Every identifier follows the same pattern. No exceptions, no partial renames.

| Old symbol | New symbol |
|---|---|
| `WorkbenchGateway` | `ICodeGateway` |
| `WorkbenchSnapshot` | `ICodeSnapshot` |
| `WorkbenchIntent` | `ICodeIntent` |
| `WorkbenchEffect` | `ICodeEffect` |
| `WorkbenchProcess` | `ICodeProcess` |
| `WorkbenchStateHolder` | `ICodeStateHolder` |
| `AndroidWorkbenchGateway` | `AndroidICodeGateway` |
| `AndroidWorkbenchViewModel` | `AndroidICodeViewModel` |
| `AndroidWorkbenchViewModelFactory` | `AndroidICodeViewModelFactory` |
| `LocalWorkbenchGateway` | `LocalICodeGateway` |
| `LocalWorkbenchSnapshot` | `LocalICodeSnapshot` |
| `rememberWorkbenchQuestions` | `rememberICodeQuestions` |
| `workbench:` (variable name in `IcodeApp.kt`) | `icode:` |

### Import path updates

Every file importing from the old names gets its import updated:

```kotlin
// Before
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.data.workspace.AndroidWorkbenchGateway
import dev.icode.mobile.ui.WorkbenchLocals

// After
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.data.workspace.AndroidICodeGateway
import dev.icode.mobile.ui.ICodeLocals
```

## 5. Natural-language replacements (docs, comments, strings)

In documentation, comments, and user-facing strings, "workbench" appears as a common noun. The replacement depends on context:

| Context | Old text | New text |
|---|---|---|
| Product title in docs | `Native Mobile Development Workbench` | `iCode — Native Mobile Development Environment` |
| Generic reference to the app | `the workbench` | `iCode` |
| Reference to the UI surface | `workbench shell` | `iCode shell` |
| Adjective | `workbench mutation lock` | `iCode mutation lock` |
| Compound | `browser-rendered workbench` | `browser-rendered IDE` |
| Glossary definition | `Workbench \| The complete native editing environment…` | `iCode \| The complete native editing environment…` |
| Architecture decision table key | `Workbench \| Product-owned native features` | `iCode UI \| Product-owned native features` |
| Mermaid diagram labels | `M["Main workbench"]` | `M["Main iCode surface"]` |

Case is preserved: `Workbench` → `ICode`, `workbench` → `iCode`.

## 6. Files grouped by execution batch

### Batch A: Domain contracts (rename files + symbols, then fix all imports)

Order matters: rename these first because everything else imports them.

Files to rename: `WorkbenchGateway.kt`, `WorkbenchSnapshot.kt`, `WorkbenchIntent.kt`, `WorkbenchEffect.kt`

Top files needing import/symbol updates (sorted by occurrence count):

1. `AndroidWorkbenchGateway.kt` (149 hits — also renamed in Batch B)
2. `WorkbenchIntent.kt` (105 hits — self)
3. `AssistantCoordinatorTest.kt` (72)
4. `DocumentSessionCoordinator.kt` (47)
5. `IcodeApp.kt` (36)
6. `WorkspaceItemsCoordinator.kt` (30)
7. `WorkspaceSessionCoordinator.kt` (29)
8. `WorkspaceImportCoordinatorTest.kt` (28)
9. `AssistantCoordinator.kt` (28)
10. `WorkspaceLibraryCoordinator.kt` (25)
11. `LanguageServiceCoordinator.kt` (24)
12. `WorkbenchQuestions.kt` (22 — also renamed)
13. `TaskRunnerTest.kt` (20)
14. `WorkbenchEffect.kt` (20 — self)
15. All remaining `.kt` files with counts 1–19

### Batch B: Data layer (rename files + symbols, fix imports)

Files to rename: `AndroidWorkbenchGateway.kt`, `WorkbenchProcess.kt`, `WorkbenchStateHolder.kt`

### Batch C: UI layer (rename files + symbols, fix imports)

Files to rename: `AndroidWorkbenchViewModel.kt`, `WorkbenchLocals.kt`, `WorkbenchQuestions.kt`

### Batch D: Test files (rename + fix)

Files to rename: `WorkbenchStateHolderTest.kt`
Files to update: all test files listed above

### Batch E: Documentation (text replacements only, no renames)

22 `.md` files + `README.md` + `Todo.md`

### Batch F: Config/tooling

1. `tools/backend-debt-allowlist.txt` (1 hit — path reference to `AndroidWorkbenchGateway.kt`)

## 7. Execution order (dependency-safe)

Each step is executed one at a time. The plan pauses between steps for verification.

```
Step 1:  git mv all 11 files (§3)
Step 2:  Replace symbols in domain/ files (WorkbenchGateway, Snapshot, Intent, Effect)
Step 3:  Replace symbols in data/workspace/ files (AndroidWorkbenchGateway, Process, StateHolder)
Step 4:  Replace symbols in ui/ files (ViewModel, Locals, Questions)
Step 5:  Replace symbols in all remaining src/main/ files
Step 6:  Replace symbols in all src/debug/ files
Step 7:  Replace symbols in all src/test/ files
Step 8:  Replace natural language in docs/*.md, README.md, Todo.md
Step 9:  Update tools/backend-debt-allowlist.txt
Step 10: Run ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
Step 11: Final grep verification → must return 0 results
```

## 8. Verification (zero-gap guarantee)

After each major step and once more at the end, run:

```bash
# 1. No "workbench" remains in any tracked file (case-insensitive)
grep -ri 'workbench' . --include='*.kt' --include='*.java' --include='*.md' \
  --include='*.txt' --include='*.kts' --include='*.xml' --include='*.json' | grep -v '.git/'

# 2. No old filenames remain
find . -iname '*workbench*' -not -path './.git/*'

# 3. Build passes
./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin

# 4. Tests pass
./gradlew :app:testDebugUnitTest

# 5. Lint passes
./gradlew :app:lintDebug
```

All five checks must return clean before the change is considered done.
