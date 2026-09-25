# Project Development Plan

---

## Phase 1 — UI Implementation & Repository Readiness

The Figma design is completed and will be treated as the **single source of truth** for the UI.

- Revert the existing code to avoid carrying forward inconsistent or incorrect implementations.
- **Clean, stabilize, and prepare the repository** for development.
- Audit and organize the existing project structure before implementing new features.

### UI/Kit Component System
- Build a **consistent UI/KIT component system** based on the Figma design.
- Reuse shared components wherever possible to avoid duplicate implementations.

### UI Implementation
- Port **all screens** from the Figma design into the application.
- Implement the **complete user flow/cycle**, including navigation and transitions between screens.
- Maintain **100% visual and functional consistency** across:
  - Screens
  - Spacing
  - Typography
  - Colors
  - States
  - Reusable components

- **Focus strictly on UI** during this phase—no backend or integration work.
- Ensure the completed UI is **fully runnable** as a demo application.
- Structure the UI and component architecture to **easily connect backend data later** without major rewrites.

---

## Phase 2 — Backend

- Plan the backend architecture **after the UI is completed and validated**.
- Define:
  - Data models
  - APIs
  - Business logic
  - Authentication
  - Validation
  - Error handling (based on finalized user flows).
- Build the backend to **accurately support the existing UI and user journey**.
- Keep **API contracts and data structures clean and predictable** for straightforward frontend integration.

---
## Phase 3 — Integration

- Connect the **finalized UI with the backend**.
- Replace demo/mock data with **real backend data**.
- Implement:
  - Authentication
  - API calls
  - Loading states
  - Error states
  - Validation
  - All required interactions
- Ensure **every screen and user flow works end-to-end**.
- Test the **complete application** for:
  - Consistency
  - Reliability
  - Edge cases
- Keep the **integration layer modular** for future services or third-party integrations.

---
## Core Principle

**Phase 1:** Build the complete UI
**→ Phase 2:** Build the robust backend
**→ Phase 3:** Integrate everything.

**Priority:** Development discipline and consistency.
- Finish the UI **completely first**.
- Avoid unnecessary changes.
- Build the architecture so backend and future integrations can be **added cleanly**.


# UI Splitting plan

I’ve completed the UI porting from Figma according to `@Todo.md` and `@plan.md`.

Before moving to the next phase, I want to make the entire UI codebase maintainable, consistent, and easy to integrate.

First, audit the current UI architecture across all ~40 screens and identify:

- Large/monolithic screen files that should be split.
- Duplicate UI patterns and components.
- Inconsistencies in spacing, typography, colors, layouts, states, and interactions.
- Components that should become shared/reusable UI-kit components.
- Poor separation of screen, component, state, navigation, and design-system responsibilities.
- Architectural/design-pattern issues that could make future integration difficult.

Then create **one master, phased plan** — do not modify code yet.

## 1. UI Kit First

- Define the shared design-system/UI-kit components we need.
- Identify which existing components can be reused, refactored, merged, or removed.
- Establish rules to prevent duplication and visual inconsistency.
- Define clear ownership for shared vs screen-specific components.

## 2. Screen Decomposition

Plan how each of the ~40 screens should be split into maintainable files/components.

- Keep each screen focused on composition/orchestration.
- Separate reusable components, screen-specific components, state, and supporting models where appropriate.
- Define what should remain in each main screen file.
- Avoid splitting components that do not actually benefit from separation.

## 3. Architecture

Follow established Jetpack Compose/Kotlin best practices and appropriate design principles.

- Prefer simple, practical architecture over unnecessary abstraction.
- Establish clear dependency boundaries between:
  - UI kit/design system
  - Screens
  - Screen-specific components
  - State
  - Navigation
  - Domain
- Avoid circular dependencies and unnecessary coupling.
- Keep the architecture easy to understand and extend.

## 4. Zero-Duplication / Zero-Inconsistency Goal

Explicitly identify and plan how to eliminate:

- Duplicate UI components.
- Duplicate layouts.
- Duplicate styling.
- Duplicate interaction patterns.
- Duplicate state-handling patterns.
- Slightly different implementations of the same UI concept.
- Inconsistent spacing, typography, colors, shapes, dimensions, icons, and behavior.

