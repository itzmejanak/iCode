# Backend Splitting and Consistency Rules

Status: **prospective rulebook** (2026-09-23). Applies to all new Android backend/integration work in this repository. It does **not** authorize or schedule a wholesale split of existing code. Existing implementations are audited and split only in a separately requested, scoped cycle. The first worked application is [Appearance and Editor Settings](./settings-backend-split-plan.md).

Read with [ui-split-plan.md](./ui-split-plan.md) (already completed UI ownership rules), [architecture.md](./architecture.md) §8–10 (intended boundaries), [integration-plan.md](./integration-plan.md) (real lifecycle and the demo→real→gaps→cleanup sequence), and [Todo.md](../Todo.md) (delivery status). This document governs *how* backend code is added or later split; feature plans govern *what* behavior is delivered. Code and tests remain the source of truth for what is currently implemented.

## 1. Starting point and non-goals

The repository currently has one Android `app` module, with practical packages `domain`, `data/{workspace,git,runtime,settings,language,notices,system}`, `ui/editor`, `ui/terminal`, `ui/screens`, `designsystem`, and `navigation`, plus the debug-only capture fixtures in `app/src/debug/.../capture`. The multi-module tree in `architecture.md` §8 is a boundary map, **not** a claim that those Gradle modules exist. `AndroidICodeGateway.kt` currently orchestrates multiple icode concerns (being split per scope, [code-separation-plan.md](./code-separation-plan.md)); the domain contract is one file per scope plus `ICodeSnapshot`, `ICodeIntent`, `ICodeEffect` and `ICodeGateway`. Their size identifies future review candidates but is not permission to move them during an unrelated feature. Existing Git, runtime, workspace, editor, settings and capture changes—some concurrent—must be preserved.

The aim is one accountable owner for each behavior and state transition, not a maximum number of files, interfaces, modules, or patterns. Do not introduce a service layer, generic repository, dependency-injection framework, event bus, or new Gradle module merely because a diagram looks cleaner. Split at a real boundary: independent lifecycle, external system, reusable policy, testable parser, distinct data source, or demonstrated second consumer.

## 2. Ownership and dependency direction

| Responsibility | Owner in the current repository | May depend on | Must not own |
|---|---|---|---|
| User-facing state, invariants, IDs, intents/effects, validation, capability/result types | `domain/<feature>` or a focused existing `domain` file | Kotlin/coroutines as needed | Android UI, DataStore, files, PRoot, Git CLI, Compose |
| Platform or library integration | `data/<feature>` (and `ui/editor` for the native editor view) | Its feature contract, Android/library APIs | Screen layout, navigation decisions, sample literals |
| Cross-feature icode orchestration | Existing `ICodeGateway` implementation and narrow coordinators it delegates to | Feature contracts/adapters | Reimplemented parsers, direct rendering, second copy of a feature's state |
| App lifecycle and concrete construction | `MainActivity`, `IcodeApp`, the existing ViewModel/composition root | Interfaces plus selected implementations | Feature business rules or screen-specific control state |
| Screen presentation/navigation | `ui/screens`, `navigation`, `designsystem` per UI split plan | Immutable domain snapshots, intents, tokens | Direct filesystem/Git/process/DataStore access or competing persisted state |
| Deterministic capture and tests | `app/src/debug/.../capture` and test source sets | The same public contracts | Production branches containing sample results or fake success |
| Build/bootstrap scripts and third-party code | `runtime/bootstrap`, `tools`, `third_party` | Explicit versioned inputs/outputs | Product state ownership or unreviewed duplicated engines |

Dependency path: `UI → feature contract ← adapter`; `composition root → implementations`; `orchestrator → feature contracts/adapters`. Keep feature-to-feature dependencies through a narrow shared contract or orchestrator rather than circular imports. `designsystem` stays feature-free. `data` never imports screen code. A parser must not call UI; a screen must not parse command output. Physical package boundaries suffice until a separate Gradle module produces a concrete benefit and migration plan.

## 3. One-source-of-truth rules

