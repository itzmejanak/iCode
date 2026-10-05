# Commands and panels: scoped close-out plan

Status: implementation in progress (2026-09-24). Follow
[backend-split-rules.md](./backend-split-rules.md) and the ordered checklist in
[Todo.md](../Todo.md). This plan records verified code boundaries, not a claim
that every demo frame is integrated.

> **Note (2026-09-26).** Names in this record are as they were then. The demo layer (`DemoICodeGateway`, `DemoContent`, `staticDemo`, `demo-fork`) was replaced by debug-only capture fixtures (`CaptureGateway`, `CaptureFixtures`, `captureMode`, `capture-fork`) — [code-separation-plan.md](./code-separation-plan.md) K1.

## Observed baseline and ownership

| Surface | Verified current state | Canonical owner and write path |
|---|---|---|
| Command Palette | `CommandPaletteScreen` had four fixed rows, ignored query, and routed Push to summary and every other row to Editor. The first code slice now uses `PaletteCommands` for IDs/query/capability; screen dispatches existing intents or navigates to their existing UI. | `ICodeSnapshot` for availability; `ICodeGateway.dispatch` for Save/Git; navigation for tool routes. Palette query/selection are ephemeral UI state. |
| Problems | The shared panel filters `snapshot.diagnostics` by severity; production currently publishes none, so it explicitly does not claim a clean-code check. | Future workspace-scoped diagnostic aggregate, sourced from actual providers; current `ICodeSnapshot.diagnostics` is the presentation contract. |
| Output | The fixed screen-owned Git log, timestamps, fake source preview and inert channel chip are removed. The shared Output tab reads the latest real `gitOperation.transcript`, bounded by `GitProgressParser`; only Git is presented because no other output-channel provider exists yet. | Git operation state is the current source of truth. A later bounded/redacted event-channel contract belongs to runtime/Git/search/language adapters, not the UI. |
| Terminal | Termux `terminal-view`/emulator and `TerminalSession` are integrated in the concurrent terminal cycle; full fresh-device acceptance remains in `terminal-maturity-plan.md`. | `TerminalSessionManager` owns sessions/PTY; `ui/terminal/TerminalHost` owns the view and rendering. Panel navigation does not replace either. |
| Languages | Configuration and observed guest process state are real; no LSP initialize exchange or editor capability is connected. | User-installed server process via the existing process contract; one protocol client owns JSON-RPC and document versions. |
| Session restoration | Recents and drafts persist, but prior workspace/open tabs/selection are not restored after process death. | Existing app-private session/settings owner stores identifiers and layout; document draft store owns dirty text, never a second copy. |
| About | Screen has fixed build/runtime/editor labels and tappable no-op diagnostic rows. The repository has no Git remote or verified product docs/releases/issues URLs; bundled third-party licence coverage is incomplete. | `BuildConfig`/package information, runtime manifest/inspector, editor dependency metadata and validated destinations; UI only presents facts/actions. |

## Slice order and lifecycle gates

### Shared-panel integration ledger (2026-09-24)

- **Enter:** Editor and empty-editor bottom bars call `openPanel` with the requested tab; the old
  terminal overflow no longer opens a second page. `TerminalExpanded` is the production route;
  `ProblemsPanel` and `OutputPanel` remain only as direct-capture aliases for the 40 frames.
- **State:** `NavigationState` owns the selected view and saves it with the back stack. Only
  Terminal opens a shell; problems filter and terminal zoom are transient panel UI state.
  The gateway remains the sole owner of sessions, Git output and diagnostics.
- **Act/react:** Tab taps change selection without a route push. Real Git progress updates the
  Output tab through the existing snapshot; Problems updates from diagnostics; terminal output
  stays in Termux. No fabricated timestamp, source preview or event is inserted.
- **Interrupt/recover:** Back leaves the whole panel. Closing the last shell while viewing
  Terminal keeps its previous return behavior; an ending shell while viewing another tab cannot
  dismiss that tab. After process death, a terminal is not falsely restored as running.
- **Demonstrate:** Legacy frame IDs render the same shared composition, initialized to their
  corresponding tab. Device/capture acceptance remains open, especially IME transitions and
  tab switching during a running session; do not remove aliases until parity is verified.

1. **Palette** — enter from Editor; load current snapshot; filter a stable command
   registry; keep unavailable commands visible with a reason; dispatch the same
   intent as the existing screen. Save returns to Editor, Git sync opens progress,
   Commit opens the message form. Do not display fake shortcuts. Check query
   focus/keyboard/touch, unavailable states, save/remote failure and back-stack
   on device. Keep capture using the same contract.
2. **Problems presentation** — filter the published list by severity, retain total
   counts, and show truthful zero/filtered-empty states. No production diagnostic
   fixtures. Provider and exact-range navigation wait for LSP's real document
   identity/range contract.
3. **Output** — inventory actual runtime/Git/search/language outputs and their
   sensitivity; define a bounded event with channel, timestamp-at-source,
   severity and redacted text. Subscribe/cancel with panel lifecycle, protect
   against stale workspace results, and verify truncation/clear/restart behavior.
4. **About** — resolve installed build/runtime/editor facts and audit every target
   before enabling actions. The user will supply project URLs later; do not
   infer them from examples or unrelated repositories. Export must be explicit,
   bounded and redacted; if a destination or update service is absent, say
   unavailable instead of no-op.
5. **Session restore** — persist only canonical workspace/document IDs and UI
   ordering/selection; validate permissions/existence before restoring. Reconcile
   drafts through the existing recovery choice. Do not replay terminal commands.
6. **Full VT acceptance** — Termux `terminal-view`/emulator is now pinned and
   integrated by the separate [terminal maturity cycle](./terminal-maturity-plan.md).
   Preserve its original UI controls and complete that cycle's fresh-device
   alternate-screen, IME, selection, resize and exit checks. This panel change
   must not replace or duplicate the Termux process/view owner.
7. **LSP protocol and diagnostics** — keep the agreed configuration/status-only
   behavior until a tested protocol client advertises real capabilities. Then
   publish versioned workspace diagnostics and exact navigation; no static
   process-running flag may imply a successful LSP handshake.

For every slice: enter/load/act/react/interrupt/recover/demo; test pure policy,
adapter failures, navigation and restart where applicable; run compile, unit,
lint, UI consistency and device/capture acceptance per the rulebook. No new
framework, whole-gateway split or fixture deletion is implied by this plan.
