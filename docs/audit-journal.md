# iCode — Autonomous Audit Journal (2026-10-14, 5h)

Started: `task.md` 5-hour audit, branch main@d604e8e (RadoxAI fix), ARM64 PRoot container (no systemd, uid 0).
Head now: `a370462` → `IcodeSelect` C1 done, retry 30s×5 (this push) — both architecture fixes, provider-agnostic and kit-unified.

## Timeline
- T0 discovery + baseline — DONE (2026-10-14 10:5x, guards PASS)
- T1 investigation (bugs, edge cases, concurrency, resources, security, config) — IN PROGRESS (resumed post-reliability fix)
- T2 architecture + scalability + performance — QUEUED
- T3 five-hour stability (controlled test, measurements) — QUEUED
- T4 validation + findings register + improvement plan — QUEUED
- T5 deliverables (exec summary, arch, bug/risk register, security, perf, stability, testing, debt, roadmap, limits) — QUEUED

## Baseline re-validated after a370462
- Guards: backend PASS, UI PASS (2026-10-14, tools/check-backend-consistency.sh / check-ui-consistency.sh)
- ICodeIntent.kt: 300 lines, AssistantTurns.kt: 285, KoogFailures.kt: 216, AssistantRetryPolicy.kt: 30, AssistantRetries.kt: 60
- Build: ARM64 cannot run AAPT2 x86-64 (findings.md:F2) — full Gradle gate needs x86-64. JVM unit tests gated on processDebugResources (AAPT2) — not runnable here.
- Pushed: d604e8e (RadoxAI streaming: KoogChunks.readable) + a370462 (retry: KoogFailures.retryKind + AssistantRetryPolicy + AssistantRetries wired in AssistantTurns)

## Fixes shipped in this audit window
1. `workbench` → `stateHolder` rename (7 files, 83 hits + 4 tests) — findings.md:F5 resolved, 0 hits left in app/src/main
2. `WorkspaceList.summary` null safety — mapNotNull + size check, avoid `it!!.bytes!!` on absent entry
3. `AssistantProviderStore.validChoice` — guard `model != null` before `model!!` (potential NPE on corrupted prefs)
4. `WorkspaceLifecycleCoordinator.rename` — replace `exceptionOrNull()!!` with fallback IOException
5. `WorkspacesProvider` — parentFile!! → safe `?.let`, oldParent captured before move/delete
6. `Todo.md` route count 40→47 (37 with design frames, Route.kt), 39-route → 37-route
7. Reliability: transient 429/rate_limit/5xx/timeout/UnknownHost/connection-ended → 3× 2s/4s/8s + Retry-After, provider-agnostic (AssistantRetryPolicy + KoogFailures.retryKind)

## T1 findings in progress (facts, not assumptions)
- Cancellation: GlobalScope/runBlocking none in app/src/main (verified); 5+ catch(Exception) sites all rethrow CancellationException
- !! remaining: 1 in WorkspaceList.kt bytes sum (now size-checked), 3 in WorkspacesProvider already fixed, rest in tests/third_party only
- Network: cleartext only localhost/127.0.0.1 (network_security_config.xml), HTTPS elsewhere; no hardcoded secrets, KeystoreCipher AES/GCM 12B IV
- Assistant bounding: MAX_ATTACHMENTS 5, MAX_TEXT_BYTES 5MB, MAX_IMAGE 25MB, MAX_DOCUMENT 10MB, 400 messages, 48k output, image edge 1568px — all bounded

## Open (from earlier + new)
- Device pass pending (editor, terminal, Git, 37-route capture) — Todo.md backlog — unverified without phone
- LICENSE — owner decision (GPL-2.0 PRoot, LGPL Sora)
- Full Gradle gate + lint + JVM tests on x86-64 — blocked here by AAPT2 arch
- Five-hour soak: PRoot/thermal/phantom-process (§6 P5) needs device; JVM loop + memory sampling feasible here

## Next steps (resuming)
1. Finish T1: resource leaks (ProcessLogs 2MB rotate, GuestCommand stderr temp delete, RuntimeProcess fd close), unbounded collections, DB/tx/index, API boundaries, config, duplication, logging
2. T2: component map + dependency direction + bottlenecks (PRoot 1s per Git, 8 managed processes, 32 phantom limit)
3. T3: start 5h controlled probe (fake spawner loops + periodic RSS) — record, never claim stability without validation
4. Update findings.md with new IDs, severities, evidence (now at a370462), then draft improvement roadmap