1. **One fact, one owner.** Record for each new behavior where its canonical state lives, who can mutate it, who observes it, and how it survives restart. Do not keep the same setting, document text, Git status, process state, tab order, or search result as independently editable copies in UI and gateway. UI-local state is only transient interaction state.
2. **One write path per resource.** Manual and automated operations call the same guarded path: document writes through the file source, Git mutations through the Git service, commands through the process contract, preferences through their repository. No screen or helper bypasses validation, conflict checks, or reporting.
3. **One parser/normalizer per external format.** Git porcelain/diff, ANSI, runtime output, path normalization, document encoding, and future `rg`/LSP payloads have dedicated tested parsers. Do not use display text as protocol data. Keep raw evidence available in errors without leaking secrets.
4. **One effective-value resolver.** App defaults, workspace overrides, capability gates and derived statuses are resolved once and exposed immutably. Do not persist derived values or duplicate fallback logic in multiple screens.
5. **One capability truth.** A control is enabled only if its underlying provider and prerequisites exist. Distinguish `Loading`, `Ready`, `Unavailable(reason)`, `Running`, `Failed(reason)`, and `Cancelled` where relevant. Never replace failure with sample success, fabricated output, or silent defaults.
6. **One capture seam.** The real implementation and the capture fixtures share domain contracts and UI composition. Fixtures live in `app/src/debug/.../capture` (`CaptureFixtures`, drawn by `CaptureGateway`), never in product screens; a screen reads `captureMode` only to stop its own timed advance, never to choose content (guard rule `capture-fork`). A release build contains no fixture.
7. **One design vocabulary.** Backend statuses map to semantic UI tones/components in a presentation adapter; backends never store colors or design strings. UI tokens and component rules remain in `ui-split-plan.md`.

## 4. When and how to split a backend component

Before coding, write a small ownership map for the requested slice: entry points → state owner → side-effect owner → output consumers → persistence → failure/cancel/restore path. Inspect the actual call sites and existing library/runtime capabilities. Only then choose files. Prefer extending a good existing owner over a parallel implementation.

Split when **one of these is true**: the responsibilities have different lifecycles (e.g. terminal session vs runtime installer); a pure algorithm/parser can be tested without Android; an external dependency needs an adapter; multiple entry points need the same guarded operation; a class has unrelated reasons to change; or a second real consumer needs the same policy. Keep together when the logic is private, small, shares one lifecycle and has one owner. A large file is an audit signal, not an automatic extraction rule. Aim for focused files of roughly ≤250 lines; >300 lines requires an ownership review, not a mechanical line-splitting patch. Existing large files are grandfathered until their feature is deliberately touched or a split is requested.

For a requested split, use this sequence:

1. Baseline reachable UI/actions, real/device behavior, tests, data/schema, the capture set, and concurrent edits. Mark confirmed facts separately from assumptions and decisions.
2. Identify the existing public contract and one source of truth. Extract **pure** model/validation/parser first; add characterization tests before changing behavior.
3. Extract a narrow adapter/coordinator behind that contract while old callers still compile. Move one operation at a time; route every caller through the shared path.
4. Remove the old implementation only after parity on success, error, cancellation, restart, multi-workspace and capture scenarios. Before the first release, stored formats change freely (§6).
5. Run proportionate checks after every step and record remaining gaps. Do not mark a feature complete because compilation passes alone.

Do not create package-per-class hierarchies, `Util`/`Manager` dumping grounds, giant “common” modules, interface+implementation pairs with only one conceivable consumer, or generic frameworks around a single setting/operation. Avoid broad constructor graphs to make a small feature work. External library/protocol reuse wins when it supports the required lifecycle; pin the version, verify actual APIs, document limitations/licensing, and wrap only the product-specific seam. Do not hand-build a second editor, shell, Git parser, formatter, or search engine to make a visual control appear active.

## 5. Lifecycle contract for every new feature

Every feature plan and implementation must account for the applicable transitions, not just its happy-path button tap:

| Stage | Questions to answer |
|---|---|
| Enter | Where is the route/state entered from? Is it a route, parent-page mode, panel tab, or dialog? What happens with no workspace, no permission, no runtime, or no repository? |
| Load | Who loads data, on what dispatcher, with what bounds and cancellation? How are `Loading`, empty, stale, partial, and error states distinguished? |
| Act | Which validated intent crosses the UI boundary? What exact path/repository/process is mutated? Are confirmation and idempotency needed? |
| React | Which source publishes the new state? Which screens, tabs, decorations, panels, search results, and native views must update together? |
| Interrupt | What happens on back, close, workspace switch, permission revocation, app background, process death, or a second request? Who cancels or completes in-flight work? |
| Recover | Can the user retry, compare, discard, restore, or inspect the original error? Are dirty data, credentials and external files protected? |
| Demonstrate | Does the same UI still render the deterministic capture fixtures without claiming that production succeeded? |