Define rules that prevent these problems from returning.

**Do not create abstractions merely for the sake of reducing file count.**

## 5. Execution Strategy

Break the work into small, safe implementation cycles.

For every cycle define:

- Goal.
- Scope.
- Files to create.
- Files to modify.
- Files to move/rename.
- Files to delete, if any.
- Dependencies affected.
- Expected result.
- Validation checklist.

Each cycle should be independently understandable and preferably compilable/testable.

Preserve existing UI behavior and visual output unless an inconsistency is intentionally corrected.

Avoid large, risky refactors.

## 6. Screen-by-Screen Plan

For every screen, document:

- Current file/location.
- Current responsibilities.
- Current problems.
- Components to extract.
- Shared components to reuse.
- Screen-specific components to keep local.
- State/model responsibilities.
- Target file structure.
- Dependencies.
- Migration order.

## 7. Target Folder/File Structure

Propose the final maintainable folder structure.

Show where:

- Design-system components belong.
- Shared UI components belong.
- Screen-specific components belong.
- Individual screens belong.
- State belongs.
- Navigation belongs.
- Domain models/contracts belong.

The structure should scale as more screens and real backend functionality are integrated.

## 8. Validation

Define validation criteria for every phase, including:

- Compilation.
- Existing tests.
- UI behavior.
- Visual consistency.
- No accidental regressions.
- No duplicated components.
- No duplicated styling/constants.
- No unnecessary dependencies.
- Correct ownership of components.
- Maintainability of each screen.

## Final Deliverable

Produce **one complete master refactoring plan** containing:

1. Current architecture assessment.
2. Current gaps, duplication, and inconsistencies.
3. UI-kit audit and proposed UI-kit structure.
4. Proposed folder/file structure.
5. Screen-by-screen decomposition plan for all ~40 screens.
6. Component ownership rules.
7. Dependency/architecture rules.
8. Zero-duplication and consistency strategy.
9. Phased implementation order.
10. Detailed cycle-by-cycle file changes.
11. Validation checklist for every cycle.
12. Final target architecture.

Use the **actual codebase as the source of truth**.

Read `@Todo.md`, `@plan.md`, and all relevant UI files before finalizing the plan.

**Do not implement anything yet. First produce the complete plan, including assumptions, discovered issues, and any decisions that require confirmation.**

**Master plan (approved and being executed): [`docs/ui-split-plan.md`](./docs/ui-split-plan.md)** — includes the duplication ledger, UI-kit audit, target structure, screen-by-screen map, cycle list and validation gates. Guardrail: `tools/check-ui-consistency.sh` + `tools/ui-debt-allowlist.txt`.

---

# Phase 2/3 Integration plan

**Master plan (approved; Cycles 0–1 complete): [`docs/integration-plan.md`](./docs/integration-plan.md)** — audit of the
regressed workspace-logic round, the demo→real→gaps→cleanup rule, library decisions (Sora Editor,
ripgrep, Git CLI/JGit interim), and the cycle-by-cycle execution order with validation gates.
The next sequence follows the live search report: frame 16 Editor Find/Sora, then frame 11
Workspace Search and frame 14 Quick Open/ripgrep, then Git frames. Each frame keeps its demo
controls and routes until its real lifecycle and failure states pass verification.
Guardrails: `tools/check-ui-consistency.sh` (`orphan-route`, `capture-fork`).

**Backend splitting standard for all future Android work:** [`docs/backend-split-rules.md`](./docs/backend-split-rules.md).
It extends the completed UI split's ownership/consistency discipline to contracts, adapters,
state, side effects, persistence, lifecycle, capture parity and verification. Apply it prospectively;
split existing implementations only in a separately requested scoped cycle. The first worked
feature plan and implementation ledger are [`docs/settings-backend-split-plan.md`](./docs/settings-backend-split-plan.md)
for Appearance and Editor Settings. The typed settings and guarded save path are wired; palette
approval, physical-device acceptance, formatter integration and broader session restore are still open.