Define an explicit result/state type when a boolean would hide meaningful cases. Propagate cancellation instead of converting it into an error or success. Use bounded I/O, timeouts and backpressure at runtime/network/file boundaries. Guard stale async results by request identity/revision/workspace identity. Redact credentials and sensitive paths from logs where appropriate. User-owned files are never silently overwritten or deleted.

## 6. Persistence and migration rules

- App-private preferences, workspace-scoped overrides, recents/session state, Git config/credentials and project files are **different owners**; choose the existing owner appropriate to the fact. Do not save app settings into a workspace directory or put Git author identity in a fixture default.
- Stable key/schema, type, validator, default, scope and reset behavior belong together. Reads and writes use one repository. Unknown future keys are not wiped casually.
- **Before the first release, no compatibility** (owner, 2026-10-01): iCode is not in production, so a stored format or key may change freely, and nothing reads, migrates or cleans up what an earlier build wrote. From the first release on, a change to a stored format comes with an explicit, tested migration.
- Store canonical workspace identity once; use it consistently for keys and stale-result guards. Derived values are recomputed, not saved. A reset removes an override, not writes today's inherited value.
- On read corruption, permission loss, full storage or write failure, report an actionable state and preserve the last confirmed value/data. No silent reset, fake success or “saved” UI before the actual write commits.
- Credentials and tokens use the security owner of the relevant feature, never a general settings snapshot, screenshot fixture or log. External project files are edited only through the guarded document/file lifecycle.

## 7. Verification and anti-regression gate

For each slice, maintain a small matrix covering unit/pure contract, adapter/integration, UI navigation/state, migration/restart, interruption/recovery, deterministic capture, and device acceptance. Test negative cases as first-class behavior. Prefer real output fixtures for external protocols. Use installed versions and official documentation to verify APIs; record any experimental capability gate.

Minimum routine after a coding cycle: `:app:compileDebugKotlin`, targeted and then full `:app:testDebugUnitTest`, `:app:lintDebug`, `tools/check-ui-consistency.sh`, and frame/device verification for touched UI. The existing UI ratchet is not a backend architecture test; future automated backend guards should be added only for concrete regressions they can detect reliably (for example forbidden UI→`data` imports, duplicated persistence ownership, or untested schema migrations). Never increase an allowlist or delete a route/fixture just to make a check green.

Definition of done for a backend feature: all existing UI actions retained; real success and failure observable; one owner for state and writes; lifecycle and scope tested; no capture fork; migrations safe; docs/Todo status matches evidence. “100% accurate” means these stated acceptance conditions have evidence, not an assertion of perfection.

## 8. How future work uses this rulebook

For a new feature, create a short feature plan under `docs/` or a focused section of `integration-plan.md` that names current code, owners, lifecycle, library decision, slices, tests and cleanup. For **existing implementation splitting**, wait for an explicit requested scope, then audit it against §§2–7 and split only that scope. Appearance/Editor Settings uses [settings-backend-split-plan.md](./settings-backend-split-plan.md); future Git/runtime/search/recovery/settings work should follow the same template without copying the settings-specific classes or defaults.

Minimum feature-plan checklist (fill with evidence, not generic prose):

- Exact user entry points, route/state/dialog identity, and controls to preserve.
- Actual current files/classes and which behavior already works; concurrent ownership or dirty-tree overlap.
- One canonical state owner, one write path, external library/protocol choice, and proposed contract/adapter boundary.
- Loading, unavailable, success, error, cancellation, stale-result, restart and workspace-switch behavior.
- Existing storage keys/formats and any migration; security/permission implications.
- Small compilable cycles naming files to add/change/remove, with tests and device/capture acceptance for each.
- Cleanup condition: remove only replaced old code after parity is proven; record unsolved decisions and known debt.

This is a development rule, not a claim that all existing code already complies. Record deviations and outstanding debt honestly; address them in their own scoped cycle.
